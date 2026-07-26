package net.lusik21556.rankedsmp.inventory;

import net.lusik21556.rankedsmp.data.RankedSaveData;
import net.lusik21556.rankedsmp.gui.ExtraInventoryScreenHandler;
import net.lusik21556.rankedsmp.rank.RankManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port of InventoryManager.java. Ranks 1-10 unlock a personal "extra
 * inventory" (54 slots down to 15) rendered as a vanilla chest GUI so no
 * client-side mod is required. Contents are persisted in {@link RankedSaveData}.
 *
 * Unlike the original SQLite-backed plugin, storage here is an in-memory map
 * backed by the world's persistent state, so loads are synchronous - there's
 * no async round trip left to guard against.
 */
public class InventoryManager {
	private static final long COOLDOWN_MS = 3000L;

	private final RankManager rankManager;
	private final Map<UUID, SimpleInventory> extraInventories = new HashMap<>();
	private final Map<UUID, Long> cooldowns = new HashMap<>();

	public InventoryManager(RankManager rankManager) {
		this.rankManager = rankManager;
	}

	private int calculateInventorySize(int slots) {
		if (slots <= 0) {
			return 9;
		}
		return ((slots - 1) / 9 + 1) * 9;
	}

	public void openExtraInventory(ServerPlayerEntity player) {
		int rank = rankManager.getPlayerRank(player);
		if (!rankManager.hasExtraInventoryAccess(rank)) {
			player.sendMessage(Text.literal("You don't have access to extra inventory! (Requires Rank 10 or better)").formatted(Formatting.RED), false);
			return;
		}

		UUID uuid = player.getUuid();
		long now = System.currentTimeMillis();
		long last = cooldowns.getOrDefault(uuid, 0L);
		long elapsed = now - last;
		if (elapsed < COOLDOWN_MS) {
			long remaining = (COOLDOWN_MS - elapsed + 999L) / 1000L;
			player.sendMessage(Text.literal("Please wait " + remaining + "s before opening your extra inventory again.").formatted(Formatting.RED), false);
			return;
		}
		cooldowns.put(uuid, now);

		int slots = rankManager.getExtraInventorySlots(player);
		int requiredSize = calculateInventorySize(slots);
		SimpleInventory inv = getOrLoadInventory(player, slots);
		if (inv.size() != requiredSize) {
			inv = rebuild(inv, requiredSize, slots);
			extraInventories.put(uuid, inv);
		}
		openScreen(player, inv, slots);
	}

	/** Called shortly after join to warm the cache so the GUI opens instantly later. */
	public void loadPlayerInventory(ServerPlayerEntity player) {
		int rank = rankManager.getPlayerRank(player);
		if (!rankManager.hasExtraInventoryAccess(rank)) {
			return;
		}
		getOrLoadInventory(player, rankManager.getExtraInventorySlots(player));
	}

	private SimpleInventory getOrLoadInventory(ServerPlayerEntity player, int slots) {
		UUID uuid = player.getUuid();
		SimpleInventory inv = extraInventories.get(uuid);
		if (inv != null) {
			return inv;
		}
		RankedSaveData.StoredInventory stored = RankedSaveData.get(player.getEntityWorld().getServer()).getInventory(uuid);
		SimpleInventory fresh = new SimpleInventory(calculateInventorySize(slots));
		applyStored(fresh, stored, slots);
		extraInventories.put(uuid, fresh);
		return fresh;
	}

	private void applyStored(SimpleInventory inv, RankedSaveData.StoredInventory stored, int usableSlots) {
		if (stored != null) {
			for (RankedSaveData.SlotEntry entry : stored.items()) {
				if (entry.slot() >= 0 && entry.slot() < inv.size()) {
					inv.setStack(entry.slot(), entry.item().copy());
				}
			}
		}
		fillBarriers(inv, usableSlots);
	}

	private SimpleInventory rebuild(SimpleInventory old, int newSize, int usableSlots) {
		SimpleInventory fresh = new SimpleInventory(newSize);
		for (int i = 0; i < Math.min(usableSlots, old.size()); i++) {
			ItemStack item = old.getStack(i);
			if (!item.isEmpty() && !item.isOf(Items.BARRIER)) {
				fresh.setStack(i, item.copy());
			}
		}
		fillBarriers(fresh, usableSlots);
		return fresh;
	}

	private void fillBarriers(SimpleInventory inv, int usableSlots) {
		ItemStack barrier = new ItemStack(Items.BARRIER);
		barrier.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Locked Slot").formatted(Formatting.RED));
		barrier.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.literal("Upgrade your rank for more slots!").formatted(Formatting.GRAY))));
		for (int i = Math.max(usableSlots, 0); i < inv.size(); i++) {
			inv.setStack(i, barrier.copy());
		}
	}

	private void openScreen(ServerPlayerEntity player, SimpleInventory inv, int usableSlots) {
		int rows = inv.size() / 9;
		player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
				(syncId, playerInv, p) -> new ExtraInventoryScreenHandler(syncId, playerInv, inv, rows, usableSlots, () -> onScreenClosed(player)),
				Text.literal("Extra Inventory").formatted(Formatting.GOLD)
		));
	}

	private void onScreenClosed(ServerPlayerEntity player) {
		int rank = rankManager.getPlayerRank(player);
		if (!rankManager.hasExtraInventoryAccess(rank)) {
			removeExtraInventory(player);
		} else {
			saveExtraInventory(player);
		}
	}

	public boolean isExtraInventory(net.minecraft.inventory.Inventory inv) {
		return extraInventories.containsValue(inv);
	}

	public boolean hasLoadedExtraInventory(UUID uuid) {
		return extraInventories.containsKey(uuid);
	}

	public boolean hasLoadedExtraInventory(ServerPlayerEntity player) {
		return hasLoadedExtraInventory(player.getUuid());
	}

	public void saveExtraInventory(ServerPlayerEntity player) {
		SimpleInventory inv = extraInventories.get(player.getUuid());
		if (inv != null) {
			int slots = rankManager.getExtraInventorySlots(player);
			persist(player.getEntityWorld().getServer(), player.getUuid(), inv, slots);
		}
	}

	public void saveAllInventories(MinecraftServer server) {
		for (Map.Entry<UUID, SimpleInventory> entry : extraInventories.entrySet()) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			int slots = player != null ? rankManager.getExtraInventorySlots(player) : entry.getValue().size();
			persist(server, entry.getKey(), entry.getValue(), slots);
		}
	}

	private void persist(MinecraftServer server, UUID uuid, SimpleInventory inv, int slots) {
		List<RankedSaveData.SlotEntry> entries = new ArrayList<>();
		for (int i = 0; i < Math.min(slots, inv.size()); i++) {
			ItemStack item = inv.getStack(i);
			if (!item.isEmpty() && !item.isOf(Items.BARRIER)) {
				entries.add(new RankedSaveData.SlotEntry(i, item.copy()));
			}
		}
		RankedSaveData.get(server).setInventory(uuid, new RankedSaveData.StoredInventory(slots, entries));
	}

	public void clearExtraInventory(ServerPlayerEntity player) {
		SimpleInventory inv = extraInventories.get(player.getUuid());
		if (inv != null) {
			for (int i = 0; i < inv.size(); i++) {
				ItemStack item = inv.getStack(i);
				if (!item.isEmpty() && !item.isOf(Items.BARRIER)) {
					ItemScatterer.spawn(player.getEntityWorld(), player.getX(), player.getY(), player.getZ(), item);
				}
			}
			inv.clear();
		}
		RankedSaveData.get(player.getEntityWorld().getServer()).removeInventory(player.getUuid());
	}

	public void removeExtraInventory(ServerPlayerEntity player) {
		clearExtraInventory(player);
		extraInventories.remove(player.getUuid());
	}

	/** Drops items beyond the new (smaller) rank's slot count when a player wasn't online to see it happen live. */
	public void handleDeathDropsFallback(ServerPlayerEntity player, ServerWorld deathWorld, Vec3d deathPos, int newRank) {
		if (extraInventories.containsKey(player.getUuid())) {
			return;
		}
		int newSlots = rankManager.getSlotsForRank(newRank);
		RankedSaveData data = RankedSaveData.get(player.getEntityWorld().getServer());
		RankedSaveData.StoredInventory stored = data.getInventory(player.getUuid());
		if (stored == null) {
			return;
		}
		List<RankedSaveData.SlotEntry> kept = new ArrayList<>();
		for (RankedSaveData.SlotEntry entry : stored.items()) {
			if (entry.slot() >= newSlots) {
				ItemScatterer.spawn(deathWorld, deathPos.x, deathPos.y, deathPos.z, entry.item().copy());
			} else {
				kept.add(entry);
			}
		}
		if (newSlots <= 0) {
			data.removeInventory(player.getUuid());
		} else {
			data.setInventory(player.getUuid(), new RankedSaveData.StoredInventory(newSlots, kept));
		}
	}

	public void unloadPlayer(ServerPlayerEntity player) {
		saveExtraInventory(player);
		extraInventories.remove(player.getUuid());
		cooldowns.remove(player.getUuid());
	}

	public void updateExtraInventory(ServerPlayerEntity player, int oldRank, int newRank) {
		boolean hadAccess = rankManager.hasExtraInventoryAccess(oldRank);
		boolean hasAccess = rankManager.hasExtraInventoryAccess(newRank);

		if (hadAccess && !hasAccess) {
			player.sendMessage(Text.literal("You lost access to extra inventory! Items dropped.").formatted(Formatting.RED), false);
			removeExtraInventory(player);
		} else if (hadAccess) {
			int oldSlots = rankManager.getSlotsForRank(oldRank);
			int newSlots = rankManager.getSlotsForRank(newRank);
			if (newSlots < oldSlots) {
				resizeInventory(player, newSlots);
				player.sendMessage(Text.literal("Your extra inventory was resized to " + newSlots + " slots.").formatted(Formatting.YELLOW), false);
			} else if (newSlots > oldSlots) {
				resizeInventory(player, newSlots);
				player.sendMessage(Text.literal("Your extra inventory expanded to " + newSlots + " slots!").formatted(Formatting.GREEN), false);
			}
		} else if (hasAccess) {
			player.sendMessage(Text.literal("You gained access to extra inventory! Use /extrainventory").formatted(Formatting.GREEN), false);
		}
	}

	private void resizeInventory(ServerPlayerEntity player, int newSlots) {
		SimpleInventory oldInv = extraInventories.get(player.getUuid());
		if (oldInv == null) {
			return;
		}
		boolean wasOpen = player.currentScreenHandler instanceof ExtraInventoryScreenHandler handler
				&& handler.backingInventory() == oldInv;

		int newSize = calculateInventorySize(newSlots);
		SimpleInventory newInv = new SimpleInventory(newSize);
		for (int i = 0; i < Math.min(newSlots, oldInv.size()); i++) {
			ItemStack item = oldInv.getStack(i);
			if (!item.isEmpty() && !item.isOf(Items.BARRIER)) {
				newInv.setStack(i, item.copy());
			}
		}
		for (int i = newSlots; i < oldInv.size(); i++) {
			ItemStack item = oldInv.getStack(i);
			if (!item.isEmpty() && !item.isOf(Items.BARRIER)) {
				ItemScatterer.spawn(player.getEntityWorld(), player.getX(), player.getY(), player.getZ(), item);
			}
		}
		fillBarriers(newInv, newSlots);
		extraInventories.put(player.getUuid(), newInv);
		saveExtraInventory(player);
		if (wasOpen) {
			openScreen(player, newInv, newSlots);
		}
	}
}

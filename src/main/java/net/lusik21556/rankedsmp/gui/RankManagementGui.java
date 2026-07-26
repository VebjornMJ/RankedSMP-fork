package net.lusik21556.rankedsmp.gui;

import net.lusik21556.rankedsmp.data.RankedSaveData;
import net.lusik21556.rankedsmp.rank.RankManager;
import net.lusik21556.rankedsmp.util.Scheduler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port of RankManagementGUI.java: an admin-facing screen showing every
 * currently ranked player as a head, sorted by rank. Left-click selects a
 * player then a destination slot to move them there (swapping with whoever
 * is already there, or shifting the rest of the list when dropped on an
 * empty slot); right-click twice removes a player's rank.
 *
 * Note: the original plugin's "drop on an empty slot to shift" branch was
 * unreachable dead code there (its outer click-guard required the clicked
 * slot to already contain a player head), even though the item lore
 * advertised it. This port fixes that so left-clicking an empty slot while
 * a player is selected behaves as the tooltip describes.
 */
public class RankManagementGui {
	private final RankManager rankManager;
	private final Scheduler scheduler;
	private final Map<UUID, UUID> pendingRemoval = new HashMap<>();
	private final Map<UUID, RankedPlayer> selectedForMove = new HashMap<>();

	public RankManagementGui(RankManager rankManager, Scheduler scheduler) {
		this.rankManager = rankManager;
		this.scheduler = scheduler;
	}

	public void openGui(ServerPlayerEntity admin) {
		MinecraftServer server = admin.getEntityWorld().getServer();
		List<RankedPlayer> players = getRankedPlayers(server);
		if (players.isEmpty()) {
			admin.sendMessage(Text.literal("No players are currently ranked!").formatted(Formatting.RED), false);
			return;
		}
		int size = Math.min(54, ((players.size() - 1) / 9 + 1) * 9);
		int rows = size / 9;

		admin.openHandledScreen(new SimpleNamedScreenHandlerFactory(
				(syncId, inv, p) -> new RankManagementScreenHandler(syncId, inv, rows, this),
				Text.literal("Rank Management").formatted(Formatting.GOLD)
		));

		if (admin.currentScreenHandler instanceof RankManagementScreenHandler handler) {
			populate(server, handler, players, null);
		}
	}

	private void populate(MinecraftServer server, RankManagementScreenHandler handler, List<RankedPlayer> players, UUID selectedUuid) {
		int size = handler.getRows() * 9;
		for (int i = 0; i < size; i++) {
			if (i < players.size()) {
				RankedPlayer rp = players.get(i);
				handler.setSlotDisplay(i, createHead(server, rp, rp.uuid().equals(selectedUuid)));
			} else {
				handler.setSlotDisplay(i, ItemStack.EMPTY);
			}
		}
	}

	private void refresh(ServerPlayerEntity admin, UUID selectedUuid) {
		if (admin.currentScreenHandler instanceof RankManagementScreenHandler handler) {
			populate(admin.getEntityWorld().getServer(), handler, getRankedPlayers(admin.getEntityWorld().getServer()), selectedUuid);
		}
	}

	private ItemStack createHead(MinecraftServer server, RankedPlayer rp, boolean selected) {
		ItemStack head = new ItemStack(Items.PLAYER_HEAD);
		head.set(DataComponentTypes.PROFILE, ProfileComponent.ofDynamic(rp.uuid()));

		Text name = selected
				? Text.literal("✓#" + rp.rank() + " - ").formatted(Formatting.GREEN).append(Text.literal(rp.name()).formatted(Formatting.WHITE))
				: Text.literal("#" + rp.rank() + " - ").formatted(Formatting.YELLOW).append(Text.literal(rp.name()).formatted(Formatting.WHITE));
		head.set(DataComponentTypes.CUSTOM_NAME, name);

		boolean online = server.getPlayerManager().getPlayer(rp.uuid()) != null;
		List<Text> lore = new ArrayList<>();
		lore.add(Text.literal(""));
		lore.add(Text.literal("Rank: ").formatted(Formatting.GRAY).append(Text.literal("#" + rp.rank()).formatted(Formatting.YELLOW)));
		lore.add(Text.literal("Status: ").formatted(Formatting.GRAY).append(online
				? Text.literal("Online").formatted(Formatting.GREEN)
				: Text.literal("Offline").formatted(Formatting.RED)));
		lore.add(Text.literal(""));
		if (selected) {
			lore.add(Text.literal("Left-Click slot ").formatted(Formatting.GREEN).append(Text.literal("to move here").formatted(Formatting.GRAY)));
			lore.add(Text.literal("Right-Click ").formatted(Formatting.YELLOW).append(Text.literal("to cancel").formatted(Formatting.GRAY)));
		} else {
			lore.add(Text.literal("Left-Click ").formatted(Formatting.GOLD).append(Text.literal("to select for moving").formatted(Formatting.GRAY)));
			lore.add(Text.literal("Right-Click ").formatted(Formatting.RED).append(Text.literal("to remove rank").formatted(Formatting.GRAY)));
			lore.add(Text.literal("Right-Click Again ").formatted(Formatting.DARK_RED).append(Text.literal("to confirm").formatted(Formatting.GRAY)));
		}
		head.set(DataComponentTypes.LORE, new LoreComponent(lore));
		return head;
	}

	private List<RankedPlayer> getRankedPlayers(MinecraftServer server) {
		List<RankedPlayer> players = new ArrayList<>();
		for (Map.Entry<UUID, Integer> entry : RankedSaveData.get(server).getAllRanks().entrySet()) {
			if (entry.getValue() <= 0) {
				continue;
			}
			players.add(new RankedPlayer(entry.getKey(), entry.getValue(), resolveName(server, entry.getKey())));
		}
		players.sort(Comparator.comparingInt(RankedPlayer::rank));
		return players;
	}

	private String resolveName(MinecraftServer server, UUID uuid) {
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
		if (online != null) {
			return online.getGameProfile().name();
		}
		return server.getApiServices().nameToIdCache().getByUuid(uuid)
				.map(PlayerConfigEntry::name)
				.orElse(uuid.toString());
	}

	public void handleClick(ServerPlayerEntity admin, int slot, boolean isLeftClick) {
		MinecraftServer server = admin.getEntityWorld().getServer();
		List<RankedPlayer> players = getRankedPlayers(server);
		RankedPlayer clicked = slot < players.size() ? players.get(slot) : null;

		if (isLeftClick) {
			handleLeftClick(admin, server, slot, clicked, players);
		} else {
			handleRightClick(admin, server, clicked);
		}
	}

	private void handleLeftClick(ServerPlayerEntity admin, MinecraftServer server, int slot, RankedPlayer clicked, List<RankedPlayer> players) {
		RankedPlayer selected = selectedForMove.get(admin.getUuid());
		if (selected == null) {
			if (clicked == null) {
				return;
			}
			selectedForMove.put(admin.getUuid(), clicked);
			admin.sendMessage(Text.literal("Selected " + clicked.name() + " (#" + clicked.rank() + "). Left-click a slot to move, right-click to cancel.").formatted(Formatting.GREEN), false);
			refresh(admin, clicked.uuid());
			return;
		}

		if (clicked != null && clicked.uuid().equals(selected.uuid())) {
			selectedForMove.remove(admin.getUuid());
			admin.sendMessage(Text.literal("Movement cancelled.").formatted(Formatting.YELLOW), false);
			refresh(admin, null);
			return;
		}

		int fromRank = selected.rank();
		int toRank = slot + 1;

		if (clicked != null) {
			int targetRank = clicked.rank();
			setRankWithNotice(server, selected.uuid(), targetRank);
			setRankWithNotice(server, clicked.uuid(), fromRank);
			admin.sendMessage(Text.literal("Swapped ranks: " + selected.name() + " (#" + targetRank + ") <-> " + clicked.name() + " (#" + fromRank + ")").formatted(Formatting.GREEN), false);
		} else {
			for (RankedPlayer rp : players) {
				int currentRank = rp.rank();
				if (currentRank == fromRank) {
					continue;
				}
				if (fromRank < toRank) {
					if (currentRank <= fromRank || currentRank > toRank) {
						continue;
					}
					setRankSilent(server, rp.uuid(), currentRank - 1);
				} else {
					if (currentRank < toRank || currentRank >= fromRank) {
						continue;
					}
					setRankSilent(server, rp.uuid(), currentRank + 1);
				}
			}
			setRankWithNotice(server, selected.uuid(), toRank);
			admin.sendMessage(Text.literal("Moved " + selected.name() + " to rank #" + toRank).formatted(Formatting.GREEN), false);
		}

		selectedForMove.remove(admin.getUuid());
		scheduler.runLater(1, () -> refresh(admin, null));
	}

	private void handleRightClick(ServerPlayerEntity admin, MinecraftServer server, RankedPlayer clicked) {
		if (selectedForMove.containsKey(admin.getUuid())) {
			selectedForMove.remove(admin.getUuid());
			admin.sendMessage(Text.literal("Movement cancelled.").formatted(Formatting.YELLOW), false);
			refresh(admin, null);
			return;
		}
		if (clicked == null) {
			return;
		}

		UUID pending = pendingRemoval.get(admin.getUuid());
		if (pending != null && pending.equals(clicked.uuid())) {
			removeRankWithNotice(server, clicked.uuid());
			admin.sendMessage(Text.literal("Removed " + clicked.name() + " from ranked system!").formatted(Formatting.GREEN), false);
			pendingRemoval.remove(admin.getUuid());
			scheduler.runLater(1, () -> refresh(admin, null));
		} else {
			pendingRemoval.put(admin.getUuid(), clicked.uuid());
			admin.sendMessage(Text.literal("Right-click " + clicked.name() + " again to confirm removal!").formatted(Formatting.YELLOW), false);
			UUID targetUuid = clicked.uuid();
			scheduler.runLater(60, () -> {
				if (targetUuid.equals(pendingRemoval.get(admin.getUuid()))) {
					pendingRemoval.remove(admin.getUuid());
					admin.sendMessage(Text.literal("Removal cancelled.").formatted(Formatting.RED), false);
				}
			});
		}
	}

	private void setRankWithNotice(MinecraftServer server, UUID uuid, int rank) {
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
		if (online != null) {
			rankManager.setPlayerRank(online, rank);
			online.sendMessage(Text.literal("Your rank has been changed to #" + rank + " by an admin!").formatted(Formatting.YELLOW), false);
		} else {
			rankManager.setOfflineRank(server, uuid, rank);
		}
	}

	private void setRankSilent(MinecraftServer server, UUID uuid, int rank) {
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
		if (online != null) {
			rankManager.setPlayerRank(online, rank);
		} else {
			rankManager.setOfflineRank(server, uuid, rank);
		}
	}

	private void removeRankWithNotice(MinecraftServer server, UUID uuid) {
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
		if (online != null) {
			rankManager.removePlayerRank(online);
			online.sendMessage(Text.literal("Your rank has been removed by an admin!").formatted(Formatting.YELLOW), false);
		} else {
			rankManager.removeOfflineRank(server, uuid);
		}
	}

	public void onClosed(ServerPlayerEntity admin) {
		pendingRemoval.remove(admin.getUuid());
		selectedForMove.remove(admin.getUuid());
	}

	private record RankedPlayer(UUID uuid, int rank, String name) {
	}
}

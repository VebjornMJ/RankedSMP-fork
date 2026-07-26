package net.lusik21556.rankedsmp.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-world persisted RankedSMP data: player ranks and stored "extra inventory"
 * contents. This is the Fabric-native replacement for the original plugin's
 * config.yml player map + SQLite `extra_inventories` table - it rides along
 * with the world save (region files) instead of needing a bundled SQLite
 * driver.
 */
public class RankedSaveData extends PersistentState {
	public static final PersistentStateType<RankedSaveData> TYPE = new PersistentStateType<>(
			"rankedsmp_data",
			RankedSaveData::new,
			createCodec(),
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE
	);

	private final Map<UUID, Integer> ranks = new HashMap<>();
	private final Map<UUID, StoredInventory> inventories = new HashMap<>();

	public static RankedSaveData get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
	}

	// ----- ranks -----

	public int getRank(UUID uuid) {
		return ranks.getOrDefault(uuid, 0);
	}

	public void setRank(UUID uuid, int rank) {
		ranks.put(uuid, rank);
		markDirty();
	}

	public void removeRank(UUID uuid) {
		if (ranks.remove(uuid) != null) {
			markDirty();
		}
	}

	public Map<UUID, Integer> getAllRanks() {
		return new HashMap<>(ranks);
	}

	public boolean isRankTaken(int rank) {
		return ranks.containsValue(rank);
	}

	public UUID getPlayerWithRank(int rank) {
		for (Map.Entry<UUID, Integer> entry : ranks.entrySet()) {
			if (entry.getValue() == rank) {
				return entry.getKey();
			}
		}
		return null;
	}

	// ----- extra inventories -----

	public StoredInventory getInventory(UUID uuid) {
		return inventories.get(uuid);
	}

	public void setInventory(UUID uuid, StoredInventory inventory) {
		inventories.put(uuid, inventory);
		markDirty();
	}

	public void removeInventory(UUID uuid) {
		if (inventories.remove(uuid) != null) {
			markDirty();
		}
	}

	public record StoredInventory(int slots, List<SlotEntry> items) {
		public static final Codec<StoredInventory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("slots").forGetter(StoredInventory::slots),
				SlotEntry.CODEC.listOf().fieldOf("items").forGetter(StoredInventory::items)
		).apply(instance, StoredInventory::new));
	}

	public record SlotEntry(int slot, ItemStack item) {
		public static final Codec<SlotEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("slot").forGetter(SlotEntry::slot),
				ItemStack.CODEC.fieldOf("item").forGetter(SlotEntry::item)
		).apply(instance, SlotEntry::new));
	}

	private record RankEntry(UUID uuid, int rank) {
		static final Codec<RankEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Uuids.STRING_CODEC.fieldOf("uuid").forGetter(RankEntry::uuid),
				Codec.INT.fieldOf("rank").forGetter(RankEntry::rank)
		).apply(instance, RankEntry::new));
	}

	private record InventoryEntry(UUID uuid, StoredInventory inventory) {
		static final Codec<InventoryEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Uuids.STRING_CODEC.fieldOf("uuid").forGetter(InventoryEntry::uuid),
				StoredInventory.CODEC.fieldOf("inventory").forGetter(InventoryEntry::inventory)
		).apply(instance, InventoryEntry::new));
	}

	private static Codec<RankedSaveData> createCodec() {
		return RecordCodecBuilder.create(instance -> instance.group(
				RankEntry.CODEC.listOf().fieldOf("ranks").forGetter(RankedSaveData::ranksToList),
				InventoryEntry.CODEC.listOf().fieldOf("inventories").forGetter(RankedSaveData::inventoriesToList)
		).apply(instance, RankedSaveData::fromLists));
	}

	private List<RankEntry> ranksToList() {
		List<RankEntry> list = new ArrayList<>();
		for (Map.Entry<UUID, Integer> entry : ranks.entrySet()) {
			list.add(new RankEntry(entry.getKey(), entry.getValue()));
		}
		return list;
	}

	private List<InventoryEntry> inventoriesToList() {
		List<InventoryEntry> list = new ArrayList<>();
		for (Map.Entry<UUID, StoredInventory> entry : inventories.entrySet()) {
			list.add(new InventoryEntry(entry.getKey(), entry.getValue()));
		}
		return list;
	}

	private static RankedSaveData fromLists(List<RankEntry> ranksList, List<InventoryEntry> inventoriesList) {
		RankedSaveData data = new RankedSaveData();
		for (RankEntry entry : ranksList) {
			data.ranks.put(entry.uuid(), entry.rank());
		}
		for (InventoryEntry entry : inventoriesList) {
			data.inventories.put(entry.uuid(), entry.inventory());
		}
		return data;
	}
}

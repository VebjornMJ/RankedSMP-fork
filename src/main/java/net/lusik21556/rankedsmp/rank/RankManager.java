package net.lusik21556.rankedsmp.rank;

import net.lusik21556.rankedsmp.RankedSMP;
import net.lusik21556.rankedsmp.config.RankedConfig;
import net.lusik21556.rankedsmp.data.RankedSaveData;
import net.lusik21556.rankedsmp.inventory.InventoryManager;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Port of RankManager.java from the original Paper plugin. Ranks range 1-20;
 * rank 1 is the strongest (20 hearts, biggest inventory, longest potions,
 * fastest XP), rank 20 the weakest (10.5 hearts). Unranked players (rank 0)
 * play vanilla.
 */
public class RankManager {
	private final RankedConfig config;
	private InventoryManager inventoryManager;

	public RankManager(RankedConfig config) {
		this.config = config;
	}

	public void setInventoryManager(InventoryManager inventoryManager) {
		this.inventoryManager = inventoryManager;
	}

	public int getPlayerRank(ServerPlayerEntity player) {
		return RankedSaveData.get(player.getEntityWorld().getServer()).getRank(player.getUuid());
	}

	public void setPlayerRank(ServerPlayerEntity player, int rank) {
		MinecraftServer server = player.getEntityWorld().getServer();
		RankedSaveData data = RankedSaveData.get(server);
		int oldRank = data.getRank(player.getUuid());
		data.setRank(player.getUuid(), rank);
		updatePlayerDisplay(server, player, rank);
		updatePlayerHealth(player, rank);
		if (inventoryManager != null) {
			inventoryManager.updateExtraInventory(player, oldRank, rank);
		}
	}

	public void removePlayerRank(ServerPlayerEntity player) {
		MinecraftServer server = player.getEntityWorld().getServer();
		RankedSaveData data = RankedSaveData.get(server);
		int oldRank = data.getRank(player.getUuid());
		data.removeRank(player.getUuid());
		updatePlayerDisplay(server, player, 0);
		resetPlayerHealth(player);
		if (inventoryManager != null) {
			inventoryManager.updateExtraInventory(player, oldRank, 0);
		}
	}

	/** Sets a rank for a UUID that may not currently be online. */
	public void setOfflineRank(MinecraftServer server, UUID uuid, int rank) {
		RankedSaveData.get(server).setRank(uuid, rank);
	}

	public void removeOfflineRank(MinecraftServer server, UUID uuid) {
		RankedSaveData.get(server).removeRank(uuid);
	}

	public void updatePlayerDisplay(MinecraftServer server, ServerPlayerEntity player, int rank) {
		updateScoreboardTeam(server, player, rank);
	}

	private void updateScoreboardTeam(MinecraftServer server, ServerPlayerEntity player, int rank) {
		Scoreboard scoreboard = server.getScoreboard();
		removeFromTeam(server, player);

		String teamName;
		Text prefix;
		if (rank > 0) {
			teamName = String.format("rank_%02d", rank);
			prefix = Text.literal("[#" + rank + "] ").formatted(Formatting.YELLOW);
		} else {
			teamName = "rank_99_unranked";
			prefix = Text.literal("[").formatted(Formatting.YELLOW)
					.append(Text.literal("UNRANKED").formatted(Formatting.DARK_GRAY))
					.append(Text.literal("] ").formatted(Formatting.YELLOW));
		}

		Team team = scoreboard.getTeam(teamName);
		if (team == null) {
			team = scoreboard.addTeam(teamName);
		}
		team.setPrefix(prefix);
		team.setShowFriendlyInvisibles(false);
		scoreboard.addScoreHolderToTeam(player.getGameProfile().name(), team);
	}

	public void removeFromTeam(MinecraftServer server, ServerPlayerEntity player) {
		Scoreboard scoreboard = server.getScoreboard();
		String name = player.getGameProfile().name();
		Team team = scoreboard.getScoreHolderTeam(name);
		if (team != null) {
			scoreboard.removeScoreHolderFromTeam(name, team);
		}
	}

	/** Re-applies display/health/rank state for every currently online player. Used on join and /rankedsmp reload. */
	public void refreshAllOnlinePlayers(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			int rank = getPlayerRank(player);
			updatePlayerDisplay(server, player, rank);
			updatePlayerHealth(player, rank);
		}
	}

	public void startRankedSMP(MinecraftServer server) {
		List<ServerPlayerEntity> onlinePlayers = new ArrayList<>(server.getPlayerManager().getPlayerList());
		if (onlinePlayers.isEmpty()) {
			return;
		}

		RankedSaveData data = RankedSaveData.get(server);
		for (UUID uuid : new ArrayList<>(data.getAllRanks().keySet())) {
			data.removeRank(uuid);
		}
		for (ServerPlayerEntity player : onlinePlayers) {
			removeFromTeam(server, player);
		}

		int maxPlayers = Math.min(20, onlinePlayers.size());
		List<Integer> availableRanks = new ArrayList<>();
		for (int i = 1; i <= maxPlayers; i++) {
			availableRanks.add(i);
		}
		Collections.shuffle(availableRanks);

		for (int i = 0; i < maxPlayers; i++) {
			ServerPlayerEntity player = onlinePlayers.get(i);
			int rank = availableRanks.get(i);
			setPlayerRank(player, rank);
			player.sendMessage(Text.literal("You have been assigned rank ")
					.formatted(Formatting.GREEN)
					.append(Text.literal("#" + rank).formatted(Formatting.YELLOW))
					.append(Text.literal("!").formatted(Formatting.GREEN)), false);
		}

		int unranked = onlinePlayers.size() - maxPlayers;
		if (unranked > 0) {
			for (int i = maxPlayers; i < onlinePlayers.size(); i++) {
				onlinePlayers.get(i).sendMessage(Text.literal("No available Ranks! You are unranked.").formatted(Formatting.RED), false);
			}
			server.getPlayerManager().broadcast(Text.literal("Ranked SMP has begun! " + maxPlayers + " players ranked, " + unranked + " unranked.").formatted(Formatting.GOLD), false);
		} else {
			server.getPlayerManager().broadcast(Text.literal("Ranked SMP has begun! Ranks have been assigned.").formatted(Formatting.GOLD), false);
		}
	}

	public void swapRanks(ServerPlayerEntity killer, ServerPlayerEntity victim) {
		if (!config.isRankStealingEnabled()) {
			return;
		}
		int killerRank = getPlayerRank(killer);
		int victimRank = getPlayerRank(victim);
		if (victimRank == 0) {
			return;
		}
		if (killerRank == 0 || killerRank > victimRank) {
			setPlayerRank(killer, victimRank);
			if (killerRank > 0) {
				setPlayerRank(victim, killerRank);
			} else {
				removePlayerRank(victim);
			}

			killer.sendMessage(Text.literal("You stole " + victim.getGameProfile().name() + "'s rank! You are now ")
					.formatted(Formatting.GOLD)
					.append(Text.literal("#" + victimRank).formatted(Formatting.YELLOW)), false);
			if (killerRank > 0) {
				victim.sendMessage(Text.literal("Your rank was stolen by " + killer.getGameProfile().name() + ". You are now ")
						.formatted(Formatting.RED)
						.append(Text.literal("#" + killerRank).formatted(Formatting.YELLOW)), false);
			} else {
				victim.sendMessage(Text.literal("Your rank was stolen by " + killer.getGameProfile().name() + ". You are now ")
						.formatted(Formatting.RED)
						.append(Text.literal("UNRANKED").formatted(Formatting.DARK_GRAY)), false);
			}
		}
	}

	public void updatePlayerHealth(ServerPlayerEntity player, int rank) {
		if (player.isDead()) {
			return;
		}
		if (rank == 0) {
			resetPlayerHealth(player);
			return;
		}
		double hearts = 10.5 + (20 - rank) * 0.5;
		double maxHealth = hearts * 2.0;
		EntityAttributeInstance attribute = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
		if (attribute != null) {
			attribute.setBaseValue(maxHealth);
		}
		player.setHealth((float) Math.min(player.getHealth(), maxHealth));
	}

	public void resetPlayerHealth(ServerPlayerEntity player) {
		if (player.isDead()) {
			return;
		}
		EntityAttributeInstance attribute = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
		if (attribute != null) {
			attribute.setBaseValue(20.0);
		}
		player.setHealth(20.0f);
	}

	public boolean isRankTaken(MinecraftServer server, int rank) {
		return RankedSaveData.get(server).isRankTaken(rank);
	}

	public UUID getPlayerWithRank(MinecraftServer server, int rank) {
		return RankedSaveData.get(server).getPlayerWithRank(rank);
	}

	public double getPotionDurationMultiplier(ServerPlayerEntity player) {
		int rank = getPlayerRank(player);
		if (rank == 0) {
			return 1.0;
		}
		double minMultiplier = 1.05;
		double maxMultiplier = 2.0;
		return minMultiplier + (21 - rank) / 20.0 * (maxMultiplier - minMultiplier);
	}

	public double getXPMultiplier(ServerPlayerEntity player) {
		int rank = getPlayerRank(player);
		if (rank == 0) {
			return 1.0;
		}
		double minMultiplier = 1.1;
		double maxMultiplier = 3.0;
		return minMultiplier + (21 - rank) / 20.0 * (maxMultiplier - minMultiplier);
	}

	public int getExtraInventorySlots(ServerPlayerEntity player) {
		return getSlotsForRank(getPlayerRank(player));
	}

	public int getSlotsForRank(int rank) {
		if (rank == 0 || rank > 10) {
			return 0;
		}
		if (rank <= 2) {
			return 54;
		}
		return 65 - rank * 5;
	}

	public boolean hasExtraInventoryAccess(int rank) {
		return rank > 0 && rank <= 10;
	}
}

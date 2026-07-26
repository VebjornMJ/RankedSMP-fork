package net.lusik21556.rankedsmp.listener;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.lusik21556.rankedsmp.inventory.InventoryManager;
import net.lusik21556.rankedsmp.rank.RankManager;
import net.lusik21556.rankedsmp.util.Scheduler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Wires up the equivalents of PlayerListener.java and part of
 * InventoryListener.java: join/leave display & health refresh, rank-stealing
 * on PvP kills, and post-respawn health re-application.
 */
public class RankedPlayerEvents {
	private final RankManager rankManager;
	private final InventoryManager inventoryManager;
	private final Scheduler scheduler;

	public RankedPlayerEvents(RankManager rankManager, InventoryManager inventoryManager, Scheduler scheduler) {
		this.rankManager = rankManager;
		this.inventoryManager = inventoryManager;
		this.scheduler = scheduler;
	}

	public void register() {
		ServerPlayerEvents.JOIN.register(this::onJoin);
		ServerPlayerEvents.LEAVE.register(this::onLeave);
		ServerPlayerEvents.AFTER_RESPAWN.register(this::onRespawn);
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(this::onKilled);
	}

	private void onJoin(ServerPlayerEntity player) {
		rankManager.updatePlayerDisplay(player.getEntityWorld().getServer(), player, rankManager.getPlayerRank(player));
		rankManager.refreshAllOnlinePlayers(player.getEntityWorld().getServer());
		scheduler.runLater(20, () -> {
			if (player.isAlive()) {
				inventoryManager.loadPlayerInventory(player);
			}
		});
	}

	private void onLeave(ServerPlayerEntity player) {
		rankManager.removeFromTeam(player.getEntityWorld().getServer(), player);
		int rank = rankManager.getPlayerRank(player);
		if (!rankManager.hasExtraInventoryAccess(rank)) {
			inventoryManager.clearExtraInventory(player);
		} else {
			inventoryManager.unloadPlayer(player);
		}
	}

	private void onRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
		scheduler.runLater(5, () -> {
			if (newPlayer.isAlive()) {
				rankManager.updatePlayerHealth(newPlayer, rankManager.getPlayerRank(newPlayer));
			}
		});
	}

	private void onKilled(net.minecraft.server.world.ServerWorld world, Entity entity, LivingEntity killed, net.minecraft.entity.damage.DamageSource damageSource) {
		if (!(entity instanceof ServerPlayerEntity killer)) {
			return;
		}
		if (!(killed instanceof ServerPlayerEntity victim)) {
			return;
		}
		if (killer.getUuid().equals(victim.getUuid())) {
			return;
		}

		int oldRank = rankManager.getPlayerRank(victim);
		boolean hadLoadedExtraInventory = inventoryManager.hasLoadedExtraInventory(victim);
		var deathWorld = victim.getEntityWorld();
		var deathPos = victim.getEntityPos();

		rankManager.swapRanks(killer, victim);

		int newRank = rankManager.getPlayerRank(victim);
		int oldSlots = rankManager.getSlotsForRank(oldRank);
		int newSlots = rankManager.getSlotsForRank(newRank);
		if (!hadLoadedExtraInventory && newSlots < oldSlots) {
			inventoryManager.handleDeathDropsFallback(victim, deathWorld, deathPos, newRank);
		}
	}
}

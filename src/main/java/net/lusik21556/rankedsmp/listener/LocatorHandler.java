package net.lusik21556.rankedsmp.listener;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Port of LocatorListener.java: carrying a Dragon Egg extends how far your
 * waypoint broadcasts and how far you can see other players' waypoints (the
 * vanilla locator-map feature introduced in 1.21.6, which this port always
 * targets, so the original's "1.21.6+ only" version gate is gone).
 *
 * The original polled on inventory click/close/drop/pickup events; Fabric
 * has no generic "inventory changed" event, so this instead polls every 10
 * ticks (0.5s), which is imperceptible for a locator toggle.
 */
public class LocatorHandler {
	private static final double MAX_RANGE = 6.0E7;
	private static final int POLL_INTERVAL_TICKS = 10;

	private final Set<UUID> playersWithEgg = new HashSet<>();
	private int ticksUntilPoll = 0;

	public void register() {
		ServerPlayerEvents.JOIN.register(this::onJoin);
		ServerPlayerEvents.LEAVE.register(player -> playersWithEgg.remove(player.getUuid()));
		ServerTickEvents.END_SERVER_TICK.register(this::onTick);
	}

	private void onJoin(ServerPlayerEntity player) {
		boolean hasEgg = hasEggInInventory(player);
		setAttributes(player, hasEgg);
		if (hasEgg) {
			playersWithEgg.add(player.getUuid());
		}
	}

	private void onTick(MinecraftServer server) {
		if (++ticksUntilPoll < POLL_INTERVAL_TICKS) {
			return;
		}
		ticksUntilPoll = 0;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			boolean hasEgg = hasEggInInventory(player);
			boolean hadEgg = playersWithEgg.contains(player.getUuid());
			if (hasEgg == hadEgg) {
				continue;
			}
			if (hasEgg) {
				playersWithEgg.add(player.getUuid());
			} else {
				playersWithEgg.remove(player.getUuid());
			}
			setAttributes(player, hasEgg);
		}
	}

	private boolean hasEggInInventory(ServerPlayerEntity player) {
		return player.getInventory().count(Items.DRAGON_EGG) > 0;
	}

	private void setAttributes(ServerPlayerEntity player, boolean hasEgg) {
		EntityAttributeInstance transmit = player.getAttributeInstance(EntityAttributes.WAYPOINT_TRANSMIT_RANGE);
		EntityAttributeInstance receive = player.getAttributeInstance(EntityAttributes.WAYPOINT_RECEIVE_RANGE);
		if (transmit != null) {
			transmit.setBaseValue(MAX_RANGE);
		}
		if (receive != null) {
			receive.setBaseValue(hasEgg ? MAX_RANGE : 0.0);
		}
	}
}

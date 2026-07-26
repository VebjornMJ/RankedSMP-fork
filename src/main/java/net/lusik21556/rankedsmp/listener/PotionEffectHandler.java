package net.lusik21556.rankedsmp.listener;

import net.fabricmc.fabric.api.entity.event.v1.effect.ServerMobEffectEvents;
import net.lusik21556.rankedsmp.rank.RankManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Port of PotionListener.java: beneficial potion effects last longer the
 * higher a player's rank is. Negative effects are left untouched so ranked
 * PvP doesn't accidentally get easier to survive by drinking poison.
 *
 * Fabric API's mob-effect events don't expose a "this was re-applied by the
 * mod itself" cause the way Bukkit's Cause.PLUGIN did, so a small per-player
 * bypass set stands in for that reentrancy guard.
 */
public class PotionEffectHandler {
	private final RankManager rankManager;
	private final Set<UUID> bypass = new HashSet<>();
	private static final Set<RegistryEntry<net.minecraft.entity.effect.StatusEffect>> NEGATIVE_EFFECTS = Set.of(
			StatusEffects.SLOWNESS,
			StatusEffects.SLOW_FALLING,
			StatusEffects.MINING_FATIGUE,
			StatusEffects.NAUSEA,
			StatusEffects.BLINDNESS,
			StatusEffects.HUNGER,
			StatusEffects.WEAKNESS,
			StatusEffects.POISON,
			StatusEffects.WITHER,
			StatusEffects.LEVITATION,
			StatusEffects.UNLUCK,
			StatusEffects.DARKNESS,
			StatusEffects.BAD_OMEN
	);

	public PotionEffectHandler(RankManager rankManager) {
		this.rankManager = rankManager;
	}

	public void register() {
		ServerMobEffectEvents.ALLOW_ADD.register(this::onAllowAdd);
	}

	private boolean onAllowAdd(StatusEffectInstance effect, LivingEntity entity, net.fabricmc.fabric.api.entity.event.v1.effect.EffectEventContext context) {
		if (!(entity instanceof ServerPlayerEntity player)) {
			return true;
		}
		UUID uuid = player.getUuid();
		if (bypass.remove(uuid)) {
			return true;
		}
		if (NEGATIVE_EFFECTS.contains(effect.getEffectType())) {
			return true;
		}
		int rank = rankManager.getPlayerRank(player);
		if (rank == 0) {
			return true;
		}

		double multiplier = rankManager.getPotionDurationMultiplier(player);
		int newDuration = (int) (effect.getDuration() * multiplier);
		StatusEffectInstance extended = new StatusEffectInstance(effect.getEffectType(), newDuration, effect.getAmplifier(),
				effect.isAmbient(), effect.shouldShowParticles(), effect.shouldShowIcon());

		bypass.add(uuid);
		player.addStatusEffect(extended);
		bypass.remove(uuid);
		return false;
	}
}

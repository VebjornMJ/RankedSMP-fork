package net.lusik21556.rankedsmp.item;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.lusik21556.rankedsmp.util.Scheduler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Port of HierarchyHammerListener.java: the HierArchy Hammer's "SENTENCING"
 * dash and "VERDICT" combo finisher. Right-click launches the wielder
 * forward and starts a particle trail; landing consecutive falling
 * mace-smash hits within the window builds a combo, and the 4th hit
 * unleashes an execution (levitate, hold, then a burst of damage/particles)
 * instead of dealing its normal hit.
 */
public class HierarchyHammerHandler {
	private static final double DASH_SPEED = 2.2;
	private static final double DASH_UP = 0.75;
	private static final long MISS_COOLDOWN_MS = 10_000L;
	private static final long VERDICT_COOLDOWN_MS = 30_000L;

	private final Scheduler scheduler;

	private final Map<UUID, Long> dashCooldowns = new HashMap<>();
	private final Map<UUID, Integer> hitCounter = new HashMap<>();
	private final Set<UUID> sentencingActive = new HashSet<>();
	private final Map<UUID, Long> sentencingStartTime = new HashMap<>();
	private final Map<UUID, Integer> dashCharges = new HashMap<>();
	private final Map<UUID, Long> lastHitTime = new HashMap<>();
	private final Set<UUID> missScheduled = new HashSet<>();
	private final Map<UUID, TrailState> activeTrails = new HashMap<>();

	public HierarchyHammerHandler(Scheduler scheduler) {
		this.scheduler = scheduler;
	}

	public void register() {
		UseItemCallback.EVENT.register(this::onUseItem);
		AttackEntityCallback.EVENT.register(this::onAttackEntity);
		ServerPlayerEvents.LEAVE.register(player -> resetAll(player.getUuid()));
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(this::checkMisses);
	}

	private ActionResult onUseItem(net.minecraft.entity.player.PlayerEntity playerEntity, World world, Hand hand) {
		if (!(playerEntity instanceof ServerPlayerEntity player) || world.isClient()) {
			return ActionResult.PASS;
		}
		ItemStack item = player.getStackInHand(hand);
		if (!HierarchyHammer.isHierarchyHammer(item)) {
			return ActionResult.PASS;
		}

		UUID uuid = player.getUuid();
		long now = System.currentTimeMillis();
		Long cooldownUntil = dashCooldowns.get(uuid);
		if (cooldownUntil != null && now < cooldownUntil) {
			return ActionResult.PASS;
		}

		if (sentencingActive.contains(uuid)) {
			int charges = dashCharges.getOrDefault(uuid, 0);
			if (charges <= 0) {
				return ActionResult.PASS;
			}
			dashCharges.put(uuid, charges - 1);
			performMiniDash(player);
			return ActionResult.SUCCESS;
		}

		sentencingActive.add(uuid);
		sentencingStartTime.put(uuid, now);
		lastHitTime.put(uuid, now);
		hitCounter.put(uuid, 0);
		dashCharges.put(uuid, 0);

		ServerWorld serverWorld = (ServerWorld) world;
		serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_BREEZE_DEFLECT, SoundCategory.PLAYERS, 0.75f, 0.7f);
		Vec3d direction = player.getRotationVector();
		player.setVelocity(direction.multiply(DASH_SPEED).add(0, DASH_UP - direction.y * DASH_SPEED, 0));
		player.velocityDirty = true;
		startDashTrail(player);
		return ActionResult.SUCCESS;
	}

	private ActionResult onAttackEntity(net.minecraft.entity.player.PlayerEntity playerEntity, World world, Hand hand, Entity entity, net.minecraft.util.hit.EntityHitResult hitResult) {
		if (!(playerEntity instanceof ServerPlayerEntity attacker) || world.isClient()) {
			return ActionResult.PASS;
		}
		if (!(entity instanceof ServerPlayerEntity victim)) {
			return ActionResult.PASS;
		}
		ItemStack item = attacker.getStackInHand(hand);
		if (!HierarchyHammer.isHierarchyHammer(item)) {
			return ActionResult.PASS;
		}
		UUID uuid = attacker.getUuid();
		if (!sentencingActive.contains(uuid)) {
			return ActionResult.PASS;
		}
		if (!(attacker.fallDistance > 1.5)) {
			return ActionResult.PASS;
		}

		int hits = hitCounter.getOrDefault(uuid, 0) + 1;
		hitCounter.put(uuid, hits);
		lastHitTime.put(uuid, System.currentTimeMillis());

		if (hits < 4) {
			dashCharges.merge(uuid, 1, Integer::sum);
			ServerWorld serverWorld = (ServerWorld) world;
			serverWorld.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.5f, 1.0f);
			attacker.sendMessage(Text.literal(hits + " / 3").formatted(net.minecraft.util.Formatting.GOLD), true);
			return ActionResult.PASS;
		}

		executeVerdict(attacker, victim);
		resetSentencing(uuid);
		dashCooldowns.put(uuid, System.currentTimeMillis() + VERDICT_COOLDOWN_MS);
		attacker.getItemCooldownManager().set(item, 600);
		return ActionResult.FAIL;
	}

	private void performMiniDash(ServerPlayerEntity player) {
		lastHitTime.put(player.getUuid(), System.currentTimeMillis());
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		Vec3d pos = player.getEntityPos();
		world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_BREEZE_DEFLECT, SoundCategory.PLAYERS, 0.75f, 0.7f);
		Vec3d direction = player.getRotationVector();
		player.setVelocity(direction.multiply(DASH_SPEED).add(0, DASH_UP - direction.y * DASH_SPEED, 0));
		player.velocityDirty = true;
		spawnRotatingCircle(world, pos.add(0, 0.1, 0), 0.0);
		startDashTrail(player);
	}

	private void resetSentencing(UUID uuid) {
		sentencingActive.remove(uuid);
		hitCounter.remove(uuid);
		sentencingStartTime.remove(uuid);
		dashCharges.remove(uuid);
		lastHitTime.remove(uuid);
		activeTrails.remove(uuid);
	}

	private void resetAll(UUID uuid) {
		dashCooldowns.remove(uuid);
		missScheduled.remove(uuid);
		resetSentencing(uuid);
	}

	private void checkMisses(MinecraftServer server) {
		if (sentencingActive.isEmpty()) {
			return;
		}
		for (UUID uuid : new ArrayList<>(sentencingActive)) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
			if (player == null) {
				resetSentencing(uuid);
				continue;
			}
			if (!player.isOnGround()) {
				continue;
			}
			long now = System.currentTimeMillis();
			if (now - sentencingStartTime.getOrDefault(uuid, now) < 1000L) {
				continue;
			}
			if (now - lastHitTime.getOrDefault(uuid, 0L) < 1000L) {
				continue;
			}
			if (missScheduled.contains(uuid)) {
				continue;
			}
			missScheduled.add(uuid);
			scheduler.runLater(10, () -> {
				missScheduled.remove(uuid);
				if (!sentencingActive.contains(uuid)) {
					return;
				}
				ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
				if (p == null || !p.isOnGround()) {
					return;
				}
				if (System.currentTimeMillis() - lastHitTime.getOrDefault(uuid, 0L) < 1000L) {
					return;
				}
				if (hitCounter.getOrDefault(uuid, 0) >= 4) {
					return;
				}
				applyMissCooldown(p);
			});
		}
	}

	private void applyMissCooldown(ServerPlayerEntity player) {
		UUID uuid = player.getUuid();
		dashCooldowns.put(uuid, System.currentTimeMillis() + MISS_COOLDOWN_MS);
		resetSentencing(uuid);
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.5f, 1.1f);
		player.sendMessage(Text.literal("You missed, now on cooldown for 10s").formatted(net.minecraft.util.Formatting.GOLD), true);
		ItemStack held = player.getMainHandStack();
		if (HierarchyHammer.isHierarchyHammer(held)) {
			player.getItemCooldownManager().set(held, 200);
		}
	}

	private void executeVerdict(ServerPlayerEntity attacker, ServerPlayerEntity victim) {
		ServerWorld world = victim.getEntityWorld();
		double damage = 40.0;
		StatusEffectInstance resistance = victim.getStatusEffect(StatusEffects.RESISTANCE);
		if (resistance != null) {
			damage = Math.max(0.0, damage * (1.0 - (resistance.getAmplifier() + 1) * 0.2));
		}
		double finalDamage = damage;

		victim.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 60, 0, false, false));

		scheduler.runLater(60, () -> {
			if (!victim.isAlive()) {
				return;
			}
			victim.removeStatusEffect(StatusEffects.LEVITATION);
			victim.setNoGravity(true);
			victim.setVelocity(Vec3d.ZERO);
			victim.velocityDirty = true;

			Vec3d pos = victim.getEntityPos();
			ServerWorld raisedWorld = victim.getEntityWorld();
			raisedWorld.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
			raisedWorld.spawnParticles(ParticleTypes.GUST_EMITTER_LARGE, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
			raisedWorld.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.5f, 1.0f);
			raisedWorld.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_ENDER_DRAGON_AMBIENT, SoundCategory.PLAYERS, 0.2f, 1.0f);

			for (int j = 0; j < 48; j++) {
				double phi = Math.acos(1.0 - 2.0 * (j + 0.5) / 48.0);
				double theta = Math.PI * (1.0 + Math.sqrt(5.0)) * j;
				double dx = Math.sin(phi) * Math.cos(theta);
				double dy = Math.cos(phi);
				double dz = Math.sin(phi) * Math.sin(theta);
				raisedWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 0, dx, dy, dz, 0.6);
			}

			victim.timeUntilRegen = 0;
			victim.damage(raisedWorld, raisedWorld.getDamageSources().playerAttack(attacker), (float) finalDamage);

			for (int i = 0; i < 36; i++) {
				double angle = Math.PI * 2 * i / 36.0;
				raisedWorld.spawnParticles(ParticleTypes.TRIAL_SPAWNER_DETECTION, pos.x + 2.0 * Math.cos(angle), pos.y, pos.z + 2.0 * Math.sin(angle), 1, 0.0, 0.0, 0.0, 0.0);
			}

			if (victim.isAlive()) {
				victim.setNoGravity(false);
			}
		});
	}

	private void startDashTrail(ServerPlayerEntity player) {
		TrailState state = new TrailState();
		activeTrails.put(player.getUuid(), state);
		tickTrail(player, state);
	}

	private void tickTrail(ServerPlayerEntity player, TrailState state) {
		if (activeTrails.get(player.getUuid()) != state) {
			return;
		}
		if (!player.isAlive()) {
			activeTrails.remove(player.getUuid());
			return;
		}
		if (player.isOnGround() && state.ticks > 5) {
			activeTrails.remove(player.getUuid());
			return;
		}

		ServerWorld world = player.getEntityWorld();
		Vec3d current = player.getEntityPos();
		world.spawnParticles(new DustParticleEffect(packRgb(242, 188, 69), 1.2f), current.x, current.y + 1.0, current.z, 3, 0.2, 0.2, 0.2, 0.0);

		if (state.ticks % 3 == 0 && state.circleCount < 4) {
			state.lastCircleLocation = current;
			state.circleCount++;
			spawnRotatingCircle(world, state.lastCircleLocation, state.angle);
		}
		if (state.lastCircleLocation != null && state.lastCircleLocation.distanceTo(current) < 3.0) {
			spawnRotatingCircle(world, state.lastCircleLocation, state.angle);
		}

		state.angle += 0.19634954084936207;
		state.ticks++;
		scheduler.runLater(1, () -> tickTrail(player, state));
	}

	private void spawnRotatingCircle(ServerWorld world, Vec3d center, double angle) {
		DustParticleEffect dust = new DustParticleEffect(packRgb(242, 188, 69), 1.0f);
		for (int i = 0; i < 16; i++) {
			double offsetAngle = angle + i * Math.PI / 8.0;
			world.spawnParticles(dust,
					center.x + Math.cos(offsetAngle) * 0.8, center.y + 0.1, center.z + Math.sin(offsetAngle) * 0.8,
					1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	private static int packRgb(int r, int g, int b) {
		return (r << 16) | (g << 8) | b;
	}

	private static final class TrailState {
		int ticks = 0;
		double angle = 0.0;
		int circleCount = 0;
		Vec3d lastCircleLocation = null;
	}
}

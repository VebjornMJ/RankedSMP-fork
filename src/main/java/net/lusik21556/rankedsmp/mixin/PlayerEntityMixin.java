package net.lusik21556.rankedsmp.mixin;

import net.lusik21556.rankedsmp.RankedSMP;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Port of XPListener.java's XP multiplier. Bukkit exposed a cancellable
 * PlayerExpChangeEvent for this; vanilla/Fabric has no equivalent event, so
 * the multiplier is applied at the source via a mixin into
 * {@link PlayerEntity#addExperience(int)}.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
	@ModifyVariable(method = "addExperience", at = @At("HEAD"), argsOnly = true)
	private int rankedsmp$multiplyExperience(int amount) {
		RankedSMP mod = RankedSMP.getInstance();
		if (mod == null) {
			return amount;
		}
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (self instanceof ServerPlayerEntity player) {
			return (int) (amount * mod.getRankManager().getXPMultiplier(player));
		}
		return amount;
	}
}

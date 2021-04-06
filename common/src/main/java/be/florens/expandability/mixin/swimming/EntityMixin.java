package be.florens.expandability.mixin.swimming;

import be.florens.expandability.EventDispatcher;
import be.florens.expandability.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
public abstract class EntityMixin {

	@Shadow public Level level;

	@Shadow public abstract boolean isInWater();
	@Shadow public abstract BlockPos blockPosition();
	@Shadow protected abstract void playSwimSound(float f);

	@Redirect(method = {"updateSwimming", "isVisuallyCrawling", "canSpawnSprintParticle", "move"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isInWater()Z"))
	private boolean setInWater(Entity entity) {
		if (entity instanceof Player) {
			return Util.processEventResult(EventDispatcher.onPlayerSwim((Player) entity), entity::isInWater);
		}

		return entity.isInWater(); // Vanilla behaviour
	}

	@Redirect(method = "updateSwimming", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isUnderWater()Z"))
	private boolean setUnderWater(Entity entity) {
		if (entity instanceof Player) {
			return Util.processEventResult(EventDispatcher.onPlayerSwim((Player) entity), entity::isUnderWater);
		}

		return entity.isUnderWater(); // Vanilla behaviour
	}

	/**
	 * Prevents the swimming sound from playing when non-vanilla swimming is enabled
	 */
	@Redirect(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;playSwimSound(F)V"))
	private void cancelPlaySwimSound(Entity entity, float f) {
		// Re-check if we're in water first so we don't cancel vanilla swimming sounds
		if (!this.isInWater() && entity instanceof Player && EventDispatcher.onPlayerSwim((Player) entity).consumesAction()) {
			return;
		}

		// Vanilla behaviour
		this.playSwimSound(f);
	}

	/**
	 * Take fall damage when in water with water physics disabled
	 */
	@Redirect(method = "updateInWaterStateAndDoWaterCurrentPushing", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;fallDistance:F", opcode = Opcodes.PUTFIELD))
	private void cancelSetFallDistance(Entity entity, float fallDistance) {
		if (entity instanceof Player && EventDispatcher.onPlayerSwim((Player) entity) == InteractionResult.FAIL) {
			return;
		}

		// Vanilla behaviour
		entity.fallDistance = fallDistance;
	}

	/**
	 * There's a check here for when there's water at the entity's feet. There aren't any vanilla blocks where this
	 * matters because honey and soulsand aren't full blocks. There is a block in the fabric testmod to test this
	 * behaviour.
	 */
	@Redirect(method = "getBlockSpeedFactor", at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC, target = "Lnet/minecraft/world/level/block/Blocks;WATER:Lnet/minecraft/world/level/block/Block;"))
	private Block fixBlockSpeedFactor() {
		//noinspection ConstantConditions
		if ((Object) this instanceof Player) {
			Player player = (Player) (Object) this;
			Block block = this.level.getBlockState(this.blockPosition()).getBlock();

			if (block == Blocks.WATER && EventDispatcher.onPlayerSwim(player) == InteractionResult.FAIL) {
				return Blocks.AIR; // Makes condition return true
			}
		}

		return Blocks.WATER; // Vanilla behaviour
	}
}

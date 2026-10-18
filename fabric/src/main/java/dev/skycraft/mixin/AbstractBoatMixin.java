package dev.skycraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.skycraft.world.SkyWater;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Boats float on Skyrim's water (lakes, rivers, the sea) like on Minecraft water. See {@link SkyWater}. */
@Mixin(AbstractBoat.class)
public abstract class AbstractBoatMixin {
	private static final float BOAT_RAISE = 0.1F + 1.5F / 16.0F; // 1.5 texture pixels more than the first nudge

	@WrapOperation(
		method = {"getWaterLevelAbove", "checkInWater", "isUnderwater", "checkFallDamage"},
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;")
	)
	private FluidState skycraft$skyrimWater(Level level, BlockPos pos, Operation<FluidState> original) {
		FluidState state = original.call(level, pos);
		if (state.isEmpty() && SkyWater.active()) {
			FluidState water = SkyWater.fluidAt(level, pos);
			if (water != null) {
				return water;
			}
		}
		return state;
	}

	@WrapOperation(
		method = {"getWaterLevelAbove", "checkInWater", "isUnderwater"},
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/material/FluidState;getHeight(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"
		)
	)
	private float skycraft$skyrimWaterHeight(FluidState state, BlockGetter level, BlockPos pos, Operation<Float> original) {
		float height = SkyWater.active() ? SkyWater.substitutedHeight(level, pos) : -1.0F;
		// A higher water level makes the boat float higher; Skyrim's surface is drawn a little above where Minecraft's would be.
		return height >= 0.0F ? height + BOAT_RAISE : original.call(state, level, pos);
	}
}

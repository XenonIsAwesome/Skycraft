package dev.skycraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.skycraft.world.SkyWater;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Boats can be placed straight onto Skyrim's terrain and onto its water surface, without a
 * Minecraft block to stand them on.
 */
@Mixin(BoatItem.class)
public abstract class BoatItemMixin {
	/** The boat goes on the water surface when Skyrim water is nearer than any ground. */
	@WrapOperation(
		method = "use",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/BoatItem;getPlayerPOVHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/ClipContext$Fluid;)Lnet/minecraft/world/phys/BlockHitResult;"
		)
	)
	private BlockHitResult skycraft$boatOnSkyrimWater(Level level, Player player, ClipContext.Fluid fluid, Operation<BlockHitResult> original) {
		BlockHitResult hit = original.call(level, player, fluid);
		Vec3 from = player.getEyePosition();
		Vec3 to = from.add(player.getViewVector(1.0F).scale(player.blockInteractionRange()));
		Vec3 water = SkyWater.surfaceHit(level, from, to);
		if (water == null) {
			return hit;
		}
		if (hit.getType() != HitResult.Type.MISS && from.distanceToSqr(hit.getLocation()) <= from.distanceToSqr(water)) {
			return hit;
		}
		// Sit a little under the surface so the boat starts out in the water and floats up to it.
		Vec3 at = new Vec3(water.x, water.y - 0.05, water.z);
		return new BlockHitResult(at, Direction.UP, BlockPos.containing(water), false);
	}

	/** Terrain is made of triangles, not blocks: if the boat clips a slope, lift it clear. */
	@WrapOperation(
		method = "use",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z")
	)
	private boolean skycraft$liftClearOfTerrain(Level level, Entity boat, AABB box, Operation<Boolean> original) {
		if (original.call(level, boat, box)) {
			return true;
		}
		double x = boat.getX(), y = boat.getY(), z = boat.getZ();
		for (int i = 1; i <= 12; i++) {
			boat.setPos(x, y + i * 0.0625, z);
			if (original.call(level, boat, boat.getBoundingBox())) {
				return true;
			}
		}
		boat.setPos(x, y, z);
		return false;
	}
}

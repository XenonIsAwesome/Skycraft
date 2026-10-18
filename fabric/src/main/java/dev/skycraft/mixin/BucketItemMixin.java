package dev.skycraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.skycraft.world.SkyClip;
import dev.skycraft.world.SkyClip.SkyrimHitResult;
import dev.skycraft.world.SkyWater;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An empty bucket scoops up Skyrim's water, which vanilla's fluid ray cast can't see; a full one
 * pours into the cell in front of the surface it is aimed at.
 */
@Mixin(BucketItem.class)
public abstract class BucketItemMixin {
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void skycraft$scoopSkyrimWater(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		ItemStack held = player.getItemInHand(hand);
		if (!held.is(Items.BUCKET)) {
			return;
		}
		Vec3 from = player.getEyePosition();
		Vec3 to = from.add(player.getViewVector(1.0F).scale(player.blockInteractionRange()));
		Vec3 water = SkyWater.surfaceHit(level, from, to);
		if (water == null) {
			return;
		}
		// Anything solid (a block, or Skyrim terrain) nearer than the water surface gets the click instead.
		BlockHitResult solid = SkyClip.refine(from, to, level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)), SkyClip.Use.PROJECTILE);
		if (solid.getType() != HitResult.Type.MISS && from.distanceToSqr(solid.getLocation()) <= from.distanceToSqr(water)) {
			return;
		}
		player.playSound(SoundEvents.BUCKET_FILL, 1.0F, 1.0F);
		level.gameEvent(player, GameEvent.FLUID_PICKUP, BlockPos.containing(water));
		cir.setReturnValue(InteractionResult.SUCCESS.heldItemTransformedTo(ItemUtils.createFilledResult(held, player, new ItemStack(Items.WATER_BUCKET))));
	}

	/**
	 * Items see Skyrim's surface as the cell it is in (see ItemMixin), which is right for projectiles
	 * but not for pouring. A bucket empties into the cell next to the block it hit, so give it a hit
	 * whose neighbour is the cell in front of the surface (where a placed block would go): the water
	 * then sits on the ground instead of a block above it or inside it.
	 */
	@WrapOperation(
		method = "use",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/BucketItem;getPlayerPOVHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/ClipContext$Fluid;)Lnet/minecraft/world/phys/BlockHitResult;"
		)
	)
	private BlockHitResult skycraft$pourInFront(Level level, Player player, ClipContext.Fluid fluid, Operation<BlockHitResult> original) {
		BlockHitResult hit = original.call(level, player, fluid);
		if (!(hit instanceof SkyrimHitResult) || ((BucketItem) (Object) this).getContent() == Fluids.EMPTY) {
			return hit;
		}
		Vec3 from = player.getEyePosition();
		Vec3 to = from.add(player.getViewVector(1.0F).scale(player.blockInteractionRange()));
		BlockHitResult vanilla = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, fluid, player));
		BlockHitResult front = SkyClip.refine(from, to, vanilla, SkyClip.Use.PICK);
		if (!(front instanceof SkyrimHitResult)) {
			return front;
		}
		Direction face = front.getDirection();
		return new BlockHitResult(front.getLocation(), face, front.getBlockPos().relative(face.getOpposite()), false);
	}
}

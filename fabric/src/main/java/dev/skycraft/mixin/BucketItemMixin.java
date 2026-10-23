package dev.skycraft.mixin;

import dev.skycraft.world.SkyBucket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Buckets fill from Skyrim's lava, snow and fish, which Minecraft's own ray cast never meets. */
@Mixin(BucketItem.class)
public abstract class BucketItemMixin {
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void skycraft$fillFromSkyrim(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		InteractionResult result = SkyBucket.use(level, player, player.getItemInHand(hand));
		if (result != null) {
			cir.setReturnValue(result);
		}
	}
}

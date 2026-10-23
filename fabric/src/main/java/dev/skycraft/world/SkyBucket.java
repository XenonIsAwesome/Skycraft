package dev.skycraft.world;

import dev.skycraft.combat.SkyrimActorEntity;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Buckets fill from what Skyrim has in front of the player: an empty one takes lava or powder snow,
 * an empty or water one scoops up the fish being looked at. Minecraft has no such blocks or mobs
 * there, so vanilla's own bucket ray cast finds nothing.
 */
public final class SkyBucket {
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("skycraft");

	private SkyBucket() {
	}

	private record Catch(ItemStack filled, SoundEvent sound, int actorFormId) {
	}

	/**
	 * What the bucket in hand becomes when used on what the player looks at, or null if there is
	 * nothing of Skyrim's to fill it from (vanilla then handles the use).
	 */
	public static @Nullable InteractionResult use(Level level, Player player, ItemStack held) {
		boolean empty = held.is(Items.BUCKET);
		if (!empty && !held.is(Items.WATER_BUCKET)) {
			return null;
		}
		Vec3 from = player.getEyePosition();
		Vec3 to = from.add(player.getViewVector(1.0F).scale(player.blockInteractionRange()));
		if (entityInSight(level, player, from, to)) {
			return null;  // a cow to milk, an axolotl to scoop: the entity's own interaction handles it
		}
		Catch got = creature(level, from, to, empty);
		if (got == null && empty) {
			got = lavaOrSnow(level, player, from, to);
		}
		if (got == null) {
			return null;
		}
		LOG.info("SkyCraft: bucket filled from Skyrim: {}", got.filled().getItem());
		if (got.actorFormId() != 0 && !level.isClientSide()) {
			SkyLink.pushEvent(Proto.EV_CATCH_ACTOR, got.actorFormId(), 0.0F, 0.0F, 0.0F, 0.0F, 0);
		}
		level.playSound(player, player.getX(), player.getY(), player.getZ(), got.sound(), SoundSource.NEUTRAL, 1.0F, 1.0F);
		ItemStack result = ItemUtils.createFilledResult(held, player, got.filled());
		return InteractionResult.SUCCESS.heldItemTransformedTo(result);
	}

	/** True if a real Minecraft entity (not a Skyrim stand-in) is on the line of sight. */
	private static boolean entityInSight(Level level, Player player, Vec3 from, Vec3 to) {
		AABB sweep = new AABB(from, to).inflate(1.0);
		for (var e : level.getEntities(player, sweep, x -> x.isPickable() && !x.isSpectator() && !(x instanceof SkyrimActorEntity))) {
			if (e.getBoundingBox().inflate(e.getPickRadius()).clip(from, to).isPresent()) {
				return true;
			}
		}
		return false;
	}

	/** The fish (as the bucket of its kind) or, for an empty bucket, the cow (milk) nearest along the segment. */
	private static @Nullable Catch creature(Level level, Vec3 from, Vec3 to, boolean empty) {
		List<SkyLink.Actor> actors = new ArrayList<>();
		if (!SkyLink.readActors(actors)) {
			return null;
		}
		SkyLink.Actor best = null;
		double bestDist = Double.MAX_VALUE;
		for (SkyLink.Actor a : actors) {
			if (a.dead() || !(a.fish() || (empty && a.cow()))) {
				continue;
			}
			double hw = Math.max(a.width(), 0.5F) / 2.0, h = Math.max(a.height(), 0.4F);
			AABB box = new AABB(a.x() - hw, a.y(), a.z() - hw, a.x() + hw, a.y() + h, a.z() + hw).inflate(a.cow() ? 0.1 : 0.4);
			Optional<Vec3> hit = box.clip(from, to);
			if (hit.isPresent() && from.distanceToSqr(hit.get()) < bestDist) {
				bestDist = from.distanceToSqr(hit.get());
				best = a;
			}
		}
		if (best == null) {
			return null;
		}
		if (!best.fish()) {
			return new Catch(new ItemStack(Items.MILK_BUCKET), SoundEvents.COW_MILK, 0);
		}
		return new Catch(new ItemStack(bucketFor(best.name())), SoundEvents.BUCKET_FILL_FISH, best.formId());
	}

	/**
	 * The Minecraft fish that looks most like Skyrim's, by name: salmonids to salmon, spiny ones to
	 * pufferfish, bright reef and ornamental ones to tropical fish, and anything else (Histcarp,
	 * Silverside Perch, Abecean Longfin, carp, bass, ...) to cod.
	 */
	static net.minecraft.world.item.Item bucketFor(String skyrimName) {
		String n = skyrimName.toLowerCase(java.util.Locale.ROOT);
		if (hasAny(n, "salmon", "trout", "char", "grayling")) {
			return Items.SALMON_BUCKET;
		}
		if (hasAny(n, "scorpion", "puffer", "direfish")) {
			return Items.PUFFERFISH_BUCKET;
		}
		if (hasAny(n, "angelfish", "goldfish", "anthias", "pearlfish", "glassfish", "glass catfish", "spadefish", "sunfish", "betty")) {
			return Items.TROPICAL_FISH_BUCKET;
		}
		return Items.COD_BUCKET;
	}

	private static boolean hasAny(String text, String... words) {
		for (String w : words) {
			if (text.contains(w)) {
				return true;
			}
		}
		return false;
	}

	/** Skyrim water (lava or plain) or snow, whichever the player's line of sight meets first. */
	private static @Nullable Catch lavaOrSnow(Level level, Player player, Vec3 from, Vec3 to) {
		Vec3 surface = SkyWater.surfaceHit(level, from, to);
		BlockHitResult vanilla = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		BlockHitResult hit = SkyClip.refine(from, to, vanilla, SkyClip.Use.PROJECTILE);
		boolean solid = hit.getType() != HitResult.Type.MISS;
		double solidDist = solid ? from.distanceToSqr(hit.getLocation()) : Double.MAX_VALUE;
		if (surface != null && from.distanceToSqr(surface) < solidDist) {
			// The water in front of the ground behind it: a lake or river is plain water, lava burns.
			if (SkyWater.lavaAt((int) Math.floor(surface.x), (int) Math.floor(surface.z))) {
				return new Catch(new ItemStack(Items.LAVA_BUCKET), SoundEvents.BUCKET_FILL_LAVA, 0);
			}
			return new Catch(new ItemStack(Items.WATER_BUCKET), SoundEvents.BUCKET_FILL, 0);
		}
		// Snow: Skyrim terrain that Minecraft blocks don't cover, in a snowy column.
		if (solid && hit instanceof SkyClip.SkyrimHitResult) {
			Vec3 at = hit.getLocation();
			if (SkyWater.snowAt((int) Math.floor(at.x), (int) Math.floor(at.z))) {
				return new Catch(new ItemStack(Items.POWDER_SNOW_BUCKET), SoundEvents.BUCKET_FILL_POWDER_SNOW, 0);
			}
		}
		return null;
	}
}

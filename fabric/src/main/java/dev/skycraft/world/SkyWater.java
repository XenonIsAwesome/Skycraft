package dev.skycraft.world;

import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * Skyrim's lakes, rivers and sea as Minecraft water: Skyrim sends the water surface over the block
 * columns around the player (see WaterGrid in the protocol), and wherever Minecraft has air below
 * that surface, entities treat it as water, so the player swims, floats, sinks slowly and drowns
 * there as in Minecraft water. Only entity physics sees it; no blocks change.
 */
public final class SkyWater {
	private record Grid(int originX, int originZ, int size, float[] surface, byte[] liquid, byte[] ground) {
	}

	private static volatile @Nullable Grid grid;

	private SkyWater() {
	}

	/** Once a frame on the client: pick up Skyrim's latest grid. */
	public static void refresh() {
		SkyLink.WaterGrid read = SkyLink.readWaterGrid();
		if (read != null) {
			grid = new Grid(read.originX, read.originZ, read.size, read.surface, read.liquid, read.ground);
		}
	}

	public static void clear() {
		grid = null;
	}

	public static boolean active() {
		return grid != null;
	}

	/** Minecraft y of Skyrim's water surface over this column, or NaN where there is none. */
	public static double surfaceAt(int x, int z) {
		Grid g = grid;
		if (g == null) {
			return Double.NaN;
		}
		int dx = x - g.originX(), dz = z - g.originZ();
		if (dx < 0 || dz < 0 || dx >= g.size() || dz >= g.size()) {
			return Double.NaN;
		}
		float s = g.surface()[dz * g.size() + dx];
		return s < -1.0e20F ? Double.NaN : s;
	}

	private static int index(Grid g, int x, int z) {
		int dx = x - g.originX(), dz = z - g.originZ();
		return dx < 0 || dz < 0 || dx >= g.size() || dz >= g.size() ? -1 : dz * g.size() + dx;
	}

	/** True if the Skyrim water over this column is lava. */
	public static boolean lavaAt(int x, int z) {
		Grid g = grid;
		int i = g == null ? -1 : index(g, x, z);
		return i >= 0 && g.liquid()[i] == Proto.LIQUID_LAVA && !Double.isNaN(surfaceAt(x, z));
	}

	/** True if Skyrim's terrain in this column is snow. */
	public static boolean snowAt(int x, int z) {
		Grid g = grid;
		int i = g == null ? -1 : index(g, x, z);
		return i >= 0 && g.ground()[i] == Proto.GROUND_SNOW;
	}

	/** How much of this block (0..1) is under Skyrim's water; 0 above the surface. */
	public static float depthIn(BlockPos pos) {
		double s = surfaceAt(pos.getX(), pos.getZ());
		if (Double.isNaN(s)) {
			return 0.0F;
		}
		double h = s - pos.getY();
		return h < 0.02 ? 0.0F : (float) Math.min(1.0, h);
	}

	/** True if Skyrim water reaches up into the box of block cells (inclusive). */
	public static boolean anyIn(int x0, int y0, int z0, int x1, int y1, int z1) {
		if (grid == null) {
			return false;
		}
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				double s = surfaceAt(x, z);
				if (!Double.isNaN(s) && s > y0) {
					return true;
				}
			}
		}
		return false;
	}

	/** Skyrim water in an otherwise empty (air) Minecraft cell, as a Minecraft fluid; null if none. */
	public static @Nullable FluidState fluidAt(BlockGetter level, BlockPos pos) {
		if (depthIn(pos) <= 0.0F || !level.getBlockState(pos).isAir()) {
			return null;
		}
		return Fluids.WATER.getSource(false);
	}

	/** The exact water height in a cell only Skyrim fills (so floating matches its surface); -1 otherwise. */
	public static float substitutedHeight(BlockGetter level, BlockPos pos) {
		float depth = depthIn(pos);
		if (depth <= 0.0F || !level.getFluidState(pos).isEmpty() || !level.getBlockState(pos).isAir()) {
			return -1.0F;
		}
		return depth;
	}

	/**
	 * Where the segment first enters Skyrim water from above (on the water surface), or null if it
	 * doesn't, or starts under the surface. Lets boats be placed on lakes and the sea, where vanilla's
	 * fluid ray cast sees no water to hit.
	 */
	public static @Nullable Vec3 surfaceHit(BlockGetter level, Vec3 from, Vec3 to) {
		if (grid == null) {
			return null;
		}
		double len = from.distanceTo(to);
		int steps = (int) Math.ceil(len / 0.1);
		if (steps <= 0) {
			return null;
		}
		Vec3 prev = from;
		for (int i = 0; i <= steps; i++) {
			Vec3 p = from.lerp(to, (double) i / steps);
			if (inWater(level, p)) {
				if (i == 0) {
					return null;
				}
				Vec3 lo = prev, hi = p;
				for (int k = 0; k < 8; k++) {
					Vec3 mid = lo.lerp(hi, 0.5);
					if (inWater(level, mid)) {
						hi = mid;
					} else {
						lo = mid;
					}
				}
				double s = surfaceAt((int) Math.floor(hi.x), (int) Math.floor(hi.z));
				return new Vec3(hi.x, Double.isNaN(s) ? hi.y : s, hi.z);
			}
			prev = p;
		}
		return null;
	}

	private static boolean inWater(BlockGetter level, Vec3 p) {
		int x = (int) Math.floor(p.x), z = (int) Math.floor(p.z);
		double s = surfaceAt(x, z);
		return !Double.isNaN(s) && p.y < s && level.getBlockState(BlockPos.containing(p)).isAir();
	}
}

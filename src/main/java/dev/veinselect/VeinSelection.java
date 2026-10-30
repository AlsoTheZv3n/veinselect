package dev.veinselect;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Eine gespeicherte Auswahl: Dimension, Blocktyp und Positionen (in Suchreihenfolge, Ursprung zuerst). */
public record VeinSelection(ResourceKey<Level> dimension, Block block, Set<BlockPos> positions) {

	public boolean contains(Level level, BlockPos pos) {
		return level.dimension() == dimension && positions.contains(pos);
	}

	/** Breitensuche über alle 26 Nachbarn (inkl. diagonal), bis {@code max} gleiche Blöcke gefunden sind. */
	public static VeinSelection scan(Level level, BlockPos origin, Block block, int max) {
		Set<BlockPos> found = new LinkedHashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		BlockPos start = origin.immutable();
		found.add(start);
		queue.add(start);

		while (!queue.isEmpty() && found.size() < max) {
			BlockPos current = queue.poll();

			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) continue;

						BlockPos next = current.offset(dx, dy, dz);
						if (found.contains(next) || !level.getBlockState(next).is(block)) continue;

						found.add(next);
						queue.add(next);
						if (found.size() >= max) return new VeinSelection(level.dimension(), block, found);
					}
				}
			}
		}

		return new VeinSelection(level.dimension(), block, found);
	}
}

package dev.veinselect.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Vorberechnete Hervorhebung einer Auswahl: nur Außenflächen (Füllung) und die Außenkontur (Linien),
 * damit innere Kanten zwischen zwei ausgewählten Blöcken nicht als Gitter sichtbar sind.
 * Koordinaten sind relativ zu {@link #origin}, damit sie auch weit vom Spawn als float genau bleiben.
 */
final class VeinHighlight {
	/** Abstand nach außen, damit Flächen und Linien nicht mit den Blockseiten flackern. */
	private static final float INFLATE = 0.003f;

	private final BlockPos origin;
	/** Je Fläche 4 Ecken à (x, y, z). */
	private final float[] faces;
	/** Je Kante Start (x, y, z), Ende (x, y, z) und Richtung (x, y, z). */
	private final float[] edges;

	private VeinHighlight(BlockPos origin, float[] faces, float[] edges) {
		this.origin = origin;
		this.faces = faces;
		this.edges = edges;
	}

	BlockPos origin() {
		return origin;
	}

	static VeinHighlight build(List<BlockPos> positions) {
		BlockPos origin = positions.getFirst();
		LongSet cells = new LongOpenHashSet(positions.size());
		for (BlockPos pos : positions) cells.add(pos.asLong());

		FloatArrayList faces = new FloatArrayList();
		FloatArrayList edges = new FloatArrayList();
		Set<Edge> visited = new HashSet<>();

		for (BlockPos pos : positions) {
			for (Direction side : Direction.values()) {
				if (!cells.contains(pos.relative(side).asLong())) addFace(faces, side, pos.subtract(origin));
			}

			int[] cell = {pos.getX(), pos.getY(), pos.getZ()};
			for (int axis = 0; axis < 3; axis++) {
				for (int du = 0; du <= 1; du++) {
					for (int dv = 0; dv <= 1; dv++) {
						addEdgeIfContour(edges, cells, visited, origin, cell, axis, du, dv);
					}
				}
			}
		}

		return new VeinHighlight(origin, faces.toFloatArray(), edges.toFloatArray());
	}

	/**
	 * Eine Kante entlang {@code axis} grenzt an 4 Zellen. Sie gehört zur Kontur, außer alle 4 sind ausgewählt (innen)
	 * oder genau 2 nebeneinander (flache Fläche). Die beiden anderen Achsen heißen hier u und v.
	 */
	private static void addEdgeIfContour(FloatArrayList out, LongSet cells, Set<Edge> visited, BlockPos origin, int[] cell, int axis, int du, int dv) {
		int u = (axis + 1) % 3;
		int v = (axis + 2) % 3;
		int gridU = cell[u] + du;
		int gridV = cell[v] + dv;
		if (!visited.add(new Edge(axis, cell[axis], gridU, gridV))) return;

		boolean[][] filled = new boolean[2][2];
		int count = 0;
		int outU = 0;
		int outV = 0;
		int[] neighbour = cell.clone();
		for (int i = 0; i <= 1; i++) {
			for (int j = 0; j <= 1; j++) {
				neighbour[u] = gridU - 1 + i;
				neighbour[v] = gridV - 1 + j;
				filled[i][j] = cells.contains(BlockPos.asLong(neighbour[0], neighbour[1], neighbour[2]));
				if (filled[i][j]) {
					count++;
				} else {
					// Richtung zu den leeren Zellen = nach außen
					outU += i == 0 ? -1 : 1;
					outV += j == 0 ? -1 : 1;
				}
			}
		}

		if (count == 4 || (count == 2 && filled[0][0] != filled[1][1])) return;

		float[] start = new float[3];
		start[axis] = cell[axis] - originAxis(origin, axis);
		start[u] = gridU - originAxis(origin, u) + Math.signum(outU) * INFLATE;
		start[v] = gridV - originAxis(origin, v) + Math.signum(outV) * INFLATE;
		float[] end = start.clone();
		end[axis] += 1;
		float[] direction = new float[3];
		direction[axis] = 1;

		out.addElements(out.size(), start);
		out.addElements(out.size(), end);
		out.addElements(out.size(), direction);
	}

	private static int originAxis(BlockPos origin, int axis) {
		return switch (axis) {
			case 0 -> origin.getX();
			case 1 -> origin.getY();
			default -> origin.getZ();
		};
	}

	private static void addFace(FloatArrayList out, Direction side, BlockPos rel) {
		float x0 = rel.getX(), y0 = rel.getY(), z0 = rel.getZ();
		float x1 = x0 + 1, y1 = y0 + 1, z1 = z0 + 1;
		float e = INFLATE;

		float[] quad = switch (side) {
			case NORTH -> new float[] {x0, y0, z0 - e, x1, y0, z0 - e, x1, y1, z0 - e, x0, y1, z0 - e};
			case SOUTH -> new float[] {x1, y0, z1 + e, x0, y0, z1 + e, x0, y1, z1 + e, x1, y1, z1 + e};
			case WEST -> new float[] {x0 - e, y0, z1, x0 - e, y0, z0, x0 - e, y1, z0, x0 - e, y1, z1};
			case EAST -> new float[] {x1 + e, y0, z0, x1 + e, y0, z1, x1 + e, y1, z1, x1 + e, y1, z0};
			case UP -> new float[] {x0, y1 + e, z0, x1, y1 + e, z0, x1, y1 + e, z1, x0, y1 + e, z1};
			case DOWN -> new float[] {x0, y0 - e, z1, x1, y0 - e, z1, x1, y0 - e, z0, x0, y0 - e, z0};
		};
		out.addElements(out.size(), quad);
	}

	/** Quads für {@code RenderTypes.debugFilledBox()}; (dx, dy, dz) = origin relativ zur Kamera. */
	void renderFill(PoseStack.Pose pose, VertexConsumer buffer, float dx, float dy, float dz, int color) {
		for (int i = 0; i < faces.length; i += 3) {
			buffer.addVertex(pose, faces[i] + dx, faces[i + 1] + dy, faces[i + 2] + dz).setColor(color);
		}
	}

	/** Linien wie die Vanilla-Blockumrandung; (dx, dy, dz) = origin relativ zur Kamera. */
	void renderOutline(PoseStack.Pose pose, VertexConsumer buffer, float dx, float dy, float dz, int color, float width) {
		for (int i = 0; i < edges.length; i += 9) {
			float nx = edges[i + 6], ny = edges[i + 7], nz = edges[i + 8];
			buffer.addVertex(pose, edges[i] + dx, edges[i + 1] + dy, edges[i + 2] + dz).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
			buffer.addVertex(pose, edges[i + 3] + dx, edges[i + 4] + dy, edges[i + 5] + dz).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
		}
	}

	private record Edge(int axis, int position, int gridU, int gridV) {}
}

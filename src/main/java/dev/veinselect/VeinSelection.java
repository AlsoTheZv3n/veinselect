package dev.veinselect;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Eine gespeicherte Auswahl: Position → Blocktyp beim Auswählen, in Abbaureihenfolge, Ursprung zuerst.
 * Ursprung, getroffene Seite und Modus werden gemerkt, damit die Auswahl beim Moduswechsel neu berechnet werden kann.
 */
public record VeinSelection(ResourceKey<Level> dimension, VeinMode mode, BlockPos origin, Direction face, Map<BlockPos, Block> blocks) {

	public boolean contains(Level level, BlockPos pos) {
		return level.dimension() == dimension && blocks.containsKey(pos);
	}

	/** Blocktyp, der beim Auswählen an {@code pos} stand, oder {@code null}. */
	public Block blockAt(BlockPos pos) {
		return blocks.get(pos);
	}

	/**
	 * Grundregel für jeden Block, den Vein Select abbaut: keine Luft, keine Flüssigkeit und außerhalb des
	 * Kreativmodus nichts Unzerstörbares (Grundgestein, Endportalrahmen). Nötig, weil destroyBlock() die Härte
	 * nicht prüft; Vanilla verhindert das sonst nur über den Abbaufortschritt beim Block, den der Spieler anklickt.
	 */
	// liquid() ist als Legacy markiert, trifft aber genau Wasser/Lava; getFluidState() würde auch geflutete Stufen ausschließen
	@SuppressWarnings("deprecation")
	public static boolean isMineable(Player player, BlockPos pos, BlockState state) {
		if (state.isAir() || state.liquid()) return false;
		return player.isCreative() || state.getDestroySpeed(player.level(), pos) >= 0;
	}

	/** Formen: außerhalb des Kreativmodus nur Blöcke, die mit dem gehaltenen Werkzeug etwas droppen. */
	public static boolean dropsWithHeldTool(Player player, BlockState state) {
		return player.isCreative() || player.hasCorrectToolForDrops(state);
	}

	/**
	 * Berechnet die Auswahl ab {@code origin}, höchstens {@code max} Blöcke.
	 * Ungeladene Chunks und Blöcke, die {@code mayBreak} ablehnt (z. B. Spawn-Schutz), werden nie aufgenommen.
	 *
	 * @param face Seite von {@code origin}, auf die der Spieler geschaut hat; bestimmt die Richtung der Formen
	 */
	public static VeinSelection scan(Player player, BlockPos origin, Direction face, VeinMode mode, int max, Predicate<BlockPos> mayBreak) {
		Collector collector = new Collector(player, max, mayBreak);
		BlockPos start = origin.immutable();
		BlockState originState = player.level().getBlockState(start);
		collector.found.put(start, originState.getBlock());

		// Sofort abbaubare Blöcke (Fackel, Blume) lassen eine Form nicht in die Umgebung wachsen
		boolean instantBreak = originState.getDestroySpeed(player.level(), start) == 0;
		if (!mode.isShape() || !instantBreak) {
			switch (mode) {
				case VEIN -> vein(collector, start, originState.getBlock());
				case TUNNEL -> tunnel(collector, start, face.getOpposite());
				case THREE_BY_THREE -> threeByThree(collector, start, face.getOpposite());
				case STAIRS_UP -> stairs(collector, start, horizontalForward(player, face), 1);
				case STAIRS_DOWN -> stairs(collector, start, horizontalForward(player, face), -1);
			}
		}

		return new VeinSelection(player.level().dimension(), mode, start, face, Collections.unmodifiableMap(collector.found));
	}

	/** Breitensuche über alle 26 Nachbarn (inkl. diagonal), nur Blöcke desselben Typs. */
	private static void vein(Collector collector, BlockPos start, Block block) {
		Level level = collector.level;
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);

		while (!queue.isEmpty() && !collector.full()) {
			BlockPos current = queue.poll();

			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) continue;

						BlockPos next = current.offset(dx, dy, dz);
						// isLoaded zuerst: getBlockState() würde fehlende Chunks synchron laden
						if (collector.found.containsKey(next) || !level.isLoaded(next) || !level.getBlockState(next).is(block) || !collector.mayBreak.test(next)) continue;

						collector.found.put(next, block);
						queue.add(next);
						if (collector.full()) return;
					}
				}
			}
		}
	}

	/** Gerade Linie in Blickrichtung, endet am ersten Block, der nicht abbaubar ist (z. B. eine Höhle). */
	private static void tunnel(Collector collector, BlockPos start, Direction forward) {
		BlockPos cursor = start.relative(forward);
		while (collector.add(cursor)) cursor = cursor.relative(forward);
	}

	/** 3×3 quer zur Blickrichtung, oben zuerst, damit Kies und Sand nicht in die Lücken fallen. */
	private static void threeByThree(Collector collector, BlockPos start, Direction forward) {
		Direction.Axis axis = forward.getAxis();
		for (int dy = 1; dy >= -1; dy--) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					boolean alongForward = switch (axis) {
						case X -> dx != 0;
						case Y -> dy != 0;
						case Z -> dz != 0;
					};
					if (!alongForward) collector.add(start.offset(dx, dy, dz));
				}
			}
		}
	}

	/**
	 * Treppe: pro Stufe eine Spalte aus 3 Blöcken, dann einen Schritt vor und einen hoch bzw. runter.
	 * Hoch: unter, auf und über dem Cursor. Runter: auf, unter und zwei unter dem Cursor.
	 * Endet, wenn eine Spalte gar nichts Abbaubares mehr enthält.
	 */
	private static void stairs(Collector collector, BlockPos start, Direction forward, int step) {
		int top = step > 0 ? 1 : 0;
		BlockPos cursor = start;

		while (!collector.full()) {
			boolean any = false;
			// Oben zuerst, damit Kies und Sand nicht in die Lücken fallen
			for (int dy = top; dy >= top - 2; dy--) {
				BlockPos pos = cursor.above(dy);
				if (collector.diggable(pos)) {
					any = true;
					collector.add(pos);
				}
			}
			if (!any) return;
			cursor = cursor.relative(forward).above(step);
		}
	}

	/** Treppen laufen waagerecht: von der getroffenen Seite weg, bei Ober-/Unterseite in Blickrichtung des Spielers. */
	private static Direction horizontalForward(Player player, Direction face) {
		return face.getAxis().isHorizontal() ? face.getOpposite() : player.getDirection();
	}

	private static final class Collector {
		final Player player;
		final Level level;
		final int max;
		final Predicate<BlockPos> mayBreak;
		final Map<BlockPos, Block> found = new LinkedHashMap<>();

		Collector(Player player, int max, Predicate<BlockPos> mayBreak) {
			this.player = player;
			this.level = player.level();
			this.max = max;
			this.mayBreak = mayBreak;
		}

		boolean full() {
			return found.size() >= max;
		}

		/**
		 * Für die Formen: gemischte Blöcke, aber nur abbaubare ({@link #isMineable}), die mit dem gehaltenen
		 * Werkzeug etwas droppen, damit z. B. ein 3×3 mit der Schaufel keinen Stein vernichtet.
		 */
		boolean diggable(BlockPos pos) {
			// isLoaded zuerst: getBlockState() würde fehlende Chunks synchron laden
			if (!level.isLoaded(pos)) return false;

			BlockState state = level.getBlockState(pos);
			return isMineable(player, pos, state) && dropsWithHeldTool(player, state) && mayBreak.test(pos);
		}

		/** @return {@code true}, wenn {@code pos} neu aufgenommen wurde */
		boolean add(BlockPos pos) {
			if (full() || found.containsKey(pos) || !diggable(pos)) return false;
			found.put(pos.immutable(), level.getBlockState(pos).getBlock());
			return true;
		}
	}
}

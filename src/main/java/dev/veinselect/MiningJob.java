package dev.veinselect;

import java.util.Iterator;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Baut eine Auswahl ab, höchstens {@link #BLOCKS_PER_TICK} Blöcke pro Tick, damit große Adern den Server nicht einfrieren.
 * Bis zu 64 Blöcke sind damit weiterhin sofort im selben Tick weg.
 */
final class MiningJob {
	private static final int BLOCKS_PER_TICK = 64;

	/** Gesetzt, während ein Job Blöcke abbaut: destroyBlock() löst das Abbau-Event erneut aus. */
	private static boolean breaking = false;

	private final UUID playerId;
	private final VeinSelection selection;
	private final Iterator<BlockPos> remaining;
	private final boolean hadTool;
	private final int toolSlot;
	private int mined = 1; // Der Block, den der Spieler selbst abgebaut hat

	MiningJob(ServerPlayer player, VeinSelection selection, BlockPos brokenByPlayer) {
		this.playerId = player.getUUID();
		this.selection = selection;
		this.remaining = selection.blocks().keySet().stream().filter(p -> !p.equals(brokenByPlayer)).iterator();
		this.hadTool = !player.getMainHandItem().isEmpty();
		this.toolSlot = player.getInventory().getSelectedSlot();
	}

	static boolean isBreaking() {
		return breaking;
	}

	/** @return {@code true}, wenn der Job fertig ist oder abgebrochen wurde */
	boolean tick(MinecraftServer server) {
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (player == null) return true;

		if (!player.isAlive() || player.level().dimension() != selection.dimension()) return finish(player);

		ServerLevel level = player.level();
		int attempts = 0;
		breaking = true;
		try {
			while (attempts < BLOCKS_PER_TICK && remaining.hasNext()) {
				// Werkzeug kaputt oder gewechselt: nicht mit einem anderen Gegenstand weitermachen
				if (player.getInventory().getSelectedSlot() != toolSlot || (hadTool && player.getMainHandItem().isEmpty())) {
					return finish(player);
				}

				BlockPos target = remaining.next();
				if (!level.isLoaded(target)) continue;

				// Nur, wenn dort noch derselbe Block steht wie beim Auswählen (Formen enthalten gemischte Blöcke).
				// Die Regeln der Auswahl hier nochmal, weil destroyBlock() weder Härte noch Werkzeug prüft.
				BlockState state = level.getBlockState(target);
				if (!state.is(selection.blockAt(target)) || !VeinSelection.isMineable(player, target, state)) continue;
				if (selection.mode().isShape() && !VeinSelection.dropsWithHeldTool(player, state)) continue;
				if (!VeinSelect.mayBreak(player, target)) continue;

				attempts++;
				// Normaler Abbau: Drops, Werkzeug-Haltbarkeit, Verzauberungen, Hunger, Abenteuer-Modus
				if (player.gameMode.destroyBlock(target)) mined++;
			}
		} finally {
			breaking = false;
		}

		if (!remaining.hasNext()) return finish(player);

		player.sendOverlayMessage(Component.translatable("message.veinselect.mining", mined, selection.blocks().size()));
		return false;
	}

	private boolean finish(ServerPlayer player) {
		player.sendOverlayMessage(Component.translatable("message.veinselect.mined", mined));
		return true;
	}
}

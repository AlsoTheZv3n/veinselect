package dev.veinselect;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.veinselect.net.SelectVeinPayload;
import dev.veinselect.net.SelectionSyncPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class VeinSelect implements ModInitializer {
	public static final String MOD_ID = "veinselect";
	public static final int MAX_BLOCKS = 64;
	private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

	/** Auswahl pro Spieler, nur auf dem Server-Thread verwendet. */
	private static final Map<UUID, VeinSelection> SELECTIONS = new HashMap<>();

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(SelectVeinPayload.TYPE, SelectVeinPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SelectionSyncPayload.TYPE, SelectionSyncPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SelectVeinPayload.TYPE, (payload, context) -> onSelect(context.player(), payload));
		PlayerBlockBreakEvents.AFTER.register(VeinSelect::onBlockBroken);
		ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> SELECTIONS.remove(listener.getPlayer().getUUID()));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	private static void onSelect(ServerPlayer player, SelectVeinPayload payload) {
		if (payload.target().isEmpty() || player.isSpectator()) {
			clear(player);
			return;
		}

		BlockPos origin = payload.target().get();
		Level level = player.level();

		// Schutz gegen manipulierte Pakete: nur Blöcke in Reichweite
		if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(origin)) > MAX_DISTANCE_SQR) return;

		// Erneut auf die bestehende Auswahl drücken = abwählen
		VeinSelection current = SELECTIONS.get(player.getUUID());
		if (current != null && current.contains(level, origin)) {
			clear(player);
			return;
		}

		BlockState state = level.getBlockState(origin);
		if (state.isAir()) {
			clear(player);
			return;
		}

		VeinSelection selection = VeinSelection.scan(level, origin, state.getBlock(), MAX_BLOCKS);
		SELECTIONS.put(player.getUUID(), selection);
		ServerPlayNetworking.send(player, new SelectionSyncPayload(List.copyOf(selection.positions())));
		player.sendOverlayMessage(Component.translatable("message.veinselect.selected", selection.positions().size(), state.getBlock().getName()));
	}

	private static void onBlockBroken(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
		if (!(player instanceof ServerPlayer serverPlayer)) return;

		VeinSelection selection = SELECTIONS.get(player.getUUID());
		if (selection == null || !selection.contains(level, pos) || !state.is(selection.block())) return;

		// Zuerst entfernen: destroyBlock() unten löst dieses Event erneut aus
		SELECTIONS.remove(player.getUUID());
		ServerPlayNetworking.send(serverPlayer, new SelectionSyncPayload(List.of()));

		boolean hadTool = !player.getMainHandItem().isEmpty();
		int mined = 1;

		for (BlockPos target : selection.positions()) {
			if (target.equals(pos)) continue;
			if (hadTool && player.getMainHandItem().isEmpty()) break; // Werkzeug ist kaputtgegangen
			if (!level.getBlockState(target).is(selection.block())) continue;

			// Normaler Abbau: Drops, Werkzeug-Haltbarkeit, Verzauberungen, Hunger, Spawn-Schutz
			if (serverPlayer.gameMode.destroyBlock(target)) mined++;
		}

		player.sendOverlayMessage(Component.translatable("message.veinselect.mined", mined));
	}

	private static void clear(ServerPlayer player) {
		if (SELECTIONS.remove(player.getUUID()) != null) {
			ServerPlayNetworking.send(player, new SelectionSyncPayload(List.of()));
			player.sendOverlayMessage(Component.translatable("message.veinselect.cleared"));
		}
	}
}

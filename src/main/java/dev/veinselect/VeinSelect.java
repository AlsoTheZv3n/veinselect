package dev.veinselect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.veinselect.net.LimitsSyncPayload;
import dev.veinselect.net.MaxBlocksPayload;
import dev.veinselect.net.ModePayload;
import dev.veinselect.net.SelectVeinPayload;
import dev.veinselect.net.SelectionSyncPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class VeinSelect implements ModInitializer {
	public static final String MOD_ID = "veinselect";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Standard für das Server-Limit und die Spieler-Einstellung. */
	public static final int DEFAULT_LIMIT = 64;
	/** Technische Obergrenze, auch für den Owner (Server-Last, Paketgröße). */
	public static final int HARD_LIMIT = 4096;
	private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

	// Nur auf dem Server-Thread verwendet
	private static final Map<UUID, VeinSelection> SELECTIONS = new HashMap<>();
	private static final Map<UUID, Integer> PREFERRED_LIMITS = new HashMap<>();
	private static final Map<UUID, VeinMode> MODES = new HashMap<>();
	private static final List<MiningJob> JOBS = new ArrayList<>();

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(SelectVeinPayload.TYPE, SelectVeinPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MaxBlocksPayload.TYPE, MaxBlocksPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ModePayload.TYPE, ModePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SelectionSyncPayload.TYPE, SelectionSyncPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LimitsSyncPayload.TYPE, LimitsSyncPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ModePayload.TYPE, ModePayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SelectVeinPayload.TYPE, (payload, context) -> onSelect(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(MaxBlocksPayload.TYPE, (payload, context) -> setPreferredLimit(context.player(), payload.maxBlocks()));
		ServerPlayNetworking.registerGlobalReceiver(ModePayload.TYPE, (payload, context) -> setMode(context.player(), payload.mode(), false));

		ServerLifecycleEvents.SERVER_STARTING.register(server -> VeinSelectConfig.load());
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> VeinSelectCommand.register(dispatcher));
		PlayerBlockBreakEvents.AFTER.register(VeinSelect::onBlockBroken);
		ServerTickEvents.END_SERVER_TICK.register(server -> JOBS.removeIf(job -> job.tick(server)));

		ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> {
			SELECTIONS.remove(listener.getPlayer().getUUID());
			PREFERRED_LIMITS.remove(listener.getPlayer().getUUID());
			MODES.remove(listener.getPlayer().getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SELECTIONS.clear();
			PREFERRED_LIMITS.clear();
			MODES.clear();
			JOBS.clear();
		});
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static int clampLimit(int limit) {
		return Math.clamp(limit, 1, HARD_LIMIT);
	}

	/** Owner = OP-Level 4 oder Host im Singleplayer/LAN. */
	public static boolean isOwner(ServerPlayer player) {
		return player.permissions().hasPermission(Permissions.COMMANDS_OWNER)
				|| player.level().getServer().isSingleplayerOwner(player.nameAndId());
	}

	/** Höchstwert, den der Server diesem Spieler erlaubt. */
	public static int allowedLimit(ServerPlayer player) {
		return isOwner(player) ? HARD_LIMIT : VeinSelectConfig.serverLimit();
	}

	/** Wert aus den Einstellungen des Spielers (vom Client gemeldet). */
	public static int preferredLimit(ServerPlayer player) {
		return PREFERRED_LIMITS.getOrDefault(player.getUUID(), DEFAULT_LIMIT);
	}

	public static int effectiveLimit(ServerPlayer player) {
		return Math.min(preferredLimit(player), allowedLimit(player));
	}

	public static void setPreferredLimit(ServerPlayer player, int limit) {
		PREFERRED_LIMITS.put(player.getUUID(), clampLimit(limit));
		syncLimits(player);
	}

	/** Schickt allen Spielern ihre aktuellen Limits, z. B. nachdem der Owner das Server-Limit geändert hat. */
	public static void syncLimits(MinecraftServer server) {
		server.getPlayerList().getPlayers().forEach(VeinSelect::syncLimits);
	}

	private static void syncLimits(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, LimitsSyncPayload.TYPE)) {
			ServerPlayNetworking.send(player, new LimitsSyncPayload(preferredLimit(player), allowedLimit(player)));
		}
	}

	/** Modus aus den Einstellungen des Spielers (vom Client gemeldet). */
	public static VeinMode mode(ServerPlayer player) {
		return MODES.getOrDefault(player.getUUID(), VeinMode.VEIN);
	}

	/**
	 * Setzt den Modus und berechnet eine bestehende Auswahl damit neu, vom selben Ursprung aus.
	 *
	 * @param syncToClient nach {@code /veinselect mode}: dem Client den Modus schicken, damit er ihn speichert
	 */
	public static void setMode(ServerPlayer player, VeinMode mode, boolean syncToClient) {
		MODES.put(player.getUUID(), mode);
		if (syncToClient && ServerPlayNetworking.canSend(player, ModePayload.TYPE)) {
			ServerPlayNetworking.send(player, new ModePayload(mode));
		}

		VeinSelection current = SELECTIONS.get(player.getUUID());
		if (current == null || current.mode() == mode) return;

		if (current.dimension() == player.level().dimension()) {
			select(player, current.origin(), current.face());
		} else {
			clear(player);
		}
	}

	/**
	 * Darf der Spieler diesen Block abbauen? destroyBlock() prüft nur den Abenteuer-Modus,
	 * Spawn-Schutz und Weltgrenze prüft Vanilla sonst nur beim Block, auf den der Spieler wirklich klickt.
	 */
	static boolean mayBreak(ServerPlayer player, BlockPos pos) {
		ServerLevel level = player.level();
		return level.mayInteract(player, pos)
				&& level.getWorldBorder().isWithinBounds(pos)
				&& !player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer());
	}

	private static void onSelect(ServerPlayer player, SelectVeinPayload payload) {
		if (payload.target().isEmpty() || player.isSpectator()) {
			clear(player);
			return;
		}

		BlockPos origin = payload.target().get();

		// Schutz gegen manipulierte Pakete: nur Blöcke in Reichweite
		if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(origin)) > MAX_DISTANCE_SQR) return;

		// Abwählen entscheidet der Client (V auf die Auswahl oder ins Leere schickt ein leeres Ziel), damit
		// V halten + Mausrad den Modus einer bestehenden Auswahl wechseln kann, ohne sie vorher aufzuheben
		select(player, origin, payload.face());
	}

	/** Neue Auswahl ab {@code origin} mit dem aktuellen Modus des Spielers; ersetzt eine bestehende. */
	private static void select(ServerPlayer player, BlockPos origin, Direction face) {
		ServerLevel level = player.level();
		// isLoaded zuerst: Beim Moduswechsel kann der Ursprung inzwischen weit weg sein
		if (!level.isLoaded(origin)) {
			clear(player);
			return;
		}

		// Der Ursprung landet immer in der Auswahl, also hier dieselbe Grundregel wie für alle anderen Blöcke.
		// Sonst: 3×3 auf Grundgestein, Nachbarblock abbauen → der Abbau-Job zerstört das Grundgestein mit.
		BlockState state = level.getBlockState(origin);
		if (!VeinSelection.isMineable(player, origin, state) || !mayBreak(player, origin)) {
			clear(player);
			return;
		}

		int limit = effectiveLimit(player);
		VeinMode mode = mode(player);
		VeinSelection selection = VeinSelection.scan(player, origin, face, mode, limit, pos -> mayBreak(player, pos));
		SELECTIONS.put(player.getUUID(), selection);
		ServerPlayNetworking.send(player, new SelectionSyncPayload(List.copyOf(selection.blocks().keySet())));

		int count = selection.blocks().size();
		boolean full = count >= limit;
		Component message = mode.isShape()
				? Component.translatable(full ? "message.veinselect.selected_shape_limit" : "message.veinselect.selected_shape", mode.displayName(), count)
				: Component.translatable(full ? "message.veinselect.selected_limit" : "message.veinselect.selected", mode.displayName(), count, state.getBlock().getName());
		player.sendOverlayMessage(message);
	}

	private static void onBlockBroken(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
		if (MiningJob.isBreaking() || !(player instanceof ServerPlayer serverPlayer)) return;

		VeinSelection selection = SELECTIONS.get(player.getUUID());
		if (selection == null || !selection.contains(level, pos) || !state.is(selection.blockAt(pos))) return;

		SELECTIONS.remove(player.getUUID());
		ServerPlayNetworking.send(serverPlayer, new SelectionSyncPayload(List.of()));

		// Erster Schwung sofort, der Rest (nur bei großen Adern) in den folgenden Ticks
		MiningJob job = new MiningJob(serverPlayer, selection, pos);
		if (!job.tick(serverPlayer.level().getServer())) JOBS.add(job);
	}

	private static void clear(ServerPlayer player) {
		if (SELECTIONS.remove(player.getUUID()) != null) {
			ServerPlayNetworking.send(player, new SelectionSyncPayload(List.of()));
			player.sendOverlayMessage(Component.translatable("message.veinselect.cleared"));
		}
	}
}

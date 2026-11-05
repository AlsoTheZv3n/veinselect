package dev.veinselect.client;

import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import org.lwjgl.sdl.SDLScancode;

import dev.veinselect.VeinMode;
import dev.veinselect.VeinSelect;
import dev.veinselect.net.LimitsSyncPayload;
import dev.veinselect.net.MaxBlocksPayload;
import dev.veinselect.net.ModePayload;
import dev.veinselect.net.SelectVeinPayload;
import dev.veinselect.net.SelectionSyncPayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

public class VeinSelectClient implements ClientModInitializer {
	private static final int FILL_COLOR = ARGB.colorFromFloat(0.35f, 0.2f, 0.85f, 1.0f);
	private static final int OUTLINE_COLOR = ARGB.colorFromFloat(1.0f, 0.3f, 0.9f, 1.0f);
	/** Gleiche Farbe, aber schwach: Kanten in oder hinter Blöcken. */
	private static final int HIDDEN_OUTLINE_COLOR = ARGB.colorFromFloat(0.35f, 0.3f, 0.9f, 1.0f);
	/** Relativ zur Breite der Vanilla-Blockumrandung. */
	private static final float OUTLINE_WIDTH_FACTOR = 1.5f;

	private static KeyMapping selectKey;
	/** V ist gedrückt. Eigene Flankenerkennung, weil Tastenwiederholung consumeClick() mehrfach auslöst. */
	private static boolean keyHeld = false;
	/** Beim Loslassen von V die Auswahl aufheben; entfällt, sobald mit dem Mausrad der Modus gewechselt wurde. */
	private static boolean clearOnRelease = false;

	// Nur auf dem Render-Thread verwendet
	private static VeinHighlight highlight = null;
	private static ClientLevel selectionLevel = null;
	/** Vom Server erlaubtes Maximum, -1 = unbekannt (nicht verbunden oder Server ohne Mod). */
	private static int serverAllowed = -1;

	@Override
	public void onInitializeClient() {
		VeinSelectClientConfig.load();

		KeyMapping.Category category = KeyMapping.Category.register(VeinSelect.id("main"));
		selectKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.veinselect.select", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_V, category));

		ClientTickEvents.END_CLIENT_TICK.register(VeinSelectClient::onTick);

		ClientPlayNetworking.registerGlobalReceiver(SelectionSyncPayload.TYPE, (payload, context) -> {
			highlight = payload.positions().isEmpty() ? null : VeinHighlight.build(payload.positions());
			selectionLevel = context.client().level;
		});
		ClientPlayNetworking.registerGlobalReceiver(LimitsSyncPayload.TYPE, (payload, context) -> {
			// Kommt auch nach /veinselect max: Wert übernehmen, damit er in den Einstellungen gespeichert ist
			VeinSelectClientConfig.setMaxBlocks(payload.maxBlocks());
			serverAllowed = payload.allowed();
		});
		// Kommt nach /veinselect mode: Modus übernehmen, damit er in den Einstellungen gespeichert ist
		ClientPlayNetworking.registerGlobalReceiver(ModePayload.TYPE, (payload, context) -> VeinSelectClientConfig.setMode(payload.mode()));

		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
			sendMaxBlocks();
			sendMode();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
			clearHighlight();
			serverAllowed = -1;
			keyHeld = false;
			clearOnRelease = false;
		});

		// COLLECT_SUBMITS statt eines Terrain-Events: Sodium/Iris ersetzen das Terrain-Rendering, dort feuern diese Events nicht
		LevelRenderEvents.COLLECT_SUBMITS.register(VeinSelectClient::renderHighlight);
	}

	/** Vom Server erlaubtes Maximum oder -1, wenn unbekannt. */
	static int serverAllowed() {
		return serverAllowed;
	}

	/** Speichert die Einstellung und meldet sie dem Server, falls verbunden. */
	static void setMaxBlocks(int maxBlocks) {
		VeinSelectClientConfig.setMaxBlocks(maxBlocks);
		sendMaxBlocks();
	}

	private static void sendMaxBlocks() {
		if (Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(MaxBlocksPayload.TYPE)) {
			ClientPlayNetworking.send(new MaxBlocksPayload(VeinSelectClientConfig.maxBlocks()));
		}
	}

	/** Speichert den Modus und meldet ihn dem Server, falls verbunden. Der Server berechnet eine bestehende Auswahl neu. */
	static void setMode(VeinMode mode) {
		VeinSelectClientConfig.setMode(mode);
		sendMode();
	}

	private static void sendMode() {
		if (Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(ModePayload.TYPE)) {
			ClientPlayNetworking.send(new ModePayload(VeinSelectClientConfig.mode()));
		}
	}

	/** Name der Auswahl-Taste, wie in der Tastenbelegung eingestellt. */
	static Component selectKeyName() {
		return selectKey.getTranslatedKeyMessage();
	}

	/**
	 * Vom Mausrad-Mixin: V gehalten + Mausrad = Modus wechseln, wie die Hotbar (nach oben = vorheriger).
	 *
	 * @param steps Rastungen, positiv = nach oben
	 * @return {@code true}, wenn das Mausrad verbraucht wurde und die Hotbar sich nicht bewegen soll
	 */
	public static boolean onScroll(int steps) {
		Minecraft client = Minecraft.getInstance();
		if (!keyHeld || steps == 0 || client.player == null || client.player.isSpectator()
				|| client.getConnection() == null || !ClientPlayNetworking.canSend(ModePayload.TYPE)) {
			return false;
		}

		VeinMode mode = VeinSelectClientConfig.mode().cycle(-steps);
		// Wer gescrollt hat, will die Auswahl behalten, nur in einem anderen Modus
		clearOnRelease = false;
		setMode(mode);
		// Mit Auswahl ersetzt die Antwort des Servers diese Meldung durch Modus + neue Blockanzahl
		client.player.sendOverlayMessage(Component.translatable("message.veinselect.mode", mode.displayName()));
		return true;
	}

	private static void onTick(Minecraft client) {
		// Dimensionswechsel: Hervorhebung gehört zur alten Welt
		if (selectionLevel != null && client.level != selectionLevel) clearHighlight();

		boolean clicked = false;
		while (selectKey.consumeClick()) clicked = true;
		boolean down = selectKey.isDown();

		// clicked fängt auch Antippen ab, das zwischen zwei Ticks gedrückt und wieder losgelassen wurde
		if (!keyHeld && (clicked || down)) {
			keyHeld = true;
			onKeyPressed(client);
		}
		if (keyHeld && !down) {
			keyHeld = false;
			onKeyReleased(client);
		}
	}

	private static void onKeyPressed(Minecraft client) {
		clearOnRelease = false;
		if (client.player == null || client.getConnection() == null) return;

		if (!ClientPlayNetworking.canSend(SelectVeinPayload.TYPE)) {
			client.player.sendOverlayMessage(Component.translatable("message.veinselect.not_on_server"));
			return;
		}

		if (client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && !isSelected(client, hit.getBlockPos())) {
			ClientPlayNetworking.send(new SelectVeinPayload(Optional.of(hit.getBlockPos()), hit.getDirection()));
			return;
		}

		// Auf die Auswahl oder ins Leere: erst beim Loslassen aufheben, damit V + Mausrad noch den Modus wechseln kann
		clearOnRelease = true;
		if (highlight == null) {
			client.player.sendOverlayMessage(Component.translatable("message.veinselect.mode_hint",
					VeinSelectClientConfig.mode().displayName(), selectKeyName()));
		}
	}

	private static void onKeyReleased(Minecraft client) {
		if (!clearOnRelease) return;

		clearOnRelease = false;
		if (client.getConnection() != null && ClientPlayNetworking.canSend(SelectVeinPayload.TYPE)) {
			ClientPlayNetworking.send(SelectVeinPayload.clear());
		}
	}

	private static boolean isSelected(Minecraft client, BlockPos pos) {
		VeinHighlight vein = highlight;
		return vein != null && client.level == selectionLevel && vein.contains(pos);
	}

	private static void renderHighlight(LevelRenderContext context) {
		VeinHighlight vein = highlight;
		Minecraft client = Minecraft.getInstance();
		if (vein == null || client.level != selectionLevel) return;

		boolean shaders = IrisCompat.shadersActive();
		if (shaders && IrisCompat.renderingShadowPass()) return;

		// Relativ zur Kamera rechnen (double), damit es auch weit vom Spawn nicht flackert
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float dx = (float) (vein.origin().getX() - camera.x);
		float dy = (float) (vein.origin().getY() - camera.y);
		float dz = (float) (vein.origin().getZ() - camera.z);
		PoseStack poseStack = context.poseStack();

		// Die halbtransparente Füllung zeigen Shader-Packs nicht oder falsch an, die Linien schon
		if (!shaders) {
			context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(),
					(pose, buffer) -> vein.renderFill(pose, buffer, dx, dy, dz, FILL_COLOR));
		}

		float width = client.gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth * OUTLINE_WIDTH_FACTOR;

		// Schwach durch Blöcke hindurch: zeigt auch verdeckte Teile, z. B. wohin eine Treppe oder ein Tunnel führt
		RenderType seeThrough = SeeThroughLines.renderType(shaders);
		if (seeThrough != null) {
			context.submitNodeCollector().submitCustomGeometry(poseStack, seeThrough,
					(pose, buffer) -> vein.renderOutline(pose, buffer, dx, dy, dz, HIDDEN_OUTLINE_COLOR, width));
		}

		// Sichtbare Kanten kräftig darüber; gleicher Render-Typ und gleiche Breite wie die Vanilla-Blockumrandung
		RenderType lines = client.gameRenderer.useImprovedTransparency() ? RenderTypes.linesTranslucentNoDepthWrite() : RenderTypes.linesTranslucent();
		context.submitNodeCollector().submitCustomGeometry(poseStack, lines,
				(pose, buffer) -> vein.renderOutline(pose, buffer, dx, dy, dz, OUTLINE_COLOR, width));
	}

	private static void clearHighlight() {
		highlight = null;
		selectionLevel = null;
	}
}

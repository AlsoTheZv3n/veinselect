package dev.veinselect.client;

import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import org.lwjgl.sdl.SDLScancode;

import dev.veinselect.VeinSelect;
import dev.veinselect.net.LimitsSyncPayload;
import dev.veinselect.net.MaxBlocksPayload;
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
	/** Relativ zur Breite der Vanilla-Blockumrandung. */
	private static final float OUTLINE_WIDTH_FACTOR = 1.5f;

	private static KeyMapping selectKey;

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

		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> sendMaxBlocks());
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
			clearHighlight();
			serverAllowed = -1;
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

	private static void onTick(Minecraft client) {
		// Dimensionswechsel: Hervorhebung gehört zur alten Welt
		if (selectionLevel != null && client.level != selectionLevel) clearHighlight();

		while (selectKey.consumeClick()) {
			if (client.player == null || client.getConnection() == null) continue;

			if (!ClientPlayNetworking.canSend(SelectVeinPayload.TYPE)) {
				client.player.sendOverlayMessage(Component.translatable("message.veinselect.not_on_server"));
				continue;
			}

			Optional<BlockPos> target = Optional.empty();
			if (client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
				target = Optional.of(hit.getBlockPos());
			}

			ClientPlayNetworking.send(new SelectVeinPayload(target));
		}
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

		// Gleicher Render-Typ und gleiche Breite wie die Vanilla-Blockumrandung
		RenderType lines = client.gameRenderer.useImprovedTransparency() ? RenderTypes.linesTranslucentNoDepthWrite() : RenderTypes.linesTranslucent();
		float width = client.gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth * OUTLINE_WIDTH_FACTOR;
		context.submitNodeCollector().submitCustomGeometry(poseStack, lines,
				(pose, buffer) -> vein.renderOutline(pose, buffer, dx, dy, dz, OUTLINE_COLOR, width));
	}

	private static void clearHighlight() {
		highlight = null;
		selectionLevel = null;
	}
}

package dev.veinselect.client;

import java.util.List;
import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.lwjgl.sdl.SDLScancode;

import dev.veinselect.VeinSelect;
import dev.veinselect.net.SelectVeinPayload;
import dev.veinselect.net.SelectionSyncPayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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
	private static final int HIGHLIGHT_COLOR = ARGB.colorFromFloat(0.35f, 0.2f, 0.85f, 1.0f);
	private static final float INFLATE = 0.003f;

	private static KeyMapping selectKey;

	// Nur auf dem Render-Thread verwendet
	private static List<BlockPos> selected = List.of();
	private static ClientLevel selectionLevel = null;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(VeinSelect.id("main"));
		selectKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.veinselect.select", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_V, category));

		ClientTickEvents.END_CLIENT_TICK.register(VeinSelectClient::onTick);

		ClientPlayNetworking.registerGlobalReceiver(SelectionSyncPayload.TYPE, (payload, context) -> {
			selected = List.copyOf(payload.positions());
			selectionLevel = context.client().level;
		});

		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> clearHighlight());
		LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(VeinSelectClient::renderHighlight);
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
		if (selected.isEmpty() || Minecraft.getInstance().level != selectionLevel) return;

		Vec3 camera = context.levelState().cameraRenderState.pos;
		List<BlockPos> positions = selected;
		PoseStack poseStack = context.poseStack();

		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> {
			for (BlockPos pos : positions) {
				// Relativ zur Kamera rechnen (double), damit es auch weit vom Spawn nicht flackert
				float x = (float) (pos.getX() - camera.x);
				float y = (float) (pos.getY() - camera.y);
				float z = (float) (pos.getZ() - camera.z);
				drawBox(pose, buffer, x - INFLATE, y - INFLATE, z - INFLATE, x + 1 + INFLATE, y + 1 + INFLATE, z + 1 + INFLATE);
			}
		});
	}

	private static void drawBox(PoseStack.Pose pose, VertexConsumer v, float x0, float y0, float z0, float x1, float y1, float z1) {
		int c = HIGHLIGHT_COLOR;
		// Vorne
		v.addVertex(pose, x0, y0, z0).setColor(c); v.addVertex(pose, x1, y0, z0).setColor(c);
		v.addVertex(pose, x1, y1, z0).setColor(c); v.addVertex(pose, x0, y1, z0).setColor(c);
		// Hinten
		v.addVertex(pose, x1, y0, z1).setColor(c); v.addVertex(pose, x0, y0, z1).setColor(c);
		v.addVertex(pose, x0, y1, z1).setColor(c); v.addVertex(pose, x1, y1, z1).setColor(c);
		// Links
		v.addVertex(pose, x0, y0, z1).setColor(c); v.addVertex(pose, x0, y0, z0).setColor(c);
		v.addVertex(pose, x0, y1, z0).setColor(c); v.addVertex(pose, x0, y1, z1).setColor(c);
		// Rechts
		v.addVertex(pose, x1, y0, z0).setColor(c); v.addVertex(pose, x1, y0, z1).setColor(c);
		v.addVertex(pose, x1, y1, z1).setColor(c); v.addVertex(pose, x1, y1, z0).setColor(c);
		// Oben
		v.addVertex(pose, x0, y1, z0).setColor(c); v.addVertex(pose, x1, y1, z0).setColor(c);
		v.addVertex(pose, x1, y1, z1).setColor(c); v.addVertex(pose, x0, y1, z1).setColor(c);
		// Unten
		v.addVertex(pose, x0, y0, z1).setColor(c); v.addVertex(pose, x1, y0, z1).setColor(c);
		v.addVertex(pose, x1, y0, z0).setColor(c); v.addVertex(pose, x0, y0, z0).setColor(c);
	}

	private static void clearHighlight() {
		selected = List.of();
		selectionLevel = null;
	}
}

package dev.veinselect.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import dev.veinselect.client.VeinSelectClient;

import net.minecraft.client.MouseHandler;

/**
 * V gehalten + Mausrad = Modus wechseln statt Hotbar-Slot.
 * Vanilla fragt den ScrollWheelHandler erst ab, wenn kein Screen offen ist und ein Spieler existiert.
 * So bleibt dessen Glättung für Touchpads erhalten.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@ModifyExpressionValue(method = "onScroll", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/ScrollWheelHandler;onMouseScroll(DD)Lorg/joml/Vector2i;"))
	private Vector2i veinselect$switchMode(Vector2i scroll) {
		// Wie Vanilla: senkrecht hat Vorrang, waagerecht zählt umgekehrt
		int steps = scroll.y != 0 ? scroll.y : -scroll.x;
		// (0, 0) lässt Vanilla danach nichts mehr tun
		return VeinSelectClient.onScroll(steps) ? new Vector2i() : scroll;
	}
}

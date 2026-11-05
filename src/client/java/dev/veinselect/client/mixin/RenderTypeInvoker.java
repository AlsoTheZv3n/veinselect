package dev.veinselect.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/** {@code RenderType.create} ist package-private, wird aber für eigene RenderTypes gebraucht. */
@Mixin(RenderType.class)
public interface RenderTypeInvoker {
	@Invoker("create")
	static RenderType veinselect$create(String name, RenderSetup setup) {
		throw new AssertionError("Mixin nicht angewendet");
	}
}

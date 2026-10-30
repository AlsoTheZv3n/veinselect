package dev.veinselect.client;

import java.lang.reflect.Method;

import dev.veinselect.VeinSelect;

import net.fabricmc.loader.api.FabricLoader;

/** Fragt per Reflection die Iris-API ab, damit Iris keine Abhängigkeit ist. Ohne Iris immer {@code false}. */
final class IrisCompat {
	private static final Object API;
	private static final Method SHADER_PACK_IN_USE;
	private static final Method RENDERING_SHADOW_PASS;

	static {
		Object api = null;
		Method inUse = null;
		Method shadowPass = null;

		if (FabricLoader.getInstance().isModLoaded("iris")) {
			try {
				Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				api = apiClass.getMethod("getInstance").invoke(null);
				inUse = apiClass.getMethod("isShaderPackInUse");
				shadowPass = apiClass.getMethod("isRenderingShadowPass");
			} catch (ReflectiveOperationException | LinkageError e) {
				VeinSelect.LOGGER.warn("Iris gefunden, aber die Iris-API ist nicht nutzbar", e);
				api = null;
			}
		}

		API = api;
		SHADER_PACK_IN_USE = inUse;
		RENDERING_SHADOW_PASS = shadowPass;
	}

	private IrisCompat() {}

	static boolean shadersActive() {
		return call(SHADER_PACK_IN_USE);
	}

	static boolean renderingShadowPass() {
		return call(RENDERING_SHADOW_PASS);
	}

	private static boolean call(Method method) {
		if (API == null) return false;

		try {
			return (boolean) method.invoke(API);
		} catch (ReflectiveOperationException e) {
			return false;
		}
	}
}

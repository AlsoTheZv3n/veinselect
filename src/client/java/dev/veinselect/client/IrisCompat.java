package dev.veinselect.client;

import java.lang.reflect.Method;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import dev.veinselect.VeinSelect;

import net.fabricmc.loader.api.FabricLoader;

/** Fragt per Reflection die Iris-API ab, damit Iris keine Abhängigkeit ist. Ohne Iris immer {@code false}. */
final class IrisCompat {
	private static final Object API;
	private static final Method SHADER_PACK_IN_USE;
	private static final Method RENDERING_SHADOW_PASS;
	/** {@code assignPipeline(RenderPipeline, IrisProgram)}, fehlt in älteren Iris-Versionen. */
	private static final Method ASSIGN_PIPELINE;
	private static final Object LINES_PROGRAM;

	static {
		Object api = null;
		Class<?> apiClass = null;
		Method inUse = null;
		Method shadowPass = null;
		Method assign = null;
		Object lines = null;

		if (FabricLoader.getInstance().isModLoaded("iris")) {
			try {
				apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				api = apiClass.getMethod("getInstance").invoke(null);
				inUse = apiClass.getMethod("isShaderPackInUse");
				shadowPass = apiClass.getMethod("isRenderingShadowPass");
			} catch (ReflectiveOperationException | LinkageError e) {
				VeinSelect.LOGGER.warn("Iris gefunden, aber die Iris-API ist nicht nutzbar", e);
				api = null;
			}

			if (api != null) {
				try {
					Class<?> programClass = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
					assign = apiClass.getMethod("assignPipeline", RenderPipeline.class, programClass);
					lines = enumConstant(programClass, "LINES");
				} catch (ReflectiveOperationException | LinkageError | IllegalArgumentException e) {
					VeinSelect.LOGGER.info("Iris kann keine eigenen Pipelines zuordnen, Umrandung durch Blöcke bleibt mit Shadern aus");
					assign = null;
				}
			}
		}

		API = api;
		SHADER_PACK_IN_USE = inUse;
		RENDERING_SHADOW_PASS = shadowPass;
		ASSIGN_PIPELINE = assign;
		LINES_PROGRAM = lines;
	}

	private IrisCompat() {}

	/**
	 * Sagt Iris, dass {@code pipeline} wie Vanilla-Linien gezeichnet werden soll.
	 *
	 * @return {@code true}, wenn Iris sie mit Shader-Pack korrekt zeichnen kann (ohne Iris immer {@code false})
	 */
	static boolean assignLinesPipeline(RenderPipeline pipeline) {
		if (ASSIGN_PIPELINE == null) return false;

		try {
			ASSIGN_PIPELINE.invoke(API, pipeline, LINES_PROGRAM);
			return true;
		} catch (ReflectiveOperationException e) {
			VeinSelect.LOGGER.warn("Iris konnte die Pipeline nicht zuordnen", e);
			return false;
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object enumConstant(Class<?> enumClass, String name) {
		return Enum.valueOf((Class) enumClass, name);
	}

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

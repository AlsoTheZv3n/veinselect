package dev.veinselect.client;

import java.util.Optional;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import dev.veinselect.VeinSelect;
import dev.veinselect.client.mixin.RenderTypeInvoker;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Linien ohne Tiefentest: Teile der Auswahl, die in oder hinter Blöcken liegen, bleiben sichtbar,
 * z. B. wohin eine Treppe nach unten führt. Aufgebaut wie Vanillas {@code lines_translucent},
 * nur ohne Tiefentest (so wie Vanillas {@code text_see_through}).
 * Wird erst beim ersten Zeichnen erzeugt, dann ist Iris sicher bereit.
 */
final class SeeThroughLines {
	private static final String NAME = "veinselect_lines_see_through";

	/** {@code null}, wenn die Pipeline nicht erzeugt werden konnte; dann gibt es nur die normale Umrandung. */
	private static final RenderType RENDER_TYPE;
	/** Mit Shader-Pack nur zeichnen, wenn Iris die Pipeline als Linien kennt, sonst fehlt ihr das passende Programm. */
	private static final boolean SHADER_READY;

	static {
		RenderType renderType = null;
		boolean shaderReady = false;

		try {
			RenderPipeline pipeline = lines(RenderPipeline.builder()
							.withBindGroupLayout(BindGroupLayouts.GLOBALS)
							.withBindGroupLayout(BindGroupLayouts.PROJECTION))
					.withLocation(VeinSelect.id("pipeline/lines_see_through"))
					.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
					.withDepthStencilState(Optional.empty())
					.build();

			// Für „Verbesserte Transparenz“: GLOBALS und PROJECTION bringen die OIT-Snippets selbst mit, wie bei Vanillas OIT-Linien
			OitPipelineSet oit = OitPipelineSet.builder(NAME, lines(RenderPipeline.builder()))
					.withoutDepthTest()
					.build();

			renderType = RenderTypeInvoker.veinselect$create(NAME, RenderSetup.builder(pipeline)
					.setOitPipelines(oit)
					.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
					.createRenderSetup());
			shaderReady = IrisCompat.assignLinesPipeline(pipeline);
		} catch (RuntimeException | LinkageError e) {
			VeinSelect.LOGGER.warn("Umrandung durch Blöcke hindurch nicht verfügbar", e);
		}

		RENDER_TYPE = renderType;
		SHADER_READY = shaderReady;
	}

	private SeeThroughLines() {}

	/** @return {@code null}, wenn gerade nicht durch Blöcke hindurch gezeichnet werden kann */
	static RenderType renderType(boolean shaders) {
		return shaders && !SHADER_READY ? null : RENDER_TYPE;
	}

	/** Shader und Vertex-Format wie Vanillas Linien. */
	private static RenderPipeline.Builder lines(RenderPipeline.Builder builder) {
		return builder
				.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
				.withBindGroupLayout(BindGroupLayouts.FOG)
				.withVertexShader("core/rendertype_lines")
				.withFragmentShader("core/rendertype_lines")
				.withCull(false)
				.withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
				.withPrimitiveTopology(PrimitiveTopology.LINES);
	}
}

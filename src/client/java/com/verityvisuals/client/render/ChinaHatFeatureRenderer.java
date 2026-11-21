package com.verityvisuals.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.verityvisuals.client.config.VisualSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.jspecify.annotations.NonNull;

public final class ChinaHatFeatureRenderer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final int SEGMENTS = 24;
	private static final float HEAD_TOP = -0.52F;

	public ChinaHatFeatureRenderer(AvatarRenderer<?> renderer) {
		super(renderer);
	}

	@Override
	public void submit(@NonNull PoseStack poseStack, @NonNull SubmitNodeCollector collector, int packedLight,
					   AvatarRenderState state, float bodyYaw, float headPitch) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!VisualSettings.chinaHatEnabled || state.isInvisible || minecraft.player == null
				|| state.id != minecraft.player.getId()) {
			return;
		}

		poseStack.pushPose();
		getParentModel().head.translateAndRotate(poseStack);
		float radius = VisualSettings.chinaHatSize * 0.5F;
		float height = VisualSettings.chinaHatHeight;
		float opacity = VisualSettings.chinaHatOpacity;
		int color = VisualSettings.chinaHatColor;
		int red = color >> 16 & 255;
		int green = color >> 8 & 255;
		int blue = color & 255;
		int alpha = Math.round(opacity * 255.0F);

		collector.submitCustomGeometry(poseStack, RenderTypes.debugTriangleFan(), (pose, vertices) ->
				renderCone(pose, vertices, radius, height, red, green, blue, alpha));
		poseStack.popPose();
	}

	private static void renderCone(PoseStack.@NonNull Pose pose, @NonNull VertexConsumer vertices,
								   float radius, float height,
								   int red, int green, int blue, int alpha) {
		float baseY = HEAD_TOP;
		float tipY = baseY - height;
		for (int i = 0; i < SEGMENTS; i++) {
			float angle1 = (float) (Math.PI * 2.0 * i / SEGMENTS);
			float angle2 = (float) (Math.PI * 2.0 * (i + 1) / SEGMENTS);
			vertices.addVertex(pose, 0.0F, tipY, 0.0F).setColor(red, green, blue, alpha);
			vertices.addVertex(pose, (float) Math.cos(angle1) * radius, baseY,
					(float) Math.sin(angle1) * radius).setColor(red, green, blue, alpha);
			vertices.addVertex(pose, (float) Math.cos(angle2) * radius, baseY,
					(float) Math.sin(angle2) * radius).setColor(red, green, blue, alpha);
		}
	}
}

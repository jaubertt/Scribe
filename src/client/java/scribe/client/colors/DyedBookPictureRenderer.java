package scribe.client.colors;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class DyedBookPictureRenderer extends PictureInPictureRenderer<DyedBookPicture> {
	private static final float TILT = 25.0F;
	private static final int BOOK_TOP = 17;

	@Override
	public Class<DyedBookPicture> getRenderStateClass() {
		return DyedBookPicture.class;
	}

	@Override
	protected void renderToTexture(DyedBookPicture picture, PoseStack poseStack, SubmitNodeCollector collector) {
		Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
		draw(picture, poseStack, collector);
	}

	public static void draw(DyedBookPicture picture, PoseStack poseStack, SubmitNodeCollector collector) {
		float shut = 1.0F - picture.open();
		poseStack.rotateDegrees(Axis.YP, 180.0F);
		poseStack.rotateDegrees(Axis.XP, TILT);
		poseStack.translate(shut * 0.2F, shut * 0.1F, shut * 0.25F);
		poseStack.rotateDegrees(Axis.YP, -shut * 90.0F - 90.0F);
		poseStack.rotateDegrees(Axis.XP, 180.0F);
		collector.submitModel(picture.bookModel(), GuiBookTint.bookState(picture.open(), picture.flip()), poseStack, picture.texture(),
			GuiBookTint.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
		GuiBookTint.submit(collector, picture.bookModel(), picture.open(), picture.flip(), poseStack, picture.leather());
	}

	@Override
	protected float getTranslateY(int height, int guiScale) {
		return BOOK_TOP * guiScale;
	}

	@Override
	protected String getTextureLabel() {
		return "dyed enchanting book";
	}
}

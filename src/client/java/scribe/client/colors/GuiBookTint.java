package scribe.client.colors;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

public final class GuiBookTint {
	public static int pending = -1;
	public static final int ORDER = 1;
	public static final int FULL_BRIGHT = 15728880;

	private GuiBookTint() {
	}

	public static RenderType renderType() {
		return RenderTypes.entityCutout(ColorsClient.BOOK_TEXTURE);
	}

	public static BookModel.State bookState(float open, float flip) {
		float flip1 = Mth.clamp(Mth.frac(flip + 0.25F) * 1.6F - 0.3F, 0.0F, 1.0F);
		float flip2 = Mth.clamp(Mth.frac(flip + 0.75F) * 1.6F - 0.3F, 0.0F, 1.0F);
		return BookModel.State.forAnimation(0.0F, flip1, flip2, open);
	}

	public static void submit(SubmitNodeCollector collector, BookModel model, float open, float flip, PoseStack poseStack, int color) {
		if (color == -1) {
			return;
		}
		collector.order(ORDER).submitModel(model, bookState(open, flip), poseStack, renderType(), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, null, 0);
	}
}

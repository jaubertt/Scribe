package scribe.client.colors;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public record DyedBookPicture(BookModel bookModel, Identifier texture, float open, float flip, int leather, int x0, int y0, int x1, int y1,
	float scale, @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {
	public static DyedBookPicture of(BookModel bookModel, Identifier texture, float scale, float open, float flip, int x0, int y0, int x1, int y1,
		@Nullable ScreenRectangle scissor, int leather) {
		return new DyedBookPicture(bookModel, texture, open, flip, leather, x0, y0, x1, y1, scale, scissor,
			PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissor));
	}
}

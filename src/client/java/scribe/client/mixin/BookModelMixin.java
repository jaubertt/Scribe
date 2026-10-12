package scribe.client.mixin;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.book.BookModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import scribe.client.colors.ColorsClient;

@Mixin(BookModel.class)
public abstract class BookModelMixin {
	@Shadow
	@Final
	private ModelPart leftPages;

	@Shadow
	@Final
	private ModelPart rightPages;

	@Shadow
	@Final
	private ModelPart flipPage1;

	@Shadow
	@Final
	private ModelPart flipPage2;

	@Inject(method = "setupAnim(Lnet/minecraft/client/model/object/book/BookModel$State;)V", at = @At("TAIL"))
	private void scribe$closedPages(BookModel.State state, CallbackInfo ci) {
		if (state == ColorsClient.CLOSED_BOOK) {
			ColorsClient.insetClosedPages(this.leftPages, this.rightPages, this.flipPage1, this.flipPage2);
		}
	}
}

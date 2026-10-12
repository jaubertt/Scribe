package scribe.client.mixin;

import scribe.client.colors.ColoredRenderState;
import scribe.client.colors.ColorsClient.LecternClasp;
import net.minecraft.client.renderer.blockentity.state.EnchantTableRenderState;
import net.minecraft.client.renderer.blockentity.state.LecternRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({EnchantTableRenderState.class, LecternRenderState.class})
public abstract class BlockEntityRenderStateColorMixin implements ColoredRenderState {
	@Unique
	private int scribe$color = -1;
	@Unique
	private boolean scribe$foil;
	@Unique
	private @Nullable String scribe$trim;
	@Unique
	private int scribe$strap;
	@Unique
	private @Nullable LecternClasp scribe$clasp;
	@Unique
	private int scribe$title;
	@Unique
	private @Nullable String scribe$spine;
	@Unique
	private boolean scribe$closed;

	@Override
	public void scribe$setColor(int color) {
		this.scribe$color = color;
	}

	@Override
	public int scribe$getColor() {
		return this.scribe$color;
	}

	@Override
	public void scribe$setFoil(boolean foil) {
		this.scribe$foil = foil;
	}

	@Override
	public boolean scribe$isFoil() {
		return this.scribe$foil;
	}

	@Override
	public void scribe$setTrim(@Nullable String material) {
		this.scribe$trim = material;
	}

	@Override
	public @Nullable String scribe$getTrim() {
		return this.scribe$trim;
	}

	@Override
	public void scribe$setStrap(int color) {
		this.scribe$strap = color;
	}

	@Override
	public int scribe$getStrap() {
		return this.scribe$strap;
	}

	@Override
	public void scribe$setClasp(@Nullable LecternClasp clasp) {
		this.scribe$clasp = clasp;
	}

	@Override
	public @Nullable LecternClasp scribe$getClasp() {
		return this.scribe$clasp;
	}

	@Override
	public void scribe$setTitle(int color) {
		this.scribe$title = color;
	}

	@Override
	public int scribe$getTitle() {
		return this.scribe$title;
	}

	@Override
	public void scribe$setSpine(@Nullable String material) {
		this.scribe$spine = material;
	}

	@Override
	public @Nullable String scribe$getSpine() {
		return this.scribe$spine;
	}

	@Override
	public void scribe$setClosed(boolean closed) {
		this.scribe$closed = closed;
	}

	@Override
	public boolean scribe$isClosed() {
		return this.scribe$closed;
	}
}

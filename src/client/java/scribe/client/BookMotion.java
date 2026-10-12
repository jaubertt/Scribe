package scribe.client;

import scribe.client.colors.GuiBookTint;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.util.Mth;

public final class BookMotion {
	public static final float FLIP_PULL = 0.4F;
	public static final float FLIP_MAX_SPEED = 0.2F;
	public static final float FLIP_SMOOTHING = 0.9F;
	public static final float OPEN_STEP = 0.1F;
	public static final int MAX_LEAVES = 3;
	static final float REST = 0.002F;

	private float flip;
	private float oFlip;
	private float flipT;
	private float flipA;
	private float open;
	private float oOpen;
	private boolean opening;

	public BookMotion(boolean open) {
		this.open = open ? 1.0F : 0.0F;
		this.oOpen = this.open;
		this.opening = open;
	}

	public void setOpen(boolean open) {
		this.opening = open;
	}

	public boolean opening() {
		return this.opening;
	}

	public void turn(int leaves) {
		if (leaves == 0) {
			return;
		}
		this.flipT = Mth.clamp(this.flipT + leaves, this.flip - MAX_LEAVES, this.flip + MAX_LEAVES);
	}

	public void tick() {
		this.oOpen = this.open;
		this.open = Mth.clamp(this.open + (this.opening ? OPEN_STEP : -OPEN_STEP), 0.0F, 1.0F);
		this.oFlip = this.flip;
		float diff = Mth.clamp((this.flipT - this.flip) * FLIP_PULL, -FLIP_MAX_SPEED, FLIP_MAX_SPEED);
		this.flipA += (diff - this.flipA) * FLIP_SMOOTHING;
		this.flip += this.flipA;
		if (Math.abs(this.flipT - this.flip) < REST && Math.abs(this.flipA) < REST) {
			float whole = (float) Math.floor(this.flipT);
			this.flipT -= whole;
			this.oFlip -= whole;
			this.flip = this.flipT;
			this.flipA = 0.0F;
		}
	}

	public boolean resting() {
		return this.flip == this.flipT && this.flipA == 0.0F && this.oFlip == this.flip
			&& this.open == (this.opening ? 1.0F : 0.0F) && this.oOpen == this.open;
	}

	public float flip(float partialTicks) {
		return Mth.lerp(partialTicks, this.oFlip, this.flip);
	}

	public float open(float partialTicks) {
		return Mth.lerp(partialTicks, this.oOpen, this.open);
	}

	public float pending() {
		return this.flipT - this.flip;
	}

	public BookModel.State state(float partialTicks, float openness) {
		return GuiBookTint.bookState(this.open(partialTicks) * openness, this.flip(partialTicks));
	}

	public BookModel.State mirroredState(float partialTicks, float openness) {
		return GuiBookTint.bookState(this.open(partialTicks) * openness, -this.flip(partialTicks));
	}
}

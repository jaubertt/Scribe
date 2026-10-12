package scribe.client.screen;

public record SignLayout(int screenWidth, int screenHeight, Rect cover, Rect panel, Rect buttons) {
	public static final int GAP = 6;
	public static final int BUTTON_WIDTH = 90;
	public static final int BUTTON_HEIGHT = 20;
	public static final int BUTTON_GAP = 4;
	public static final int BUTTON_COUNT = 3;
	public static final int BUTTONS_WIDTH = BUTTON_COUNT * BUTTON_WIDTH + (BUTTON_COUNT - 1) * BUTTON_GAP;
	public static final int WIDTH = CoverView.WIDTH + GAP + DesignPanel.WIDTH;
	public static final int HEIGHT = CoverView.HEIGHT + GAP + BUTTON_HEIGHT;
	public static final int NOTE_LINE = 10;
	public static final int NOTE_PAD = DesignPanel.PAD;
	public static final int SYMBOLS_GAP = 3;
	public static final int SYMBOLS_TOP = 2;

	public record Rect(int x, int y, int width, int height) {
		public int right() {
			return this.x + this.width;
		}

		public int bottom() {
			return this.y + this.height;
		}

		public boolean intersects(Rect other) {
			return this.x < other.right() && other.x < this.right() && this.y < other.bottom() && other.y < this.bottom();
		}

		public boolean contains(double px, double py) {
			return px >= this.x && px < this.right() && py >= this.y && py < this.bottom();
		}

		public boolean encloses(Rect other) {
			return other.x >= this.x && other.y >= this.y && other.right() <= this.right() && other.bottom() <= this.bottom();
		}

		public Rect moved(int dx, int dy) {
			return new Rect(this.x + dx, this.y + dy, this.width, this.height);
		}
	}

	public static SignLayout of(int width, int height) {
		int x = Math.max(0, (width - WIDTH) / 2);
		int y = Math.max(0, (height - HEIGHT) / 2);
		Rect cover = new Rect(x, y, CoverView.WIDTH, CoverView.HEIGHT);
		Rect panel = new Rect(cover.right() + GAP, y, DesignPanel.WIDTH, DesignPanel.HEIGHT);
		Rect buttons = new Rect(x + (WIDTH - BUTTONS_WIDTH) / 2, cover.bottom() + GAP, BUTTONS_WIDTH, BUTTON_HEIGHT);
		return new SignLayout(width, height, cover, panel, buttons);
	}

	public SignLayout shifted(int dx) {
		return dx == 0 ? this : new SignLayout(this.screenWidth, this.screenHeight, this.cover.moved(dx, 0), this.panel.moved(dx, 0), this.buttons.moved(dx, 0));
	}

	public Rect symbols() {
		int w = BookGeometry.CELL_WIDTH;
		int h = BookGeometry.CELL_HEIGHT;
		int y = Math.max(this.cover.y() - SYMBOLS_GAP - h, Math.min(SYMBOLS_TOP, this.cover.y() - 1 - h));
		return new Rect(this.cover.x() + CoverView.TEXT_CENTER - w / 2, Math.max(0, y), w, h);
	}

	public Rect button(int index) {
		return new Rect(this.buttons.x() + index * (BUTTON_WIDTH + BUTTON_GAP), this.buttons.y(), BUTTON_WIDTH, BUTTON_HEIGHT);
	}

	public Rect button(int index, int count) {
		int width = count * BUTTON_WIDTH + (count - 1) * BUTTON_GAP;
		int x = this.buttons.x() + (this.buttons.width() - width) / 2;
		return new Rect(x + index * (BUTTON_WIDTH + BUTTON_GAP), this.buttons.y(), BUTTON_WIDTH, BUTTON_HEIGHT);
	}

	public Rect note(int lines) {
		return new Rect(this.panel.x(), this.panel.y(), DesignPanel.WIDTH, 2 * NOTE_PAD + lines * NOTE_LINE);
	}

	public static int noteTextWidth() {
		return DesignPanel.WIDTH - 2 * NOTE_PAD;
	}
}

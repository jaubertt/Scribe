package scribe.client.screen.popup;

import scribe.Scribe;
import scribe.client.compat.Keys;
import scribe.client.screen.BookStyle;
import scribe.client.screen.WheelSteps;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class HelpPopup extends Popup {
	private static final int ROW = 10;
	private static final int FRAME = 26;
	private static final int MARGIN = 2;
	private static final int SIDES = 22;
	private static final int MIN_ACTIONS = 40;
	private static final int ARROW_WIDTH = 7;
	private static final int ARROW_HEIGHT = 8;

	public record Line(String keys, Component action) {
	}

	public record Row(String keys, List<FormattedCharSequence> action) {
		public int lines() {
			return Math.max(1, this.action.size());
		}
	}

	public record Layout(int width, int height, int keysWidth, int actionsWidth, List<List<Row>> pages) {
		public int pageLines() {
			int most = 0;
			for (List<Row> page : this.pages) {
				most = Math.max(most, lines(page));
			}
			return most;
		}
	}

	private final Component title;
	private final Layout layout;
	private final WheelSteps wheel = new WheelSteps();
	private int page;

	public HelpPopup(Font font, Component title, List<List<Line>> groups, int screenWidth, int screenHeight) {
		super(font, 0, 0);
		this.title = title;
		this.layout = layout(font, title, groups, screenWidth, screenHeight);
		this.width = this.layout.width();
		this.height = this.layout.height();
	}

	public static Layout layout(Font font, Component title, List<List<Line>> groups, int screenWidth, int screenHeight) {
		int keys = 0;
		int natural = font.width(title);
		for (List<Line> group : groups) {
			for (Line line : group) {
				keys = Math.max(keys, font.width(line.keys()));
				natural = Math.max(natural, font.width(line.action()));
			}
		}
		int room = screenWidth - 2 * MARGIN - SIDES - keys;
		int actions = Math.max(MIN_ACTIONS, Math.min(natural, room));
		List<List<Row>> rowGroups = new ArrayList<>();
		int total = 0;
		for (List<Line> group : groups) {
			List<Row> rows = new ArrayList<>();
			for (Line line : group) {
				Row row = new Row(line.keys(), font.split(line.action(), actions));
				rows.add(row);
				total += row.lines();
			}
			if (!rows.isEmpty()) {
				rowGroups.add(rows);
			}
		}
		int height = screenHeight - 2 * MARGIN;
		List<List<Row>> pages = new ArrayList<>();
		if (FRAME + total * ROW <= height) {
			List<Row> all = new ArrayList<>();
			rowGroups.forEach(all::addAll);
			pages.add(all);
			return new Layout(keys + actions + SIDES, FRAME + total * ROW, keys, actions, List.copyOf(pages));
		}
		int perPage = Math.max(1, (height - FRAME - ROW) / ROW);
		List<Row> current = new ArrayList<>();
		for (List<Row> group : rowGroups) {
			if (!current.isEmpty() && lines(current) + lines(group) > perPage) {
				pages.add(current);
				current = new ArrayList<>();
			}
			for (Row row : group) {
				if (!current.isEmpty() && lines(current) + row.lines() > perPage) {
					pages.add(current);
					current = new ArrayList<>();
				}
				current.add(row);
			}
		}
		if (!current.isEmpty()) {
			pages.add(current);
		}
		int most = 0;
		for (List<Row> p : pages) {
			most = Math.max(most, lines(p));
		}
		return new Layout(keys + actions + SIDES, FRAME + (most + 1) * ROW, keys, actions, List.copyOf(pages));
	}

	private static int lines(List<Row> rows) {
		int n = 0;
		for (Row row : rows) {
			n += row.lines();
		}
		return n;
	}

	public Layout layout() {
		return this.layout;
	}

	public int page() {
		return this.page;
	}

	public int pages() {
		return this.layout.pages().size();
	}

	public boolean turn(int delta) {
		int next = Math.clamp(this.page + delta, 0, this.pages() - 1);
		if (next == this.page) {
			return false;
		}
		this.page = next;
		return true;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		BookStyle.paper(graphics, this.x, this.y, this.width, this.height);
		graphics.text(this.font, this.title, this.x + 6, this.y + 6, BookStyle.INK, false);
		int rowY = this.y + 20;
		int actionX = this.x + 16 + this.layout.keysWidth();
		for (Row row : this.layout.pages().get(this.page)) {
			graphics.text(this.font, row.keys(), this.x + 6, rowY, BookStyle.INK, false);
			for (FormattedCharSequence line : row.action()) {
				graphics.text(this.font, line, actionX, rowY, BookStyle.INK_SOFT, false);
				rowY += ROW;
			}
			if (row.action().isEmpty()) {
				rowY += ROW;
			}
		}
		if (this.pages() > 1) {
			int footerY = this.footerY();
			String number = (this.page + 1) + " / " + this.pages();
			graphics.text(this.font, number, this.x + this.width / 2 - this.font.width(number) / 2, footerY, BookStyle.PAGE_NUMBER, false);
			if (this.page > 0) {
				boolean hot = this.overArrow(-1, mouseX, mouseY);
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Scribe.id(hot ? "page/previous_highlighted" : "page/previous"), this.arrowX(-1), footerY - 1,
					ARROW_WIDTH, ARROW_HEIGHT);
			}
			if (this.page < this.pages() - 1) {
				boolean hot = this.overArrow(1, mouseX, mouseY);
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Scribe.id(hot ? "page/next_highlighted" : "page/next"), this.arrowX(1), footerY - 1,
					ARROW_WIDTH, ARROW_HEIGHT);
			}
		}
	}

	private int footerY() {
		return this.y + 20 + this.layout.pageLines() * ROW;
	}

	private int arrowX(int direction) {
		return direction < 0 ? this.x + 4 : this.x + this.width - 11;
	}

	private boolean overArrow(int direction, double mouseX, double mouseY) {
		if (direction < 0 ? this.page == 0 : this.page >= this.pages() - 1) {
			return false;
		}
		int ax = this.arrowX(direction);
		int ay = this.footerY() - 1;
		return mouseX >= ax - 2 && mouseX < ax + ARROW_WIDTH + 2 && mouseY >= ay - 2 && mouseY < ay + ARROW_HEIGHT + 2;
	}

	@Override
	public void mouseClicked(double mouseX, double mouseY, int button) {
		if (this.pages() > 1) {
			if (this.overArrow(-1, mouseX, mouseY)) {
				this.turn(-1);
				return;
			}
			if (this.overArrow(1, mouseX, mouseY)) {
				this.turn(1);
				return;
			}
			int footerY = this.footerY();
			if (mouseY >= footerY - 3 && mouseY < footerY + ROW) {
				return;
			}
		}
		this.close();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		int steps = this.wheel.take(amount);
		if (steps != 0) {
			this.turn(steps > 0 ? 1 : -1);
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (Keys.isLeft(event) || Keys.isPageUp(event)) {
			this.turn(-1);
			return true;
		}
		if (Keys.isRight(event) || Keys.isPageDown(event)) {
			this.turn(1);
			return true;
		}
		return false;
	}
}

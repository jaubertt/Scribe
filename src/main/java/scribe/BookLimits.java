package scribe;

import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;

public final class BookLimits {
	public static final int MAX_PAGES = WritableBookContent.MAX_PAGES;
	public static final int PAGE_CHARS = WritableBookContent.PAGE_EDIT_LENGTH;
	public static final int TITLE_CHARS = WrittenBookContent.TITLE_MAX_LENGTH;
	public static final int PAGE_WIDTH = 114;
	public static final int PAGE_LINES = 14;
	public static final int LINE_HEIGHT = 9;
	public static final int OFFHAND_SLOT = 40;

	private BookLimits() {
	}
}

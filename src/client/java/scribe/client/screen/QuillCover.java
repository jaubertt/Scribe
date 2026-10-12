package scribe.client.screen;

import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import scribe.SavedDetails;
import scribe.binding.Binding;
import scribe.binding.Bookbinding;
import scribe.binding.CoverDesign;
import scribe.client.compat.Keys;
import scribe.client.compat.TextKeys;
import scribe.client.memory.BookDetails;
import scribe.client.text.FormatCodes;
import scribe.config.ScribeConfig;

public final class QuillCover {
	private QuillCover() {
	}

	public static boolean wanted() {
		return ScribeConfig.get().coversShown();
	}

	public static boolean hasText(List<String> pages) {
		for (String page : pages) {
			if (!FormatCodes.strip(page).isBlank()) {
				return true;
			}
		}
		return false;
	}

	public static boolean startsAtCover(boolean covers, ScribeConfig.EditorStart start, boolean onLectern, boolean hasText, boolean known,
		boolean closedAtCover) {
		if (!covers || start != ScribeConfig.EditorStart.REMEMBERED) {
			return false;
		}
		if (onLectern) {
			return closedAtCover;
		}
		return !hasText || !known || closedAtCover;
	}

	public static boolean remembers(List<String> savedPages) {
		return hasText(savedPages);
	}

	public static boolean canTurnBack(int basePage, boolean hasCover) {
		return basePage > 0 || hasCover;
	}

	public static boolean startIsCover(int basePage, boolean hasCover) {
		return hasCover && basePage == 0;
	}

	public static boolean turnsToCover(int basePage, int delta, int step, boolean hasCover) {
		return hasCover && delta < 0 && basePage + (long) delta * step < 0;
	}

	public static int openingPage(int delta, int step, int lastBase) {
		if (delta <= 0) {
			return -1;
		}
		return delta == 1 ? 0 : Math.clamp((long) (delta - 1) * step, 0, Math.max(0, lastBase));
	}

	public enum CoverKey {
		OPEN,
		OPEN_LAST,
		SAVE,
		NOTHING,
		SCREEN
	}

	public static CoverKey key(KeyEvent event, TextKeys.Shortcut shortcut, boolean focusFree) {
		return key(event, shortcut, focusFree, Keys.mac());
	}

	public static CoverKey key(KeyEvent event, TextKeys.Shortcut shortcut, boolean focusFree, boolean mac) {
		if (shortcut == TextKeys.Shortcut.SAVE) {
			return CoverKey.SAVE;
		}
		if (shortcut != TextKeys.Shortcut.NONE || event.isPaste()) {
			return CoverKey.NOTHING;
		}
		boolean ctrl = Keys.ctrl(event, mac);
		if (Keys.isPageDown(event)) {
			return ctrl ? CoverKey.OPEN_LAST : CoverKey.OPEN;
		}
		if (Keys.isPageUp(event)) {
			return ctrl ? CoverKey.OPEN : CoverKey.NOTHING;
		}
		if (Keys.isHome(event) || Keys.isEnd(event)) {
			return CoverKey.NOTHING;
		}
		boolean free = focusFree && !Keys.alt(event);
		if (free && (Keys.isEnter(event) || Keys.isRight(event))) {
			return CoverKey.OPEN;
		}
		if (free && Keys.isLeft(event)) {
			return CoverKey.NOTHING;
		}
		return CoverKey.SCREEN;
	}

	public static int coverX(BookGeometry geo) {
		return geo.centerX() - CoverView.WIDTH / 2;
	}

	public static boolean over(BookGeometry geo, double mouseX, double mouseY) {
		int x = coverX(geo);
		return mouseX >= x && mouseX < x + CoverView.WIDTH && mouseY >= geo.top && mouseY < geo.top + CoverView.HEIGHT;
	}

	public static BookDetails.Details savedDetails(BookDetails.Details kept, boolean hasText) {
		return hasText ? kept : BookDetails.Details.NONE;
	}

	public record Opened(BookDetails.Details sign, BookDetails.Details shown) {
	}

	public static Opened opened(ItemStack book, boolean hasText, Optional<BookDetails.Details> local) {
		SavedDetails onBook = SavedDetails.of(book);
		if (onBook != null) {
			BookDetails.Details details = details(onBook);
			return new Opened(details, details);
		}
		BookDetails.Details kept = local.orElse(BookDetails.Details.NONE);
		return new Opened(kept, savedDetails(kept, hasText));
	}

	public static BookDetails.Details details(SavedDetails details) {
		return new BookDetails.Details(details.title(), details.subtitle(), details.penName());
	}

	public static SavedDetails onBook(BookDetails.Details details) {
		return new SavedDetails(details.title(), details.subtitle(), details.penName());
	}

	public static void keepLocally(BookDetails store, String key, BookDetails.Details details, boolean onBook) {
		if (onBook) {
			store.remove(key);
		} else {
			store.put(key, details);
		}
	}

	public static Optional<BookDetails.Details> planned(ItemStack book, Optional<BookDetails.Details> local) {
		SavedDetails onBook = SavedDetails.of(book);
		return onBook != null ? Optional.of(details(onBook)) : local;
	}

	public static boolean showsAuthor(boolean hasText, boolean penName) {
		return hasText || penName;
	}

	static CoverView view(BookEditorScreen editor, Font font) {
		SignForm.Features features = SignForm.Features.current();
		return view(editor.target().book(), editor.cover(), features, editor.target().playerName(), editor.savedTitle(), editor.savedSubtitle(),
			editor.savedPenName(), editor.hasText(), font);
	}

	public static CoverView view(ItemStack book, int cover, SignForm.Features features, String playerName, String title, String subtitle,
		String penName, boolean hasText, Font font) {
		CoverChoices choices = CoverChoices.resume(null, Bookbinding.materialName(Binding.of(book)), CoverDesign.of(book));
		SignForm form = new SignForm(font, features, choices, playerName, title, SignForm.startingSubtitle(features, subtitle),
			SignForm.startingPenName(features, penName));
		CoverText text = form.text();
		boolean author = showsAuthor(hasText, text.showsPenName());
		return CoverView.unsigned(book, cover, font, form.title().value(), form.subtitleLines(), author ? text.author() : "", author && text.showsPenName());
	}
}

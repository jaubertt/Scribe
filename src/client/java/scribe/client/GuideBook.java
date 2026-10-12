package scribe.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jspecify.annotations.Nullable;
import scribe.BookLimits;
import scribe.client.compat.Keys;
import scribe.client.screen.BookReaderScreen;
import scribe.client.screen.SettingsLayout;
import scribe.client.screen.ShortcutsHelp;
import scribe.client.screen.popup.HelpPopup;
import scribe.client.text.Chapters;
import scribe.client.text.PageCodec;
import scribe.client.text.PageFitter;
import scribe.config.ScribeConfig;
import scribe.network.Payloads;
import scribe.server.ServerBooks;

public final class GuideBook {
	public static final String AUTHOR = "Jaubert";
	private static final List<List<String>> CHAPTERS = List.of(
		List.of("welcome", "welcome.1", "welcome.2"),
		List.of("writing", "writing.1", "writing.2", "writing.3", "writing.4"),
		List.of("chapters", "chapters.1", "chapters.2"),
		List.of("signing", "signing.1", "signing.2"),
		List.of("files", "files.1", "files.2"),
		List.of("world", "world.1", "world.2", "world.3"),
		List.of("ink", "ink.1", "ink.2"),
		List.of("binding", "binding.1", "binding.2"),
		List.of("enchanted", "enchanted.1"),
		List.of("commands", "commands.files", "commands.cover", "commands.pose", "commands.guide", "commands.author", "commands.book"));

	private GuideBook() {
	}

	private static final String[][] MARKS = {{"**", "l"}, {"__", "n"}, {"~~", "m"}, {"`", "1"}, {"*", "o"}};

	private static String tr(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	public static String format(String text) {
		StringBuilder out = new StringBuilder(text.length() + 16);
		int i = 0;
		while (i < text.length()) {
			int next = marked(text, i, out);
			if (next == i) {
				out.append(text.charAt(i));
				next = i + 1;
			}
			i = next;
		}
		return out.toString();
	}

	private static int marked(String text, int i, StringBuilder out) {
		for (String[] mark : MARKS) {
			String m = mark[0];
			if (text.startsWith(m, i)) {
				int end = text.indexOf(m, i + m.length());
				if (end > i + m.length()) {
					out.append('§').append(mark[1]).append(text, i + m.length(), end).append("§r");
					return end + m.length();
				}
			}
		}
		return i;
	}

	public static void open(@Nullable Screen parent) {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.gui.setScreen(new GuideScreen(book(minecraft.font), parent));
	}

	public static ItemStack book(Font font) {
		PageFitter.Pagination pages = new PageFitter(font).paginate(text(), BookLimits.MAX_PAGES);
		List<Filterable<Component>> content = new ArrayList<>();
		for (String page : PageCodec.encodeBook(font, pages.marked())) {
			content.add(Filterable.passThrough(Component.literal(page)));
		}
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		String title = tr("scribe.guide.title");
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), AUTHOR, 0, content, true));
		return ServerBooks.applySignDetails(book, new Payloads.SignDetails("", List.of(tr("scribe.guide.subtitle")), "angle_mitred", "wayfinder",
			"gold", "gold", "gold", ""));
	}

	public static String text() {
		List<String> chapters = new ArrayList<>();
		for (List<String> chapter : CHAPTERS) {
			StringBuilder out = new StringBuilder(Chapters.MARK).append(tr("scribe.guide." + chapter.getFirst()));
			for (String paragraph : chapter.subList(1, chapter.size())) {
				out.append("\n\n").append(format(tr("scribe.guide." + paragraph)));
			}
			chapters.add(out.toString());
			if (chapter.getFirst().equals("writing")) {
				chapters.add(shortcuts());
			}
		}
		StringBuilder intro = new StringBuilder(Chapters.MARK).append(tr("scribe.guide.settings")).append("\n\n").append(format(tr("scribe.guide.settings.1")));
		for (SettingsLayout.Row row : SettingsLayout.top()) {
			for (SettingsLayout.Item item : row.items()) {
				if (item.kind() != SettingsLayout.Kind.BUTTON) {
					setting(intro, item);
				}
			}
		}
		chapters.add(intro.toString());
		for (SettingsLayout.Section section : SettingsLayout.sections()) {
			chapters.add(settings(section));
		}
		return String.join("\n---\n", chapters);
	}

	private static String shortcuts() {
		StringBuilder out = new StringBuilder(Chapters.MARK).append(tr("scribe.help.title"));
		List<List<HelpPopup.Line>> groups = ShortcutsHelp.editor(Keys.mac(), Keys.clearFormattingKeys(), ScribeConfig.get().fastScrollPages,
			BookScreens.shiftHelp(false));
		for (List<HelpPopup.Line> group : groups) {
			out.append('\n');
			for (HelpPopup.Line line : group) {
				out.append("\n§l").append(line.keys()).append("§r ").append(line.action().getString());
			}
		}
		return out.toString();
	}

	private static String settings(SettingsLayout.Section section) {
		StringBuilder out = new StringBuilder(Chapters.MARK).append(tr("scribe.settings.section." + section.key()));
		for (SettingsLayout.Row row : section.rows()) {
			for (SettingsLayout.Item item : row.items()) {
				if (item.kind() == SettingsLayout.Kind.NOTE) {
					out.append("\n\n§o").append(tr("scribe.guide.settings.world")).append("§r");
					continue;
				}
				setting(out, item);
			}
		}
		return out.toString();
	}

	private static void setting(StringBuilder out, SettingsLayout.Item item) {
		String key = "scribe.settings." + item.key();
		out.append("\n\n§l").append(tr(key)).append("§r");
		if (Language.getInstance().has(key + ".tooltip")) {
			int pages = ScribeConfig.get().fastScrollPages;
			out.append('\n').append(tr(key + ".tooltip", Keys.ctrlName(), Keys.altName(), pages, 2 * pages));
		}
	}

	public static final class GuideScreen extends BookReaderScreen {
		private final @Nullable Screen parent;

		GuideScreen(ItemStack book, @Nullable Screen parent) {
			super(book, null);
			this.parent = parent;
			this.setOnCover(true);
		}

		@Override
		public void onClose() {
			super.onClose();
			if (this.parent != null && this.minecraft != null) {
				this.minecraft.gui.setScreen(this.parent);
			}
		}
	}
}

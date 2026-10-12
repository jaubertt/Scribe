package scribe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jspecify.annotations.Nullable;

public final class ContentsLinks {
	public static final String MARK = "§*";
	private static final String COLOR_CODES = "0123456789abcdef";
	private static final String STYLE_CODES = "klmno";

	private ContentsLinks() {
	}

	public static int targetPage(String visibleLine, int pageCount) {
		String line = visibleLine.strip();
		int end = line.length();
		int start = end;
		while (start > 0 && line.charAt(start - 1) >= '0' && line.charAt(start - 1) <= '9') {
			start--;
		}
		if (start == end || end - start > 3 || start < 2 || line.charAt(start - 1) != ' ') {
			return -1;
		}
		int page = Integer.parseInt(line.substring(start, end));
		return page >= 1 && page <= pageCount ? page : -1;
	}

	public static boolean isContentsPage(String text) {
		return text.startsWith(MARK);
	}

	public static WrittenBookContent link(WrittenBookContent content) {
		int count = content.pages().size();
		List<Filterable<Component>> pages = new ArrayList<>(count);
		boolean changed = false;
		for (Filterable<Component> page : content.pages()) {
			Filterable<Component> linked = page.map(component -> link(component, count));
			boolean pageChanged = linked.raw() != page.raw() || linked.filtered().orElse(null) != page.filtered().orElse(null);
			pages.add(pageChanged ? linked : page);
			changed |= pageChanged;
		}
		return changed ? new WrittenBookContent(content.title(), content.author(), content.generation(), pages, content.resolved()) : content;
	}

	public static Component link(Component page, int pageCount) {
		if (page.getContents() instanceof PlainTextContents.LiteralContents(String text) && page.getSiblings().isEmpty() && page.getStyle().isEmpty()) {
			Component linked = linkText(text, pageCount);
			return linked == null ? page : linked;
		}
		return page;
	}

	static @Nullable Component linkText(String text, int pageCount) {
		if (!isContentsPage(text)) {
			return null;
		}
		MutableComponent root = Component.empty();
		StringBuilder pending = new StringBuilder();
		boolean linked = false;
		int start = 0;
		while (true) {
			int end = text.indexOf('\n', start);
			if (end < 0) {
				end = text.length();
			}
			String line = text.substring(start, end);
			int target = targetPage(strip(line), pageCount);
			if (target > 0) {
				if (!pending.isEmpty()) {
					root.append(Component.literal(pending.toString()));
					pending.setLength(0);
				}
				root.append(Component.literal(codesAt(text, start) + line).withStyle(Style.EMPTY.withClickEvent(new ClickEvent.ChangePage(target))));
				linked = true;
				pending.append(codesAt(text, end));
			} else {
				pending.append(line);
			}
			if (end >= text.length()) {
				break;
			}
			pending.append('\n');
			start = end + 1;
		}
		if (!pending.isEmpty()) {
			root.append(Component.literal(pending.toString()));
		}
		return linked ? root : null;
	}

	static String strip(String text) {
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '§') {
				i++;
			} else {
				out.append(c);
			}
		}
		return out.toString();
	}

	static String codesAt(String text, int index) {
		char color = 0;
		boolean[] styles = new boolean[STYLE_CODES.length()];
		int end = Math.min(index, text.length());
		for (int i = 0; i < end; i++) {
			if (text.charAt(i) != '§') {
				continue;
			}
			if (i + 1 < end) {
				char code = Character.toLowerCase(text.charAt(i + 1));
				if (code == 'r') {
					color = 0;
					Arrays.fill(styles, false);
				} else if (COLOR_CODES.indexOf(code) >= 0) {
					color = code;
					Arrays.fill(styles, false);
				} else if (STYLE_CODES.indexOf(code) >= 0) {
					styles[STYLE_CODES.indexOf(code)] = true;
				}
			}
			i++;
		}
		StringBuilder out = new StringBuilder();
		if (color != 0) {
			out.append('§').append(color);
		}
		for (int i = 0; i < styles.length; i++) {
			if (styles[i]) {
				out.append('§').append(STYLE_CODES.charAt(i));
			}
		}
		return out.toString();
	}
}

package scribe.client.screen;

import scribe.client.compat.Keys;
import scribe.client.screen.popup.HelpPopup;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

public final class ShortcutsHelp {
	private static final String LEFT_RIGHT = "← / →";
	private static final String UP_DOWN = "↑ / ↓";

	private ShortcutsHelp() {
	}

	public static List<List<HelpPopup.Line>> editor(boolean mac, String clearKeys, int fastPages, HelpPopup.Line shift) {
		String c = Keys.ctrlName(mac);
		String a = Keys.altName(mac);
		List<HelpPopup.Line> formatting = List.of(
			line(c + "+B / I / U / K", "scribe.help.style"),
			line(c + "+Shift+X", "scribe.button.strikethrough"),
			line(clearKeys, "scribe.button.reset"),
			line(c + "+L / E / R", "scribe.help.align"),
			line(c + "+" + a + "+1", "scribe.button.chapter"),
			line(a, "scribe.help.section_sign"));
		List<HelpPopup.Line> editing = List.of(
			line(c + "+Z", "scribe.button.undo"),
			line(c + "+Shift+Z / " + c + "+Y", "scribe.button.redo"),
			line(c + "+Enter", "scribe.page.split"),
			line(c + "+S", "scribe.help.save"));
		List<HelpPopup.Line> pages = new ArrayList<>();
		pages.add(line(c + "+PgUp / PgDn", "scribe.help.first_last"));
		pages.addAll(navigation(mac, fastPages, true));
		pages.add(shift);
		return List.of(formatting, editing, caret(mac), List.copyOf(pages));
	}

	public static List<HelpPopup.Line> caret(boolean mac) {
		if (mac) {
			return List.of(
				line("Option+" + LEFT_RIGHT, "scribe.help.word"),
				line("Cmd+" + LEFT_RIGHT, "scribe.help.line_ends"),
				line("Cmd+" + UP_DOWN, "scribe.help.page_ends"),
				line("Option+Backspace", "scribe.help.delete_word_before"),
				line("Option+Delete", "scribe.help.delete_word_after"),
				line("Cmd+Backspace / Delete", "scribe.help.delete_line"),
				line("Shift", "scribe.help.select"));
		}
		return List.of(
			line("Ctrl+" + LEFT_RIGHT, "scribe.help.word"),
			line("Home / End", "scribe.help.line_ends"),
			line("Ctrl+Home / End", "scribe.help.page_ends"),
			line("Ctrl+Backspace", "scribe.help.delete_word_before"),
			line("Ctrl+Delete", "scribe.help.delete_word_after"),
			line("Shift", "scribe.help.select"));
	}

	public static List<HelpPopup.Line> navigation(boolean mac, int fastPages, boolean chapters) {
		List<HelpPopup.Line> lines = new ArrayList<>();
		lines.add(line("PgUp / PgDn", "scribe.help.turn"));
		lines.add(new HelpPopup.Line(Component.translatable("scribe.help.wheel_keys").getString(),
			Component.translatable("scribe.help.wheel", Keys.ctrlName(mac), fastPages)));
		if (chapters) {
			lines.add(line(Keys.altName(mac) + " + " + UP_DOWN, "scribe.help.chapters"));
		}
		lines.add(new HelpPopup.Line(Component.translatable("scribe.help.number_keys").getString(), Component.translatable("scribe.help.number")));
		return lines;
	}

	public static List<List<HelpPopup.Line>> reader(boolean mac, int fastPages, boolean bookmarks, boolean chapters, HelpPopup.Line shift) {
		List<HelpPopup.Line> reading = new ArrayList<>();
		reading.add(line(LEFT_RIGHT, "scribe.help.turn"));
		reading.add(line("Home / End", "scribe.help.first_last"));
		if (bookmarks) {
			reading.add(new HelpPopup.Line(Component.translatable("scribe.help.right_click").getString(), Component.translatable("scribe.help.bookmark_color")));
		}
		List<HelpPopup.Line> pages = new ArrayList<>(navigation(mac, fastPages, chapters));
		pages.add(shift);
		return List.of(List.copyOf(reading), List.copyOf(pages));
	}

	private static HelpPopup.Line line(String keys, String action) {
		return new HelpPopup.Line(keys, Component.translatable(action));
	}
}

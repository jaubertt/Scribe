package scribe.client;

import scribe.Scribe;
import scribe.client.compat.Keys;
import scribe.client.io.BookFiles;
import scribe.client.mixin.AbstractSignEditScreenAccessor;
import scribe.client.screen.BookStyle;
import scribe.client.screen.SignEditLayout;
import scribe.client.screen.SymbolPlacement;
import scribe.client.screen.popup.MenuPopup;
import scribe.client.screen.popup.Popup;
import scribe.client.screen.popup.SymbolPopup;
import scribe.client.text.SignEditor;
import scribe.client.widget.SpriteButton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public final class SignTools {
	private static final Map<Screen, Popup> POPUPS = new WeakHashMap<>();
	private static final int MAX_LOAD = 10;
	private static final long NOTICE_MS = 2500;
	private static final String[] BUTTONS = {"copy", "paste", "save", "load"};
	private static final String[] STYLE_NAMES = {"bold", "italic", "underline", "strikethrough", "obfuscated"};
	private static final String[] STYLE_SHORTCUTS = {"+B", "+I", "+U", "+Shift+X", "+K"};

	private SignTools() {
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof AbstractSignEditScreen sign) {
				install(sign, width);
			}
		});
	}

	private static final class Tools {
		SignEditor editor;
		final List<Button> largeButtons = new ArrayList<>();
		final List<SpriteButton> formatCells = new ArrayList<>();
		final List<SignEditLayout.Cell> formatLayout = new ArrayList<>();
		@Nullable Boolean shownAvailable;
		@Nullable Component notice;
		long noticeUntil;

		void notice(Component message) {
			this.notice = message;
			this.noticeUntil = Util.getMillis() + NOTICE_MS;
		}

		void refresh() {
			boolean available = this.editor.formattingAvailable();
			if (this.shownAvailable == null || this.shownAvailable != available) {
				this.shownAvailable = available;
				Component why = available ? null : BookNet.signFormattingOff();
				for (int i = 0; i < this.formatCells.size(); i++) {
					SpriteButton cell = this.formatCells.get(i);
					cell.active = available;
					cell.setTooltipText(tooltip(this.formatLayout.get(i), why));
				}
			}
			for (int i = 0; i < this.formatCells.size(); i++) {
				SignEditLayout.Cell cell = this.formatLayout.get(i);
				this.formatCells.get(i).setSelected(available && cell.kind() == SignEditLayout.Kind.STYLE && this.editor.has(cell.code()));
			}
		}
	}

	private static void install(AbstractSignEditScreen screen, int width) {
		close(screen);
		AbstractSignEditScreenAccessor sign = (AbstractSignEditScreenAccessor) screen;
		Font font = screen.getFont();
		Minecraft minecraft = Minecraft.getInstance();
		Tools tools = new Tools();
		tools.editor = new SignEditor(font, line(sign), BookNet::signFormatting, BookNet::signFormattingOff,
			() -> minecraft.keyboardHandler.getClipboard(), text -> minecraft.keyboardHandler.setClipboard(text), tools::notice);

		Runnable[] actions = {
			() -> copy(sign),
			() -> paste(sign, tools, font, minecraft.keyboardHandler.getClipboard()),
			() -> save(sign, tools),
			() -> openLoadMenu(screen, sign, tools, font, width)};
		for (int i = 0; i < BUTTONS.length; i++) {
			Button button = button(BUTTONS[i], actions[i]);
			button.setX(SignEditLayout.buttonX(width, i));
			button.setY(SignEditLayout.BUTTON_Y);
			button.setWidth(SignEditLayout.BUTTON_WIDTH);
			Screens.getWidgets(screen).add(button);
			tools.largeButtons.add(button);
		}

		for (SignEditLayout.Cell cell : SignEditLayout.cells(width)) {
			Runnable action = switch (cell.kind()) {
				case STYLE -> () -> tools.editor.toggleStyle(cell.code());
				case CLEAR -> () -> tools.editor.clearFormatting();
				case COLOR -> () -> tools.editor.setColor(cell.code());
				case SYMBOLS -> () -> openSymbols(screen, tools, font);
			};
			SpriteButton button = new SpriteButton(cell.x(), cell.y(), cell.width(), cell.height(), cell.sprite(), tooltip(cell, null), action).noFocus();
			Screens.getWidgets(screen).add(button);
			if (cell.kind() != SignEditLayout.Kind.SYMBOLS) {
				tools.formatCells.add(button);
				tools.formatLayout.add(cell);
			}
		}
		tools.refresh();
		tools.editor.restCaret();

		ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> tools.refresh());
		ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> {
			Popup popup = POPUPS.get(s);
			if (popup != null) {
				graphics.nextStratum();
				popup.render(graphics, mouseX, mouseY);
			}
			drawNotice(s, graphics, font, tools);
		});
		ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
			tools.editor.mouseClicked();
			Popup popup = POPUPS.get(s);
			if (popup == null) {
				return true;
			}
			if (popup.contains(event.x(), event.y())) {
				popup.mouseClicked(event.x(), event.y(), event.button());
				return false;
			}
			if (popup.modal()) {
				close(s);
				return false;
			}
			popup.clickedOutside();
			return true;
		});
		ScreenMouseEvents.afterMouseClick(screen).register((s, event, consumed) -> {
			if (s.getFocused() instanceof Button focused && tools.largeButtons.contains(focused)) {
				s.setFocused(null);
			}
			return consumed;
		});
		ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, scrollX, scrollY) -> {
			Popup popup = POPUPS.get(s);
			if (popup == null) {
				return true;
			}
			if (popup.contains(mouseX, mouseY)) {
				popup.mouseScrolled(mouseX, mouseY, scrollY);
				return false;
			}
			return !popup.modal();
		});
		ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
			Popup popup = POPUPS.get(s);
			if (popup != null) {
				if (popup.modal() || popup.takesKeys()) {
					if (!popup.keyPressed(event) && event.isEscape()) {
						close(s);
					}
					return false;
				}
				if (event.isEscape()) {
					close(s);
					return false;
				}
			}
			return !tools.editor.keyPressed(event);
		});
		ScreenKeyboardEvents.afterKeyPress(screen).register((s, event) -> tools.editor.afterKeyPressed(event));
		ScreenKeyboardEvents.allowKeyRelease(screen).register((s, event) -> {
			Popup popup = POPUPS.get(s);
			if (popup != null && (popup.modal() || popup.takesKeys())) {
				return true;
			}
			return !tools.editor.keyReleased(event);
		});
		ScreenKeyboardEvents.allowCharType(screen).register((s, event) -> {
			Popup popup = POPUPS.get(s);
			if (popup != null && (popup.modal() || popup.takesKeys())) {
				popup.charTyped(event);
				return false;
			}
			return !tools.editor.charTyped(event);
		});
	}

	private static SignEditor.Line line(AbstractSignEditScreenAccessor sign) {
		return new SignEditor.Line() {
			@Override
			public String text() {
				String text = sign.scribe$messages()[sign.scribe$line()];
				return text == null ? "" : text;
			}

			@Override
			public int cursor() {
				return sign.scribe$signField().getCursorPos();
			}

			@Override
			public int anchor() {
				return sign.scribe$signField().getSelectionPos();
			}

			@Override
			public void set(String text, int cursor, int anchor) {
				sign.scribe$setMessage(text);
				sign.scribe$signField().setSelectionRange(cursor, anchor);
			}

			@Override
			public int maxWidth() {
				return sign.scribe$sign().getMaxTextLineWidth();
			}
		};
	}

	public static Component tooltip(SignEditLayout.Cell cell, @Nullable Component unavailable) {
		String ctrl = Keys.ctrlName();
		Component name = switch (cell.kind()) {
			case STYLE -> {
				int i = SignEditLayout.STYLE_CODES.indexOf(cell.code());
				yield tip(STYLE_NAMES[i], STYLE_SHORTCUTS[i] == null ? null : ctrl + STYLE_SHORTCUTS[i]);
			}
			case CLEAR -> tip("reset", Keys.clearFormattingKeys());
			case COLOR -> {
				ChatFormatting format = ChatFormatting.getByCode(cell.code());
				yield Component.translatable("scribe.color." + (format == null ? "black" : format.name().toLowerCase(Locale.ROOT)));
			}
			case SYMBOLS -> Component.translatable("scribe.button.symbols");
		};
		if (unavailable == null || cell.kind() == SignEditLayout.Kind.SYMBOLS) {
			return name;
		}
		return name.copy().append("\n").append(unavailable);
	}

	private static Component tip(String key, @Nullable String shortcut) {
		Component name = Component.translatable("scribe.button." + key);
		return shortcut == null ? name : Component.translatable("scribe.tooltip.with_shortcut", name, shortcut);
	}

	private static void drawNotice(Screen screen, GuiGraphicsExtractor graphics, Font font, Tools tools) {
		if (tools.notice == null || Util.getMillis() >= tools.noticeUntil) {
			return;
		}
		List<FormattedCharSequence> lines = font.split(tools.notice, Math.max(60, Math.min(300, screen.width - 24)));
		int w = 0;
		for (FormattedCharSequence line : lines) {
			w = Math.max(w, font.width(line));
		}
		w += 10;
		int h = 4 + lines.size() * 9;
		int x = screen.width / 2 - w / 2;
		int y = SignEditLayout.TITLE_Y - 2;
		graphics.nextStratum();
		BookStyle.paper(graphics, x, y, w, h);
		for (int i = 0; i < lines.size(); i++) {
			graphics.text(font, lines.get(i), x + 5, y + 3 + i * 9, BookStyle.INK, false);
		}
	}

	private static void openSymbols(AbstractSignEditScreen screen, Tools tools, Font font) {
		if (POPUPS.get(screen) instanceof SymbolPopup) {
			close(screen);
			return;
		}
		SymbolPlacement place = SymbolPlacement.forSign(screen.width, screen.height, SignEditLayout.toolsBottom(), font.width(screen.getTitle()));
		open(screen, new SymbolPopup(font, symbol -> tools.editor.insertSymbol(symbol), place.columns(), place.rows()), place.x(), place.y());
	}

	private static Button button(String key, Runnable action) {
		Button button = Button.builder(Component.translatable("scribe.sign_tools." + key), b -> action.run())
			.size(SignEditLayout.BUTTON_WIDTH, SignEditLayout.BUTTON_HEIGHT).build();
		button.setTooltip(Tooltip.create(Component.translatable("scribe.sign_tools." + key + ".tooltip")));
		return button;
	}

	private static void open(Screen screen, Popup popup, int x, int y) {
		close(screen);
		popup.at(x, y, screen.width, screen.height);
		popup.setCloser(() -> close(screen));
		POPUPS.put(screen, popup);
	}

	private static void close(Screen screen) {
		Popup popup = POPUPS.remove(screen);
		if (popup != null) {
			popup.onClosed();
		}
	}

	private static void copy(AbstractSignEditScreenAccessor sign) {
		Minecraft.getInstance().keyboardHandler.setClipboard(String.join("\n", sign.scribe$messages()));
	}

	private static void paste(AbstractSignEditScreenAccessor sign, Tools tools, Font font, String text) {
		SignEditor.Lines lines = SignEditor.signLines(font, text, sign.scribe$sign().getMaxTextLineWidth(), tools.editor.formattingAvailable());
		int keep = sign.scribe$line();
		for (int i = 0; i < SignEditor.LINES; i++) {
			sign.scribe$setLine(i);
			sign.scribe$setMessage(lines.lines().get(i));
		}
		sign.scribe$setLine(keep);
		sign.scribe$signField().setCursorToEnd();
		tools.editor.restCaret();
		if (lines.cut()) {
			tools.notice(Component.translatable("scribe.sign_tools.cut"));
		}
	}

	private static void save(AbstractSignEditScreenAccessor sign, Tools tools) {
		String[] lines = sign.scribe$messages();
		String first = "";
		for (String line : lines) {
			if (!line.isBlank()) {
				first = line;
				break;
			}
		}
		String name = BookFiles.fileName((first.isBlank() ? "sign" : first) + ".txt");
		try {
			Path dir = Files.createDirectories(BookFiles.signsDir());
			Path path = dir.resolve(name);
			int n = 2;
			while (Files.exists(path)) {
				path = dir.resolve(name.substring(0, name.length() - 4) + "_" + n++ + ".txt");
			}
			Files.writeString(path, String.join("\n", lines), StandardCharsets.UTF_8);
			tools.notice(Component.translatable("scribe.sign_tools.saved", path.getFileName().toString()));
		} catch (IOException e) {
			Scribe.LOGGER.warn("Could not save the sign text", e);
		}
	}

	private static void openLoadMenu(Screen screen, AbstractSignEditScreenAccessor sign, Tools tools, Font font, int width) {
		List<Path> files = new ArrayList<>();
		Path dir = BookFiles.signsDir();
		if (Files.isDirectory(dir)) {
			try (Stream<Path> stream = Files.list(dir)) {
				stream.filter(p -> p.getFileName().toString().endsWith(".txt")).forEach(files::add);
			} catch (IOException e) {
				Scribe.LOGGER.warn("Could not list saved signs", e);
			}
		}
		files.sort(Comparator.comparingLong((Path p) -> {
			try {
				return Files.getLastModifiedTime(p).toMillis();
			} catch (IOException e) {
				return 0L;
			}
		}).reversed());
		List<MenuPopup.Item> items = new ArrayList<>();
		for (Path file : files.subList(0, Math.min(MAX_LOAD, files.size()))) {
			String name = file.getFileName().toString();
			items.add(new MenuPopup.Item(Component.literal(name.substring(0, name.length() - 4)), null, true, () -> {
				try {
					paste(sign, tools, font, Files.readString(file, StandardCharsets.UTF_8));
				} catch (IOException e) {
					Scribe.LOGGER.warn("Could not read {}", file, e);
				}
			}));
		}
		if (items.isEmpty()) {
			items.add(new MenuPopup.Item(Component.translatable("scribe.sign_tools.none"), null, false, () -> {
			}));
		}
		MenuPopup menu = new MenuPopup(font, items);
		int loadCenter = SignEditLayout.buttonX(width, BUTTONS.length - 1) + SignEditLayout.BUTTON_WIDTH / 2;
		open(screen, menu, loadCenter - menu.width() / 2, SignEditLayout.BUTTON_Y + SignEditLayout.BUTTON_HEIGHT + 2);
	}
}

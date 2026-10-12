package scribe.client.screen;

import scribe.client.BookNet;
import scribe.client.Looks;
import scribe.client.compat.Keys;
import scribe.client.compat.OtherMods;
import scribe.client.screen.SettingsLayout.Item;
import scribe.client.screen.SettingsLayout.Place;
import scribe.client.screen.SettingsLayout.Row;
import scribe.client.screen.SettingsLayout.Section;
import scribe.config.ScribeConfig;
import scribe.server.LiveSettings;
import scribe.server.ServerBooks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

public class SettingsScreen extends OptionsSubScreen {
	public static final int NOTE_COLOR = 0xFFA0A0A0;

	private final ScribeConfig config = ScribeConfig.get();
	private final Map<String, AbstractWidget> widgets = new LinkedHashMap<>();
	private Place place = Place.MENU;
	private int serverFlags;
	private int serverAtOpen;
	private boolean leavingToParent;
	private boolean openingChild;

	public SettingsScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable("scribe.settings.title"));
		this.serverAtOpen = ServerBooks.features();
	}

	public static Place place(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.getConnection() == null) {
			return Place.MENU;
		}
		if (minecraft.hasSingleplayerServer()) {
			return Place.HOSTED;
		}
		return BookNet.scribeOnServer() ? Place.SERVER : Place.NO_SCRIBE;
	}

	@Override
	protected void addOptions() {
		if (this.list == null) {
			return;
		}
		this.widgets.clear();
		this.place = place(this.minecraft);
		this.serverFlags = BookNet.serverFlags();
		this.addRows(SettingsLayout.top());
		for (Section section : SettingsLayout.sections()) {
			this.list.addHeader(Component.translatable("scribe.settings.section." + section.key()));
			this.addRows(section.rows());
		}
		this.refresh();
	}

	private void addRows(List<Row> rows) {
		for (Row row : rows) {
			List<AbstractWidget> cells = new ArrayList<>();
			for (Item item : row.items()) {
				AbstractWidget widget = this.widget(item);
				this.widgets.put(item.key(), widget);
				cells.add(widget);
			}
			if (row.wide()) {
				this.list.addBig(cells.getFirst());
			} else {
				this.list.addSmall(cells.getFirst(), cells.size() > 1 ? cells.get(1) : null);
			}
		}
	}

	private AbstractWidget widget(Item item) {
		String key = item.key();
		ScribeConfig c = this.config;
		return switch (item.kind()) {
			case TOGGLE -> this.toggle(key, SettingsLayout.shown(key, c, this.place, this.serverFlags), v -> SettingsLayout.set(key, c, v));
			case CHOICE -> this.choiceFor(key);
			case SLIDER -> fastScrollOption(c, () -> this.refreshTooltip("scroll")).createButton(this.options);
			case BUTTON -> this.button(key);
			case NOTE -> new Note(serverNote(this.place));
		};
	}

	private AbstractWidget choiceFor(String key) {
		ScribeConfig c = this.config;
		return switch (key) {
			case "layout" -> this.choice(key, ScribeConfig.Layout.values(), c.layout, v -> c.layout = v);
			case "escape" -> this.choice(key, ScribeConfig.EscapeAction.values(), c.escapeAction, v -> c.escapeAction = v);
			case "overflow" -> this.choice(key, ScribeConfig.OverflowMode.values(), c.overflowMode, v -> c.overflowMode = v);
			case "section_key" -> this.choice(key, ScribeConfig.SectionSignKey.values(), c.sectionSignKey, v -> c.sectionSignKey = v);
			case "editor_start" -> this.choice(key, ScribeConfig.EditorStart.values(), c.editorStart, v -> c.editorStart = v);
			case "look_levels" -> this.choice(key, ScribeConfig.LevelNumerals.values(), c.lookAtLevels, v -> c.lookAtLevels = v);
			case "look_size" -> this.choice(key, ScribeConfig.TextSize.values(), c.lookAtSize, v -> c.lookAtSize = v);
			default -> throw new IllegalArgumentException(key);
		};
	}

	private AbstractWidget button(String key) {
		Runnable action = switch (key) {
			case SettingsLayout.ENCHANT_COLORS -> () -> {
				this.openingChild = true;
				this.minecraft.gui.setScreen(new EnchantColorsScreen(this));
			};
			case SettingsLayout.PRESETS -> () -> {
				this.openingChild = true;
				this.minecraft.gui.setScreen(new PresetsScreen(this));
			};
			case SettingsLayout.GUIDE -> () -> {
				this.openingChild = true;
				scribe.client.GuideBook.open(this);
			};
			default -> throw new IllegalArgumentException(key);
		};
		return Button.builder(name(key), b -> action.run()).tooltip(tooltip(key)).build();
	}

	private void changed() {
		this.refresh();
		Looks.apply();
	}

	private void refresh() {
		ScribeConfig c = this.config;
		for (Map.Entry<String, AbstractWidget> entry : this.widgets.entrySet()) {
			String key = entry.getKey();
			AbstractWidget widget = entry.getValue();
			SettingsLayout.Switch s = SettingsLayout.switchOf(key);
			if (s != null && widget instanceof CycleButton<?> button) {
				@SuppressWarnings("unchecked")
				CycleButton<Boolean> toggle = (CycleButton<Boolean>) button;
				toggle.active = SettingsLayout.active(key, c, this.place, this.serverFlags);
				toggle.setValue(SettingsLayout.shown(key, c, this.place, this.serverFlags));
			} else if (widget instanceof CycleButton<?> choice) {
				choice.active = SettingsLayout.active(key, c, this.place, this.serverFlags);
				this.refreshTooltip(key);
			} else if (widget instanceof OptionInstance.OptionInstanceSliderButton<?> || widget instanceof Button) {
				widget.active = SettingsLayout.active(key, c, this.place, this.serverFlags);
				widget.setTooltip(this.tooltipFor(key, c.fastScrollPages));
			}
		}
	}

	private void refreshTooltip(String key) {
		AbstractWidget widget = this.widgets.get(key);
		if (widget != null) {
			widget.setTooltip(this.tooltipFor(key, this.config.fastScrollPages));
		}
	}

	private static Component name(String key) {
		return Component.translatable("scribe.settings." + key);
	}

	private static Tooltip tooltip(String key) {
		return tooltip(key, ScribeConfig.get().fastScrollPages);
	}

	private static Tooltip tooltip(String key, int fastScrollPages) {
		return Tooltip.create(text(key, fastScrollPages));
	}

	private static MutableComponent text(String key, int fastScrollPages) {
		return Component.translatable("scribe.settings." + key + ".tooltip", Keys.ctrlName(), Keys.altName(), fastScrollPages, 2 * fastScrollPages);
	}

	private Tooltip tooltipFor(String key, int fastScrollPages) {
		return Tooltip.create(tooltipText(key, this.config, this.place, this.serverFlags, fastScrollPages));
	}

	public static Component tooltipText(String key, ScribeConfig config, Place place, int serverFlags, int fastScrollPages) {
		MutableComponent text = text(key, fastScrollPages);
		Component line = whyLine(key, config, place, serverFlags);
		return line == null ? text : Component.empty().append(text).append("\n").append(line);
	}

	public static @Nullable Component whyLine(String key, ScribeConfig config, Place place, int serverFlags) {
		SettingsLayout.Switch s = SettingsLayout.switchOf(key);
		if (s != null && s.server() && !place.ownsServer()) {
			return Component.translatable(place == Place.SERVER ? "scribe.settings.server_decides" : "scribe.settings.server_no_scribe")
				.withStyle(ChatFormatting.GRAY);
		}
		String blocker = SettingsLayout.blockedBy(key, config, place, serverFlags);
		if (blocker == null) {
			return null;
		}
		if (SettingsLayout.SCREENS.equals(blocker)) {
			return Component.translatable("scribe.settings.needs_screens").withStyle(ChatFormatting.GRAY);
		}
		if (SettingsLayout.PACK.equals(blocker)) {
			return Component.translatable("scribe.settings.needs_pack").withStyle(ChatFormatting.GRAY);
		}
		if (blocker.startsWith(SettingsLayout.MOD)) {
			return Component.translatable("scribe.settings.other_mod", OtherMods.name(blocker.substring(SettingsLayout.MOD.length()))).withStyle(ChatFormatting.GRAY);
		}
		if (blocker.startsWith(SettingsLayout.OFF)) {
			return Component.translatable("scribe.settings.needs_off", name(blocker.substring(SettingsLayout.OFF.length()))).withStyle(ChatFormatting.GRAY);
		}
		SettingsLayout.Switch need = SettingsLayout.switchOf(blocker);
		String line = need != null && need.server() ? "scribe.settings.needs_server" : "scribe.settings.needs";
		return Component.translatable(line, name(blocker)).withStyle(ChatFormatting.GRAY);
	}

	public static Component serverNote(Place place) {
		return Component.translatable("scribe.settings.server_note." + place.name().toLowerCase(Locale.ROOT));
	}

	private static OptionInstance<Integer> fastScrollOption(ScribeConfig c, Runnable changed) {
		return new OptionInstance<>("scribe.settings.fast_scroll",
			pages -> tooltip("fast_scroll", pages),
			(caption, pages) -> Options.genericValueLabel(caption, pages),
			new OptionInstance.IntRange(ScribeConfig.MIN_FAST_SCROLL_PAGES, ScribeConfig.MAX_FAST_SCROLL_PAGES),
			c.fastScrollPages,
			pages -> {
				c.fastScrollPages = pages;
				changed.run();
			});
	}

	private CycleButton<Boolean> toggle(String key, boolean value, Consumer<Boolean> setter) {
		return CycleButton.onOffBuilder(value)
			.withTooltip(v -> this.tooltipFor(key, this.config.fastScrollPages))
			.create(name(key), (button, v) -> {
				setter.accept(v);
				this.changed();
			});
	}

	public static Component choiceLabel(String key, Enum<?> value) {
		return Component.translatable("scribe.settings." + key + "." + value.name().toLowerCase(Locale.ROOT), Keys.ctrlName(), Keys.altName());
	}

	private <E extends Enum<E>> CycleButton<E> choice(String key, E[] values, E value, Consumer<E> setter) {
		return CycleButton.builder((E v) -> choiceLabel(key, v), value)
			.withValues(List.of(values))
			.withTooltip(v -> this.tooltipFor(key, this.config.fastScrollPages))
			.create(name(key), (button, v) -> setter.accept(v));
	}

	@Override
	public void onClose() {
		this.leavingToParent = true;
		super.onClose();
	}

	@Override
	public void removed() {
		super.removed();
		this.config.save();
		this.tellHostedWorld();
		if (this.openingChild) {
			this.openingChild = false;
			return;
		}
		if (!this.leavingToParent) {
			closedByGame(this.lastScreen);
		}
	}

	private void tellHostedWorld() {
		IntegratedServer server = this.minecraft.getSingleplayerServer();
		int now = ServerBooks.features();
		if (server != null && now != this.serverAtOpen) {
			int before = this.serverAtOpen;
			this.serverAtOpen = now;
			server.execute(() -> LiveSettings.changed(server, before));
		}
	}

	public static void closedByGame(Screen parent) {
		if (parent instanceof SettingsScreen settings) {
			closedByGame(settings.lastScreen);
		} else if (parent instanceof BookEditorScreen editor) {
			editor.autosaveFromChild();
		} else if (parent instanceof BookReaderScreen reader) {
			reader.finishFromChild();
		}
	}

	static final class Note extends AbstractWidget {
		Note(Component text) {
			super(0, 0, 310, 20, text);
			this.active = false;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			Font font = Minecraft.getInstance().font;
			List<FormattedCharSequence> lines = font.split(this.getMessage(), this.getWidth());
			int y = this.getY() + (this.getHeight() - lines.size() * font.lineHeight) / 2;
			for (FormattedCharSequence line : lines) {
				graphics.text(font, line, this.getX() + (this.getWidth() - font.width(line)) / 2, y, NOTE_COLOR, true);
				y += font.lineHeight;
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			output.add(NarratedElementType.TITLE, this.getMessage());
		}
	}
}

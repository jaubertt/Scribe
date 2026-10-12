package scribe.client.screen;

import scribe.client.colors.EnchantColors;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jspecify.annotations.Nullable;

public class EnchantColorsScreen extends OptionsSubScreen {
	private final EnchantColors colors = EnchantColors.get();
	private final Map<Button, Optional<DyeColor>> current = new HashMap<>();
	private final List<Button> categoryButtons = new ArrayList<>();
	private final List<Button> enchantmentButtons = new ArrayList<>();
	private final List<Button> ribbonAllButtons = new ArrayList<>();
	private final List<Button> ribbonCategoryButtons = new ArrayList<>();
	private final List<AbstractWidget> ribbonOptions = new ArrayList<>();
	private boolean leavingToParent;
	private boolean changed;
	private @Nullable ColorDropdown dropdown;

	public EnchantColorsScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable("scribe.enchant_colors.title"));
	}

	private static Component label(Component name, Optional<DyeColor> color, Component noneLabel) {
		return Component.empty().append(name).append(": ").append(ColorDropdown.colorName(color, noneLabel));
	}

	private Button colorButton(Component name, Optional<DyeColor> initial, Component noneLabel, Consumer<Optional<DyeColor>> store) {
		Button button = Button.builder(label(name, initial, noneLabel), b -> {
			Optional<DyeColor> now = this.current.getOrDefault(b, Optional.empty());
			this.dropdown = new ColorDropdown(this.font, noneLabel, now, b.getX(), b.getY(), b.getBottom(), this.width, this.height, picked -> {
				store.accept(picked);
				b.setMessage(label(name, picked, noneLabel));
				this.current.put(b, picked);
				this.changed = true;
			});
		}).bounds(0, 0, 150, 20).build();
		this.current.put(button, initial);
		return button;
	}

	private void addRows(List<? extends AbstractWidget> buttons) {
		for (int i = 0; i < buttons.size(); i += 2) {
			this.list.addSmall(buttons.get(i), i + 1 < buttons.size() ? buttons.get(i + 1) : null);
		}
	}

	@Override
	protected void addOptions() {
		if (this.list == null) {
			return;
		}
		this.current.clear();
		this.categoryButtons.clear();
		this.enchantmentButtons.clear();
		this.ribbonAllButtons.clear();
		this.ribbonCategoryButtons.clear();
		this.ribbonOptions.clear();
		Component none = Component.translatable("scribe.enchant_colors.none");

		this.list.addBig(CycleButton.builder((EnchantColors.Style style) -> Component.translatable("scribe.enchant_colors.style." + style.name().toLowerCase(Locale.ROOT)), this.colors.style())
			.withValues(List.of(EnchantColors.Style.values()))
			.withTooltip(style -> Tooltip.create(Component.translatable("scribe.enchant_colors.style.tooltip")))
			.create(Component.translatable("scribe.enchant_colors.style"), (button, style) -> {
				this.colors.setStyle(style);
				this.changed = true;
				this.updateActive();
			}));
		this.list.addBig(CycleButton.builder((EnchantColors.Mode mode) -> Component.translatable("scribe.enchant_colors.mode." + mode.name().toLowerCase(Locale.ROOT)), this.colors.mode())
			.withValues(List.of(EnchantColors.Mode.values()))
			.withTooltip(mode -> Tooltip.create(Component.translatable("scribe.enchant_colors.mode.tooltip")))
			.create(Component.translatable("scribe.enchant_colors.mode"), (button, mode) -> {
				this.colors.setMode(mode);
				this.changed = true;
				this.updateActive();
			}));
		this.list.addBig(CycleButton.onOffBuilder(this.colors.levelShades())
			.withTooltip(on -> Tooltip.create(Component.translatable("scribe.enchant_colors.levels.tooltip")))
			.create(Component.translatable("scribe.enchant_colors.levels"), (button, on) -> {
				this.colors.setLevelShades(on);
				this.changed = true;
			}));

		CycleButton<EnchantColors.RibbonMode> ribbonMode = CycleButton.builder((EnchantColors.RibbonMode mode) -> Component.translatable("scribe.enchant_colors.ribbon_mode." + mode.name().toLowerCase(Locale.ROOT)), this.colors.ribbonMode())
			.withValues(List.of(EnchantColors.RibbonMode.values()))
			.withTooltip(mode -> Tooltip.create(Component.translatable("scribe.enchant_colors.ribbon_mode.tooltip")))
			.create(Component.translatable("scribe.enchant_colors.ribbon_mode"), (button, mode) -> {
				this.colors.setRibbonMode(mode);
				this.changed = true;
				this.updateActive();
			});
		this.ribbonOptions.add(ribbonMode);
		this.list.addBig(ribbonMode);
		CycleButton<Boolean> curseLook = CycleButton.onOffBuilder(this.colors.curseLook())
			.withTooltip(on -> Tooltip.create(Component.translatable("scribe.enchant_colors.curse_look.tooltip")))
			.create(Component.translatable("scribe.enchant_colors.curse_look"), (button, on) -> {
				this.colors.setCurseLook(on);
				this.changed = true;
			});
		this.ribbonOptions.add(curseLook);
		this.list.addBig(curseLook);

		this.list.addHeader(Component.translatable("scribe.enchant_colors.ribbon_all_header"));
		Button ribbonAll = this.colorButton(Component.translatable("scribe.enchant_colors.ribbon_all"), Optional.ofNullable(this.colors.ribbonAll()),
			Component.translatable("scribe.enchant_colors.regular"), c -> this.colors.setRibbonAll(c.orElse(null)));
		this.ribbonAllButtons.add(ribbonAll);
		this.list.addSmall(ribbonAll, null);

		this.list.addHeader(Component.translatable("scribe.enchant_colors.ribbon_categories"));
		Component sameAsAll = Component.translatable("scribe.enchant_colors.same_as_all");
		for (EnchantColors.Category category : EnchantColors.Category.values()) {
			this.ribbonCategoryButtons.add(this.colorButton(Component.translatable("scribe.enchant_colors.category." + category.key()),
				Optional.ofNullable(this.colors.ribbonCategory(category)), sameAsAll, c -> this.colors.setRibbonCategory(category, c.orElse(null))));
		}
		this.addRows(this.ribbonCategoryButtons);

		this.list.addHeader(Component.translatable("scribe.enchant_colors.categories"));
		for (EnchantColors.Category category : EnchantColors.Category.values()) {
			this.categoryButtons.add(this.colorButton(Component.translatable("scribe.enchant_colors.category." + category.key()),
				Optional.ofNullable(this.colors.category(category)), none, c -> this.colors.setCategory(category, c.orElse(null))));
		}
		this.addRows(this.categoryButtons);

		var level = this.minecraft.level;
		if (level == null) {
			this.list.addHeader(Component.translatable("scribe.enchant_colors.join_world"));
			this.updateActive();
			return;
		}
		this.list.addHeader(Component.translatable("scribe.enchant_colors.enchantments"));
		Component useCategory = Component.translatable("scribe.enchant_colors.use_category");
		List<Holder.Reference<Enchantment>> enchantments = new ArrayList<>(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().toList());
		enchantments.sort(Comparator.comparing(holder -> holder.value().description().getString()));
		for (Holder.Reference<Enchantment> holder : enchantments) {
			String id = holder.key().identifier().toString();
			this.enchantmentButtons.add(this.colorButton(holder.value().description(), this.colors.enchantment(id), useCategory, c -> this.colors.setEnchantment(id, c.orElse(null))));
		}
		this.addRows(this.enchantmentButtons);
		this.updateActive();
	}

	private void updateActive() {
		EnchantColors.Mode mode = this.colors.mode();
		boolean ribbon = this.colors.style() == EnchantColors.Style.RIBBON;
		EnchantColors.RibbonMode ribbonMode = this.colors.ribbonMode();
		this.categoryButtons.forEach(b -> b.active = mode != EnchantColors.Mode.OFF);
		this.enchantmentButtons.forEach(b -> b.active = mode == EnchantColors.Mode.ENCHANTMENT);
		this.ribbonOptions.forEach(w -> w.active = ribbon);
		this.ribbonAllButtons.forEach(b -> b.active = ribbon);
		this.ribbonCategoryButtons.forEach(b -> b.active = ribbon && ribbonMode == EnchantColors.RibbonMode.CATEGORY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		ColorDropdown open = this.dropdown;
		super.extractRenderState(graphics, open == null ? mouseX : -1, open == null ? mouseY : -1, a);
		if (open != null) {
			graphics.nextStratum();
			open.render(graphics, mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		ColorDropdown open = this.dropdown;
		if (open != null) {
			if (open.contains(event.x(), event.y())) {
				if (open.mouseClicked(event.x(), event.y())) {
					this.dropdown = null;
				}
			} else {
				this.dropdown = null;
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (this.dropdown != null) {
			this.dropdown = null;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.dropdown != null) {
			if (event.isEscape()) {
				this.dropdown = null;
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	protected void repositionElements() {
		this.dropdown = null;
		super.repositionElements();
	}

	@Override
	public void onClose() {
		this.leavingToParent = true;
		super.onClose();
	}

	@Override
	public void removed() {
		super.removed();
		if (this.changed && this.minecraft.level != null) {
			this.minecraft.levelExtractor.allChanged();
		}
		if (!this.leavingToParent) {
			SettingsScreen.closedByGame(this.lastScreen);
		}
	}
}

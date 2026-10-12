package scribe.client;

import scribe.binding.Binding;
import scribe.binding.Bookbinding;
import scribe.binding.Clasp;
import scribe.binding.CoverDesign;
import scribe.client.text.Chapters;
import scribe.client.text.FormatCodes;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jspecify.annotations.Nullable;

public final class BookInfoTooltip {
	public static final int NO_GENERATION = -1;

	private BookInfoTooltip() {
	}

	public static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> addInfo(stack, lines, context.registries()));
	}

	public static void addInfo(ItemStack stack, List<Component> lines, HolderLookup.@Nullable Provider registries) {
		if (!isBook(stack)) {
			return;
		}
		lines.removeIf(BookInfoTooltip::isDyedLine);
		if (lines.isEmpty()) {
			return;
		}
		int at = insertAt(lines);
		Component count = count(stack);
		if (count != null) {
			lines.add(at++, count);
		}
		WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		CoverDesign design = written != null ? CoverDesign.of(stack) : CoverDesign.NONE;
		lines.addAll(at, bindingLines(Binding.of(stack), design, written != null ? written.generation() : NO_GENERATION, registries));
	}

	static boolean isBook(ItemStack stack) {
		return stack.is(Items.BOOK) || stack.is(Items.WRITABLE_BOOK) || stack.is(Items.WRITTEN_BOOK) || stack.is(Items.ENCHANTED_BOOK);
	}

	public static boolean isDyedLine(Component line) {
		return line.getContents() instanceof TranslatableContents t && "item.dyed".equals(t.getKey());
	}

	public static int insertAt(List<Component> lines) {
		for (int i = lines.size() - 1; i >= 1; i--) {
			if (lines.get(i).getContents() instanceof TranslatableContents t
				&& (t.getKey().startsWith("book.generation.") || t.getKey().equals("scribe.tooltip.not_signed"))) {
				return i + 1;
			}
		}
		return 1;
	}

	public static Component countLine(int pages, int chapters) {
		MutableComponent line = pages == 1 ? Component.translatable("scribe.tooltip.pages.one") : Component.translatable("scribe.tooltip.pages.many", pages);
		if (chapters > 0) {
			line.append(", ").append(chapters == 1 ? Component.translatable("scribe.tooltip.chapters.one")
				: Component.translatable("scribe.tooltip.chapters.many", chapters));
		}
		return line.withStyle(ChatFormatting.GRAY);
	}

	public static List<Component> bindingLines(Binding binding, HolderLookup.@Nullable Provider registries) {
		return bindingLines(binding, CoverDesign.NONE, registries);
	}

	public static List<Component> bindingLines(Binding binding, CoverDesign design, HolderLookup.@Nullable Provider registries) {
		return bindingLines(binding, design, NO_GENERATION, registries);
	}

	public static List<Component> bindingLines(Binding binding, CoverDesign design, int generation, HolderLookup.@Nullable Provider registries) {
		if (binding.isEmpty()) {
			return List.of();
		}
		List<Component> out = new ArrayList<>(4);
		out.add(Component.translatable("scribe.tooltip.binding").withStyle(ChatFormatting.GRAY));
		if (binding.hasStrap()) {
			DyeColor dye = binding.strapDye();
			Component strap = dye == null ? Component.translatable("scribe.tooltip.strap")
				: Component.translatable("scribe.tooltip.strap.dyed", Component.translatable("color.minecraft." + dye.getSerializedName()));
			out.add(CommonComponents.space().append(strap).withStyle(ChatFormatting.GRAY));
			Component clasp = claspLine(binding, registries);
			if (clasp != null) {
				out.add(CommonComponents.space().append(clasp));
				if (generation >= 0) {
					out.add(CommonComponents.space().append(metalworkLine(design, generation)));
				}
			}
		}
		ResourceKey<TrimMaterial> trim = binding.trim();
		if (trim != null) {
			Component material = registries == null ? null : registries.lookup(Registries.TRIM_MATERIAL)
				.flatMap(lookup -> lookup.get(trim))
				.map(holder -> holder.value().description())
				.orElse(null);
			if (material == null) {
				Identifier id = trim.identifier();
				material = Component.translatable("trim_material." + id.getNamespace() + "." + id.getPath()).withStyle(ChatFormatting.GRAY);
			}
			out.add(CommonComponents.space().append(material));
		}
		Component corners = cornerLine(binding, design);
		if (corners != null) {
			out.add(CommonComponents.space().append(corners).withStyle(ChatFormatting.GRAY));
		}
		return out;
	}

	public static @Nullable Component claspLine(Binding binding, HolderLookup.@Nullable Provider registries) {
		String clasp = Clasp.shown(binding);
		if (clasp == null || Clasp.isLeather(clasp)) {
			return null;
		}
		MutableComponent name = Component.translatable("scribe.metal." + clasp);
		if (registries != null) {
			ResourceKey<TrimMaterial> key = ResourceKey.create(Registries.TRIM_MATERIAL, Identifier.withDefaultNamespace(clasp));
			registries.lookup(Registries.TRIM_MATERIAL).flatMap(lookup -> lookup.get(key))
				.map(holder -> holder.value().description().getStyle())
				.ifPresent(name::setStyle);
		}
		return Component.translatable("scribe.tooltip.clasp", name).withStyle(ChatFormatting.GRAY);
	}

	public static Component metalworkLine(CoverDesign design, int generation) {
		return Component.translatable("scribe.tooltip.metalwork", Component.translatable("scribe.metalwork." + design.metalworkShown(generation)))
			.withStyle(ChatFormatting.GRAY);
	}

	public static @Nullable Component cornerLine(Binding binding, CoverDesign design) {
		String corner = design.corner();
		if (Bookbinding.materialName(binding) == null || !CoverDesign.isCornerChoice(corner)) {
			return null;
		}
		return CoverDesign.NO_CORNERS.equals(corner) ? Component.translatable("scribe.corner." + corner)
			: Component.translatable("scribe.tooltip.corners", Component.translatable("scribe.corner." + corner));
	}

	private static @Nullable Object lastContent;
	private static @Nullable Component lastLine;

	private static @Nullable Component count(ItemStack stack) {
		Object content = stack.has(DataComponents.WRITTEN_BOOK_CONTENT) ? stack.get(DataComponents.WRITTEN_BOOK_CONTENT)
			: stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
		if (content == null) {
			return null;
		}
		if (content != lastContent) {
			lastContent = content;
			List<String> pages = new ArrayList<>();
			if (content instanceof WrittenBookContent written) {
				for (Filterable<Component> page : written.pages()) {
					pages.add(FormatCodes.toLegacy(page.raw()));
				}
			} else if (content instanceof WritableBookContent writable) {
				for (Filterable<String> page : writable.pages()) {
					pages.add(page.raw());
				}
			}
			boolean empty = pages.stream().allMatch(page -> FormatCodes.strip(page).isBlank());
			lastLine = empty ? null : countLine(pages.size(), Chapters.find(pages).size());
		}
		return lastLine;
	}
}

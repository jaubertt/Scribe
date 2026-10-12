package scribe.client.colors;

import scribe.EnchantedBooks;
import scribe.Scribe;
import scribe.binding.Binding;
import scribe.binding.Bookbinding;
import scribe.binding.Clasp;
import scribe.binding.CoverDesign;
import scribe.client.Looks;
import scribe.client.mixin.ConditionalItemModelPropertiesAccessor;
import scribe.client.mixin.ItemTintSourcesAccessor;
import scribe.client.mixin.RangeSelectItemModelPropertiesAccessor;
import scribe.colors.BookColors;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

public final class ColorsClient {
	public static final SpriteId BOOK_SPRITE = new SpriteId(TextureAtlas.LOCATION_BLOCKS, Scribe.id("entity/enchanting_table_book"));
	public static final Identifier BOOK_TEXTURE = Scribe.id("textures/entity/enchanting_table_book.png");
	public static final SpriteId CORNERS_SPRITE = new SpriteId(TextureAtlas.LOCATION_BLOCKS, Scribe.id("entity/enchanting_table_book_corners"));
	public static final SpriteId CORNER_PAGES_SPRITE = new SpriteId(TextureAtlas.LOCATION_BLOCKS, Scribe.id("entity/enchanting_table_book_corner_pages"));
	public static final int UNDYED_CORNERS = 0xFFB77831;
	public static final int RIBBON_COVER = 0xFF5B5B5B;
	public static final int CLASSIC_RIBBON = 0xFFC51339;
	public static final SpriteId STRAP_SPRITE = new SpriteId(TextureAtlas.LOCATION_BLOCKS, Scribe.id("entity/enchanting_table_book_strap"));
	public static final SpriteId OPEN_STRAP_SPRITE = new SpriteId(TextureAtlas.LOCATION_BLOCKS, Scribe.id("entity/enchanting_table_book_strap_open"));
	public static final int FIRST_CLASP = 2;
	public static final int LAST_CLASP = 5;
	public static final int CURSE_CLASP = 4;
	static final int[] METALWORK_CLASP = {2, 3, 4, 5};
	public static final SpriteId TITLE_SPRITE = new SpriteId(TextureAtlas.LOCATION_BLOCKS, Scribe.id("entity/enchanting_table_book_title"));
	public static final int LECTERN_LEATHER = 0xFFC58439;
	public static final int ICON_LEATHER = 0xFF75321E;
	public static final int ICON_TITLE_LINES = 0xFF391003;
	public static final float ENGRAVED_VALUE = 0.62F;
	public static final float ENGRAVED_SATURATION = saturation(ICON_TITLE_LINES) / saturation(ICON_LEATHER);
	public static final int TITLE_ORDER = 4;
	public static final int SPINE_ORDER = 5;
	public static final int GLINT_ORDER = 6;
	public static final BookModel.State CLOSED_BOOK = BookModel.State.forAnimation(0.0F, 0.0F, 0.0F, 0.0F);
	public static final float CLOSED_PAGES_INSET = 0.25F;
	public static final Identifier GUI_BOOK = Scribe.id("textures/gui/tinted/book.png");
	public static final Identifier GUI_BOOK_OVERLAY = Scribe.id("textures/gui/tinted/book_overlay.png");

	private static final String[] MCBV_SHELVES = {
		"spruce", "birch", "jungle", "acacia", "dark_oak", "pale_oak", "mangrove", "cherry", "bamboo", "crimson", "warped"
	};

	public static final int UNDYED_SHELF_BOOK = BookColors.DEFAULT;

	private ColorsClient() {
	}

	public static void init() {
		RangeSelectItemModelPropertiesAccessor.scribe$idMapper().put(Scribe.id("enchanted_book_style"), BookStyleProperty.MAP_CODEC);
		ItemTintSourcesAccessor.scribe$idMapper().put(Scribe.id("ribbon"), RibbonTint.MAP_CODEC);
		ConditionalItemModelPropertiesAccessor.scribe$idMapper().put(Scribe.id("book_icons"), BookIconsProperty.MAP_CODEC);
		Looks.init();
		PictureInPictureRendererRegistry.register(context -> new DyedBookPictureRenderer());
		BlockColorRegistry.register(ShelfTint.SLOTS, Blocks.CHISELED_BOOKSHELF);
		List<Block> mcbvShelves = new ArrayList<>();
		if (FabricLoader.getInstance().isModLoaded("lolmcbv")) {
			for (String wood : MCBV_SHELVES) {
				BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath("lolmcbv", wood + "_chiseled_bookshelf")).ifPresent(block -> {
					BlockColorRegistry.register(ShelfTint.SLOTS, new Block[] {block});
					mcbvShelves.add(block);
				});
			}
		}
		ShelfGlintRenderer.register(mcbvShelves);
	}

	public static void submitLeather(SubmitNodeCollector collector, BookModel model, BookModel.State state, PoseStack poseStack,
		int lightCoords, int color, SpriteGetter sprites) {
		if (color == BookColors.DEFAULT || color == 0) {
			return;
		}
		collector.order(1).submitModel(model, state, poseStack, BOOK_SPRITE.renderType(RenderTypes::entityCutout), lightCoords,
			OverlayTexture.NO_OVERLAY, color, sprites.get(BOOK_SPRITE), 0);
	}

	public static @Nullable String lecternMetal(ItemStack book) {
		return CoverDesign.NO_CORNERS.equals(CoverDesign.of(book).corner()) ? null : Bookbinding.materialName(Binding.of(book));
	}

	public static void submitCorners(SubmitNodeCollector collector, BookModel model, BookModel.State state, PoseStack poseStack,
		int lightCoords, int color, @Nullable String trim, SpriteGetter sprites) {
		if (trim != null) {
			SpriteId metal = new SpriteId(TextureAtlas.LOCATION_BLOCKS, Scribe.id("entity/enchanting_table_book_trim_" + trim));
			collector.order(1).submitModel(model, state, poseStack, metal.renderType(RenderTypes::entityCutout), lightCoords,
				OverlayTexture.NO_OVERLAY, -1, sprites.get(metal), 0);
			return;
		}
		int leather = color == BookColors.DEFAULT || color == 0 || color == -1 ? UNDYED_CORNERS : color;
		collector.order(1).submitModel(model, state, poseStack, CORNERS_SPRITE.renderType(RenderTypes::entityCutout), lightCoords,
			OverlayTexture.NO_OVERLAY, leather, sprites.get(CORNERS_SPRITE), 0);
		collector.order(1).submitModel(model, state, poseStack, CORNER_PAGES_SPRITE.renderType(RenderTypes::entityCutout), lightCoords,
			OverlayTexture.NO_OVERLAY, -1, sprites.get(CORNER_PAGES_SPRITE), 0);
	}

	public static int lecternStrap(ItemStack book) {
		if (EnchantedBooks.is(book)) {
			return enchantedRibbon(book);
		}
		Binding binding = Binding.of(book);
		return binding.hasStrap() ? ARGB.opaque(binding.strapColor()) : 0;
	}

	public static int enchantedRibbon(ItemStack book) {
		EnchantColors colors = EnchantColors.get();
		return colors.styleValue(book) < 0 ? CLASSIC_RIBBON : ARGB.opaque(colors.ribbonColor(book));
	}

	public record LecternClasp(int level, String material) {
		public SpriteId sprite() {
			return claspSprite(this.material, this.level);
		}
	}

	public static @Nullable LecternClasp lecternClasp(ItemStack book) {
		if (EnchantedBooks.is(book)) {
			EnchantColors colors = EnchantColors.get();
			return enchantedClasp(colors.style() != EnchantColors.Style.RIBBON, colors.markLevel(book));
		}
		String material = Clasp.metal(Binding.of(book));
		if (material == null) {
			return null;
		}
		WrittenBookContent written = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		return new LecternClasp(metalworkClasp(CoverDesign.of(book).metalworkShown(written == null ? 0 : written.generation())), material);
	}

	public static int metalworkClasp(String design) {
		int index = CoverDesign.METALWORKS.indexOf(design);
		return METALWORK_CLASP[Math.max(0, index)];
	}

	public static @Nullable LecternClasp enchantedClasp(boolean classic, int mark) {
		int level = mark == 0 ? CURSE_CLASP : Math.min(mark, LAST_CLASP);
		if (level < FIRST_CLASP) {
			return null;
		}
		return new LecternClasp(level, mark == 0 && !classic ? "redstone" : "gold");
	}

	public static int lecternCover(ItemStack book, int stored) {
		EnchantColors colors = EnchantColors.get();
		int base = EnchantedBooks.is(book) && !book.has(DataComponents.DYED_COLOR) && colors.styleValue(book) >= 0.0F ? RIBBON_COVER : stored;
		return colors.bookColor(book, base);
	}

	public static SpriteId claspSprite(String material, int level) {
		return new SpriteId(TextureAtlas.LOCATION_BLOCKS, Scribe.id("entity/enchanting_table_book_clasp_level_" + level + "_" + material));
	}

	public static void submitStrap(SubmitNodeCollector collector, BookModel model, BookModel.State state, PoseStack poseStack,
		int lightCoords, boolean closed, int strap, @Nullable LecternClasp clasp, SpriteGetter sprites) {
		if (!closed || strap == 0) {
			return;
		}
		submitStrap(collector, model, state, poseStack, lightCoords, STRAP_SPRITE, strap, clasp, sprites);
	}

	private static void submitStrap(SubmitNodeCollector collector, BookModel model, BookModel.State state, PoseStack poseStack,
		int lightCoords, SpriteId strapSprite, int strap, @Nullable LecternClasp clasp, SpriteGetter sprites) {
		collector.order(2).submitModel(model, state, poseStack, strapSprite.renderType(RenderTypes::entityCutout), lightCoords,
			OverlayTexture.NO_OVERLAY, strap, sprites.get(strapSprite), 0);
		if (clasp != null) {
			SpriteId metal = clasp.sprite();
			collector.order(3).submitModel(model, state, poseStack, metal.renderType(RenderTypes::entityCutout), lightCoords,
				OverlayTexture.NO_OVERLAY, -1, sprites.get(metal), 0);
		}
	}

	public static int lecternTitle(ItemStack book) {
		LecternClasp clasp = lecternClasp(book);
		if (EnchantedBooks.is(book)) {
			return clasp != null && clasp.level() == LAST_CLASP ? 0 : engravedTitle(lecternLeather(book));
		}
		WrittenBookContent written = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (written == null || written.title().raw().isBlank() || (clasp != null && clasp.level() == LAST_CLASP)) {
			return 0;
		}
		ChatFormatting color = CoverDesign.of(book).titleFormatting();
		if (color != null) {
			return ARGB.opaque(TextColor.fromLegacyFormat(color).getValue());
		}
		return engravedTitle(lecternLeather(book));
	}

	public static int lecternLeather(ItemStack book) {
		int cover = EnchantedBooks.is(book) ? lecternCover(book, BookColors.of(book)) : BookColors.of(book);
		return cover == BookColors.DEFAULT || cover == 0 ? LECTERN_LEATHER : ARGB.opaque(cover);
	}

	public static int engravedTitle(int leather) {
		int max = maxChannel(leather);
		int min = Math.min(ARGB.red(leather), Math.min(ARGB.green(leather), ARGB.blue(leather)));
		float value = max * ENGRAVED_VALUE;
		float saturation = Math.min(1.0F, saturation(leather) * ENGRAVED_SATURATION);
		int[] out = new int[3];
		int[] in = {ARGB.red(leather), ARGB.green(leather), ARGB.blue(leather)};
		for (int i = 0; i < 3; i++) {
			float place = max == min ? 0.0F : (max - in[i]) / (float) (max - min);
			out[i] = Math.round(value * (1.0F - saturation * place));
		}
		return ARGB.color(255, out[0], out[1], out[2]);
	}

	private static int maxChannel(int rgb) {
		return Math.max(ARGB.red(rgb), Math.max(ARGB.green(rgb), ARGB.blue(rgb)));
	}

	private static float saturation(int rgb) {
		int max = maxChannel(rgb);
		int min = Math.min(ARGB.red(rgb), Math.min(ARGB.green(rgb), ARGB.blue(rgb)));
		return max == 0 ? 0.0F : (max - min) / (float) max;
	}

	public static @Nullable String lecternSpine(ItemStack book) {
		return EnchantedBooks.is(book) ? null : CoverDesign.of(book).spineMetal(Binding.of(book));
	}

	public static SpriteId spineSprite(String material) {
		return new SpriteId(TextureAtlas.LOCATION_BLOCKS, Scribe.id("entity/enchanting_table_book_spine_" + material));
	}

	public static void submitTitleAndSpine(SubmitNodeCollector collector, BookModel model, BookModel.State state, PoseStack poseStack,
		int lightCoords, boolean closed, int title, @Nullable String spine, SpriteGetter sprites) {
		if (!closed) {
			return;
		}
		if (title != 0) {
			collector.order(TITLE_ORDER).submitModel(model, state, poseStack, TITLE_SPRITE.renderType(RenderTypes::entityCutout), lightCoords,
				OverlayTexture.NO_OVERLAY, title, sprites.get(TITLE_SPRITE), 0);
		}
		if (spine != null) {
			SpriteId metal = spineSprite(spine);
			collector.order(SPINE_ORDER).submitModel(model, state, poseStack, metal.renderType(RenderTypes::entityCutout), lightCoords,
				OverlayTexture.NO_OVERLAY, -1, sprites.get(metal), 0);
		}
	}

	public record LecternLook(int color, boolean foil, @Nullable String trim, int strap, @Nullable LecternClasp clasp, int title, @Nullable String spine) {
		public static LecternLook of(ItemStack preview, int stored) {
			return new LecternLook(lecternCover(preview, stored), EnchantedBooks.is(preview) && preview.hasFoil(), lecternMetal(preview),
				lecternStrap(preview), lecternClasp(preview), lecternTitle(preview), lecternSpine(preview));
		}
	}

	public static void submitLecternLayers(SubmitNodeCollector collector, BookModel model, BookModel.State state, PoseStack poseStack, int lightCoords,
		LecternLook look, boolean shut, boolean front, SpriteGetter sprites) {
		submitLeather(collector, model, state, poseStack, lightCoords, look.color(), sprites);
		submitCorners(collector, model, state, poseStack, lightCoords, look.color(), look.trim(), sprites);
		submitStrap(collector, model, state, poseStack, lightCoords, shut, look.strap(), look.clasp(), sprites);
		submitTitleAndSpine(collector, model, state, poseStack, lightCoords, front, look.title(), look.spine(), sprites);
		if (look.foil()) {
			submitGlint(collector, model, state, poseStack, lightCoords, sprites);
		}
	}

	public static void submitHeldLayers(SubmitNodeCollector collector, BookModel model, BookModel.State state, PoseStack poseStack, int lightCoords,
		LecternLook look, boolean shut, SpriteGetter sprites) {
		submitLeather(collector, model, state, poseStack, lightCoords, look.color(), sprites);
		submitCorners(collector, model, state, poseStack, lightCoords, look.color(), look.trim(), sprites);
		if (look.strap() != 0) {
			submitStrap(collector, model, state, poseStack, lightCoords, shut ? STRAP_SPRITE : OPEN_STRAP_SPRITE, look.strap(), look.clasp(), sprites);
		}
		submitTitleAndSpine(collector, model, state, poseStack, lightCoords, true, look.title(), look.spine(), sprites);
		if (look.foil()) {
			submitGlint(collector, model, state, poseStack, lightCoords, sprites);
		}
	}

	public static void layClosedBook(PoseStack poseStack) {
		poseStack.translate(1.0F / 16.0F, 0.0F, 3.0F / 16.0F);
		poseStack.rotateDegrees(Axis.YP, 90.0F);
	}

	public static void insetClosedPages(ModelPart... pages) {
		for (ModelPart page : pages) {
			page.x += CLOSED_PAGES_INSET;
		}
	}

	public static void submitGlint(SubmitNodeCollector collector, BookModel model, BookModel.State state, PoseStack poseStack,
		int lightCoords, SpriteGetter sprites) {
		collector.order(GLINT_ORDER).submitModel(model, state, poseStack, RenderTypes.patternedShieldGlint(), lightCoords,
			OverlayTexture.NO_OVERLAY, -1, sprites.get(EnchantTableRenderer.BOOK_TEXTURE), 0);
	}

	public static int shelfBookColor(int dyed, ItemStack preview) {
		int color = dyed == 0 ? UNDYED_SHELF_BOOK : dyed;
		return EnchantColors.get().bookColor(preview, color);
	}

	public static final int HIDDEN_SHELF_BOOK = 0x00FFFFFF;
}

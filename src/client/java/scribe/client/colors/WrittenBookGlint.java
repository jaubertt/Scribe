package scribe.client.colors;

import scribe.Scribe;
import scribe.client.mixin.RenderTypeInvoker;
import scribe.config.ScribeConfig;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

public final class WrittenBookGlint {
	public static final Identifier TEXTURE = Scribe.id("textures/misc/written_book_glint.png");

	private WrittenBookGlint() {
	}

	private static RenderType glint(String name, RenderPipeline pipeline, @Nullable OitPipelineSet oit, Identifier atlas) {
		RenderSetup.RenderSetupBuilder setup = RenderSetup.builder(pipeline);
		if (oit != null) {
			setup.setOitPipelines(oit);
		}
		setup.withTexture("Sampler0", atlas)
			.withTexture("GlintSampler", TEXTURE)
			.setTextureTransform(TextureTransform.GLINT_TEXTURING)
			.useLightmap()
			.useOverlay()
			.affectsCrumbling();
		if (oit != null) {
			setup.sortOnUpload();
		}
		setup.setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE);
		return RenderTypeInvoker.scribe$create("scribe_written_book_" + name, setup.createRenderSetup());
	}

	public static boolean isWrittenBook(Identifier sprite) {
		String path = sprite.getPath();
		if (sprite.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
			return path.equals("item/written_book");
		}
		return sprite.getNamespace().equals(Scribe.MOD_ID)
			&& (path.startsWith("item/written_book") || path.startsWith("trims/items/written_book"));
	}

	public static BakedQuad.MaterialInfo material(BakedQuad.MaterialInfo info) {
		if (!isWrittenBook(info.sprite().contents().name())) {
			return info;
		}
		RenderType glint = Types.GLINTS.getOrDefault(info.itemGlintRenderType(), info.itemGlintRenderType());
		RenderType special = Types.GLINTS.getOrDefault(info.itemGlintSpecialRenderType(), info.itemGlintSpecialRenderType());
		if (glint == info.itemGlintRenderType() && special == info.itemGlintSpecialRenderType()) {
			return info;
		}
		return new BakedQuad.MaterialInfo(info.sprite(), info.layer(), info.itemRenderType(), glint, special,
			info.tintIndex(), info.shadeDirectionOverride(), info.lightEmission());
	}

	public static void load(Minecraft minecraft) {
		minecraft.getTextureManager().registerAndLoad(TEXTURE, new GlintTexture());
	}

	public static int neutral(int argb) {
		int r = ARGB.red(argb);
		int g = ARGB.green(argb);
		int b = ARGB.blue(argb);
		int v = (int) Math.min(255, Math.round(Math.sqrt(0.299 * r * r + 0.587 * g * g + 0.114 * b * b)));
		return ARGB.color(ARGB.alpha(argb), v, v, v);
	}

	private static final class Types {
		static final Map<RenderType, RenderType> GLINTS = new IdentityHashMap<>();

		static {
			for (Identifier atlas : new Identifier[] {TextureAtlas.LOCATION_ITEMS, TextureAtlas.LOCATION_BLOCKS}) {
				boolean items = atlas.equals(TextureAtlas.LOCATION_ITEMS);
				GLINTS.put(items ? Sheets.cutoutItemGlintSheet() : Sheets.cutoutBlockItemGlintSheet(),
					glint("item_cutout_glint", RenderPipelines.ITEM_CUTOUT_GLINT, null, atlas));
				GLINTS.put(items ? Sheets.cutoutItemGlintSpecialSheet() : Sheets.cutoutBlockItemGlintSpecialSheet(),
					glint("item_cutout_glint_special", RenderPipelines.ITEM_CUTOUT_GLINT_SPECIAL, null, atlas));
				GLINTS.put(items ? Sheets.translucentItemGlintSheet() : Sheets.translucentBlockItemGlintSheet(),
					glint("item_translucent_glint", RenderPipelines.ITEM_TRANSLUCENT_GLINT, RenderPipelines.OIT_ITEM_GLINT, atlas));
				GLINTS.put(items ? Sheets.translucentItemGlintSpecialSheet() : Sheets.translucentBlockItemGlintSpecialSheet(),
					glint("item_translucent_glint_special", RenderPipelines.ITEM_TRANSLUCENT_GLINT_SPECIAL, RenderPipelines.OIT_ITEM_GLINT_SPECIAL, atlas));
			}
		}

		private Types() {
		}
	}

	private static final class GlintTexture extends ReloadableTexture {
		GlintTexture() {
			super(ItemFeatureRenderer.ENCHANTED_GLINT_ITEM);
		}

		@Override
		public TextureContents loadContents(ResourceManager resources) throws IOException {
			TextureContents contents = TextureContents.load(resources, this.resourceId());
			if (ScribeConfig.get().neutralGlintShown()) {
				NativeImage image = contents.image();
				for (int y = 0; y < image.getHeight(); y++) {
					for (int x = 0; x < image.getWidth(); x++) {
						image.setPixel(x, y, neutral(image.getPixel(x, y)));
					}
				}
			}
			return contents;
		}
	}
}

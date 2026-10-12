package scribe.client.placed;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.event.player.ItemEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import scribe.client.BookNet;
import scribe.client.colors.ColorsClient;
import scribe.colors.BookColors;
import scribe.config.ScribeConfig;
import scribe.network.Payloads;
import scribe.placed.PlacedBooks;
import scribe.placed.PlacedLayout;
import scribe.placed.PlacedLayout.Kind;
import scribe.placed.PlacedLayout.Place;

public final class PlacedBooksClient {
	public static final RenderStateDataKey<ColorsClient.LecternLook> LOOK = RenderStateDataKey.create(() -> "scribe:placed_book");
	public static final RenderStateDataKey<Matrix4f> FRAME = RenderStateDataKey.create(() -> "scribe:placed_book_frame");
	public static final RenderStateDataKey<Outline> OUTLINE = RenderStateDataKey.create(() -> "scribe:placed_book_outline");
	public static final VoxelShape BOOK_BOX = Shapes.box(0.0, -5.0 / 16.0, -1.0 / 16.0, 6.0 / 16.0, 5.0 / 16.0, 1.0 / 16.0);
	public static final int OUTLINE_COLOR = ARGB.black(102);
	public static final int HIGH_CONTRAST_COLOR = -11010079;
	public static final int HIGH_CONTRAST_UNDER_COLOR = -16777216;
	public static final float HIGH_CONTRAST_UNDER_WIDTH = 7.0F;

	private PlacedBooksClient() {
	}

	public static void init() {
		ItemEvents.USE_ON.register(PlacedBooksClient::predict);
		LevelExtractionEvents.AFTER_BLOCK_OUTLINE_EXTRACTION.register(PlacedBooksClient::extractOutline);
	}

	public static ColorsClient.LecternLook look(ItemStack copy) {
		return ColorsClient.LecternLook.of(copy, BookColors.of(copy));
	}

	public static Matrix4f frameOf(ItemStack copy, Transformation transformation) {
		Place place = PlacedBooks.placeOf(copy);
		return place != null ? place.frame() : PlacedLayout.frameOfFittedIcon(transformation.getMatrix());
	}

	public static @Nullable Matrix4f fromTransformation(Matrix4fc transformation, Matrix4fc frame) {
		if (Math.abs(transformation.determinant()) < 1.0E-12F) {
			return null;
		}
		return new Matrix4f(transformation).invertAffine().mul(frame);
	}

	public static boolean scribeLayers() {
		return ScribeConfig.get().lecternLookShown();
	}

	public static void submitBook(SubmitNodeCollector collector, BookModel model, ColorsClient.LecternLook look, PoseStack poseStack, int lightCoords,
		int outlineColor, SpriteGetter sprites) {
		BookModel.State state = ColorsClient.CLOSED_BOOK;
		collector.submitModel(model, state, poseStack, lightCoords, OverlayTexture.NO_OVERLAY, -1, EnchantTableRenderer.BOOK_TEXTURE, sprites, outlineColor);
		if (scribeLayers()) {
			ColorsClient.submitLecternLayers(collector, model, state, poseStack, lightCoords, look, true, true, sprites);
		}
	}

	private static @Nullable InteractionResult predict(UseOnContext context) {
		Level level = context.getLevel();
		if (!level.isClientSide() || !(context.getPlayer() instanceof LocalPlayer player)) {
			return null;
		}
		ItemStack stack = context.getItemInHand();
		Direction face = context.getClickedFace();
		if (PlacedLayout.kindFor(face) == null || !PlacedBooks.placesWith(stack, context.getHand(), player.isSecondaryUseActive()) || !BookNet.scribeOnServer()) {
			return null;
		}
		BlockPos clicked = context.getClickedPos();
		BlockPos space = PlacedLayout.spaceFor(clicked, face);
		if (spotAt(level, space) != null) {
			return InteractionResult.CONSUME;
		}
		return placeableOnServer() && player.mayBuild() && PlacedBooks.canPutDown(level, clicked, face, space) ? InteractionResult.CONSUME : null;
	}

	public static boolean placeableOnServer() {
		if (Minecraft.getInstance().hasSingleplayerServer()) {
			return ScribeConfig.get().placeableBooksOn();
		}
		return BookNet.has(Payloads.ServerFeatures.PLACED_BOOKS);
	}

	public static @Nullable Interaction spotAt(Level level, BlockPos space) {
		for (Interaction box : level.getEntitiesOfClass(Interaction.class, new AABB(space))) {
			if (box.blockPosition().equals(space) && !displaysOf(level, box).isEmpty()) {
				return box;
			}
		}
		return null;
	}

	public static List<Display.ItemDisplay> displaysOf(Level level, Entity box) {
		Vec3 at = box.position();
		return level.getEntitiesOfClass(Display.ItemDisplay.class, new AABB(at, at).inflate(1.0 / 64.0), PlacedBooksClient::isPlacedBook);
	}

	private static boolean isPlacedBook(Display.ItemDisplay display) {
		var slot = display.getSlot(0);
		return slot != null && PlacedBooks.isMarked(slot.get());
	}

	public record Book(ItemStack copy, Matrix4f frame) {
	}

	public static List<Book> books(Level level, Entity box) {
		List<Book> books = new ArrayList<>();
		for (Display.ItemDisplay display : displaysOf(level, box)) {
			var slot = display.getSlot(0);
			if (slot == null) {
				continue;
			}
			ItemStack copy = slot.get();
			Place place = PlacedBooks.placeOf(copy);
			Display.RenderState state = display.renderState();
			if (place != null) {
				books.add(new Book(copy, place.frame()));
			} else if (state != null) {
				books.add(new Book(copy, frameOf(copy, state.transformation().get(1.0F))));
			}
		}
		return order(books);
	}

	public static List<Book> order(List<Book> books) {
		List<Book> out = new ArrayList<>(books);
		if (out.size() > 1) {
			Kind kind = kindOf(out.getFirst().frame());
			Direction facing = facingOf(out.getFirst().frame());
			out.sort(kind == Kind.PILE ? Comparator.comparingDouble(b -> PlacedLayout.center(b.frame()).y())
				: Comparator.comparingDouble(b -> {
					Vector3f c = PlacedLayout.center(b.frame());
					return PlacedLayout.along(facing, Vec3.ZERO, new Vec3(c.x(), c.y(), c.z()));
				}));
		}
		return out;
	}

	public static int aimedIndex(List<Book> books, Vec3 anchor, Vec3 hit) {
		Matrix4f first = books.getFirst().frame();
		return PlacedLayout.aimed(kindOf(first), facingOf(first), books.size(), anchor, hit);
	}

	public static Kind kindOf(Matrix4f frame) {
		Vector3f thickness = frame.transformDirection(new Vector3f(0.0F, 0.0F, 1.0F), new Vector3f());
		return Math.abs(thickness.y()) > 0.5F ? Kind.PILE : Kind.ROW;
	}

	public static Direction facingOf(Matrix4f frame) {
		Vector3f away = frame.transformDirection(kindOf(frame) == Kind.PILE ? new Vector3f(0.0F, 1.0F, 0.0F) : new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f());
		return Direction.getApproximateNearest(away.x(), 0.0F, away.z());
	}

	public static ItemStack aimedBook(Level level, Interaction box, Vec3 hit) {
		List<Book> books = books(level, box);
		return books.isEmpty() ? ItemStack.EMPTY : books.get(aimedIndex(books, box.position(), hit)).copy();
	}

	public record Outline(Vec3 anchor, Matrix4f frame, boolean highContrast) {
	}

	private static void extractOutline(LevelExtractionContext context, @Nullable HitResult hit) {
		Minecraft minecraft = Minecraft.getInstance();
		Outline outline = null;
		if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof Interaction box && minecraft.getCameraEntity() instanceof Player player) {
			outline = outline(context.level(), box, entityHit.getLocation(), player.getAbilities().mayBuild,
				minecraft.options.highContrastBlockOutline().get());
		}
		((FabricRenderState) context.levelState()).setData(OUTLINE, outline);
	}

	public static @Nullable Outline outline(Level level, Interaction box, Vec3 hit, boolean mayBuild, boolean highContrast) {
		if (!mayBuild || !level.getWorldBorder().isWithinBounds(box.blockPosition())) {
			return null;
		}
		return outline(books(level, box), box.position(), hit, highContrast);
	}

	public static @Nullable Outline outline(List<Book> books, Vec3 anchor, Vec3 hit, boolean highContrast) {
		return books.isEmpty() ? null : new Outline(anchor, books.get(aimedIndex(books, anchor, hit)).frame(), highContrast);
	}

	public static void submitOutline(PoseStack poseStack, SubmitNodeCollector collector, LevelRenderState levelState) {
		Outline outline = ((FabricRenderState) levelState).getData(OUTLINE);
		if (outline == null) {
			return;
		}
		Vec3 camera = levelState.cameraRenderState.pos;
		GameRenderer game = Minecraft.getInstance().gameRenderer;
		poseStack.pushPose();
		poseStack.translate(outline.anchor().x - camera.x, outline.anchor().y - camera.y, outline.anchor().z - camera.z);
		poseStack.mulPose(outline.frame());
		submitOutline(poseStack, collector, outline.highContrast(), game.useImprovedTransparency(), game.gameRenderState().windowRenderState.appropriateLineWidth);
		poseStack.popPose();
	}

	public static void submitOutline(PoseStack poseStack, SubmitNodeCollector collector, boolean highContrast, boolean improvedTransparency, float width) {
		submitOutline(poseStack, collector, BOOK_BOX, highContrast, improvedTransparency, width);
	}

	public static void submitOutline(PoseStack poseStack, SubmitNodeCollector collector, VoxelShape shape, boolean highContrast, boolean improvedTransparency,
		float width) {
		if (highContrast) {
			collector.submitShapeOutline(poseStack, shape, RenderTypes.secondaryBlockOutline(), HIGH_CONTRAST_UNDER_COLOR, HIGH_CONTRAST_UNDER_WIDTH, false);
		}
		RenderType type = highContrast ? RenderTypes.linesDepthBias()
			: improvedTransparency ? RenderTypes.linesTranslucentNoDepthWrite() : RenderTypes.linesTranslucent();
		collector.submitShapeOutline(poseStack, shape, type, highContrast ? HIGH_CONTRAST_COLOR : OUTLINE_COLOR, width, false);
	}
}

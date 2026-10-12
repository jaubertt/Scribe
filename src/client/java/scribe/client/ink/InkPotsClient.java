package scribe.client.ink;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.event.player.ItemEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.DisplayEntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemDisplayEntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import scribe.Scribe;
import scribe.client.BookNet;
import scribe.client.mixin.ItemTintSourcesAccessor;
import scribe.client.placed.PlacedBooksClient;
import scribe.ink.InkLayout;
import scribe.ink.InkPots;
import scribe.ink.PlacedInk;

public final class InkPotsClient {
	private static final Identifier[] ITEM_MODELS = new Identifier[DyeColor.values().length];

	static {
		for (DyeColor color : DyeColor.values()) {
			ITEM_MODELS[color.getId()] = Scribe.id(color.getSerializedName() + "_ink_bottle");
		}
	}
	public static final Identifier PLACED_MODEL = Scribe.id("placed_ink_pot");
	public static final Identifier PLACED_OPEN_MODEL = Scribe.id("placed_ink_pot_open");
	public static final Identifier PLACED_QUILL_MODEL = Scribe.id("placed_ink_quill");
	public static final RenderStateDataKey<Matrix4f> FRAME = RenderStateDataKey.create(() -> "scribe:placed_ink_frame");
	public static final RenderStateDataKey<Outline> OUTLINE = RenderStateDataKey.create(() -> "scribe:placed_ink_outline");

	private InkPotsClient() {
	}

	public static void init() {
		ItemTintSourcesAccessor.scribe$idMapper().put(Scribe.id("ink"), InkTint.MAP_CODEC);
		ItemEvents.USE_ON.register(InkPotsClient::predict);
		LevelExtractionEvents.AFTER_BLOCK_OUTLINE_EXTRACTION.register(InkPotsClient::extractOutline);
	}

	public static @Nullable Identifier itemModel(ItemStack stack, @Nullable Identifier model) {
		if (PlacedInk.isQuillCopy(stack)) {
			return PLACED_QUILL_MODEL;
		}
		if (!InkPots.isInk(stack)) {
			return model;
		}
		PlacedInk.Shown shown = PlacedInk.shownOf(stack);
		if (shown != null) {
			return shown.open() ? PLACED_OPEN_MODEL : PLACED_MODEL;
		}
		DyeColor color = InkPots.color(stack);
		return color == null ? model : itemModel(color);
	}

	public static Identifier itemModel(DyeColor color) {
		return ITEM_MODELS[color.getId()];
	}

	private static @Nullable InteractionResult predict(UseOnContext context) {
		Level level = context.getLevel();
		if (!level.isClientSide() || !(context.getPlayer() instanceof LocalPlayer player) || !InkPots.isInk(context.getItemInHand())
			|| !BookNet.scribeOnServer() || !PlacedBooksClient.placeableOnServer() || !player.mayBuild()) {
			return null;
		}
		BlockPos space = context.getClickedPos().relative(context.getClickedFace());
		return PlacedInk.canPlace(level, space) || PlacedInk.canAdd(level, space) ? InteractionResult.CONSUME : null;
	}

	public static void extract(Display.ItemDisplay entity, ItemDisplayEntityRenderState state) {
		Display.ItemDisplay.ItemRenderState item = entity.itemRenderState();
		Matrix4f frame = frameOf(item == null ? ItemStack.EMPTY : item.itemStack());
		FabricRenderState data = (FabricRenderState) state;
		if (frame != null || data.getData(FRAME) != null) {
			data.setData(FRAME, frame);
		}
	}

	public static @Nullable Matrix4f frameOf(ItemStack stack) {
		if (PlacedInk.isQuillCopy(stack)) {
			return InkLayout.quillFrame(PlacedInk.quillRotationOf(stack));
		}
		return PlacedInk.isPotCopy(stack) ? InkLayout.potFrame() : null;
	}

	public static boolean drawnFixed(DisplayEntityRenderState state) {
		return state instanceof ItemDisplayEntityRenderState && ((FabricRenderState) state).getData(FRAME) != null;
	}

	public static void submit(ItemDisplayEntityRenderState state, PoseStack poseStack, float progress) {
		Matrix4f frame = ((FabricRenderState) state).getData(FRAME);
		if (frame == null || state.renderState == null) {
			return;
		}
		Matrix4f pose = PlacedBooksClient.fromTransformation(state.renderState.transformation().get(progress).getMatrix(), frame);
		if (pose != null) {
			poseStack.mulPose(pose);
		}
	}

	public static ItemStack potOf(Level level, Entity box) {
		Vec3 at = box.position();
		for (Display.ItemDisplay display : level.getEntitiesOfClass(Display.ItemDisplay.class, new AABB(at, at).inflate(1.0 / 64.0))) {
			var slot = display.getSlot(0);
			if (slot != null && PlacedInk.isPotCopy(slot.get())) {
				return slot.get();
			}
		}
		return ItemStack.EMPTY;
	}

	public static List<Component> lookLines(ItemStack copy) {
		List<Component> lines = new ArrayList<>();
		if (copy.isEmpty()) {
			return lines;
		}
		lines.add(copy.getStyledHoverName());
		PlacedInk.Shown shown = PlacedInk.shownOf(copy);
		if (shown != null && shown.open()) {
			lines.add(Component.translatable("scribe.look.quill").withStyle(ChatFormatting.GRAY));
		}
		return lines;
	}

	public record Outline(Vec3 anchor, boolean highContrast) {
	}

	private static void extractOutline(LevelExtractionContext context, @Nullable HitResult hit) {
		Minecraft minecraft = Minecraft.getInstance();
		Outline outline = null;
		if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof Interaction box && minecraft.getCameraEntity() instanceof Player player) {
			outline = outline(context.level(), box, player.getAbilities().mayBuild, minecraft.options.highContrastBlockOutline().get());
		}
		((FabricRenderState) context.levelState()).setData(OUTLINE, outline);
	}

	public static @Nullable Outline outline(Level level, Interaction box, boolean mayBuild, boolean highContrast) {
		if (!mayBuild || !level.getWorldBorder().isWithinBounds(box.blockPosition()) || potOf(level, box).isEmpty()) {
			return null;
		}
		return new Outline(box.position(), highContrast);
	}

	public static void submitOutline(PoseStack poseStack, SubmitNodeCollector collector, LevelRenderState levelState) {
		Outline outline = ((FabricRenderState) levelState).getData(OUTLINE);
		if (outline == null) {
			return;
		}
		Vec3 camera = levelState.cameraRenderState.pos;
		GameRenderer game = Minecraft.getInstance().gameRenderer;
		poseStack.pushPose();
		poseStack.translate(outline.anchor().x - 0.5 - camera.x, outline.anchor().y - camera.y, outline.anchor().z - 0.5 - camera.z);
		PlacedBooksClient.submitOutline(poseStack, collector, InkLayout.OUTLINE, outline.highContrast(), game.useImprovedTransparency(),
			game.gameRenderState().windowRenderState.appropriateLineWidth);
		poseStack.popPose();
	}
}

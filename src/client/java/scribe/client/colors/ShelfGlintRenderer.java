package scribe.client.colors;

import scribe.Scribe;
import scribe.access.BookPreviews;
import scribe.client.mixin.RenderTypeInvoker;
import scribe.config.ScribeConfig;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class ShelfGlintRenderer implements BlockEntityRenderer<BlockEntity, ShelfGlintRenderer.State> {
	public static final float GLINT_SCALE = 1024.0F;

	private final Faces faces = new Faces();

	public ShelfGlintRenderer(BlockEntityRendererProvider.Context context) {
	}

	public static void register(Collection<Block> moreShelves) {
		Set<BlockEntityType<?>> types = new LinkedHashSet<>();
		types.add(BlockEntityTypes.CHISELED_BOOKSHELF);
		for (Block block : moreShelves) {
			BlockState state = block.defaultBlockState();
			for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
				if (type.isValid(state) && makesShelves(type, state)) {
					types.add(type);
				}
			}
		}
		for (BlockEntityType<?> type : types) {
			registerFor(type);
		}
	}

	private static boolean makesShelves(BlockEntityType<?> type, BlockState state) {
		try {
			return type.create(BlockPos.ZERO, state) instanceof ChiseledBookShelfBlockEntity;
		} catch (RuntimeException | LinkageError e) {
			Scribe.LOGGER.debug("Could not make a {} to check it", state, e);
			return false;
		}
	}

	private static <E extends BlockEntity> void registerFor(BlockEntityType<E> type) {
		BlockEntityRendererProvider<BlockEntity, State> renderer = ShelfGlintRenderer::new;
		BlockEntityRendererRegistry.register(type, renderer);
	}

	private static int slots(BlockEntity shelf) {
		return shelf instanceof BookPreviews books && ScribeConfig.get().shelfColorsShown() ? ShelfGlint.slots(books, shelf.getBlockState()) : 0;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public boolean shouldRender(BlockEntity shelf, Vec3 camera) {
		int distance = this.getViewDistance();
		return shelf.getBlockPos().distToCenterSqr(camera.x, camera.y, camera.z) < distance * distance && slots(shelf) != 0;
	}

	@Override
	public void extractRenderState(BlockEntity shelf, State state, float partialTicks, Vec3 camera, @Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(shelf, state, partialTicks, camera, breakProgress);
		state.slots = slots(shelf);
		state.facing = state.slots == 0 ? Direction.NORTH : shelf.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.slots == 0) {
			return;
		}
		poseStack.pushPose();
		ShelfGlint.turn(poseStack, state.facing);
		collector.submitModel(this.faces, state, poseStack, renderType(), state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}

	public static RenderPipeline pipeline() {
		return Pipeline.GLINT;
	}

	public static RenderType renderType() {
		return Types.GLINT;
	}

	public static final class State extends BlockEntityRenderState {
		public int slots;
		public Direction facing = Direction.NORTH;
	}

	public static final class Faces extends Model<State> {
		private final ModelPart[] slots = new ModelPart[ShelfGlint.SLOTS];

		public Faces() {
			super(build(), texture -> ShelfGlintRenderer.renderType());
			for (int slot = 0; slot < this.slots.length; slot++) {
				this.slots[slot] = this.root.getChild(name(slot));
			}
		}

		private static String name(int slot) {
			return "slot_" + slot;
		}

		private static ModelPart build() {
			Map<String, ModelPart> children = new LinkedHashMap<>();
			for (int slot = 0; slot < ShelfGlint.SLOTS; slot++) {
				int[] books = ShelfGlint.books(slot);
				int x = 16 - books[2];
				int y = 16 - books[3];
				ModelPart.Cube quad = new ModelPart.Cube(x, y, x, y, 0.0F, books[2] - books[0], books[3] - books[1], 0.0F,
					0.0F, 0.0F, 0.0F, false, GLINT_SCALE, GLINT_SCALE, Set.of(Direction.NORTH));
				children.put(name(slot), new ModelPart(List.of(quad), Map.of()));
			}
			ModelPart root = new ModelPart(List.of(), children);
			PartPose halfTurn = new PartPose(16.0F, 16.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.0F, -1.0F, 1.0F);
			root.setInitialPose(halfTurn);
			root.loadPose(halfTurn);
			return root;
		}

		@Override
		public void setupAnim(State state) {
			for (int slot = 0; slot < this.slots.length; slot++) {
				this.slots[slot].visible = (state.slots & 1 << slot) != 0;
			}
		}
	}

	private static final class Pipeline {
		static final RenderPipeline GLINT = RenderPipeline.builder()
			.withLocation(Scribe.id("pipeline/shelf_glint"))
			.withBindGroupLayout(BindGroupLayouts.GLOBALS)
			.withBindGroupLayout(BindGroupLayouts.PROJECTION)
			.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
			.withBindGroupLayout(BindGroupLayouts.FOG)
			.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
			.withVertexShader("core/glint")
			.withFragmentShader("core/glint")
			.withCull(false)
			.withColorTargetState(new ColorTargetState(BlendFunction.GLINT))
			.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false, 1.0F, 10.0F))
			.build();

		private Pipeline() {
		}
	}

	private static final class Types {
		static final RenderType GLINT = RenderTypeInvoker.scribe$create("scribe_shelf_glint", RenderSetup.builder(Pipeline.GLINT)
			.withTexture("Sampler0", ItemFeatureRenderer.ENCHANTED_GLINT_ITEM)
			.setTextureTransform(TextureTransform.GLINT_TEXTURING)
			.withForcedSolidModelPhase()
			.createRenderSetup());

		private Types() {
		}
	}
}

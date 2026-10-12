package scribe.colors;

import java.util.List;
import java.util.Objects;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public final class TableDyeing {
	private TableDyeing() {
	}

	public static void init() {
		UseBlockCallback.EVENT.register(TableDyeing::use);
		LootTableEvents.MODIFY.register((key, table, source, registries) -> keepColor(key, table, source));
	}

	public static void keepColor(ResourceKey<LootTable> key, LootTable.Builder table, LootTableSource source) {
		if (source.isBuiltin() && Blocks.ENCHANTING_TABLE.getLootTable().filter(key::equals).isPresent()) {
			table.apply(CopyComponentsFunction.copyComponentsFromBlockEntity(LootContextParams.BLOCK_ENTITY).include(DataComponents.DYED_COLOR));
		}
	}

	private static InteractionResult use(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (player.isSpectator() || player.isSecondaryUseActive() || !player.mayBuild() || !BookDyeing.tables(level)) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		if (!(level.getBlockEntity(pos) instanceof DyedTable table)) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		@Nullable DyeColor dye = stack.is(ItemTags.DYES) ? stack.get(DataComponents.DYE) : null;
		boolean water = stack.is(Items.POTION) && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER);
		if (dye == null && !water) {
			return InteractionResult.PASS;
		}
		DyedItemColor current = table.scribe$dyedColor();
		DyedItemColor next = water ? null : DyedItemColor.applyDyes(current, List.of(dye));
		if (Objects.equals(current, next)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		table.scribe$setDyedColor(next);
		BlockState state = level.getBlockState(pos);
		level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
		level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
		player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
		if (water) {
			level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
			player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
		} else {
			level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
			stack.consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}
}

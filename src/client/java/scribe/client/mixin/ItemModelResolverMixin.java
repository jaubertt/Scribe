package scribe.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import scribe.client.compat.BookIcons;
import scribe.client.ink.InkPotsClient;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
	@ModifyExpressionValue(method = "appendItemLayers", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
	private Object scribe$inkModel(Object model, @Local(argsOnly = true) ItemStack item) {
		return model == null || model instanceof Identifier ? InkPotsClient.itemModel(item, BookIcons.model(item, (Identifier) model)) : model;
	}
}

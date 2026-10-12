package scribe.client.mixin;

import net.minecraft.client.player.FirstPersonHandsAndItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FirstPersonHandsAndItems.class)
public interface FirstPersonHandsAndItemsAccessor {
	@Accessor("mainHandHeight")
	void scribe$setMainHandHeight(float height);

	@Accessor("oMainHandHeight")
	void scribe$setOldMainHandHeight(float height);

	@Accessor("offHandHeight")
	void scribe$setOffHandHeight(float height);

	@Accessor("oOffHandHeight")
	void scribe$setOldOffHandHeight(float height);
}

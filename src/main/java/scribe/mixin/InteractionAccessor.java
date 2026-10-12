package scribe.mixin;

import net.minecraft.world.entity.Interaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Interaction.class)
public interface InteractionAccessor {
	@Invoker("setWidth")
	void scribe$setWidth(float width);

	@Invoker("setHeight")
	void scribe$setHeight(float height);
}

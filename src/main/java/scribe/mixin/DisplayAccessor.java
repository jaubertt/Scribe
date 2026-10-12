package scribe.mixin;

import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.class)
public interface DisplayAccessor {
	@Invoker("setTransformation")
	void scribe$setTransformation(Transformation transformation);

	@Invoker("setWidth")
	void scribe$setWidth(float width);

	@Invoker("setHeight")
	void scribe$setHeight(float height);

	@Invoker("setBillboardConstraints")
	void scribe$setBillboardConstraints(Display.BillboardConstraints constraints);
}

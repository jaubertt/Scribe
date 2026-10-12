package scribe.client.mixin;

import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Hud.class)
public interface HudStateAccessor {
	@Accessor("displayHealth")
	int scribe$displayHealth();

	@Accessor("toolHighlightTimer")
	int scribe$toolHighlightTimer();

	@Accessor("lastToolHighlight")
	ItemStack scribe$lastToolHighlight();

	@Accessor("overlayMessageString")
	@Nullable Component scribe$overlayMessage();

	@Accessor("overlayMessageTime")
	int scribe$overlayMessageTime();
}

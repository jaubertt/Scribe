package scribe.client.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.network.chat.FontDescription;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Font.class)
public interface FontAccessor {
	@Invoker("getGlyphSource")
	GlyphSource scribe$glyphs(FontDescription font);
}

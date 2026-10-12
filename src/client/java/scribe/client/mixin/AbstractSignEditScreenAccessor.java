package scribe.client.mixin;

import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractSignEditScreen.class)
public interface AbstractSignEditScreenAccessor {
	@Accessor("messages")
	String[] scribe$messages();

	@Accessor("line")
	int scribe$line();

	@Accessor("line")
	void scribe$setLine(int line);

	@Accessor("signField")
	TextFieldHelper scribe$signField();

	@Accessor("sign")
	SignBlockEntity scribe$sign();

	@Invoker("setMessage")
	void scribe$setMessage(String message);
}

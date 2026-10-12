package scribe.client.mixin;

import scribe.client.colors.HeldBook;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin({BookEditScreen.class, BookSignScreen.class})
public abstract class HeldBookScreenMixin implements HeldBook {
	@Shadow(remap = false)
	@Final
	private Player owner;

	@Shadow(remap = false)
	@Final
	private InteractionHand hand;

	@Override
	public ItemStack scribe$heldBook() {
		return this.owner.getItemInHand(this.hand);
	}
}

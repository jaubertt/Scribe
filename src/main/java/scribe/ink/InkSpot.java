package scribe.ink;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

public record InkSpot(ItemStack pot, ItemStack quill, int quillRotation, UUID potDisplay, UUID quillDisplay) {
	public static final UUID NO_DISPLAY = new UUID(0L, 0L);
	public static final Codec<InkSpot> CODEC = RecordCodecBuilder.create(i -> i.group(
		ItemStack.CODEC.lenientOptionalFieldOf("pot", ItemStack.EMPTY).forGetter(InkSpot::pot),
		ItemStack.CODEC.lenientOptionalFieldOf("quill", ItemStack.EMPTY).forGetter(InkSpot::quill),
		Codec.intRange(0, InkLayout.ROTATIONS - 1).lenientOptionalFieldOf("quill_rotation").forGetter(spot -> Optional.of(spot.quillRotation())),
		Direction.CODEC.lenientOptionalFieldOf("facing").forGetter(spot -> Optional.<Direction>empty()),
		UUIDUtil.CODEC.lenientOptionalFieldOf("pot_display", NO_DISPLAY).forGetter(InkSpot::potDisplay),
		UUIDUtil.CODEC.lenientOptionalFieldOf("quill_display", NO_DISPLAY).forGetter(InkSpot::quillDisplay)
	).apply(i, InkSpot::read));

	public InkSpot {
		quillRotation = InkLayout.normalize(quillRotation);
	}

	private static InkSpot read(ItemStack pot, ItemStack quill, Optional<Integer> rotation, Optional<Direction> oldFacing, UUID potDisplay, UUID quillDisplay) {
		int quillRotation = rotation.orElseGet(() -> oldFacing.map(InkLayout::rotationOf).orElse(0));
		return new InkSpot(pot, quill, quillRotation, potDisplay, quillDisplay);
	}

	public static InkSpot of(ItemStack pot) {
		return new InkSpot(pot, ItemStack.EMPTY, 0, NO_DISPLAY, NO_DISPLAY);
	}

	public boolean open() {
		return !this.quill.isEmpty();
	}

	public InkSpot withQuill(ItemStack quill, int rotation) {
		return new InkSpot(this.pot, quill, rotation, this.potDisplay, this.quillDisplay);
	}

	public InkSpot withoutQuill() {
		return new InkSpot(this.pot, ItemStack.EMPTY, this.quillRotation, this.potDisplay, this.quillDisplay);
	}

	public InkSpot withDisplays(UUID pot, UUID quill) {
		return new InkSpot(this.pot, this.quill, this.quillRotation, pot, quill);
	}
}

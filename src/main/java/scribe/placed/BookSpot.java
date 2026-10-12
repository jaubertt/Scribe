package scribe.placed;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;
import scribe.Scribe;
import scribe.placed.PlacedLayout.Kind;

public record BookSpot(Kind kind, Direction facing, List<ItemStack> books, List<UUID> displays) {
	public static final UUID NO_DISPLAY = new UUID(0L, 0L);
	private static final Codec<Kind> KIND_CODEC = Codec.STRING.comapFlatMap(name -> {
		for (Kind kind : Kind.values()) {
			if (kind.name().toLowerCase(Locale.ROOT).equals(name)) {
				return DataResult.success(kind);
			}
		}
		return DataResult.error(() -> "Not a kind of placed books: " + name);
	}, kind -> kind.name().toLowerCase(Locale.ROOT));

	private record Saved(ItemStack book, UUID display) {
		static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
			ItemStack.CODEC.fieldOf("book").forGetter(Saved::book),
			UUIDUtil.CODEC.lenientOptionalFieldOf("display", NO_DISPLAY).forGetter(Saved::display)
		).apply(i, Saved::new));
	}

	private static final Codec<List<Saved>> SAVED_BOOKS = new Codec<>() {
		@Override
		public <T> DataResult<Pair<List<Saved>, T>> decode(DynamicOps<T> ops, T input) {
			return ops.getList(input).map(elements -> {
				List<Saved> read = new ArrayList<>();
				elements.accept(element -> Saved.CODEC.parse(ops, element)
					.resultOrPartial(error -> Scribe.LOGGER.error("A placed book could not be read, it was left out: {}", error))
					.filter(saved -> !saved.book().isEmpty())
					.ifPresent(read::add));
				return Pair.of(read, input);
			});
		}

		@Override
		public <T> DataResult<T> encode(List<Saved> books, DynamicOps<T> ops, T prefix) {
			return Saved.CODEC.listOf().encode(books, ops, prefix);
		}
	};

	public static final Codec<BookSpot> CODEC = RecordCodecBuilder.create(i -> i.group(
		KIND_CODEC.lenientOptionalFieldOf("kind", Kind.PILE).forGetter(BookSpot::kind),
		Direction.CODEC.lenientOptionalFieldOf("facing", Direction.NORTH).forGetter(BookSpot::facing),
		SAVED_BOOKS.fieldOf("books").forGetter(BookSpot::saved)
	).apply(i, BookSpot::fromSaved));

	public BookSpot {
		if (facing.getAxis() == Direction.Axis.Y) {
			facing = Direction.NORTH;
		}
		books = List.copyOf(books);
		List<UUID> d = new ArrayList<>(displays.subList(0, Math.min(displays.size(), books.size())));
		while (d.size() < books.size()) {
			d.add(NO_DISPLAY);
		}
		displays = List.copyOf(d);
	}

	private static BookSpot fromSaved(Kind kind, Direction facing, List<Saved> saved) {
		return new BookSpot(kind, facing, saved.stream().map(Saved::book).toList(), saved.stream().map(Saved::display).toList());
	}

	private List<Saved> saved() {
		List<Saved> out = new ArrayList<>();
		for (int i = 0; i < this.books.size(); i++) {
			out.add(new Saved(this.books.get(i), this.displays.get(i)));
		}
		return out;
	}

	public int count() {
		return this.books.size();
	}

	public boolean full() {
		return this.books.size() >= this.kind.max;
	}

	public BookSpot with(int index, ItemStack book, UUID display) {
		List<ItemStack> b = new ArrayList<>(this.books);
		List<UUID> d = new ArrayList<>(this.displays);
		b.add(index, book);
		d.add(index, display);
		return new BookSpot(this.kind, this.facing, b, d);
	}

	public BookSpot without(int index) {
		List<ItemStack> b = new ArrayList<>(this.books);
		List<UUID> d = new ArrayList<>(this.displays);
		b.remove(index);
		d.remove(index);
		return new BookSpot(this.kind, this.facing, b, d);
	}

	public BookSpot withDisplay(int index, UUID display) {
		List<UUID> d = new ArrayList<>(this.displays);
		d.set(index, display);
		return new BookSpot(this.kind, this.facing, this.books, d);
	}
}

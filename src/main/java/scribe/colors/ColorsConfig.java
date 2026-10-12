package scribe.colors;

import scribe.Scribe;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ColorsConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public enum CopyType implements StringRepresentable {
		ALL,
		DYE,
		NONE;

		public static final Codec<CopyType> CODEC = StringRepresentable.fromEnum(CopyType::values);

		@Override
		public String getSerializedName() {
			return switch (this) {
				case ALL -> "all";
				case DYE -> "dye";
				case NONE -> "none";
			};
		}

		public ItemStack apply(ItemStack output, ItemStack input) {
			return switch (this) {
				case ALL -> {
					ItemStack copy = input.transmuteCopy(output.getItem(), output.getCount());
					ItemStack outputWithoutDye = output.copy();
					outputWithoutDye.remove(DataComponents.DYED_COLOR);
					copy.applyComponentsAndValidate(outputWithoutDye.getComponentsPatch());
					yield copy;
				}
				case DYE -> {
					output.set(DataComponents.DYED_COLOR, input.get(DataComponents.DYED_COLOR));
					yield output;
				}
				case NONE -> output;
			};
		}
	}

	private record Glints(boolean writtenBook, boolean enchantedBook) {
		private static final Codec<Glints> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.fieldOf("written_book").forGetter(Glints::writtenBook),
			Codec.BOOL.fieldOf("enchanted_book").forGetter(Glints::enchantedBook)
		).apply(i, Glints::new));
	}

	private record CopyStrategies(Map<String, CopyType> crafting, CopyType bookCloning, CopyType villagerTrade) {
		private static final String CRAFTING_PREFIX = "crafting_";
		private static final Codec<String> KEY_CODEC = Codec.STRING.validate(raw -> Identifier.isValidPath(raw)
			? DataResult.success(raw)
			: DataResult.error(() -> "Non [a-z0-9/._-] character in path: " + raw));
		private static final Codec<Map<String, CopyType>> CRAFTING_CODEC = Codec.unboundedMap(
			KEY_CODEC.xmap(raw -> raw.startsWith(CRAFTING_PREFIX) ? raw.substring(CRAFTING_PREFIX.length()) : raw, key -> CRAFTING_PREFIX + key),
			CopyType.CODEC);
		private static final Codec<Pair<CopyType, CopyType>> OTHER_CODEC = RecordCodecBuilder.create(i -> i.group(
			CopyType.CODEC.fieldOf("book_cloning").forGetter(Pair::getFirst),
			CopyType.CODEC.fieldOf("villager_trade").forGetter(Pair::getSecond)
		).apply(i, Pair::of));
		private static final Codec<CopyStrategies> CODEC = Codec.pair(CRAFTING_CODEC, OTHER_CODEC).xmap(
			pair -> new CopyStrategies(pair.getFirst(), pair.getSecond().getFirst(), pair.getSecond().getSecond()),
			s -> Pair.of(s.crafting(), Pair.of(s.bookCloning(), s.villagerTrade())));
	}

	private static final Codec<ColorsConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		Glints.CODEC.fieldOf("glints").forGetter(c -> c.glints),
		CopyStrategies.CODEC.fieldOf("copy_strategies").forGetter(c -> c.copyStrategies)
	).apply(i, ColorsConfig::new));

	private static ColorsConfig instance;

	private final Glints glints;
	private final CopyStrategies copyStrategies;

	private ColorsConfig(Glints glints, CopyStrategies copyStrategies) {
		this.glints = glints;
		this.copyStrategies = copyStrategies;
	}

	private static ColorsConfig defaults() {
		return new ColorsConfig(
			new Glints(true, true),
			new CopyStrategies(Map.of("writable_book", CopyType.DYE, "enchanting_table", CopyType.DYE), CopyType.NONE, CopyType.DYE));
	}

	public static ColorsConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public static void reload() {
		instance = load();
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("scribe").resolve("colors.json");
	}

	private static ColorsConfig load() {
		Path path = path();
		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				JsonElement json = JsonParser.parseReader(reader);
				return CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(IOException::new);
			} catch (Exception e) {
				Scribe.LOGGER.error("Could not read {}, using the defaults", path, e);
				return defaults();
			}
		}
		ColorsConfig config = defaults();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(CODEC.encodeStart(JsonOps.INSTANCE, config).getOrThrow(IOException::new), writer);
			}
		} catch (IOException e) {
			Scribe.LOGGER.error("Could not write {}", path, e);
		}
		return config;
	}

	public boolean writtenBookGlint() {
		return this.glints.writtenBook();
	}

	public boolean enchantedBookGlint() {
		return this.glints.enchantedBook();
	}

	public CopyType craftingStrategy(Item item) {
		CopyType type = this.copyStrategies.crafting().get(BuiltInRegistries.ITEM.getKey(item).getPath());
		return type == null ? CopyType.DYE : type;
	}

	public CopyType bookCloningStrategy() {
		return this.copyStrategies.bookCloning();
	}

	public CopyType villagerTradeStrategy() {
		return this.copyStrategies.villagerTrade();
	}
}

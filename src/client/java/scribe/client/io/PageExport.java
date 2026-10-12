package scribe.client.io;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import scribe.Scribe;
import scribe.client.screen.BookStyle;
import scribe.BookLimits;
import scribe.client.text.Chapters;
import scribe.client.text.TextWrap;

public final class PageExport {
	public static final Style PAGE_STYLE = Style.EMPTY.withoutShadow().withColor(BookStyle.INK & 0xFFFFFF);

	public record Prepared(List<List<FormattedCharSequence>> lines, List<List<Chapters.TitleRun>> titles, Set<Integer> letters, int cover, String title, String author) {
	}

	private PageExport() {
	}

	public static Prepared prepare(Font font, BookFile book, int cover) {
		List<List<FormattedCharSequence>> lines = new ArrayList<>();
		List<List<Chapters.TitleRun>> titles = new ArrayList<>();
		Set<Integer> letters = new HashSet<>();
		for (int i = 0; i < 10; i++) {
			letters.add((int) '0' + i);
		}
		for (String page : book.pages()) {
			List<FormattedCharSequence> pageLines = TextWrap.pageLines(font, Component.literal(page), PAGE_STYLE, BookLimits.PAGE_WIDTH);
			for (FormattedCharSequence line : pageLines) {
				line.accept((position, style, codepoint) -> {
					letters.add(codepoint);
					return true;
				});
			}
			lines.add(pageLines);
			titles.add(Chapters.titleRuns(font, page, BookLimits.PAGE_WIDTH));
		}
		return new Prepared(lines, titles, letters, cover, book.title().orElse(""), book.author().orElse(""));
	}

	public static List<PageRaster> draw(Prepared book, PageFont font, PageFont.@Nullable Image bookPicture, PageFont.@Nullable Image dyedPicture) {
		List<PageRaster> out = new ArrayList<>();
		for (int i = 0; i < book.lines().size(); i++) {
			out.add(PageRaster.page(font, bookPicture, dyedPicture, book.cover(), book.lines().get(i), book.titles().get(i), i + 1));
		}
		return out;
	}

	public static void writePdf(Path file, Prepared book, List<PageRaster> pages) throws IOException {
		List<PictureFiles.Picture> pictures = new ArrayList<>();
		for (PageRaster page : pages) {
			pictures.add(new PictureFiles.Picture(page.width(), page.height(), page.pixels(), PageRaster.WIDTH * PictureFiles.POINTS_PER_PIXEL,
				PageRaster.HEIGHT * PictureFiles.POINTS_PER_PIXEL));
		}
		Files.createDirectories(file.toAbsolutePath().getParent());
		Files.write(file, PictureFiles.pdf(pictures, book.title(), book.author()));
	}

	public static Path picturesFolder(Path file) {
		String name = file.getFileName().toString();
		if (name.toLowerCase(Locale.ROOT).endsWith(".png")) {
			name = name.substring(0, name.length() - 4);
		}
		return file.resolveSibling(name.isBlank() ? "pages" : name);
	}

	public static void writePictures(Path folder, List<PageRaster> pages) throws IOException {
		Files.createDirectories(folder);
		for (int i = 0; i < pages.size(); i++) {
			PageRaster page = pages.get(i);
			Files.write(folder.resolve(String.format(Locale.ROOT, "%03d.png", i + 1)), PictureFiles.png(page.width(), page.height(), page.pixels()));
		}
	}

	public static CompletableFuture<Path> export(Path target, BookFile book, int cover, boolean pdf) {
		Minecraft minecraft = Minecraft.getInstance();
		Prepared prepared = prepare(minecraft.font, book, cover);
		ResourceManager resources = minecraft.getResourceManager();
		return CompletableFuture.supplyAsync(() -> {
			try {
				PageFont font = PageFont.load(id -> open(resources, id), id -> image(resources, id), prepared.letters());
				List<PageRaster> pages = draw(prepared, font, image(resources, BookStyle.VANILLA_BOOK), image(resources, BookStyle.SINGLE_PAGE));
				if (pdf) {
					writePdf(target, prepared, pages);
					return target;
				}
				Path folder = picturesFolder(target);
				writePictures(folder, pages);
				return folder;
			} catch (IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		}, Util.backgroundExecutor());
	}

	private static @Nullable InputStream open(ResourceManager resources, Identifier id) throws IOException {
		Optional<Resource> resource = resources.getResource(id);
		return resource.isPresent() ? resource.get().open() : null;
	}

	private static PageFont.@Nullable Image image(ResourceManager resources, Identifier id) throws IOException {
		try (InputStream in = open(resources, id)) {
			if (in == null) {
				Scribe.LOGGER.warn("Page pictures: {} not found", id);
				return null;
			}
			try (NativeImage image = NativeImage.read(in)) {
				int w = image.getWidth();
				int h = image.getHeight();
				int[] argb = new int[w * h];
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						argb[y * w + x] = image.getPixel(x, y);
					}
				}
				return new PageFont.Image(w, h, argb);
			}
		}
	}
}

package scribe.client.io;

import scribe.Scribe;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

public final class FileDialogs {
	private FileDialogs() {
	}

	public static boolean open(Path start, Consumer<Path> chosen) {
		return show(false, start, chosen);
	}

	public static boolean save(Path suggested, Consumer<Path> chosen) {
		return show(true, suggested, chosen);
	}

	private static boolean show(boolean save, Path location, Consumer<Path> chosen) {
		Minecraft minecraft = Minecraft.getInstance();
		ByteBuffer name = null;
		ByteBuffer pattern = null;
		ByteBuffer start = null;
		SDL_DialogFileFilter.Buffer filters = null;
		try {
			name = MemoryUtil.memUTF8("Books");
			pattern = MemoryUtil.memUTF8("json;txt;md");
			start = MemoryUtil.memUTF8(location.toAbsolutePath().toString());
			filters = SDL_DialogFileFilter.calloc(1);
			filters.get(0).name(name).pattern(pattern);
			Native nativeParts = new Native(name, pattern, start, filters);
			SDL_DialogFileCallback callback = SDL_DialogFileCallback.create((userdata, fileList, filter) -> {
				Path result = null;
				if (fileList != 0) {
					long first = MemoryUtil.memGetAddress(fileList);
					if (first != 0) {
						result = Path.of(MemoryUtil.memUTF8(first));
					}
				}
				Path picked = result;
				minecraft.execute(() -> {
					nativeParts.free();
					if (picked != null) {
						chosen.accept(picked);
					}
				});
			});
			nativeParts.callback = callback;
			long window = minecraft.getWindow().handle();
			if (save) {
				SDLDialog.SDL_ShowSaveFileDialog(callback, 0L, window, filters, start);
			} else {
				SDLDialog.SDL_ShowOpenFileDialog(callback, 0L, window, filters, start, false);
			}
			return true;
		} catch (Throwable e) {
			Scribe.LOGGER.warn("Could not open the file window", e);
			if (filters != null) {
				filters.free();
			}
			MemoryUtil.memFree(name);
			MemoryUtil.memFree(pattern);
			MemoryUtil.memFree(start);
			return false;
		}
	}

	private static final class Native {
		private final ByteBuffer name;
		private final ByteBuffer pattern;
		private final ByteBuffer start;
		private final SDL_DialogFileFilter.Buffer filters;
		private SDL_DialogFileCallback callback;

		Native(ByteBuffer name, ByteBuffer pattern, ByteBuffer start, SDL_DialogFileFilter.Buffer filters) {
			this.name = name;
			this.pattern = pattern;
			this.start = start;
			this.filters = filters;
		}

		void free() {
			this.filters.free();
			MemoryUtil.memFree(this.name);
			MemoryUtil.memFree(this.pattern);
			MemoryUtil.memFree(this.start);
			if (this.callback != null) {
				this.callback.free();
			}
		}
	}
}

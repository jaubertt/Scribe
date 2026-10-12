package scribe.client;

import scribe.access.BookPreviews;
import scribe.access.LecternAccess;
import scribe.access.LecternWatch;
import scribe.client.colors.ColorsClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class LecternMotions {
	public static final float OPENNESS = 1.2F;
	public static final RenderStateDataKey<Moment> MOMENT = RenderStateDataKey.create(() -> "scribe:lectern_book");
	public static final Moment OPEN = new Moment(1.0F, null);
	public static final Moment CLOSED = new Moment(0.0F, null);

	private static final Map<LecternBlockEntity, Watched> WATCHED = new WeakHashMap<>();
	private static @Nullable ClientLevel watchedLevel;

	public record Moment(float open, BookModel.@Nullable State state) {
		public boolean shut() {
			return this.open <= 0.0F;
		}
	}

	static final class Watched {
		boolean book;
		boolean closed;
		int page;
		BookMotion motion;

		Watched(boolean book, boolean closed, int page) {
			this.book = book;
			this.closed = closed;
			this.page = page;
			this.motion = new BookMotion(!closed);
		}
	}

	private LecternMotions() {
	}

	public static void init() {
		LecternWatch.listen(LecternMotions::arrived);
		ClientTickEvents.END_LEVEL_TICK.register(LecternMotions::tick);
	}

	static void arrived(LecternBlockEntity lectern, boolean wasClosed, boolean closed, int oldPage, int newPage) {
		boolean book = hasBook(lectern.getBlockState());
		Watched seen = WATCHED.get(lectern);
		if (seen == null) {
			WATCHED.put(lectern, new Watched(book, closed, newPage));
			return;
		}
		apply(seen, book, closed, newPage);
	}

	static void apply(Watched seen, boolean book, boolean closed, int page) {
		if (!seen.book || !book) {
			seen.motion = new BookMotion(!closed);
		} else if (seen.closed != closed) {
			seen.motion.setOpen(!closed);
			if (!closed) {
				seen.motion.turn(leaves(seen.page, page));
			}
		} else if (!closed) {
			seen.motion.turn(leaves(seen.page, page));
		}
		seen.book = book;
		seen.closed = closed;
		seen.page = page;
	}

	public static int leaves(int from, int to) {
		if (from < 0 || to < 0 || from == to) {
			return 0;
		}
		int leaves = Math.min(BookMotion.MAX_LEAVES, (Math.abs(to - from) + 1) / 2);
		return to > from ? leaves : -leaves;
	}

	public static void pageSound(BlockPos pos) {
		pageSound(pos, 0);
	}

	public static void pageSound(BlockPos pos, int data) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || !(level.getBlockEntity(pos) instanceof LecternBlockEntity lectern)) {
			return;
		}
		boolean closed = ((LecternAccess) lectern).scribe$isClosed();
		int page = ((BookPreviews) lectern).scribe$page();
		Watched seen = WATCHED.computeIfAbsent(lectern, l -> new Watched(true, closed, page));
		if (soundTurns(page, seen.closed, data)) {
			seen.motion.turn(1);
		}
	}

	public static void leavesTurned(BlockPos pos, int leaves) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || !(level.getBlockEntity(pos) instanceof LecternBlockEntity lectern)) {
			return;
		}
		boolean closed = ((LecternAccess) lectern).scribe$isClosed();
		int page = ((BookPreviews) lectern).scribe$page();
		Watched seen = WATCHED.computeIfAbsent(lectern, l -> new Watched(true, closed, page));
		if (!closed) {
			seen.motion.turn(leaves);
		}
	}

	static boolean soundTurns(int sharedPage, boolean closed) {
		return soundTurns(sharedPage, closed, 0);
	}

	static boolean soundTurns(int sharedPage, boolean closed, int data) {
		return !closed && (data == LecternWatch.LEAF_TURN || sharedPage < 0);
	}

	private static boolean hasBook(BlockState state) {
		return state.hasProperty(LecternBlock.HAS_BOOK) && state.getValue(LecternBlock.HAS_BOOK);
	}

	static void tick(ClientLevel level) {
		if (level != watchedLevel) {
			WATCHED.clear();
			watchedLevel = level;
		}
		for (Watched seen : WATCHED.values()) {
			seen.motion.tick();
		}
	}

	public static Moment moment(LecternBlockEntity lectern, boolean closed, float partialTicks) {
		Watched seen = WATCHED.get(lectern);
		return seen == null ? (closed ? CLOSED : OPEN) : moment(seen.motion, closed, partialTicks);
	}

	static Moment moment(BookMotion motion, boolean closed, float partialTicks) {
		if (motion.resting()) {
			return motion.opening() && !closed ? OPEN : CLOSED;
		}
		float open = motion.open(partialTicks);
		return new Moment(open, open <= 0.0F ? ColorsClient.CLOSED_BOOK : motion.state(partialTicks, OPENNESS));
	}

	public static void lay(PoseStack poseStack, float open) {
		if (open >= 1.0F) {
			return;
		}
		if (open <= 0.0F) {
			ColorsClient.layClosedBook(poseStack);
			return;
		}
		float turn = Mth.HALF_PI * (1.0F - open);
		float sin = Mth.sin(turn);
		poseStack.translate(sin / 16.0F, 0.0F, 3.0F * sin / 16.0F);
		poseStack.rotate(Axis.YP.rotation(turn));
	}
}

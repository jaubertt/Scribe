package scribe.access;

import scribe.LibrarianLecterns.Hold;
import org.jspecify.annotations.Nullable;

public interface LecternAccess {
	void scribe$bookEdited();

	boolean scribe$isClosed();

	void scribe$setClosed(boolean closed);

	@Nullable Hold scribe$librarian();

	void scribe$setLibrarian(@Nullable Hold hold);
}

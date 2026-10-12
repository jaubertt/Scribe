package scribe.server;

import scribe.config.ScribeConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.FilterMask;
import net.minecraft.server.network.FilteredText;

public final class SignCodes {
	private static final Pattern CODE = Pattern.compile("(?i)§[0-9A-FK-OR]");

	private SignCodes() {
	}

	public static List<FilteredText> keepCodes(List<String> sent, List<FilteredText> filtered) {
		if (!ScribeConfig.get().signFormattingOn() || sent.size() != filtered.size()) {
			return filtered;
		}
		List<FilteredText> out = new ArrayList<>(filtered.size());
		for (int i = 0; i < filtered.size(); i++) {
			out.add(withCodes(sent.get(i), filtered.get(i)));
		}
		return out;
	}

	static FilteredText withCodes(String sent, FilteredText line) {
		if (sent == null) {
			return line;
		}
		String stripped = ChatFormatting.stripFormatting(sent);
		if (sent.equals(stripped) || !line.raw().equals(stripped)) {
			return line;
		}
		FilterMask mask = line.mask();
		if (mask.isEmpty()) {
			return FilteredText.passThrough(sent);
		}
		if (mask.isFullyFiltered()) {
			return new FilteredText(sent, FilterMask.FULLY_FILTERED);
		}
		int[] at = new int[stripped.length()];
		int n = 0;
		int pos = 0;
		Matcher m = CODE.matcher(sent);
		while (m.find()) {
			for (int i = pos; i < m.start() && n < at.length; i++) {
				at[n++] = i;
			}
			pos = m.end();
		}
		for (int i = pos; i < sent.length() && n < at.length; i++) {
			at[n++] = i;
		}
		if (n != at.length) {
			return line;
		}
		String hidden = mask.apply("x".repeat(stripped.length()));
		if (hidden == null) {
			return new FilteredText(sent, FilterMask.FULLY_FILTERED);
		}
		FilterMask kept = new FilterMask(sent.length());
		for (int i = 0; i < hidden.length() && i < at.length; i++) {
			if (hidden.charAt(i) == '#') {
				kept.setFiltered(at[i]);
			}
		}
		return new FilteredText(sent, kept);
	}
}

package scribe.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public final class Keys {
	private Keys() {
	}

	public static boolean mac() {
		return InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY;
	}

	public static boolean ctrl(InputWithModifiers e) {
		return ctrl(e, mac());
	}

	public static boolean ctrl(InputWithModifiers e, boolean mac) {
		return (e.modifiers() & (mac ? InputConstants.MOD_SUPER : InputConstants.MOD_CONTROL)) != 0;
	}

	public static boolean shift(InputWithModifiers e) {
		return e.hasShiftDown();
	}

	public static boolean alt(InputWithModifiers e) {
		return e.hasAltDown();
	}

	public static boolean isLeftClick(int button) {
		return button == InputConstants.MOUSE_BUTTON_LEFT;
	}

	public static boolean isRightClick(int button) {
		return button == InputConstants.MOUSE_BUTTON_RIGHT;
	}

	public static boolean ctrlLetter(KeyEvent e, char letter) {
		return ctrlLetter(e, letter, mac());
	}

	public static boolean ctrlLetter(KeyEvent e, char letter, boolean mac) {
		return ctrl(e, mac) && !alt(e) && e.shortcutKey() == Character.toLowerCase(letter);
	}

	public static boolean ctrlBackslash(KeyEvent e) {
		return ctrlBackslash(e, mac());
	}

	public static boolean ctrlBackslash(KeyEvent e, boolean mac) {
		return ctrl(e, mac) && !alt(e) && (e.shortcutKey() == '\\' || e.input() == InputConstants.KEY_BACKSLASH);
	}

	public static @Nullable String backslashKeyName() {
		if (Minecraft.getInstance() == null) {
			return null;
		}
		try {
			return InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_BACKSLASH).getDisplayName().getString();
		} catch (RuntimeException | LinkageError e) {
			return null;
		}
	}

	public static String clearFormattingKeys(boolean mac, @Nullable String physicalName) {
		String keys = ctrlName(mac) + "+\\";
		if (physicalName == null || physicalName.isBlank() || physicalName.equals("\\") || physicalName.codePointCount(0, physicalName.length()) != 1) {
			return keys;
		}
		return keys + " / " + ctrlName(mac) + "+" + physicalName;
	}

	public static String clearFormattingKeys() {
		return clearFormattingKeys(mac(), backslashKeyName());
	}

	public static boolean isEscape(KeyEvent e) {
		return e.isEscape();
	}

	public static boolean isEnter(KeyEvent e) {
		return e.isConfirmation();
	}

	public static boolean isBackspace(KeyEvent e) {
		return e.input() == InputConstants.KEY_BACKSPACE;
	}

	public static boolean isDelete(KeyEvent e) {
		return e.input() == InputConstants.KEY_DELETE;
	}

	public static boolean isTab(KeyEvent e) {
		return e.input() == InputConstants.KEY_TAB;
	}

	public static boolean shiftHeldNow() {
		return Minecraft.getInstance().hasShiftDown();
	}

	public static boolean isLeft(KeyEvent e) {
		return e.isLeft();
	}

	public static boolean isRight(KeyEvent e) {
		return e.isRight();
	}

	public static boolean isUp(KeyEvent e) {
		return e.isUp();
	}

	public static boolean isDown(KeyEvent e) {
		return e.isDown();
	}

	public static boolean isPageUp(KeyEvent e) {
		return e.input() == InputConstants.KEY_PAGEUP;
	}

	public static boolean isPageDown(KeyEvent e) {
		return e.input() == InputConstants.KEY_PAGEDOWN;
	}

	public static boolean isHome(KeyEvent e) {
		return e.input() == InputConstants.KEY_HOME;
	}

	public static boolean isEnd(KeyEvent e) {
		return e.input() == InputConstants.KEY_END;
	}

	public static boolean isLeftAlt(KeyEvent e) {
		return e.input() == InputConstants.KEY_LALT;
	}

	public static boolean isRightAlt(KeyEvent e) {
		return e.input() == InputConstants.KEY_RALT;
	}

	public static boolean otherModifierHeld(KeyEvent e) {
		return e.hasShiftDown() || e.hasControlDown() || (e.modifiers() & InputConstants.MOD_SUPER) != 0;
	}

	public static boolean fastModifierHeld() {
		if (Minecraft.getInstance().hasControlDown()) {
			return true;
		}
		return Util.getPlatform() == Util.OS.OSX
			&& (InputConstants.isKeyDown(InputConstants.KEY_LGUI) || InputConstants.isKeyDown(InputConstants.KEY_RGUI));
	}

	public static boolean slowModifierHeld() {
		return Minecraft.getInstance().hasShiftDown();
	}

	public static String ctrlName() {
		return ctrlName(mac());
	}

	public static String ctrlName(boolean mac) {
		return mac ? "Cmd" : "Ctrl";
	}

	public static String altName() {
		return altName(mac());
	}

	public static String altName(boolean mac) {
		return mac ? "Option" : "Alt";
	}
}

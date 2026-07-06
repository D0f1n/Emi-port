package dev.emi.emi.input;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

public class EmiInput {
	public static final int CONTROL_MASK = 1;
	public static final int ALT_MASK = 2;
	public static final int SHIFT_MASK = 4;

	// 26.2 moved Screen's static modifier helpers onto per-event InputWithModifiers;
	// EMI needs the global keyboard state (e.g. for mouse binds), so poll GLFW directly.
	public static boolean isControlDown() {
		Window window = Minecraft.getInstance().getWindow();
		if (Util.getPlatform() == Util.OS.OSX) {
			return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SUPER)
				|| InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SUPER);
		}
		return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
			|| InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
	}

	public static boolean isAltDown() {
		Window window = Minecraft.getInstance().getWindow();
		return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT)
			|| InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
	}

	public static boolean isShiftDown() {
		Window window = Minecraft.getInstance().getWindow();
		return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
			|| InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
	}

	public static int maskFromCode(int keyCode) {
		if (Util.getPlatform() == Util.OS.OSX) {
			if (keyCode == GLFW.GLFW_KEY_LEFT_SUPER || keyCode == GLFW.GLFW_KEY_RIGHT_SUPER) {
				return CONTROL_MASK;
			}
		}
		if (keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL) {
			return CONTROL_MASK;
		} else if (keyCode == GLFW.GLFW_KEY_LEFT_ALT || keyCode == GLFW.GLFW_KEY_RIGHT_ALT) {
			return ALT_MASK;
		} else if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
			return SHIFT_MASK;
		}
		return 0;
	}

	public static int getCurrentModifiers() {
		int ret = 0;
		if (isControlDown()) {
			ret |= CONTROL_MASK;
		}
		if (isAltDown()) {
			ret |= ALT_MASK;
		}
		if (isShiftDown()) {
			ret |= SHIFT_MASK;
		}
		return ret;
	}
}

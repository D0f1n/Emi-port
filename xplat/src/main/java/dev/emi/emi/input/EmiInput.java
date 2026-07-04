package dev.emi.emi.input;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;

public class EmiInput {
	private static final int GLFW_KEY_LEFT_SHIFT = 340;
	private static final int GLFW_KEY_RIGHT_SHIFT = 344;

	public static boolean isShiftDown() {
		var window = Minecraft.getInstance().getWindow();
		return InputConstants.isKeyDown(window, GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(window, GLFW_KEY_RIGHT_SHIFT);
	}
}

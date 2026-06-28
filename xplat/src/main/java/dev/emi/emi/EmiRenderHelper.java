package dev.emi.emi;

import java.text.DecimalFormat;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiDrawContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Trimmed render helper for Stage 4. Only the small widget-overlay drawers used by the stack render
 * methods are ported (amount text + the 4x4 corner sprites from {@code widgets.png}). The original's
 * recipe/screen drawing, nine-patch, and full tooltip rendering are screen/recipe-round work and are
 * omitted here. TODO(screen/recipe): reintroduce the rest with the screen and recipe rounds.
 */
public class EmiRenderHelper {
	public static final DecimalFormat TEXT_FORMAT = new DecimalFormat("#,###.##");
	public static final Component EMPTY_TEXT = EmiPort.literal("");
	public static final Minecraft CLIENT = Minecraft.getInstance();
	public static final Identifier WIDGETS = EmiPort.id("emi", "textures/gui/widgets.png");

	public static void renderAmount(EmiDrawContext context, int x, int y, Component amount) {
		int tx = x + 17 - Math.min(14, CLIENT.font.width(amount));
		context.drawTextWithShadow(amount, tx, y + 9, -1);
	}

	public static void renderIngredient(EmiIngredient ingredient, EmiDrawContext context, int x, int y) {
		context.drawTexture(WIDGETS, x, y, 8, 252, 4, 4);
	}

	public static void renderTag(EmiIngredient ingredient, EmiDrawContext context, int x, int y) {
		if (ingredient.getEmiStacks().size() > 1) {
			context.drawTexture(WIDGETS, x, y + 12, 0, 252, 4, 4);
		}
	}

	public static void renderRemainder(EmiIngredient ingredient, EmiDrawContext context, int x, int y) {
		for (EmiStack stack : ingredient.getEmiStacks()) {
			EmiStack remainder = stack.getRemainder();
			if (!remainder.isEmpty()) {
				if (remainder.equals(ingredient)) {
					renderCatalyst(ingredient, context, x, y);
				} else {
					context.drawTexture(WIDGETS, x + 12, y, 4, 252, 4, 4);
				}
				return;
			}
		}
	}

	public static void renderCatalyst(EmiIngredient ingredient, EmiDrawContext context, int x, int y) {
		context.drawTexture(WIDGETS, x + 12, y, 12, 252, 4, 4);
	}
}

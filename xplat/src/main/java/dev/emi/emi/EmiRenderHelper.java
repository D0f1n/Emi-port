package dev.emi.emi;

import java.text.DecimalFormat;
import java.util.List;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiDrawContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The EMI render helper: the small widget-overlay drawers used by the stack render methods (amount
 * text + the 4x4 corner sprites from {@code widgets.png}), plus the recipe screen drawing (nine
 * patch, page text, tooltips).
 */
public class EmiRenderHelper {
	public static final DecimalFormat TEXT_FORMAT = new DecimalFormat("#,###.##");
	public static final Component EMPTY_TEXT = EmiPort.literal("");
	public static final Minecraft CLIENT = Minecraft.getInstance();
	public static final Identifier WIDGETS = EmiPort.id("emi", "textures/gui/widgets.png");
	public static final Identifier BUTTONS = EmiPort.id("emi", "textures/gui/buttons.png");
	public static final Identifier BACKGROUND = EmiPort.id("emi", "textures/gui/background.png");

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

	public static void drawNinePatch(EmiDrawContext context, Identifier texture, int x, int y, int w, int h, int u, int v, int cornerLength, int centerLength) {
		int cor = cornerLength;
		int cen = centerLength;
		int corcen = cor + cen;
		int innerWidth = w - cornerLength * 2;
		int innerHeight = h - cornerLength * 2;
		int coriw = cor + innerWidth;
		int corih = cor + innerHeight;
		// TL
		context.drawTexture(texture, x,         y,         cor,        cor,         u,          v,          cor, cor, 256, 256);
		// T
		context.drawTexture(texture, x + cor,   y,         innerWidth, cor,         u + cor,    v,          cen, cor, 256, 256);
		// TR
		context.drawTexture(texture, x + coriw, y,         cor,        cor,         u + corcen, v,          cor, cor, 256, 256);
		// L
		context.drawTexture(texture, x,         y + cor,   cor,        innerHeight, u,          v + cor,    cor, cen, 256, 256);
		// C
		context.drawTexture(texture, x + cor,   y + cor,   innerWidth, innerHeight, u + cor,    v + cor,    cen, cen, 256, 256);
		// R
		context.drawTexture(texture, x + coriw, y + cor,   cor,        innerHeight, u + corcen, v + cor,    cor, cen, 256, 256);
		// BL
		context.drawTexture(texture, x,         y + corih, cor,        cor,         u,          v + corcen, cor, cor, 256, 256);
		// B
		context.drawTexture(texture, x + cor,   y + corih, innerWidth, cor,         u + cor,    v + corcen, cen, cor, 256, 256);
		// BR
		context.drawTexture(texture, x + coriw, y + corih, cor,        cor,         u + corcen, v + corcen, cor, cor, 256, 256);
	}

	public static void drawScroll(EmiDrawContext context, int x, int y, int width, int height, int progress, int total, int color) {
		if (total <= 1) {
			return;
		}
		int start = x + width * progress / total;
		int end = start + Math.max(width / total, 1);
		if (progress == total - 1) {
			end = x + width;
			start = end - Math.max(width / total, 1);
		}
		context.fill(start, y, end - start, height, color);
	}

	public static Component getPageText(int page, int total, int maxWidth) {
		Component text = EmiPort.translatable("emi.page", page, total);
		if (CLIENT.font.width(text) > maxWidth) {
			text = EmiPort.translatable("emi.page.short", page, total);
			if (CLIENT.font.width(text) > maxWidth) {
				text = EmiPort.literal("" + page);
				if (CLIENT.font.width(text) > maxWidth) {
					text = EmiPort.literal("");
				}
			}
		}
		return text;
	}

	/**
	 * Draws a tooltip through the two-phase context. The original's wrap-to-width reflow of long
	 * lines is polish-round work.
	 */
	public static void drawTooltip(EmiDrawContext context, List<ClientTooltipComponent> components, int x, int y) {
		if (components.isEmpty()) {
			return;
		}
		context.raw().tooltip(CLIENT.font, components, x, Math.max(16, y), DefaultTooltipPositioner.INSTANCE, null);
	}
}

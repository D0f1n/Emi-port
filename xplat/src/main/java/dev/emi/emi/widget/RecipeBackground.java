package dev.emi.emi.widget;

import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.runtime.EmiDrawContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class RecipeBackground extends Widget {
	private final int x, y, width, height;

	public RecipeBackground(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	@Override
	public Bounds getBounds() {
		return Bounds.EMPTY;
	}

	@Override
	public void render(GuiGraphicsExtractor raw, int mouseX, int mouseY, float delta) {
		EmiDrawContext context = EmiDrawContext.wrap(raw);
		EmiRenderHelper.drawNinePatch(context, EmiRenderHelper.BACKGROUND, x, y, width, height, 27, 0, 4, 1);
	}
}

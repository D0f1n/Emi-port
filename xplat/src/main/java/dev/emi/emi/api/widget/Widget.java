package dev.emi.emi.api.widget;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

/**
 * The recipe display widget base. On 26.2 the original's {@code Drawable} interface and
 * {@code DrawContext} parameter are replaced by the {@link GuiGraphicsExtractor} context, and
 * tooltips use the client tooltip component type directly.
 */
public abstract class Widget {

	public abstract Bounds getBounds();

	public abstract void render(GuiGraphicsExtractor draw, int mouseX, int mouseY, float delta);

	public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
		return List.of();
	}

	public boolean mouseClicked(int mouseX, int mouseY, int button) {
		return false;
	}

	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		return false;
	}
}

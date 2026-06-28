package dev.emi.emi.api.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Provides a method to render something at a position.
 *
 * <p>On 26.2 the raw GUI context is {@link GuiGraphicsExtractor} (the renamed, render-state-based
 * replacement for {@code GuiGraphics}/{@code DrawContext}).
 */
public interface EmiRenderable {

	void render(GuiGraphicsExtractor draw, int x, int y, float delta);
}

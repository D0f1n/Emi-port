package dev.emi.emi.screen.tooltip;

import dev.emi.emi.EmiPort;
import dev.emi.emi.runtime.EmiDrawContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

/**
 * The bridge from EMI drawing to vanilla tooltip components. The original overrode
 * {@code TooltipComponent.drawItems/drawText}; on 26.2 those became
 * {@link ClientTooltipComponent#extractImage}/{@code extractText}, and both hand over the live
 * {@link GuiGraphicsExtractor}, so a component draws inside a tooltip with the same context as the
 * rest of the GUI. The original's render data carried an {@code ItemRenderer} and a text matrix +
 * immediate buffer; neither survives — items and text submit through the extractor like everything
 * else.
 */
public interface EmiTooltipComponent extends ClientTooltipComponent {

	default void drawTooltip(EmiDrawContext context, TooltipRenderData tooltip) {
	}

	default void drawTooltipText(TextRenderData text) {
	}

	@Override
	default void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor raw) {
		EmiDrawContext context = EmiDrawContext.wrap(raw);
		context.push();
		context.pose().translate(x, y);
		drawTooltip(context, new TooltipRenderData(font, x, y));
		context.pop();
	}

	@Override
	default void extractText(GuiGraphicsExtractor raw, Font font, int x, int y) {
		drawTooltipText(new TextRenderData(EmiDrawContext.wrap(raw), font, x, y));
	}

	public static class TextRenderData {
		private final EmiDrawContext context;
		public final Font renderer;
		public final int x, y;

		public TextRenderData(EmiDrawContext context, Font renderer, int x, int y) {
			this.context = context;
			this.renderer = renderer;
			this.x = x;
			this.y = y;
		}

		public void draw(String text, int x, int y, int color, boolean shadow) {
			draw(EmiPort.literal(text), x, y, color, shadow);
		}

		public void draw(Component text, int x, int y, int color, boolean shadow) {
			if (shadow) {
				context.drawTextWithShadow(text, x + this.x, y + this.y, color);
			} else {
				context.drawText(text, x + this.x, y + this.y, color);
			}
		}
	}

	public static class TooltipRenderData {
		public final Font text;
		public final int x, y;

		public TooltipRenderData(Font text, int x, int y) {
			this.text = text;
			this.x = x;
			this.y = y;
		}
	}
}

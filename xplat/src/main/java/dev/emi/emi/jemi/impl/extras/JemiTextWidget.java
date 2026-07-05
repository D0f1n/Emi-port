package dev.emi.emi.jemi.impl.extras;

import java.util.List;

import dev.emi.emi.api.widget.WidgetHolder;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.gui.widgets.ITextWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;

/**
 * JEI's extras text widget mapped onto EMI text widgets. Lines are wrapped to the requested width
 * and materialized when the recipe display is built (after the plugin had a chance to position and
 * style this placeable).
 */
public class JemiTextWidget extends JemiPlaceable<ITextWidget> implements ITextWidget {
	public int color = 0xff404040;
	public boolean shadow = false;
	public int spacing = 2;
	public HorizontalAlignment horizontal = HorizontalAlignment.LEFT;
	public VerticalAlignment vertical = VerticalAlignment.TOP;
	public List<FormattedText> text;

	public JemiTextWidget(List<FormattedText> text, int width, int height) {
		super(width, height);
		this.text = text;
	}

	public void addWidgets(WidgetHolder holder) {
		Font font = Minecraft.getInstance().font;
		List<FormattedText> lines = text.stream()
			.flatMap(t -> font.getSplitter().splitLines(t, Math.max(1, width), net.minecraft.network.chat.Style.EMPTY).stream())
			.toList();
		int lineHeight = font.lineHeight + spacing;
		int textHeight = lines.size() * lineHeight - spacing;
		int startY = y + switch (vertical) {
			case TOP -> 0;
			case CENTER -> Math.max(0, (height - textHeight) / 2);
			case BOTTOM -> Math.max(0, height - textHeight);
		};
		for (int i = 0; i < lines.size(); i++) {
			FormattedText line = lines.get(i);
			int lineWidth = font.width(line);
			int lineX = x + switch (horizontal) {
				case LEFT -> 0;
				case CENTER -> Math.max(0, (width - lineWidth) / 2);
				case RIGHT -> Math.max(0, width - lineWidth);
			};
			holder.addText(Language.getInstance().getVisualOrder(line), lineX, startY + i * lineHeight, color, shadow);
		}
	}

	@Override
	public ITextWidget setFont(Font font) {
		// TODO(polish): custom fonts are not supported; EMI renders with the default font.
		return this;
	}

	@Override
	public ITextWidget setColor(int color) {
		this.color = color;
		return this;
	}

	@Override
	public ITextWidget setLineSpacing(int spacing) {
		this.spacing = spacing;
		return this;
	}

	@Override
	public ITextWidget setShadow(boolean shadow) {
		this.shadow = shadow;
		return this;
	}

	@Override
	public ITextWidget setTextAlignment(HorizontalAlignment horizontalAlignment) {
		this.horizontal = horizontalAlignment;
		return this;
	}

	@Override
	public ITextWidget setTextAlignment(VerticalAlignment verticalAlignment) {
		this.vertical = verticalAlignment;
		return this;
	}
}

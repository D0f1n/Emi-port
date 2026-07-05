package dev.emi.emi.jemi.impl.extras;

import java.util.List;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IScrollBoxWidget;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.FormattedText;

/** TODO(polish): scroll boxes are recorded but not rendered (matches the 1.21.1 bridge). */
public class JemiScrollBoxWidget implements IScrollBoxWidget {
	private static final int SCROLL_BAR_WIDTH = 18;
	public int x, y;
	public int width, height;

	public JemiScrollBoxWidget(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width + SCROLL_BAR_WIDTH;
		this.height = height;
	}

	@Override
	public ScreenPosition getPosition() {
		return new ScreenPosition(x, y);
	}

	@Override
	public ScreenRectangle getArea() {
		return new ScreenRectangle(x, y, width, height);
	}

	@Override
	public int getContentAreaWidth() {
		return width - SCROLL_BAR_WIDTH;
	}

	@Override
	public int getContentAreaHeight() {
		return height;
	}

	@Override
	public IScrollBoxWidget setContents(IDrawable contents) {
		return this;
	}

	@Override
	public IScrollBoxWidget setContents(List<FormattedText> text) {
		return this;
	}
}

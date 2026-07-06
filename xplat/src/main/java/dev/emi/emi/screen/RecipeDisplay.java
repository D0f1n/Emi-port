package dev.emi.emi.screen;

import java.util.List;

import com.google.common.collect.Lists;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.RecipeFillButtonWidget;
import dev.emi.emi.api.widget.TextWidget;
import dev.emi.emi.api.widget.TextWidget.Alignment;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.runtime.EmiLog;

/**
 * A single recipe's display within the recipe screen.
 *
 * <p>Port note: of the original's side buttons only craft-fill is present — the recipe tree /
 * default buttons wait for the BoM round; the screenshot button and the config gates for the
 * buttons are polish. TODO(polish)
 */
public class RecipeDisplay {
	public static final int DISPLAY_PADDING = 8;
	public final EmiRecipe recipe;
	private final int width, height;
	private List<ButtonType> rightButtons = Lists.newArrayList();
	private int rightWidth = 0;
	private int rows = 0;
	public Throwable exception;

	public RecipeDisplay(EmiRecipe recipe) {
		this.recipe = recipe;
		width = recipe.getDisplayWidth();
		height = recipe.getDisplayHeight();
		if (EmiRecipeFiller.isSupported(recipe) && EmiConfig.recipeFillButton) {
			rightButtons.add(ButtonType.FILL);
		}
		rows = Math.max(1, (height + DISPLAY_PADDING + 2) / 14);
		rightWidth = Math.max(0, (rightButtons.size() + rows - 1) / rows * 14 - 1);
	}

	// Error display
	public RecipeDisplay(Throwable exception) {
		this.recipe = null;
		this.width = 128;
		this.height = 64;
		this.exception = exception;
	}

	public WidgetGroup getWidgets(int x, int y, int availableWidth, int availableHeight) {
		int wx = x + (availableWidth - width) / 2;
		wx = Math.max(x, Math.min(x + availableWidth - width - getRightWidth(), wx));
		int wy = y;
		int wWidth = width;
		int wHeight = Math.min(availableHeight, height);
		WidgetGroup widgets = new WidgetGroup(recipe, wx, wy, wWidth, wHeight);
		if (recipe != null) {
			try {
				recipe.addWidgets(widgets);
				addButtons(widgets, rightButtons, width + 5, 14);
			} catch (Throwable t) {
				EmiLog.error("Error constructing recipe widgets", t);
				widgets = new WidgetGroup(recipe, wx, wy, wWidth, wHeight);
				widgets.add(new TextWidget(EmiPort.ordered(EmiPort.translatable("emi.error.recipe.render")),
					wWidth / 2, wHeight / 2 - 5, 0xFFFF5555, true).horizontalAlign(Alignment.CENTER));
			}
		} else {
			widgets.add(new TextWidget(EmiPort.ordered(EmiPort.translatable("emi.error.recipe.initialize")),
				wWidth / 2, wHeight / 2 - 5, 0xFFFF5555, true).horizontalAlign(Alignment.CENTER));
		}
		return widgets;
	}

	private void addButtons(WidgetGroup widgets, List<ButtonType> types, int x, int xOff) {
		int space = Math.min(8, height + 8 - (Math.min(rows, types.size()) * 14 - 2));
		int bottom = height + DISPLAY_PADDING / 2 - 12 - space / 2;
		int size = types.size();
		while (size > 0) {
			int used = Math.min(rows, size);
			List<ButtonType> current = types.subList(size - used, size);
			int yOff = 0;
			for (ButtonType type : current) {
				int bx = x;
				int by = bottom - yOff;
				widgets.add(switch (type) {
					case FILL -> new RecipeFillButtonWidget(bx, by, recipe);
				});
				yOff += 14;
			}
			size -= used;
			x += xOff;
		}
	}

	public int getRightWidth() {
		return rightWidth;
	}

	public int getWidth() {
		return rightWidth + width;
	}

	public int getHeight() {
		return height;
	}

	private static enum ButtonType {
		FILL
	}
}

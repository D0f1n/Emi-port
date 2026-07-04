package dev.emi.emi.screen.widget;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.runtime.EmiDrawContext;
import net.minecraft.resources.Identifier;

/**
 * The recipe screen's small arrow button. On 26.2 the vanilla {@code Button} moved to the two-phase
 * render-state model; since the recipe screen draws and hit-tests everything itself, this is a plain
 * EMI-side widget rather than a vanilla button subclass.
 */
public class SizedButtonWidget {
	public int x, y;
	protected final int width, height;
	protected final int u, v;
	protected Identifier texture = EmiRenderHelper.BUTTONS;
	private final BooleanSupplier isActive;
	private final Consumer<SizedButtonWidget> action;

	public SizedButtonWidget(int x, int y, int width, int height, int u, int v,
			BooleanSupplier isActive, Consumer<SizedButtonWidget> action) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.u = u;
		this.v = v;
		this.isActive = isActive;
		this.action = action;
	}

	public boolean isActive() {
		return isActive.getAsBoolean();
	}

	public boolean contains(int mouseX, int mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	/** @return whether the click was consumed */
	public boolean mouseClicked(int mouseX, int mouseY, int button) {
		if (button == 0 && isActive() && contains(mouseX, mouseY)) {
			action.accept(this);
			return true;
		}
		return false;
	}

	public void render(EmiDrawContext context, int mouseX, int mouseY, float delta) {
		int v = this.v;
		if (!isActive()) {
			v += height * 2;
		} else if (contains(mouseX, mouseY)) {
			v += height;
		}
		context.drawTexture(texture, x, y, u, v, width, height);
	}
}

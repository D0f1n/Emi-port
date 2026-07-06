package dev.emi.emi.screen.widget;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.runtime.EmiDrawContext;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * EMI's small textured button. On 26.2 the vanilla {@code Button} moved to the two-phase
 * render-state model; since EMI's screens draw and hit-test everything themselves, this is a plain
 * EMI-side widget rather than a vanilla button subclass.
 */
public class SizedButtonWidget {
	public int x, y;
	public boolean visible = true;
	protected final int width, height;
	protected final int u, v;
	protected Identifier texture = EmiRenderHelper.BUTTONS;
	protected Supplier<List<Component>> text;
	private final BooleanSupplier isActive;
	private final Consumer<SizedButtonWidget> action;
	private final IntSupplier vOffset;

	public SizedButtonWidget(int x, int y, int width, int height, int u, int v,
			BooleanSupplier isActive, Consumer<SizedButtonWidget> action) {
		this(x, y, width, height, u, v, isActive, action, () -> 0, null);
	}

	public SizedButtonWidget(int x, int y, int width, int height, int u, int v,
			BooleanSupplier isActive, Consumer<SizedButtonWidget> action, List<Component> text) {
		this(x, y, width, height, u, v, isActive, action, () -> 0, () -> text);
	}

	public SizedButtonWidget(int x, int y, int width, int height, int u, int v,
			BooleanSupplier isActive, Consumer<SizedButtonWidget> action, IntSupplier vOffset) {
		this(x, y, width, height, u, v, isActive, action, vOffset, null);
	}

	public SizedButtonWidget(int x, int y, int width, int height, int u, int v,
			BooleanSupplier isActive, Consumer<SizedButtonWidget> action, IntSupplier vOffset, Supplier<List<Component>> text) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.u = u;
		this.v = v;
		this.isActive = isActive;
		this.action = action;
		this.vOffset = vOffset;
		this.text = text;
	}

	public boolean isActive() {
		return isActive.getAsBoolean();
	}

	public int getWidth() {
		return width;
	}

	public boolean contains(int mouseX, int mouseY) {
		return visible && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	/** @return whether the click was consumed */
	public boolean mouseClicked(int mouseX, int mouseY, int button) {
		if (button == 0 && visible && isActive() && contains(mouseX, mouseY)) {
			action.accept(this);
			return true;
		}
		return false;
	}

	protected int getU(int mouseX, int mouseY) {
		return this.u;
	}

	protected int getV(int mouseX, int mouseY) {
		int v = this.v + vOffset.getAsInt();
		if (!isActive()) {
			v += height * 2;
		} else if (contains(mouseX, mouseY)) {
			v += height;
		}
		return v;
	}

	public void render(EmiDrawContext context, int mouseX, int mouseY, float delta) {
		if (!visible) {
			return;
		}
		context.drawTexture(texture, x, y, getU(mouseX, mouseY), getV(mouseX, mouseY), width, height);
		if (text != null && isActive() && contains(mouseX, mouseY)) {
			EmiRenderHelper.drawTooltip(context,
				text.get().stream().map(EmiPort::ordered).map(ClientTooltipComponent::create).toList(), mouseX, mouseY);
		}
	}
}

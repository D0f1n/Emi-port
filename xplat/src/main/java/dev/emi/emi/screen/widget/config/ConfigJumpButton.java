package dev.emi.emi.screen.widget.config;

import java.util.List;
import java.util.function.Consumer;

import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.EmiPort;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.widget.SizedButtonWidget;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

public class ConfigJumpButton extends SizedButtonWidget {

	public ConfigJumpButton(int x, int y, int u, int v, Consumer<SizedButtonWidget> action, List<Component> text) {
		super(x, y, 16, 16, u, v, () -> true, action, text);
		this.texture = EmiRenderHelper.CONFIG;
	}

	@Override
	protected int getV(int mouseX, int mouseY) {
		return this.v;
	}

	@Override
	public void render(EmiDrawContext context, int mouseX, int mouseY, float delta) {
		// The original tinted via the global shader color; on 26.2 the tint rides the blit itself.
		int color = contains(mouseX, mouseY) ? 0xFF8099FF : 0xFFFFFFFF;
		context.drawTexture(texture, x, y, getU(mouseX, mouseY), getV(mouseX, mouseY), width, height, color);
		if (text != null && contains(mouseX, mouseY)) {
			EmiRenderHelper.drawTooltip(context,
				text.get().stream().map(EmiPort::ordered).map(ClientTooltipComponent::create).toList(), mouseX, mouseY);
		}
	}
}

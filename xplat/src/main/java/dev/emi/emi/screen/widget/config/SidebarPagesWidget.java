package dev.emi.emi.screen.widget.config;

import java.util.List;
import java.util.function.Supplier;

import com.google.common.collect.Lists;

import dev.emi.emi.EmiPort;
import dev.emi.emi.config.SidebarPages;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.screen.ConfigScreen.Mutator;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

public class SidebarPagesWidget extends ConfigEntryWidget {
	private List<Button> buttons = Lists.newArrayList();
	private Mutator<SidebarPages> mutator;

	public SidebarPagesWidget(Component name, List<ClientTooltipComponent> tooltip, Supplier<String> search, Mutator<SidebarPages> mutator) {
		super(name, tooltip, search, 0);
		this.mutator = mutator;
		setChildren(buttons);
		updateButtons();
	}

	public void updateButtons() {
		buttons.clear();
		SidebarPages pages = mutator.get();
		for (int i = 0; i < pages.pages.size(); i++) {
			final int j = i;
			SidebarPages.SidebarPage page = pages.pages.get(i);
			buttons.add(EmiPort.newButton(0, 0, 194, 20, page.type.getText(), b -> {
				EnumWidget.page(page.type, t -> pages.canShowChess() || t != SidebarType.CHESS, t -> {
					pages.pages.get(j).type = (SidebarType) t;
					pages.unique();
				});
			}));
		}
		buttons.add(EmiPort.newButton(0, 0, 20, 20, EmiPort.literal("+"), b -> {
			EnumWidget.page(SidebarType.INDEX, t -> pages.canShowChess() || t != SidebarType.CHESS, t -> {
				pages.pages.add(new SidebarPages.SidebarPage((SidebarType) t));
				pages.unique();
			});
		}));
	}

	@Override
	public void update(int y, int x, int width, int height) {
		if (buttons.size() != mutator.get().pages.size() + 1) {
			updateButtons();
		}
		int h = 0;
		for (int i = 0; i < buttons.size() - 1; i++) {
			Button button = buttons.get(i);
			button.setX(x + width - 218);
			button.setY(y + h);
			h += 24;
		}
		Button button = buttons.get(buttons.size() - 1);
		button.setX(x + width - 20);
		button.setY(y);
	}

	@Override
	public int getHeight() {
		if (!isVisible() || !isParentVisible()) {
			return 0;
		}
		if (buttons.size() == 1) {
			return 20;
		}
		return buttons.size() * 24 - 28;
	}
}

package dev.emi.emi.screen;

import java.util.List;
import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.widget.config.EmiNameWidget;
import dev.emi.emi.screen.widget.config.ListWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class ConfigEnumScreen<T> extends Screen {
	private final ConfigScreen last;
	private final List<Entry<T>> entries;
	private final Consumer<T> selection;
	private ListWidget list;

	public ConfigEnumScreen(ConfigScreen last, List<Entry<T>> entries, Consumer<T> selection) {
		super(EmiPort.translatable("screen.emi.config"));
		this.last = last;
		this.entries = entries;
		this.selection = selection;
	}

	@Override
	public void init() {
		super.init();
		this.addRenderableOnly(new EmiNameWidget(width / 2, 16));
		int w = 200;
		int x = (width - w) / 2;
		this.addRenderableWidget(EmiPort.newButton(x, height - 30, w, 20, EmiPort.translatable("gui.done"), button -> {
			onClose();
		}));
		list = new ListWidget(minecraft, width, height, 40, height - 40);
		for (Entry<T> e : entries) {
			list.addEntry(new SelectionWidget<T>(this, e));
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor raw, int mouseX, int mouseY, float delta) {
		EmiDrawContext context = EmiDrawContext.wrap(raw);
		list.setScrollAmount(list.getScrollAmount());
		super.extractRenderState(raw, mouseX, mouseY, delta);
		list.render(raw, mouseX, mouseY, delta);
		ListWidget.Entry entry = list.getHoveredEntry();
		if (entry instanceof SelectionWidget<?> widget) {
			if (widget.button.isMouseOver(mouseX, mouseY)) {
				EmiRenderHelper.drawTooltip(context, widget.tooltip, mouseX, mouseY);
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		return list.mouseClicked(event.x(), event.y(), event.button());
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		list.mouseReleased(event.x(), event.y(), event.button());
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (super.mouseDragged(event, deltaX, deltaY)) {
			return true;
		}
		return list.mouseDragged(event.x(), event.y(), event.button(), deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double amount) {
		if (list.isMouseOver(mouseX, mouseY)) {
			return list.mouseScrolled(mouseX, mouseY, amount);
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, amount);
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(last);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			this.onClose();
			return true;
		} else if (minecraft.options.keyInventory.matches(event)) {
			this.onClose();
			return true;
		} else if (event.key() == GLFW.GLFW_KEY_TAB) {
			return false;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	public static record Entry<T>(T value, Component name, List<ClientTooltipComponent> tooltip) {
	}

	public static class SelectionWidget<T> extends ListWidget.Entry {
		private final Button button;
		private final List<ClientTooltipComponent> tooltip;

		public SelectionWidget(ConfigEnumScreen<T> screen, Entry<T> e) {
			button = EmiPort.newButton(0, 0, 200, 20, e.name(), t -> {
				screen.selection.accept(e.value());
				screen.onClose();
			});
			tooltip = e.tooltip();
		}

		@Override
		public List<?> children() {
			return List.of(button);
		}

		@Override
		public void render(GuiGraphicsExtractor raw, int index, int y, int x, int width, int height, int mouseX, int mouseY,
				boolean hovered, float delta) {
			button.setY(y);
			button.setX(x + width / 2 - button.getWidth() / 2);
			button.extractRenderState(raw, mouseX, mouseY, delta);
		}

		@Override
		public int getHeight() {
			return 20;
		}
	}
}

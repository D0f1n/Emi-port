package dev.emi.emi.screen;

import java.lang.reflect.Field;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.com.unascribed.qdcss.QDCSS;
import dev.emi.emi.config.ConfigPresets;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.EmiConfig.ConfigGroup;
import dev.emi.emi.config.EmiConfig.ConfigValue;
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

public class ConfigPresetScreen extends Screen {
	private final ConfigScreen last;
	private ListWidget list;
	public Button resetButton;

	public ConfigPresetScreen(ConfigScreen last) {
		super(EmiPort.translatable("screen.emi.presets"));
		this.last = last;
	}

	@Override
	public void init() {
		super.init();
		this.addRenderableOnly(new EmiNameWidget(width / 2, 16));
		int w = Math.min(400, width - 40);
		int x = (width - w) / 2;
		this.resetButton = EmiPort.newButton(x + 2, height - 30, w / 2 - 2, 20, EmiPort.translatable("gui.done"), button -> {
			EmiConfig.loadConfig(QDCSS.load("revert", last.originalConfig));
			this.init(this.width, this.height);
		});
		this.addRenderableWidget(resetButton);
		this.addRenderableWidget(EmiPort.newButton(x + w / 2 + 2, height - 30, w / 2 - 2, 20, EmiPort.translatable("gui.done"), button -> {
			this.onClose();
		}));
		list = new ListWidget(minecraft, width, height, 40, height - 40);
		try {
			for (Field field : ConfigPresets.class.getFields()) {
				ConfigValue config = field.getDeclaredAnnotation(ConfigValue.class);
				if (config != null) {
					if (field.get(null) instanceof Runnable runnable) {
						ConfigGroup group = field.getDeclaredAnnotation(ConfigGroup.class);
						if (group != null) {
							Component translation = EmiPort.translatable("config.emi." + group.value().replace('-', '_'));
							list.addEntry(new PresetGroupWidget(translation));
						}
						Component translation = EmiPort.translatable("config.emi." + config.value().replace('-', '_'));
						list.addEntry(new PresetWidget(runnable, translation, ConfigScreen.getFieldTooltip(field)));
					}
				}
			}
		} catch (Exception e) {
		}
		updateChanges();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor raw, int mouseX, int mouseY, float delta) {
		EmiDrawContext context = EmiDrawContext.wrap(raw);
		list.setScrollAmount(list.getScrollAmount());
		super.extractRenderState(raw, mouseX, mouseY, delta);
		list.render(raw, mouseX, mouseY, delta);
		if (list.getHoveredEntry() instanceof PresetWidget widget) {
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

	public void updateChanges() {
		// Split on the blank lines between config options
		String[] oLines = last.originalConfig.split("\n\n");
		String[] cLines = EmiConfig.getSavedConfig().split("\n\n");
		int different = 0;
		for (int i = 0; i < oLines.length; i++) {
			if (i >= cLines.length) {
				break;
			}
			if (!oLines[i].equals(cLines[i])) {
				different++;
			}
		}
		this.resetButton.active = different > 0;
		this.resetButton.setMessage(EmiPort.translatable("screen.emi.config.reset", different));
	}

	public class PresetWidget extends ListWidget.Entry {
		private final Button button;
		private final List<ClientTooltipComponent> tooltip;

		public PresetWidget(Runnable runnable, Component name, List<ClientTooltipComponent> tooltip) {
			button = EmiPort.newButton(0, 0, 200, 20, name, t -> {
				runnable.run();
				updateChanges();
			});
			this.tooltip = tooltip;
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

	public class PresetGroupWidget extends ListWidget.Entry {
		private final Component text;

		public PresetGroupWidget(Component text) {
			this.text = text;
		}

		@Override
		public void render(GuiGraphicsExtractor raw, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
			EmiDrawContext context = EmiDrawContext.wrap(raw);
			context.drawCenteredTextWithShadow(text, x + width / 2, y + 3, -1);
		}

		@Override
		public int getHeight() {
			return 20;
		}
	}
}

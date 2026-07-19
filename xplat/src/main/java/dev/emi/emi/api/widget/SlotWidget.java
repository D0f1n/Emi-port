package dev.emi.emi.api.widget;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.collect.Lists;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.render.EmiRender;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.input.EmiBind;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class SlotWidget extends Widget {
	protected final EmiIngredient stack;
	protected final int x, y;
	protected Identifier textureId;
	protected int u, v;
	protected int customWidth, customHeight;
	protected boolean drawBack = true, output = false, catalyst = false, custom = false;
	protected List<Supplier<ClientTooltipComponent>> tooltipSuppliers = Lists.newArrayList();
	private EmiRecipe recipe;

	public SlotWidget(EmiIngredient stack, int x, int y) {
		this.stack = stack;
		this.x = x;
		this.y = y;
	}

	public EmiIngredient getStack() {
		return stack;
	}

	/**
	 * @return The recipe associated with a slot.
	 *	Logical output slots will return the recipe they represent.
	 *  Otherwise, the result will be null.
	 */
	public EmiRecipe getRecipe() {
		return recipe;
	}

	/**
	 * Whether to draw the background texture of a slot.
	 */
	public SlotWidget drawBack(boolean drawBack) {
		this.drawBack = drawBack;
		return this;
	}

	/**
	 * Whether to draw the slot as the large 26x26 or small 18x18 slot.
	 * This is a purely visual change.
	 */
	public SlotWidget large(boolean large) {
		this.output = large;
		return this;
	}

	/**
	 * Whether to draw a catalyst icon on the slot.
	 */
	public SlotWidget catalyst(boolean catalyst) {
		this.catalyst = catalyst;
		return this;
	}

	/**
	 * Provides a function for appending {@link ClientTooltipComponent}s to the slot's tooltip.
	 */
	public SlotWidget appendTooltip(Function<EmiIngredient, ClientTooltipComponent> function) {
		return appendTooltip(() -> function.apply(getStack()));
	}

	/**
	 * Provides a supplier for appending {@link ClientTooltipComponent}s to the slot's tooltip.
	 */
	public SlotWidget appendTooltip(Supplier<ClientTooltipComponent> supplier) {
		tooltipSuppliers.add(supplier);
		return this;
	}

	/**
	 * Provides a shorthand for appending text to the slot's tooltip.
	 */
	public SlotWidget appendTooltip(Component text) {
		tooltipSuppliers.add(() -> ClientTooltipComponent.create(EmiPort.ordered(text)));
		return this;
	}

	/**
	 * Provides EMI context that the slot contains the provided recipe's output.
	 * This is used for resolving recipes, displaying extra information in tooltips, and more.
	 */
	public SlotWidget recipeContext(EmiRecipe recipe) {
		this.recipe = recipe;
		return this;
	}

	/**
	 * Sets the slot to use a custom texture.
	 * The size of the texture drawn is 18x18, or 26x26 if the slot is large,
	 * which is set by {@link SlotWidget#large(boolean)}.
	 */
	public SlotWidget backgroundTexture(Identifier id, int u, int v) {
		this.textureId = id;
		this.u = u;
		this.v = v;
		return this;
	}

	/**
	 * Sets the slot to use a custom texture and custom sizing
	 * @param id The texture identifier to use to draw the background
	 */
	public SlotWidget customBackground(Identifier id, int u, int v, int width, int height) {
		backgroundTexture(id, u, v);
		this.custom = true;
		this.customWidth = width;
		this.customHeight = height;
		return this;
	}

	@Override
	public Bounds getBounds() {
		if (custom) {
			return new Bounds(x, y, customWidth, customHeight);
		} else if (output) {
			return new Bounds(x, y, 26, 26);
		} else {
			return new Bounds(x, y, 18, 18);
		}
	}

	@Override
	public void render(GuiGraphicsExtractor draw, int mouseX, int mouseY, float delta) {
		drawBackground(draw, mouseX, mouseY, delta);
		drawStack(draw, mouseX, mouseY, delta);
		drawOverlay(draw, mouseX, mouseY, delta);
	}

	public void drawBackground(GuiGraphicsExtractor draw, int mouseX, int mouseY, float delta) {
		EmiDrawContext context = EmiDrawContext.wrap(draw);
		Bounds bounds = getBounds();
		int width = bounds.width();
		int height = bounds.height();
		if (drawBack) {
			if (textureId != null) {
				context.drawTexture(textureId, bounds.x(), bounds.y(), width, height, u, v, width, height, 256, 256);
			} else {
				int v = getStack().getChance() != 1 ? bounds.height() : 0;
				if (output) {
					context.drawTexture(EmiRenderHelper.WIDGETS, bounds.x(), bounds.y(), 26, 26, 18, v, 26, 26, 256, 256);
				} else {
					context.drawTexture(EmiRenderHelper.WIDGETS, bounds.x(), bounds.y(), 18, 18, 0, v, 18, 18, 256, 256);
				}
			}
		}
	}

	public void drawStack(GuiGraphicsExtractor draw, int mouseX, int mouseY, float delta) {
		Bounds bounds = getBounds();
		int xOff = (bounds.width() - 16) / 2;
		int yOff = (bounds.height() - 16) / 2;
		getStack().render(draw, bounds.x() + xOff, bounds.y() + yOff, delta);
	}

	public void drawOverlay(GuiGraphicsExtractor draw, int mouseX, int mouseY, float delta) {
		EmiDrawContext context = EmiDrawContext.wrap(draw);
		Bounds bounds = getBounds();
		int xOff = (bounds.width() - 16) / 2;
		int yOff = (bounds.height() - 16) / 2;
		if (catalyst) {
			EmiRender.renderCatalystIcon(getStack(), context.raw(), x + xOff, y + yOff);
		}
		if (getBounds().contains(mouseX, mouseY)) {
			drawSlotHighlight(context, bounds);
		}
	}

	public void drawSlotHighlight(EmiDrawContext context, Bounds bounds) {
		context.fill(bounds.x() + 1, bounds.y() + 1, bounds.width() - 2, bounds.height() - 2, 0x80ffffff);
	}

	@Override
	public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
		List<ClientTooltipComponent> list = Lists.newArrayList();
		if (getStack().isEmpty()) {
			return list;
		}
		list.addAll(getStack().getTooltip());
		addSlotTooltip(list);
		return list;
	}

	protected void addSlotTooltip(List<ClientTooltipComponent> list) {
		for (Supplier<ClientTooltipComponent> supplier : tooltipSuppliers) {
			list.add(supplier.get());
		}
		if (getStack().getChance() != 1) {
			String key = recipe != null ? "tooltip.emi.chance.produce" : "tooltip.emi.chance.consume";
			list.add(ClientTooltipComponent.create(EmiPort.ordered(EmiPort.translatable(key,
				(int) (getStack().getChance() * 100)).withStyle(ChatFormatting.GOLD))));
		}
	}

	@Override
	public boolean mouseClicked(int mouseX, int mouseY, int button) {
		if (slotInteraction(bind -> bind.matchesMouse(button))) {
			return true;
		}
		return EmiScreenManager.stackInteraction(new EmiStackInteraction(getStack(), getRecipe(), true),
			bind -> bind.matchesMouse(button));
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (slotInteraction(bind -> bind.matchesKey(keyCode, scanCode))) {
			return true;
		}
		return EmiScreenManager.stackInteraction(new EmiStackInteraction(getStack(), getRecipe(), true),
			bind -> bind.matchesKey(keyCode, scanCode));
	}

	private boolean slotInteraction(Function<EmiBind, Boolean> function) {
		EmiRecipe recipe = getRecipe();
		// TODO(bom): the resolution round adds the RecipeScreen.resolve branches here, assigning
		// defaults and tree resolutions for the ingredient being resolved.
		if (recipe != null && recipe.supportsRecipeTree()) {
			if (function.apply(EmiConfig.defaultStack)) {
				if (BoM.isDefaultRecipe(getStack(), recipe)) {
					BoM.removeRecipe(getStack(), recipe);
				} else {
					BoM.addRecipe(getStack(), recipe);
				}
				return true;
			}
		}
		return false;
	}
}

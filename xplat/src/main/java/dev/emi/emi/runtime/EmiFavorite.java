package dev.emi.emi.runtime;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Lists;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.screen.StackBatcher.Batchable;
import dev.emi.emi.screen.tooltip.RecipeTooltipComponent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

public class EmiFavorite implements EmiIngredient, Batchable {
	protected final EmiIngredient stack;
	protected final @Nullable EmiRecipe recipe;

	public EmiFavorite(EmiIngredient stack, @Nullable EmiRecipe recipe) {
		this.stack = stack;
		this.recipe = recipe;
	}

	public EmiIngredient getStack() {
		return stack;
	}

	@Override
	public EmiIngredient copy() {
		return new EmiFavorite(stack, recipe);
	}

	@Override
	public long getAmount() {
		return stack.getAmount();
	}

	@Override
	public EmiIngredient setAmount(long amount) {
		return this;
	}

	@Override
	public float getChance() {
		return 1;
	}

	@Override
	public EmiIngredient setChance(float chance) {
		return this;
	}

	public EmiRecipe getRecipe() {
		return recipe;
	}

	@Override
	public List<EmiStack> getEmiStacks() {
		return stack.getEmiStacks();
	}

	@Override
	public void render(GuiGraphicsExtractor raw, int x, int y, float delta, int flags) {
		EmiDrawContext context = EmiDrawContext.wrap(raw);
		if (recipe != null) {
			flags |= EmiIngredient.RENDER_AMOUNT;
		}
		stack.render(context.raw(), x, y, delta, flags);
		if ((flags & EmiIngredient.RENDER_INGREDIENT) > 0 && recipe != null) {
			EmiRenderHelper.renderRecipeFavorite(stack, context, x, y);
		}
	}

	@Override
	public List<ClientTooltipComponent> getTooltip() {
		List<ClientTooltipComponent> list = Lists.newArrayList();
		list.addAll(stack.getTooltip());
		if (recipe != null) {
			list.add(new RecipeTooltipComponent(recipe, true));
		}
		return list;
	}

	public boolean strictEquals(EmiIngredient other) {
		List<EmiStack> as = this.getEmiStacks();
		List<EmiStack> bs = other.getEmiStacks();
		if (as.size() != bs.size()) {
			return false;
		}
		for (int i = 0; i < as.size(); i++) {
			if (!as.get(i).isEqual(bs.get(i), EmiPort.compareStrict())) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof EmiIngredient ingredient && EmiIngredient.areEqual(this, ingredient);
	}

	public static class Craftable extends EmiFavorite {

		public Craftable(EmiRecipe recipe) {
			super(recipe.getOutputs().isEmpty() ? EmiStack.EMPTY : recipe.getOutputs().get(0), recipe);
		}

		@Override
		public void render(GuiGraphicsExtractor raw, int x, int y, float delta, int flags) {
			super.render(raw, x, y, delta, flags & (~EmiIngredient.RENDER_INGREDIENT));
		}
	}

	// The original's Synthetic subclass (BoM crafting-mode cost entries) is not ported. TODO(bom)
}

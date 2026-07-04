package dev.emi.emi.recipe;

import java.util.List;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.resources.Identifier;

/**
 * On 26.2 stonecutting recipes are read from {@code StonecutterRecipeDisplay} (input/result), so
 * this takes the resolved display data directly.
 */
public class EmiStonecuttingRecipe implements EmiRecipe {
	private final Identifier id;
	private final EmiIngredient input;
	private final EmiStack output;

	public EmiStonecuttingRecipe(EmiIngredient input, EmiStack output, Identifier id) {
		this.id = id;
		this.input = input;
		this.output = output;
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return VanillaEmiRecipeCategories.STONECUTTING;
	}

	@Override
	public Identifier getId() {
		return id;
	}

	@Override
	public List<EmiIngredient> getInputs() {
		return List.of(input);
	}

	@Override
	public List<EmiStack> getOutputs() {
		return List.of(output);
	}

	@Override
	public int getDisplayWidth() {
		return 76;
	}

	@Override
	public int getDisplayHeight() {
		return 18;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		widgets.addTexture(EmiTexture.EMPTY_ARROW, 26, 1);
		widgets.addSlot(input, 0, 0);
		widgets.addSlot(output, 58, 0).recipeContext(this);
	}
}

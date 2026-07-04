package dev.emi.emi.recipe;

import java.util.List;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.resources.Identifier;

public class EmiSmithingRecipe implements EmiRecipe {
	protected final Identifier id;
	protected final EmiIngredient template;
	protected final EmiIngredient input;
	protected final EmiIngredient addition;
	protected final EmiStack output;

	public EmiSmithingRecipe(EmiIngredient template, EmiIngredient input, EmiIngredient addition, EmiStack output, Identifier id) {
		this.id = id;
		this.template = template;
		this.input = input;
		this.addition = addition;
		this.output = output;
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return VanillaEmiRecipeCategories.SMITHING;
	}

	@Override
	public Identifier getId() {
		return id;
	}

	@Override
	public List<EmiIngredient> getInputs() {
		return List.of(template, input, addition);
	}

	@Override
	public List<EmiStack> getOutputs() {
		return List.of(output);
	}

	@Override
	public int getDisplayWidth() {
		return 112;
	}

	@Override
	public int getDisplayHeight() {
		return 18;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		// Display widgets land with the vanilla-categories checkpoint.
	}
}

package dev.emi.emi.recipe;

import java.util.List;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/**
 * On 26.2 cooking recipes are read from {@code FurnaceRecipeDisplay} (ingredient/result/duration/
 * experience) rather than the removed client-side {@code AbstractCookingRecipe} view, so this takes
 * the resolved display data directly.
 */
public class EmiCookingRecipe implements EmiRecipe {
	private final Identifier id;
	private final EmiRecipeCategory category;
	private final EmiIngredient input;
	private final EmiStack output;
	private final int cookingTime;
	private final float experience;
	private final int fuelMultiplier;
	private final boolean infiniBurn;

	public EmiCookingRecipe(EmiIngredient input, EmiStack output, int cookingTime, float experience,
			EmiRecipeCategory category, int fuelMultiplier, boolean infiniBurn, Identifier id) {
		this.id = id;
		this.category = category;
		this.input = input;
		this.output = output;
		if (!input.getEmiStacks().isEmpty() && input.getEmiStacks().get(0).getItemStack().is(Items.WET_SPONGE)) {
			input.getEmiStacks().get(0).setRemainder(EmiStack.of(Fluids.WATER));
		}
		this.cookingTime = cookingTime;
		this.experience = experience;
		this.fuelMultiplier = fuelMultiplier;
		this.infiniBurn = infiniBurn;
	}

	public int getCookingTime() {
		return cookingTime;
	}

	public float getExperience() {
		return experience;
	}

	public int getFuelMultiplier() {
		return fuelMultiplier;
	}

	public boolean isInfiniBurn() {
		return infiniBurn;
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return category;
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
		return 82;
	}

	@Override
	public int getDisplayHeight() {
		return 38;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		// Display widgets land with the vanilla-categories checkpoint.
	}
}

package dev.emi.emi.registry;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.google.common.collect.Lists;

import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

public class EmiRegistryImpl implements EmiRegistry {

	@Override
	public void addCategory(EmiRecipeCategory category) {
		EmiRecipes.addCategory(category);
	}

	@Override
	public void addWorkstation(EmiRecipeCategory category, EmiIngredient workstation) {
		EmiRecipes.addWorkstation(category, workstation);
	}

	@Override
	public void addRecipe(EmiRecipe recipe) {
		EmiRecipes.addRecipe(recipe);
	}

	@Override
	public void removeRecipes(Predicate<EmiRecipe> predicate) {
		EmiRecipes.invalidators.add(predicate);
	}

	@Override
	public void addDeferredRecipes(Consumer<Consumer<EmiRecipe>> consumer) {
		EmiRecipes.lateRecipes.add(consumer);
	}

	@Override
	public <T extends AbstractContainerMenu> void addRecipeHandler(MenuType<T> type, EmiRecipeHandler<T> handler) {
		EmiRecipeFiller.handlers.computeIfAbsent(type, k -> Lists.newArrayList()).add(handler);
	}

	@Override
	public void setDefaultComparison(Object key, Function<Comparison, Comparison> comparison) {
		EmiComparisonDefaults.comparisons.put(key, comparison.apply(EmiComparisonDefaults.get(key)));
	}
}

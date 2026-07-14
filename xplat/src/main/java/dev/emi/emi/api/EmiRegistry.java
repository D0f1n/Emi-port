package dev.emi.emi.api;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * The registration surface handed to {@link EmiPlugin}s.
 *
 * <p>Port note, recipe round scope: the recipe-side registration methods and default comparisons.
 * The original's stack sidebar mutators, exclusion areas, drag-drop, stack providers and recipe
 * handlers return with later rounds. The original's {@code getRecipeManager()} is gone on 26.2 — client-side
 * recipe enumeration was removed in 1.21.2; the built-in plugin reads the harvested server displays
 * instead.
 */
public interface EmiRegistry {

	/**
	 * Adds a recipe category.
	 * Recipes are organized based on recipe category.
	 */
	void addCategory(EmiRecipeCategory category);

	/**
	 * Adds a workstation to a recipe category.
	 */
	void addWorkstation(EmiRecipeCategory category, EmiIngredient workstation);

	/**
	 * Adds a recipe to EMI that can be viewed and associated with its components.
	 */
	void addRecipe(EmiRecipe recipe);

	/**
	 * Adds a predicate to run on all current and future recipes to prevent certain ones from being added.
	 */
	void removeRecipes(Predicate<EmiRecipe> predicate);

	/**
	 * Adds a predicate to run on all current and future recipes to prevent certain ones with the given identifier from being added.
	 */
	default void removeRecipes(Identifier id) {
		removeRecipes(r -> id.equals(r.getId()));
	}

	/**
	 * Add recipes that are reliant on a majority of EMI metadata is populated.
	 * The passed consumer will be run after all EMI plugins have executed.
	 */
	void addDeferredRecipes(Consumer<Consumer<EmiRecipe>> consumer);

	/**
	 * Adds a recipe handler for a given menu type, to craft recipes in that menu.
	 * A null type is used for the player's own inventory menu.
	 */
	<T extends AbstractContainerMenu> void addRecipeHandler(MenuType<T> type, EmiRecipeHandler<T> handler);

	/**
	 * Adds an EmiStackProvider to screens of a given class.
	 * Stack providers can inform EMI of EmiIngredients that are located on the screen.
	 */
	<T extends Screen> void addStackProvider(Class<T> clazz, EmiStackProvider<T> provider);

	/**
	 * Adds an EmiStackProvider to every screen.
	 * Stack providers can inform EMI of EmiIngredients that are located on the screen.
	 */
	void addGenericStackProvider(EmiStackProvider<Screen> provider);

	/**
	 * Adds a default compraison method for a stack key.
	 * @param key A stack key such as an item or fluid.
	 * @param comparison A function to mutate the current comprison method.
	 */
	void setDefaultComparison(Object key, Function<Comparison, Comparison> comparison);

	/**
	 * Adds a default compraison method for a stack using its key.
	 * @param key A stack key such as an item or fluid.
	 * @param comparison The desired comparison method.
	 */
	default void setDefaultComparison(Object key, Comparison comparison) {
		setDefaultComparison(key, old -> comparison);
	}

	/**
	 * Adds a default compraison method for a stack using its key.
	 * @param stack A stack to derive a key from.
	 * @param comparison A function to mutate the current comprison method.
	 */
	default void setDefaultComparison(EmiStack stack, Function<Comparison, Comparison> comparison) {
		setDefaultComparison(stack.getKey(), comparison);
	}

	/**
	 * Adds a default compraison method for a stack using its key.
	 * @param stack A stack to derive a key from.
	 * @param comparison The desired comparison method.
	 */
	default void setDefaultComparison(EmiStack stack, Comparison comparison) {
		setDefaultComparison(stack.getKey(), old -> comparison);
	}
}

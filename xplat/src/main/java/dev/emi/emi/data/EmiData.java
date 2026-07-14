package dev.emi.emi.data;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import dev.emi.emi.api.recipe.EmiRecipe;

/**
 * Static registry for datapack-driven EMI data, loaded from {@code assets/emi/**} by the
 * reload listeners constructed in {@link #init}.
 *
 * <p>Unlike the original, the fields are volatile: the resource reload publishes them from its
 * apply thread while the EMI reload worker reads them, so publication must be safe. Values are
 * always replaced wholesale with effectively-immutable contents.
 */
public class EmiData {
	public static volatile List<Predicate<EmiRecipe>> recipeFilters = List.of();
	public static volatile List<Supplier<EmiRecipe>> recipes = List.of();

	public static void init(Consumer<EmiResourceReloadListener> register) {
	}
}

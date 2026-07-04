package dev.emi.emi.api;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiRecipes;
import dev.emi.emi.registry.EmiStackList;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.screen.EmiScreenManager;

/**
 * The public static facade of EMI.
 *
 * <p>Port note, recipe round scope: recipe lookup and navigation. Favorites, cheat mode, the
 * recipe tree (BoM) and hovered-stack queries return with later rounds.
 */
public class EmiApi {

	public static List<EmiStack> getIndexStacks() {
		return EmiStackList.stacks;
	}

	public static EmiRecipeManager getRecipeManager() {
		return EmiRecipes.manager;
	}

	/**
	 * @return Current search text
	 */
	public static String getSearchText() {
		return EmiScreenManager.search != null ? EmiScreenManager.search.getValue() : "";
	}

	/**
	 * Sets the current search to the provided query
	 */
	public static void setSearchText(String text) {
		if (EmiScreenManager.search != null) {
			EmiScreenManager.search.setValue(text);
		}
	}

	public static boolean isSearchFocused() {
		return EmiScreenManager.isSearchFocused();
	}

	public static void displayAllRecipes() {
		EmiRecipeManager manager = EmiApi.getRecipeManager();
		setPages(manager.getCategories().stream().collect(Collectors.toMap(c -> c, c -> manager.getRecipes(c))), EmiStack.EMPTY);
	}

	public static void displayRecipeCategory(EmiRecipeCategory category) {
		setPages(Map.of(category, getRecipeManager().getRecipes(category)), EmiStack.EMPTY);
	}

	public static void displayRecipe(EmiRecipe recipe) {
		setPages(Map.of(recipe.getCategory(), List.of(recipe)), EmiStack.EMPTY);
	}

	public static void displayRecipes(EmiIngredient stack) {
		// The original also resolves tag and list ingredients to synthetic recipes here; those
		// return with the tag/ingredient categories in the polish round.
		if (stack.getEmiStacks().size() == 1) {
			EmiStack es = stack.getEmiStacks().get(0);
			setPages(mapRecipes(pruneSources(EmiApi.getRecipeManager().getRecipesByOutput(es), es)), stack);
		}
	}

	public static void displayUses(EmiIngredient stack) {
		if (!stack.isEmpty()) {
			EmiStack zero = stack.getEmiStacks().get(0);
			Map<EmiRecipeCategory, List<EmiRecipe>> map
				= mapRecipes(Stream.concat(
						pruneUses(getRecipeManager().getRecipesByInput(zero), stack).stream(),
						EmiRecipes.byWorkstation.getOrDefault(zero, List.of()).stream()).distinct().toList());
			setPages(map, stack);
		}
	}

	public static void focusRecipe(EmiRecipe recipe) {
		// Wired to the recipe screen with the screen checkpoint.
	}

	private static List<EmiRecipe> pruneSources(List<EmiRecipe> list, EmiStack context) {
		return list.stream().filter(r -> {
			return r.getOutputs().stream().anyMatch(i -> i.isEqual(context));
		}).toList();
	}

	private static List<EmiRecipe> pruneUses(List<EmiRecipe> list, EmiIngredient context) {
		return list.stream().filter(r -> {
			return r.getInputs().stream().anyMatch(i -> containsAll(i, context))
				|| r.getCatalysts().stream().anyMatch(i -> containsAll(i, context));
		}).sorted((a, b) -> getSmallestPresence(a, context) - getSmallestPresence(b, context)).toList();
	}

	private static int getSmallestPresence(EmiRecipe recipe, EmiIngredient context) {
		int ideal = context.getEmiStacks().size();
		int smallestPresence = Integer.MAX_VALUE;
		for (EmiIngredient i : recipe.getInputs()) {
			if (containsAll(i, context)) {
				smallestPresence = Math.min(smallestPresence, i.getEmiStacks().size());
				if (smallestPresence <= ideal) {
					break;
				}
			}
		}
		return smallestPresence;
	}

	private static Map<EmiRecipeCategory, List<EmiRecipe>> mapRecipes(List<EmiRecipe> list) {
		Map<EmiRecipeCategory, List<EmiRecipe>> map = Maps.newHashMap();
		for (EmiRecipe recipe : list) {
			map.computeIfAbsent(recipe.getCategory(), k -> Lists.newArrayList()).add(recipe);
		}
		return map;
	}

	private static boolean containsAll(EmiIngredient collection, EmiIngredient ingredient) {
		outer:
		for (EmiStack ing : ingredient.getEmiStacks()) {
			for (EmiStack col : collection.getEmiStacks()) {
				if (col.isEqual(ing)) {
					continue outer;
				}
			}
			return false;
		}
		return true;
	}

	private static void setPages(Map<EmiRecipeCategory, List<EmiRecipe>> recipes, EmiIngredient stack) {
		recipes = recipes.entrySet().stream().filter(e -> !e.getValue().isEmpty())
			.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
		if (!recipes.isEmpty()) {
			// The screen checkpoint replaces this log with opening the recipe screen.
			int total = recipes.values().stream().mapToInt(List::size).sum();
			EmiLog.info("Found " + total + " recipes in " + recipes.size() + " categories for " + stack);
		}
	}
}

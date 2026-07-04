package dev.emi.emi.registry;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.recipe.EmiRecipeSorting;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiLog;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import net.minecraft.resources.Identifier;

/**
 * The recipe registry: plugin-registered categories, workstations and recipes, plus the baked
 * multi-index lookup manager (by input / output / category / id).
 *
 * <p>Trimmed against the original for the recipe round: the data-driven layer (EmiData recipe
 * additions/filters, category property overrides), stack hiding and recipe decorators return with
 * later rounds.
 */
public class EmiRecipes {
	public static volatile Worker activeWorker = null;
	public static EmiRecipeManager manager = Manager.EMPTY;
	public static List<Consumer<Consumer<EmiRecipe>>> lateRecipes = Lists.newArrayList();
	public static List<Predicate<EmiRecipe>> invalidators = Lists.newArrayList();

	public static List<EmiRecipeCategory> categories = Lists.newArrayList();
	private static Map<EmiRecipeCategory, List<EmiIngredient>> workstations = Maps.newHashMap();
	private static List<EmiRecipe> recipes = Lists.newArrayList();

	public static Map<EmiStack, List<EmiRecipe>> byWorkstation = Maps.newHashMap();

	public static void clear() {
		setWorker(null);
		lateRecipes.clear();
		invalidators.clear();
		categories.clear();
		workstations.clear();
		recipes.clear();
		byWorkstation.clear();
		manager = Manager.EMPTY;
	}

	public static void bake() {
		long start = System.currentTimeMillis();
		List<EmiRecipe> filtered = recipes.stream().filter(r -> {
			for (Predicate<EmiRecipe> predicate : invalidators) {
				if (predicate.test(r)) {
					return false;
				}
			}
			return true;
		}).toList();
		Map<EmiRecipeCategory, List<EmiIngredient>> filteredWorkstations = Maps.newHashMap();
		for (Map.Entry<EmiRecipeCategory, List<EmiIngredient>> entry : workstations.entrySet()) {
			if (!entry.getValue().isEmpty()) {
				filteredWorkstations.put(entry.getKey(), Lists.newArrayList(entry.getValue()));
			}
		}
		manager = new Manager(categories, filteredWorkstations, filtered, false);
		setWorker(new Worker(categories, filteredWorkstations, filtered));
		EmiLog.info("Baked " + recipes.size() + " recipes in " + (System.currentTimeMillis() - start) + "ms");
	}

	public static void addCategory(EmiRecipeCategory category) {
		categories.add(category);
	}

	public static void addWorkstation(EmiRecipeCategory category, EmiIngredient workstation) {
		workstations.computeIfAbsent(category, k -> Lists.newArrayList()).add(workstation);
	}

	public static void addRecipe(EmiRecipe recipe) {
		recipes.add(recipe);
	}

	private static synchronized void setWorker(Worker worker) {
		activeWorker = worker;
		if (worker != null) {
			new Thread(activeWorker).start();
		}
	}

	private static class Manager implements EmiRecipeManager {
		public static final EmiRecipeManager EMPTY = new Manager();
		private final List<EmiRecipeCategory> categories;
		private final Map<EmiRecipeCategory, List<EmiIngredient>> workstations;
		private final List<EmiRecipe> recipes;
		private Map<EmiStack, List<EmiRecipe>> byInput = new Object2ObjectOpenCustomHashMap<>(new EmiStackList.ComparisonHashStrategy());
		private Map<EmiStack, List<EmiRecipe>> byOutput = new Object2ObjectOpenCustomHashMap<>(new EmiStackList.ComparisonHashStrategy());
		private Map<EmiRecipeCategory, List<EmiRecipe>> byCategory = Maps.newHashMap();
		private Map<Identifier, EmiRecipe> byId = Maps.newHashMap();

		private Manager() {
			this.categories = List.of();
			this.workstations = Map.of();
			this.recipes = List.of();
		}

		public Manager(List<EmiRecipeCategory> categories, Map<EmiRecipeCategory, List<EmiIngredient>> workstations, List<EmiRecipe> recipes, boolean doSort) {
			this.categories = categories.stream().distinct().toList();
			this.workstations = workstations;
			this.recipes = List.copyOf(recipes);

			Object2IntMap<Identifier> duplicateIds = new Object2IntOpenHashMap<>();
			for (EmiRecipe recipe : recipes) {
				Identifier id = recipe.getId();
				EmiRecipeCategory category = recipe.getCategory();
				if (!categories.contains(category)) {
					EmiLog.warn("Recipe " + id + " loaded with unregistered category: " + category.getId());
				}
				byCategory.computeIfAbsent(category, a -> Lists.newArrayList()).add(recipe);
				if (id != null) {
					if (byId.containsKey(id)) {
						duplicateIds.put(id, duplicateIds.getOrDefault(id, 1) + 1);
					} else {
						byId.put(id, recipe);
					}
				}
			}

			Map<EmiStack, Set<EmiRecipe>> byInput = new Object2ObjectOpenCustomHashMap<>(new EmiStackList.ComparisonHashStrategy());
			Map<EmiStack, Set<EmiRecipe>> byOutput = new Object2ObjectOpenCustomHashMap<>(new EmiStackList.ComparisonHashStrategy());

			for (EmiRecipeCategory category : byCategory.keySet()) {
				List<EmiRecipe> cRecipes = byCategory.get(category);
				Comparator<EmiRecipe> sort = category.getSort();
				if (doSort && sort != null && sort != EmiRecipeSorting.none()) {
					cRecipes = cRecipes.stream().sorted(sort).collect(Collectors.toList());
					EmiRecipeSorter.clear();
				}
				byCategory.put(category, cRecipes);
				for (EmiRecipe recipe : cRecipes) {
					recipe.getInputs().stream().flatMap(i -> i.getEmiStacks().stream()).forEach(i -> {
						byInput.computeIfAbsent(i.copy(), b -> Sets.newLinkedHashSet()).add(recipe);
					});
					recipe.getCatalysts().stream().flatMap(i -> i.getEmiStacks().stream()).forEach(i -> {
						byInput.computeIfAbsent(i.copy(), b -> Sets.newLinkedHashSet()).add(recipe);
					});
					recipe.getOutputs().stream().forEach(i -> {
						byOutput.computeIfAbsent(i.copy(), b -> Sets.newLinkedHashSet()).add(recipe);
					});
				}
			}
			for (EmiStack key : byInput.keySet()) {
				Set<EmiRecipe> r = byInput.getOrDefault(key, null);
				if (r != null) {
					this.byInput.put(key, r.stream().toList());
				} else {
					EmiLog.warn("Stack illegally self-mutated during recipe bake, causing recipe loss: " + key);
				}
			}
			for (EmiStack key : byOutput.keySet()) {
				Set<EmiRecipe> r = byOutput.getOrDefault(key, null);
				if (r != null) {
					this.byOutput.put(key, r.stream().toList());
				} else {
					EmiLog.warn("Stack illegally self-mutated during recipe bake, causing recipe loss: " + key);
				}
			}
			for (EmiRecipeCategory category : workstations.keySet()) {
				List<EmiIngredient> w = workstations.getOrDefault(category, null);
				if (w != null) {
					workstations.put(category, w.stream().distinct().toList());
				} else {
					EmiLog.warn("Recipe category illegally self-mutated during recipe bake, causing recipe loss: " + category);
				}
			}
			for (Map.Entry<EmiRecipeCategory, List<EmiRecipe>> entry : byCategory.entrySet()) {
				for (EmiIngredient ingredient : workstations.getOrDefault(entry.getKey(), List.of())) {
					for (EmiStack stack : ingredient.getEmiStacks()) {
						byWorkstation.computeIfAbsent(stack, (s) -> Lists.newArrayList()).addAll(entry.getValue());
					}
				}
			}

			for (Identifier id : duplicateIds.keySet()) {
				EmiLog.warn(duplicateIds.getInt(id) + " recipes loaded with the same id: " + id);
			}
		}

		@Override
		public List<EmiRecipeCategory> getCategories() {
			return categories;
		}

		@Override
		public List<EmiIngredient> getWorkstations(EmiRecipeCategory category) {
			return workstations.getOrDefault(category, List.of());
		}

		@Override
		public List<EmiRecipe> getRecipes() {
			return recipes;
		}

		@Override
		public List<EmiRecipe> getRecipes(EmiRecipeCategory category) {
			return byCategory.getOrDefault(category, List.of());
		}

		@Override
		public @Nullable EmiRecipe getRecipe(Identifier id) {
			return byId.getOrDefault(id, null);
		}

		@Override
		public List<EmiRecipe> getRecipesByInput(EmiStack stack) {
			return byInput.getOrDefault(stack, List.of());
		}

		@Override
		public List<EmiRecipe> getRecipesByOutput(EmiStack stack) {
			return byOutput.getOrDefault(stack, List.of());
		}
	}

	private static class Worker implements Runnable {
		private List<EmiRecipeCategory> categories;
		private Map<EmiRecipeCategory, List<EmiIngredient>> workstations;
		private List<EmiRecipe> recipes;

		public Worker(List<EmiRecipeCategory> categories, Map<EmiRecipeCategory, List<EmiIngredient>> workstations, List<EmiRecipe> recipes) {
			this.categories = categories;
			this.workstations = workstations;
			this.recipes = recipes;
		}

		@Override
		public void run() {
			long startTime = System.currentTimeMillis();
			try {
				Manager manager = new Manager(categories, workstations, recipes, true);
				if (activeWorker == this) {
					long endTime = System.currentTimeMillis();
					EmiLog.info("Baked recipes after reload in " + (endTime - startTime) + "ms");
					EmiRecipes.manager = manager;
				}
			} catch (Throwable t) {
				EmiLog.error("Failed to bake sorted recipe index", t);
			}
			setWorker(null);
		}
	}
}

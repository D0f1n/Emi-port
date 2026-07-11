package dev.emi.emi.jemi;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.jemi.runtime.JemiBookmarkOverlay;
import dev.emi.emi.jemi.runtime.JemiIngredientFilter;
import dev.emi.emi.jemi.runtime.JemiIngredientListOverlay;
import dev.emi.emi.jemi.runtime.JemiRecipesGui;
import dev.emi.emi.registry.EmiIngredientSerializers;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.registry.EmiRecipes;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.runtime.EmiReloadManager;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.vanilla.IJeiIngredientInfoRecipe;
import mezz.jei.api.registration.IRuntimeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * The JEI bridge. Registered with JEI as a regular plugin (annotation scan on NeoForge, the
 * {@code jei_mod_plugin} entrypoint on Fabric) and with EMI as an {@link EmiPlugin} — but only
 * when JEI is actually installed; without JEI this class is never loaded.
 *
 * <p>JEI builds its runtime asynchronously after world join, which can land before or after EMI's
 * own world-join reload. The original waited for the runtime on the reload worker; on 26.2 the
 * reload runs on the client thread, so instead {@link #onRuntimeAvailable} re-schedules the
 * (idempotent, coalesced) reload and {@link #register} simply skips when the runtime isn't there
 * yet.
 */
@JeiPlugin
public class JemiPlugin implements IModPlugin, EmiPlugin {
	public static final Map<EmiRecipeCategory, IRecipeCategory<?>> CATEGORY_MAP = Maps.newHashMap();
	public static IJeiRuntime runtime;

	@Override
	public Identifier getPluginUid() {
		return EmiPort.id("emi:jemi");
	}

	@Override
	public void registerRuntime(IRuntimeRegistration registration) {
		registration.setIngredientListOverlay(new JemiIngredientListOverlay());
		registration.setBookmarkOverlay(new JemiBookmarkOverlay());
		registration.setRecipesGui(new JemiRecipesGui());
		registration.setIngredientFilter(new JemiIngredientFilter());
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime runtime) {
		JemiPlugin.runtime = runtime;
		EmiLog.info("[JEMI] JEI runtime available, rebuilding the EMI index");
		EmiReloadManager.reload();
	}

	@Override
	public void onRuntimeUnavailable() {
		JemiPlugin.runtime = null;
	}

	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public void register(EmiRegistry registry) {
		if (runtime == null) {
			EmiLog.info("[JEMI] JEI runtime not available yet; skipping the JEI bridge this reload");
			return;
		}

		EmiIngredientSerializers.BY_CLASS.put(JemiStack.class, new JemiStackSerializer(runtime.getIngredientManager()));
		EmiIngredientSerializers.BY_TYPE.put("jemi", new JemiStackSerializer(runtime.getIngredientManager()));
		EmiRecipeFiller.extraHandlers = JemiPlugin::getRecipeHandler;

		// TODO(polish): the original also indexed custom JEI ingredient types into the EMI
		// sidebar (addEmiStack), filtered hidden ingredients (removeEmiStacks + ingredient
		// visibility), registered subtype-based comparisons, exposed JEI's screen exclusion
		// areas / clickable stacks / ghost-ingredient drag-drop, and skipped mods that ship a
		// native EMI plugin. Those EmiRegistry surfaces return with later rounds.

		Map<IRecipeType<?>, EmiRecipeCategory> categoryMap = Maps.newHashMap();
		putMapped(categoryMap, RecipeTypes.CRAFTING, VanillaEmiRecipeCategories.CRAFTING);
		putMapped(categoryMap, RecipeTypes.SMELTING, VanillaEmiRecipeCategories.SMELTING);
		putMapped(categoryMap, RecipeTypes.BLASTING, VanillaEmiRecipeCategories.BLASTING);
		putMapped(categoryMap, RecipeTypes.SMOKING, VanillaEmiRecipeCategories.SMOKING);
		putMapped(categoryMap, RecipeTypes.CAMPFIRE_COOKING, VanillaEmiRecipeCategories.CAMPFIRE_COOKING);
		putMapped(categoryMap, RecipeTypes.STONECUTTING, VanillaEmiRecipeCategories.STONECUTTING);
		putMapped(categoryMap, RecipeTypes.SMITHING, VanillaEmiRecipeCategories.SMITHING);
		putMapped(categoryMap, RecipeTypes.ANVIL, VanillaEmiRecipeCategories.ANVIL_REPAIRING);
		putMapped(categoryMap, RecipeTypes.GRINDSTONE, VanillaEmiRecipeCategories.GRINDING);
		putMapped(categoryMap, RecipeTypes.BREWING, VanillaEmiRecipeCategories.BREWING);
		putMapped(categoryMap, RecipeTypes.COMPOSTING, VanillaEmiRecipeCategories.COMPOSTING);
		putMapped(categoryMap, RecipeTypes.SMELTING_FUEL, VanillaEmiRecipeCategories.FUEL);
		putMapped(categoryMap, RecipeTypes.BLASTING_FUEL, VanillaEmiRecipeCategories.FUEL);
		putMapped(categoryMap, RecipeTypes.SMOKING_FUEL, VanillaEmiRecipeCategories.FUEL);

		CATEGORY_MAP.clear();

		Set<Identifier> existingCategories = EmiRecipes.categories.stream().map(EmiRecipeCategory::getId).collect(Collectors.toSet());
		List<IRecipeCategory<?>> categories = runtime.getRecipeManager().createRecipeCategoryLookup().includeHidden().get().toList();
		EmiLog.info("[JEMI] " + categories.size() + " JEI recipe categories available");
		for (IRecipeCategory<?> c : categories) {
			try {
				IRecipeType type = c.getRecipeType();
				Identifier id = type.getUid();
				List<EmiStack> catalysts = runtime.getRecipeManager().createCraftingStationLookup(type).includeHidden().get()
					.map(JemiUtil::getStack).toList();
				if (categoryMap.containsKey(type)) {
					// Native EMI category: keep EMI's recipes, only pick up JEI-side workstations.
					EmiRecipeCategory category = categoryMap.get(type);
					CATEGORY_MAP.put(category, c);
					for (EmiStack catalyst : catalysts) {
						if (!catalyst.isEmpty()) {
							registry.addWorkstation(category, catalyst);
						}
					}
					continue;
				}
				if (type == RecipeTypes.INFORMATION) {
					addInfoRecipes(registry, (IRecipeCategory<IJeiIngredientInfoRecipe>) c);
					continue;
				}
				if (existingCategories.contains(id)) {
					EmiLog.info("[JEMI] Skipping recipe category " + id + " because native EMI recipe category already exists");
					continue;
				}
				List<?> recipes = runtime.getRecipeManager().createRecipeLookup(type).includeHidden().get().toList();
				if (recipes.isEmpty() && catalysts.stream().allMatch(EmiStack::isEmpty)) {
					// JEI categories whose recipes were filtered out (e.g. JEI's own vanilla
					// plugin stages skipped by PluginCallerMixin) would show up empty.
					continue;
				}
				EmiRecipeCategory category = new JemiCategory(c);
				CATEGORY_MAP.put(category, c);
				registry.addCategory(category);
				for (EmiStack catalyst : catalysts) {
					if (!catalyst.isEmpty()) {
						registry.addWorkstation(category, catalyst);
					}
				}
				int added = 0;
				for (Object r : recipes) {
					try {
						registry.addRecipe(new JemiRecipe(category, (IRecipeCategory<Object>) c, r));
						added++;
					} catch (Throwable t) {
						EmiLog.error("Exception thrown adding JEI recipe", t);
					}
				}
				EmiLog.info("[JEMI] " + id + ": " + added + " JEI recipes bridged");
			} catch (Throwable t) {
				EmiLog.error("Exception thrown adding JEI recipes", t);
			}
		}
	}

	private static void putMapped(Map<IRecipeType<?>, EmiRecipeCategory> map, IRecipeType<?> type, EmiRecipeCategory category) {
		if (category != null) {
			map.put(type, category);
		}
	}

	private void addInfoRecipes(EmiRegistry registry, IRecipeCategory<IJeiIngredientInfoRecipe> category) {
		List<IJeiIngredientInfoRecipe> recipes = runtime.getRecipeManager().createRecipeLookup(RecipeTypes.INFORMATION).includeHidden().get().toList();
		Map<List<EmiStack>, List<IJeiIngredientInfoRecipe>> grouped = Maps.newHashMap();
		for (IJeiIngredientInfoRecipe recipe : recipes) {
			grouped.computeIfAbsent(recipe.getIngredients().stream().map(JemiUtil::getStack).toList(), k -> Lists.newArrayList()).add(recipe);
		}
		Map<Component, List<EmiStack>> identical = Maps.newHashMap();
		for (Map.Entry<List<EmiStack>, List<IJeiIngredientInfoRecipe>> group : grouped.entrySet()) {
			MutableComponent text = EmiPort.literal("");
			for (IJeiIngredientInfoRecipe recipe : group.getValue()) {
				for (FormattedText sv : recipe.getDescription()) {
					MutableComponent current = EmiPort.literal("");
					sv.visit((style, string) -> {
						current.append(EmiPort.literal(string, style));
						return Optional.empty();
					}, Style.EMPTY);
					if (!current.getString().isBlank()) {
						if (!text.getString().isEmpty()) {
							text.append(" ");
						}
						text.append(current);
					}
				}
			}
			identical.computeIfAbsent(text, k -> Lists.newArrayList()).addAll(group.getKey());
		}

		for (Component text : identical.keySet()) {
			registry.addRecipe(new EmiInfoRecipe(identical.get(text).stream().map(s -> (EmiIngredient) s).toList(), List.of(text), null));
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static EmiRecipeHandler<?> getRecipeHandler(AbstractContainerMenu menu, EmiRecipe recipe) {
		if (runtime == null) {
			return null;
		}
		IRecipeCategory category = CATEGORY_MAP.getOrDefault(recipe.getCategory(), null);
		if (category != null) {
			return (EmiRecipeHandler<?>) runtime.getRecipeTransferManager().getRecipeTransferHandler(menu, category)
				.map(h -> new JemiRecipeHandler((mezz.jei.api.recipe.transfer.IRecipeTransferHandler) h)).orElse(null);
		}
		return null;
	}
}

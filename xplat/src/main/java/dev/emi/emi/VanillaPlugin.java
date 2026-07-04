package dev.emi.emi;

import java.util.List;

import com.google.common.collect.Lists;

import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiRecipeSorting;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.recipe.EmiCookingRecipe;
import dev.emi.emi.recipe.EmiSmithingRecipe;
import dev.emi.emi.recipe.EmiStonecuttingRecipe;
import dev.emi.emi.registry.EmiRecipeSource;
import dev.emi.emi.registry.EmiRecipeSource.HarvestedRecipe;
import dev.emi.emi.runtime.EmiLog;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

import static dev.emi.emi.api.recipe.VanillaEmiRecipeCategories.*;

/**
 * The built-in plugin registering vanilla recipe categories and mapping the harvested 26.2
 * {@link RecipeDisplay} data into EMI recipes.
 *
 * <p>Recipe round scope: the data-driven vanilla types (crafting, cooking, stonecutting,
 * smithing) — synthesized categories (fuel, composting, brewing, anvil, grinding) land with the
 * category displays checkpoint. Custom mod display types are not mapped here. TODO(jemi)
 */
public class VanillaPlugin implements EmiPlugin {

	@Override
	public void register(EmiRegistry registry) {
		CRAFTING = new EmiRecipeCategory(EmiPort.id("minecraft:crafting"),
			EmiStack.of(Items.CRAFTING_TABLE), EmiStack.of(Items.CRAFTING_TABLE), EmiRecipeSorting.compareOutputThenInput());
		SMELTING = new EmiRecipeCategory(EmiPort.id("minecraft:smelting"),
			EmiStack.of(Items.FURNACE), EmiStack.of(Items.FURNACE), EmiRecipeSorting.compareOutputThenInput());
		BLASTING = new EmiRecipeCategory(EmiPort.id("minecraft:blasting"),
			EmiStack.of(Items.BLAST_FURNACE), EmiStack.of(Items.BLAST_FURNACE), EmiRecipeSorting.compareOutputThenInput());
		SMOKING = new EmiRecipeCategory(EmiPort.id("minecraft:smoking"),
			EmiStack.of(Items.SMOKER), EmiStack.of(Items.SMOKER), EmiRecipeSorting.compareOutputThenInput());
		CAMPFIRE_COOKING = new EmiRecipeCategory(EmiPort.id("minecraft:campfire_cooking"),
			EmiStack.of(Items.CAMPFIRE), EmiStack.of(Items.CAMPFIRE), EmiRecipeSorting.compareOutputThenInput());
		STONECUTTING = new EmiRecipeCategory(EmiPort.id("minecraft:stonecutting"),
			EmiStack.of(Items.STONECUTTER), EmiStack.of(Items.STONECUTTER), EmiRecipeSorting.compareInputThenOutput());
		SMITHING = new EmiRecipeCategory(EmiPort.id("minecraft:smithing"),
			EmiStack.of(Items.SMITHING_TABLE), EmiStack.of(Items.SMITHING_TABLE), EmiRecipeSorting.compareInputThenOutput());

		registry.addCategory(CRAFTING);
		registry.addCategory(SMELTING);
		registry.addCategory(BLASTING);
		registry.addCategory(SMOKING);
		registry.addCategory(CAMPFIRE_COOKING);
		registry.addCategory(STONECUTTING);
		registry.addCategory(SMITHING);

		registry.addWorkstation(CRAFTING, EmiStack.of(Items.CRAFTING_TABLE));
		registry.addWorkstation(SMELTING, EmiStack.of(Items.FURNACE));
		registry.addWorkstation(BLASTING, EmiStack.of(Items.BLAST_FURNACE));
		registry.addWorkstation(SMOKING, EmiStack.of(Items.SMOKER));
		registry.addWorkstation(CAMPFIRE_COOKING, EmiStack.of(Items.CAMPFIRE));
		registry.addWorkstation(CAMPFIRE_COOKING, EmiStack.of(Items.SOUL_CAMPFIRE));
		registry.addWorkstation(STONECUTTING, EmiStack.of(Items.STONECUTTER));
		registry.addWorkstation(SMITHING, EmiStack.of(Items.SMITHING_TABLE));

		for (HarvestedRecipe entry : EmiRecipeSource.recipes) {
			try {
				addRecipe(registry, entry);
			} catch (Exception e) {
				EmiLog.warn("Failed to map recipe display for " + entry.id(), e);
			}
		}
	}

	private void addRecipe(EmiRegistry registry, HarvestedRecipe entry) {
		RecipeDisplay display = entry.display();
		if (display instanceof ShapedCraftingRecipeDisplay d) {
			registry.addRecipe(new EmiCraftingRecipe(padIngredients(d),
				EmiPort.ofSlotDisplayStack(d.result()), entry.id(), false));
		} else if (display instanceof ShapelessCraftingRecipeDisplay d) {
			registry.addRecipe(new EmiCraftingRecipe(
				d.ingredients().stream().map(EmiPort::ofSlotDisplay).toList(),
				EmiPort.ofSlotDisplayStack(d.result()), entry.id(), true));
		} else if (display instanceof FurnaceRecipeDisplay d) {
			EmiRecipeCategory category = cookingCategory(entry, d);
			int fuelMultiplier = category == BLASTING || category == SMOKING ? 2 : 1;
			boolean infiniBurn = category == CAMPFIRE_COOKING;
			registry.addRecipe(new EmiCookingRecipe(EmiPort.ofSlotDisplay(d.ingredient()),
				EmiPort.ofSlotDisplayStack(d.result()), d.duration(), d.experience(),
				category, fuelMultiplier, infiniBurn, entry.id()));
		} else if (display instanceof StonecutterRecipeDisplay d) {
			registry.addRecipe(new EmiStonecuttingRecipe(EmiPort.ofSlotDisplay(d.input()),
				EmiPort.ofSlotDisplayStack(d.result()), entry.id()));
		} else if (display instanceof SmithingRecipeDisplay d) {
			registry.addRecipe(new EmiSmithingRecipe(EmiPort.ofSlotDisplay(d.template()),
				EmiPort.ofSlotDisplay(d.base()), EmiPort.ofSlotDisplay(d.addition()),
				EmiPort.ofSlotDisplayStack(d.result()), entry.id()));
		}
		// Custom mod display types are not mapped natively. TODO(jemi)
	}

	/** Pads a shaped display's row-major ingredient grid into EMI's 3x3 slot layout. */
	private static List<EmiIngredient> padIngredients(ShapedCraftingRecipeDisplay display) {
		List<SlotDisplay> ingredients = display.ingredients();
		List<EmiIngredient> list = Lists.newArrayList();
		for (int y = 0; y < 3; y++) {
			for (int x = 0; x < 3; x++) {
				if (x >= display.width() || y >= display.height()) {
					list.add(EmiStack.EMPTY);
					continue;
				}
				int i = y * display.width() + x;
				if (i < ingredients.size()) {
					list.add(EmiPort.ofSlotDisplay(ingredients.get(i)));
				} else {
					list.add(EmiStack.EMPTY);
				}
			}
		}
		return list;
	}

	/**
	 * The cooking category for a furnace display. The integrated-server path knows the recipe
	 * type; the synced-display fallback infers it from the display's crafting station.
	 */
	private static EmiRecipeCategory cookingCategory(HarvestedRecipe entry, FurnaceRecipeDisplay display) {
		RecipeType<?> type = entry.type();
		if (type == RecipeType.SMELTING) {
			return SMELTING;
		} else if (type == RecipeType.BLASTING) {
			return BLASTING;
		} else if (type == RecipeType.SMOKING) {
			return SMOKING;
		} else if (type == RecipeType.CAMPFIRE_COOKING) {
			return CAMPFIRE_COOKING;
		}
		for (EmiStack stack : EmiPort.ofSlotDisplay(display.craftingStation()).getEmiStacks()) {
			if (stack.getItemStack().is(Items.BLAST_FURNACE)) {
				return BLASTING;
			} else if (stack.getItemStack().is(Items.SMOKER)) {
				return SMOKING;
			} else if (stack.getItemStack().is(Items.CAMPFIRE) || stack.getItemStack().is(Items.SOUL_CAMPFIRE)) {
				return CAMPFIRE_COOKING;
			}
		}
		return SMELTING;
	}
}

package dev.emi.emi;

import java.util.List;
import java.util.Random;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.data.EmiRecipeCategoryProperties;
import dev.emi.emi.platform.EmiAgnos;
import dev.emi.emi.registry.EmiRecipeFiller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/**
 * Trimmed data-side helpers for the Stage 3 layer. Recipe/screen/inventory helpers from the original
 * return with their respective rounds.
 */
public class EmiUtil {
	public static final Random RANDOM = new Random();

	/** The recipe a craft bind should fill for a plain stack: the best craftable match, as the original. */
	public static EmiRecipe getPreferredRecipe(EmiIngredient ingredient, EmiPlayerInventory inventory, boolean requireCraftable) {
		if (ingredient.getEmiStacks().size() == 1 && !ingredient.isEmpty()) {
			AbstractContainerScreen<?> hs = EmiApi.getHandledScreen();
			EmiStack stack = ingredient.getEmiStacks().get(0);
			return getPreferredRecipe(EmiApi.getRecipeManager().getRecipesByOutput(stack).stream().filter(r -> {
				@SuppressWarnings("rawtypes")
				EmiRecipeHandler handler = EmiRecipeFiller.getFirstValidHandler(r, hs);
				return handler != null && handler.supportsRecipe(r);
			}).toList(), inventory, requireCraftable);
		}
		return null;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static EmiRecipe getPreferredRecipe(List<EmiRecipe> recipes, EmiPlayerInventory inventory, boolean requireCraftable) {
		EmiRecipe preferred = null;
		int preferredWeight = -1;
		AbstractContainerScreen<?> hs = EmiApi.getHandledScreen();
		EmiCraftContext context = new EmiCraftContext<>(hs, inventory, EmiCraftContext.Type.CRAFTABLE);
		for (EmiRecipe recipe : recipes) {
			if (!recipe.supportsRecipeTree()) {
				continue;
			}
			int weight = 0;
			EmiRecipeHandler handler = EmiRecipeFiller.getFirstValidHandler(recipe, hs);
			if (handler != null && handler.canCraft(recipe, context)) {
				weight += 16;
			} else if (requireCraftable) {
				continue;
			} else if (inventory.canCraft(recipe)) {
				weight += 8;
			}
			// The original also weighs BoM-enabled recipes (+4) here. TODO(bom)
			if (recipe.getCategory() == VanillaEmiRecipeCategories.CRAFTING) {
				weight += 2;
			}
			if (weight > preferredWeight) {
				preferredWeight = weight;
				preferred = recipe;
			} else if (weight == preferredWeight) {
				if (categoryOrder(recipe) < categoryOrder(preferred)) {
					preferredWeight = weight;
					preferred = recipe;
				}
			}
		}
		return preferred;
	}

	private static int categoryOrder(EmiRecipe recipe) {
		return EmiRecipeCategoryProperties.getOrder(recipe.getCategory());
	}

	public static String subId(Identifier id) {
		return id.getNamespace() + "/" + id.getPath();
	}

	public static String subId(Block block) {
		return subId(EmiPort.getBlockRegistry().getKey(block));
	}

	public static String subId(Item item) {
		return subId(EmiPort.getItemRegistry().getKey(item));
	}

	public static String subId(Fluid fluid) {
		return subId(EmiPort.getFluidRegistry().getKey(fluid));
	}

	public static boolean showAdvancedTooltips() {
		Minecraft client = Minecraft.getInstance();
		return client.options.advancedItemTooltips;
	}

	public static String translateId(String prefix, Identifier id) {
		return prefix + id.getNamespace() + "." + id.getPath().replace('/', '.');
	}

	public static String getModName(String namespace) {
		return EmiAgnos.getModName(namespace);
	}
}

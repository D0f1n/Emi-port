package dev.emi.emi.handler;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Lists;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;

public class StonecuttingRecipeHandler implements StandardRecipeHandler<StonecutterMenu> {

	@Override
	public List<Slot> getInputSources(StonecutterMenu handler) {
		List<Slot> list = Lists.newArrayList();
		list.add(handler.getSlot(0));
		int invStart = 2;
		for (int i = invStart; i < invStart + 36; i++) {
			list.add(handler.getSlot(i));
		}
		return list;
	}

	@Override
	public List<Slot> getCraftingSlots(StonecutterMenu handler) {
		return List.of(handler.slots.get(0));
	}

	@Override
	public boolean supportsRecipe(EmiRecipe recipe) {
		return recipe.getCategory() == VanillaEmiRecipeCategories.STONECUTTING;
	}

	@Override
	public @Nullable Slot getOutputSlot(StonecutterMenu handler) {
		return handler.getSlot(1);
	}

	@Override
	public boolean craft(EmiRecipe recipe, EmiCraftContext<StonecutterMenu> context) {
		boolean action = StandardRecipeHandler.super.craft(recipe, context);
		Minecraft client = Minecraft.getInstance();
		// The original walked the client RecipeManager, gone on 26.2. The synced stonecutter option
		// list hangs off Level.recipeAccess() — the same source the server menu's setupRecipeList
		// filters with selectByInput, so indices line up with the server's button ids.
		ItemStack input = recipe.getInputs().get(0).getEmiStacks().get(0).getItemStack();
		List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> entries =
			client.level.recipeAccess().stonecutterRecipes().selectByInput(input).entries();
		int index = indexOf(entries, recipe);
		if (index >= 0) {
			StonecutterMenu sh = context.getScreenHandler();
			client.gameMode.handleInventoryButtonClick(sh.containerId, index);
			if (context.getDestination() == EmiCraftContext.Destination.CURSOR) {
				client.gameMode.handleContainerInput(sh.containerId, 1, 0, ContainerInput.PICKUP, client.player);
			} else if (context.getDestination() == EmiCraftContext.Destination.INVENTORY) {
				client.gameMode.handleContainerInput(sh.containerId, 1, 0, ContainerInput.QUICK_MOVE, client.player);
			}
		}
		return action;
	}

	private static int indexOf(List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> entries, EmiRecipe recipe) {
		// Prefer the real recipe id (integrated server / EMI-synced ids match holders).
		if (recipe.getId() != null) {
			for (int i = 0; i < entries.size(); i++) {
				SelectableRecipe<StonecutterRecipe> option = entries.get(i).recipe();
				if (option.recipe().isPresent() && option.recipe().get().id().identifier().equals(recipe.getId())) {
					return i;
				}
			}
		}
		// On plain dedicated servers holders are stripped and EMI ids are synthetic; match the
		// option display's result instead.
		if (!recipe.getOutputs().isEmpty()) {
			for (int i = 0; i < entries.size(); i++) {
				EmiIngredient display = EmiPort.ofSlotDisplay(entries.get(i).recipe().optionDisplay());
				if (!display.isEmpty() && display.getEmiStacks().get(0).isEqual(recipe.getOutputs().get(0))) {
					return i;
				}
			}
		}
		return -1;
	}
}

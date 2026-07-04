package dev.emi.emi.platform;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.network.EmiNetwork;
import dev.emi.emi.network.FillRecipeC2SPacket;
import dev.emi.emi.runtime.EmiSyncedRecipes;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class EmiClient {
	private static final Logger LOGGER = System.getLogger("emi");
	/** Whether the connected server has EMI (set by the ping packet on join). */
	public static boolean onServer = false;

	public static void init() {
		LOGGER.log(Level.INFO, "EMI 26.2 port skeleton: client init");
	}

	/** Called from the loaders' client disconnect events. */
	public static void onDisconnect() {
		onServer = false;
		EmiSyncedRecipes.clear();
	}

	public static <T extends AbstractContainerMenu> void sendFillRecipe(StandardRecipeHandler<T> handler,
			AbstractContainerScreen<T> screen, int syncId, int action, List<ItemStack> stacks, EmiRecipe recipe) {
		T menu = screen.getMenu();
		List<Slot> crafting = handler.getCraftingSlots(recipe, menu);
		Slot output = handler.getOutputSlot(menu);
		EmiNetwork.sendToServer(new FillRecipeC2SPacket(menu, action, handler.getInputSources(menu), crafting, output, stacks));
	}
}

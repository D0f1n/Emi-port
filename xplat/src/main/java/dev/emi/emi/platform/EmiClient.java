package dev.emi.emi.platform;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.google.common.collect.Maps;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.network.EmiNetwork;
import dev.emi.emi.network.FillRecipeC2SPacket;
import dev.emi.emi.runtime.EmiSyncedRecipes;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ItemLike;

public class EmiClient {
	private static final Logger LOGGER = System.getLogger("emi");
	/** Whether the connected server has EMI (set by the ping packet on join). */
	public static boolean onServer = false;
	/**
	 * Hoe till consumers to their visible results, captured by {@code HoeItemMixin} as vanilla's
	 * {@code HoeItem.TILLABLES} is built (the map values are opaque consumers otherwise).
	 */
	public static final Map<Consumer<UseOnContext>, List<ItemLike>> HOE_ACTIONS = Maps.newHashMap();

	public static void init() {
		EmiConfig.loadConfig();
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

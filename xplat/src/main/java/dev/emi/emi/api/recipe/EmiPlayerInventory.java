package dev.emi.emi.api.recipe;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.ApiStatus;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiRecipeFiller;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Port note: the craftable-discovery surface ({@code getCraftables}/{@code getPredicate}) returns
 * with the sidebars. TODO(polish)
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class EmiPlayerInventory {
	private final Comparison none = Comparison.DEFAULT_COMPARISON;
	private final Comparison strict = EmiPort.compareStrict();
	public Map<EmiStack, EmiStack> inventory = Maps.newHashMap();

	@Deprecated
	@ApiStatus.Internal
	public EmiPlayerInventory(Player entity) {
		AbstractContainerScreen<?> screen = EmiApi.getHandledScreen();
		if (screen != null && screen.getMenu() != null) {
			if (screen.getMenu().getCarried() != null) {
				addStack(screen.getMenu().getCarried());
			}
			List<EmiRecipeHandler<?>> handlers = (List) EmiRecipeFiller.getAllHandlers(screen);
			if (!handlers.isEmpty()) {
				if (handlers.get(0) instanceof StandardRecipeHandler standard) {
					List<Slot> slots = standard.getInputSources(screen.getMenu());
					for (Slot slot : slots) {
						if (slot.mayPickup(entity)) {
							addStack(slot.getItem());
						}
					}
					return;
				}
			}
		}

		Inventory pInv = entity.getInventory();
		for (int i = 0; i < pInv.getContainerSize(); i++) {
			addStack(pInv.getItem(i));
		}
	}

	public EmiPlayerInventory(List<EmiStack> stacks) {
		for (EmiStack stack : stacks) {
			addStack(stack);
		}
		AbstractContainerScreen<?> screen = EmiApi.getHandledScreen();
		if (screen != null && screen.getMenu() != null) {
			if (screen.getMenu().getCarried() != null) {
				addStack(screen.getMenu().getCarried());
			}
		}
	}

	public static EmiPlayerInventory of(Player entity) {
		AbstractContainerScreen<?> screen = EmiApi.getHandledScreen();
		if (screen != null) {
			List<EmiRecipeHandler<?>> handlers = (List) EmiRecipeFiller.getAllHandlers(screen);
			if (!handlers.isEmpty()) {
				return handlers.get(0).getInventory((AbstractContainerScreen) screen);
			}
		}
		if (entity == null) {
			return new EmiPlayerInventory(List.of());
		}
		return new EmiPlayerInventory(entity);
	}

	private void addStack(ItemStack is) {
		EmiStack stack = EmiStack.of(is).comparison(c -> none);
		addStack(stack);
	}

	private void addStack(EmiStack stack) {
		if (!stack.isEmpty()) {
			inventory.merge(stack, stack, (a, b) -> a.setAmount(a.getAmount() + b.getAmount()));
		}
	}

	public List<Boolean> getCraftAvailability(EmiRecipe recipe) {
		Object2LongMap<EmiStack> used = new Object2LongOpenHashMap<>();
		List<Boolean> states = Lists.newArrayList();
		outer:
		for (EmiIngredient ingredient : recipe.getInputs()) {
			for (EmiStack stack : ingredient.getEmiStacks()) {
				long desired = stack.getAmount();
				if (inventory.containsKey(stack)) {
					EmiStack identity = inventory.get(stack);
					long alreadyUsed = used.getOrDefault(identity, 0);
					long available = identity.getAmount() - alreadyUsed;
					if (available >= desired) {
						used.put(identity, desired + alreadyUsed);
						states.add(true);
						continue outer;
					}
				}
			}
			states.add(false);
		}
		return states;
	}

	public boolean canCraft(EmiRecipe recipe) {
		return canCraft(recipe, 1);
	}

	public boolean canCraft(EmiRecipe recipe, long amount) {
		Object2LongMap<EmiStack> used = new Object2LongOpenHashMap<>();
		outer:
		for (EmiIngredient ingredient : recipe.getInputs()) {
			if (ingredient.isEmpty()) {
				continue;
			}
			for (EmiStack stack : ingredient.getEmiStacks()) {
				long desired = stack.getAmount() * amount;
				if (inventory.containsKey(stack)) {
					EmiStack identity = inventory.get(stack);
					long alreadyUsed = used.getOrDefault(identity, 0);
					long available = identity.getAmount() - alreadyUsed;
					if (available >= desired) {
						used.put(identity, desired + alreadyUsed);
						continue outer;
					}
				}
			}
			return false;
		}
		return true;
	}

	public boolean isEqual(EmiPlayerInventory other) {
		if (other == null) {
			return false;
		}
		Comparison comparison = Comparison.of((a, b) -> {
			return strict.compare(a, b) && a.getAmount() == b.getAmount();
		});
		if (other.inventory.size() != inventory.size()) {
			return false;
		} else {
			for (EmiStack stack : inventory.keySet()) {
				if (!other.inventory.containsKey(stack) || !other.inventory.get(stack).isEqual(stack, comparison)) {
					return false;
				}
			}
		}
		return true;
	}
}

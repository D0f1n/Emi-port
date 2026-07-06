package dev.emi.emi;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiRecipeSorting;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.handler.CraftingRecipeHandler;
import dev.emi.emi.handler.InventoryRecipeHandler;
import dev.emi.emi.mixin.accessor.PotionBrewingAccessor;
import dev.emi.emi.mixin.accessor.PotionBrewingMixAccessor;
import dev.emi.emi.recipe.EmiAnvilRecipe;
import dev.emi.emi.recipe.EmiBrewingRecipe;
import dev.emi.emi.recipe.EmiCompostingRecipe;
import dev.emi.emi.recipe.EmiCookingRecipe;
import dev.emi.emi.recipe.EmiFuelRecipe;
import dev.emi.emi.recipe.EmiGrindstoneRecipe;
import dev.emi.emi.recipe.EmiSmithingRecipe;
import dev.emi.emi.recipe.EmiStonecuttingRecipe;
import dev.emi.emi.recipe.EmiTagRecipe;
import dev.emi.emi.recipe.special.EmiAnvilRepairItemRecipe;
import dev.emi.emi.registry.EmiRecipeSource;
import dev.emi.emi.registry.EmiRecipeSource.HarvestedRecipe;
import dev.emi.emi.registry.EmiTags;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.runtime.EmiTagKey;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.entity.FuelValues;

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
	public static EmiRecipeCategory TAG = new EmiRecipeCategory(EmiPort.id("emi:tag"),
		EmiStack.of(Items.NAME_TAG), EmiStack.of(Items.NAME_TAG), EmiRecipeSorting.none());

	public static EmiRecipeCategory RESOLUTION = new EmiRecipeCategory(EmiPort.id("emi:resolution"),
		EmiStack.of(Items.COMPASS), EmiStack.of(Items.COMPASS));

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
		ANVIL_REPAIRING = new EmiRecipeCategory(EmiPort.id("emi:anvil_repairing"),
			EmiStack.of(Items.ANVIL), EmiStack.of(Items.ANVIL));
		GRINDING = new EmiRecipeCategory(EmiPort.id("emi:grinding"),
			EmiStack.of(Items.GRINDSTONE), EmiStack.of(Items.GRINDSTONE));
		BREWING = new EmiRecipeCategory(EmiPort.id("minecraft:brewing"),
			EmiStack.of(Items.BREWING_STAND), EmiStack.of(Items.BREWING_STAND));
		FUEL = new EmiRecipeCategory(EmiPort.id("emi:fuel"),
			EmiTexture.FULL_FLAME, EmiTexture.FULL_FLAME, EmiRecipeSorting.compareInputThenOutput());
		COMPOSTING = new EmiRecipeCategory(EmiPort.id("emi:composting"),
			EmiStack.of(Items.COMPOSTER), EmiStack.of(Items.COMPOSTER), EmiRecipeSorting.compareInputThenOutput());

		registry.addCategory(CRAFTING);
		registry.addCategory(SMELTING);
		registry.addCategory(BLASTING);
		registry.addCategory(SMOKING);
		registry.addCategory(CAMPFIRE_COOKING);
		registry.addCategory(STONECUTTING);
		registry.addCategory(SMITHING);
		registry.addCategory(ANVIL_REPAIRING);
		registry.addCategory(GRINDING);
		registry.addCategory(BREWING);
		registry.addCategory(FUEL);
		registry.addCategory(COMPOSTING);
		registry.addCategory(TAG);
		registry.addCategory(RESOLUTION);

		registry.addWorkstation(CRAFTING, EmiStack.of(Items.CRAFTING_TABLE));
		registry.addWorkstation(SMELTING, EmiStack.of(Items.FURNACE));
		registry.addWorkstation(BLASTING, EmiStack.of(Items.BLAST_FURNACE));
		registry.addWorkstation(SMOKING, EmiStack.of(Items.SMOKER));
		registry.addWorkstation(CAMPFIRE_COOKING, EmiStack.of(Items.CAMPFIRE));
		registry.addWorkstation(CAMPFIRE_COOKING, EmiStack.of(Items.SOUL_CAMPFIRE));
		registry.addWorkstation(STONECUTTING, EmiStack.of(Items.STONECUTTER));
		registry.addWorkstation(SMITHING, EmiStack.of(Items.SMITHING_TABLE));
		registry.addWorkstation(ANVIL_REPAIRING, EmiStack.of(Items.ANVIL));
		registry.addWorkstation(ANVIL_REPAIRING, EmiStack.of(Items.CHIPPED_ANVIL));
		registry.addWorkstation(ANVIL_REPAIRING, EmiStack.of(Items.DAMAGED_ANVIL));
		registry.addWorkstation(GRINDING, EmiStack.of(Items.GRINDSTONE));
		registry.addWorkstation(BREWING, EmiStack.of(Items.BREWING_STAND));
		registry.addWorkstation(COMPOSTING, EmiStack.of(Items.COMPOSTER));

		// Craft-fill handlers for the vanilla crafting grids. The original's cooking/stonecutting
		// handlers return with the polish round. TODO(polish)
		registry.addRecipeHandler(null, new InventoryRecipeHandler());
		registry.addRecipeHandler(MenuType.CRAFTING, new CraftingRecipeHandler());

		for (HarvestedRecipe entry : EmiRecipeSource.recipes) {
			try {
				addRecipe(registry, entry);
			} catch (Exception e) {
				EmiLog.warn("Failed to map recipe display for " + entry.id(), e);
			}
		}

		safely("repair", () -> addRepair(registry));
		safely("brewing", () -> addBrewing(registry));
		safely("fuel", () -> addFuel(registry));
		safely("composting", () -> addComposting(registry));

		for (EmiTagKey<?> key : EmiTags.TAGS) {
			if (new TagEmiIngredient(key.raw(), 1).getEmiStacks().size() > 1) {
				addRecipeSafe(registry, () -> new EmiTagRecipe(key.raw()));
			}
		}
	}

	private static void addRepair(EmiRegistry registry) {
		// On 26.2 repair materials live in the REPAIRABLE data component; tool/armor material
		// classes are gone. Enchanting/disenchanting recipes are deferred to the polish round.
		for (Item i : EmiPort.getItemRegistry()) {
			try {
				if (i.components().getOrDefault(DataComponents.MAX_DAMAGE, 0) <= 0) {
					continue;
				}
				Repairable repairable = i.components().get(DataComponents.REPAIRABLE);
				if (repairable != null && repairable.items().size() > 0) {
					Item material = repairable.items().get(0).value();
					Identifier id = synthetic("anvil/repairing/material", EmiUtil.subId(i) + "/" + EmiUtil.subId(material));
					registry.addRecipe(new EmiAnvilRecipe(EmiStack.of(i),
						EmiIngredient.of(Ingredient.of(repairable.items())), id));
				}
				registry.addRecipe(new EmiAnvilRepairItemRecipe(i, synthetic("anvil/repairing/tool", EmiUtil.subId(i))));
				registry.addRecipe(new EmiGrindstoneRecipe(i, synthetic("grindstone/repairing", EmiUtil.subId(i))));
			} catch (Throwable t) {
				EmiLog.warn("Exception thrown registering repair recipes for " + EmiUtil.subId(i), t);
			}
		}
	}

	private static void addBrewing(EmiRegistry registry) {
		Level level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		PotionBrewing brewing = level.potionBrewing();
		PotionBrewingAccessor access = (PotionBrewingAccessor) brewing;
		List<Ingredient> containers = access.emi$getContainers();
		for (Object mixObj : access.emi$getPotionMixes()) {
			PotionBrewingMixAccessor mix = (PotionBrewingMixAccessor) mixObj;
			for (Ingredient container : containers) {
				container.items().forEach(containerItem -> {
					try {
						@SuppressWarnings("unchecked")
						Holder<Potion> from = (Holder<Potion>) mix.emi$getFrom();
						@SuppressWarnings("unchecked")
						Holder<Potion> to = (Holder<Potion>) mix.emi$getTo();
						Item ingredientItem = mix.emi$getIngredient().items()
							.findFirst().map(Holder::value).orElse(Items.AIR);
						Identifier id = synthetic("brewing/potion", EmiUtil.subId(containerItem.value())
							+ "/" + EmiUtil.subId(ingredientItem)
							+ "/" + EmiUtil.subId(from.unwrapKey().get().identifier())
							+ "/" + EmiUtil.subId(to.unwrapKey().get().identifier()));
						registry.addRecipe(new EmiBrewingRecipe(
							EmiStack.of(PotionContents.createItemStack(containerItem.value(), from)),
							EmiIngredient.of(mix.emi$getIngredient()),
							EmiStack.of(PotionContents.createItemStack(containerItem.value(), to)), id));
					} catch (Exception e) {
						EmiLog.warn("Failed to register a potion brewing recipe", e);
					}
				});
			}
		}
		List<Holder.Reference<Potion>> potions = EmiPort.getRegistryAccess()
			.lookupOrThrow(Registries.POTION).listElements().toList();
		for (Object mixObj : access.emi$getContainerMixes()) {
			PotionBrewingMixAccessor mix = (PotionBrewingMixAccessor) mixObj;
			for (Holder<Potion> potion : potions) {
				try {
					@SuppressWarnings("unchecked")
					Holder<Item> from = (Holder<Item>) mix.emi$getFrom();
					@SuppressWarnings("unchecked")
					Holder<Item> to = (Holder<Item>) mix.emi$getTo();
					Identifier id = synthetic("brewing/container", EmiUtil.subId(from.value())
						+ "/" + EmiUtil.subId(to.value())
						+ "/" + EmiUtil.subId(potion.unwrapKey().get().identifier()));
					registry.addRecipe(new EmiBrewingRecipe(
						EmiStack.of(PotionContents.createItemStack(from.value(), potion)),
						EmiIngredient.of(mix.emi$getIngredient()),
						EmiStack.of(PotionContents.createItemStack(to.value(), potion)), id));
				} catch (Exception e) {
					EmiLog.warn("Failed to register a container brewing recipe", e);
				}
			}
		}
	}

	private static void addFuel(EmiRegistry registry) {
		Level level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		FuelValues fuelValues = level.fuelValues();
		Set<Item> fuels = Set.copyOf(fuelValues.fuelItems());
		compressRecipesToTags(fuels, (a, b) -> {
			return Integer.compare(fuelValues.burnDuration(a.getDefaultInstance()), fuelValues.burnDuration(b.getDefaultInstance()));
		}, tag -> {
			EmiIngredient stack = EmiIngredient.of(tag.raw());
			Item item = stack.getEmiStacks().get(0).getItemStack().getItem();
			int time = fuelValues.burnDuration(item.getDefaultInstance());
			registry.addRecipe(new EmiFuelRecipe(stack, time, synthetic("fuel/tag", EmiUtil.subId(tag.id()))));
		}, item -> {
			int time = fuelValues.burnDuration(item.getDefaultInstance());
			registry.addRecipe(new EmiFuelRecipe(EmiStack.of(item), time, synthetic("fuel/item", EmiUtil.subId(item))));
		});
	}

	private static void addComposting(EmiRegistry registry) {
		compressRecipesToTags(ComposterBlock.COMPOSTABLES.keySet().stream()
			.map(i -> i.asItem()).collect(Collectors.toSet()), (a, b) -> {
				return Float.compare(ComposterBlock.COMPOSTABLES.getFloat(a), ComposterBlock.COMPOSTABLES.getFloat(b));
			}, tag -> {
				EmiIngredient stack = EmiIngredient.of(tag.raw());
				Item item = stack.getEmiStacks().get(0).getItemStack().getItem();
				float chance = ComposterBlock.COMPOSTABLES.getFloat(item);
				registry.addRecipe(new EmiCompostingRecipe(stack, chance, synthetic("composting/tag", EmiUtil.subId(tag.id()))));
			}, item -> {
				float chance = ComposterBlock.COMPOSTABLES.getFloat(item);
				registry.addRecipe(new EmiCompostingRecipe(EmiStack.of(item), chance, synthetic("composting/item", EmiUtil.subId(item))));
			});
	}

	private static void compressRecipesToTags(Set<Item> stacks, Comparator<Item> comparator, Consumer<EmiTagKey<Item>> tagConsumer, Consumer<Item> itemConsumer) {
		Set<Item> handled = Sets.newHashSet();
		outer:
		for (EmiTagKey<Item> key : EmiTags.getTags(EmiPort.getItemRegistry())) {
			List<Item> items = key.getList();
			if (items.size() < 2) {
				continue;
			}
			Item base = items.get(0);
			if (!stacks.contains(base)) {
				continue;
			}
			for (int i = 1; i < items.size(); i++) {
				Item item = items.get(i);
				if (!stacks.contains(item) || comparator.compare(base, item) != 0) {
					continue outer;
				}
			}
			if (handled.containsAll(items)) {
				continue;
			}
			handled.addAll(items);
			tagConsumer.accept(key);
		}
		for (Item item : stacks) {
			if (handled.contains(item)) {
				continue;
			}
			itemConsumer.accept(item);
		}
	}

	private static Identifier synthetic(String type, String name) {
		return EmiPort.id("emi", "/" + type + "/" + name);
	}

	private static void safely(String name, Runnable runnable) {
		try {
			runnable.run();
		} catch (Throwable t) {
			EmiLog.warn("Exception thrown when reloading " + name + " step in vanilla EMI plugin", t);
		}
	}

	private static void addRecipeSafe(EmiRegistry registry, Supplier<EmiRecipe> supplier) {
		try {
			registry.addRecipe(supplier.get());
		} catch (Throwable e) {
			EmiLog.warn("Exception thrown when parsing EMI recipe (no ID available)", e);
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

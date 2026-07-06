package dev.emi.emi;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;

import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiRecipeSorting;
import dev.emi.emi.api.recipe.EmiWorldInteractionRecipe;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.api.widget.GeneratedSlotWidget;
import dev.emi.emi.handler.CookingRecipeHandler;
import dev.emi.emi.handler.CraftingRecipeHandler;
import dev.emi.emi.handler.InventoryRecipeHandler;
import dev.emi.emi.handler.StonecuttingRecipeHandler;
import dev.emi.emi.mixin.accessor.AxeItemAccessor;
import dev.emi.emi.mixin.accessor.HoeItemAccessor;
import dev.emi.emi.mixin.accessor.PotionBrewingAccessor;
import dev.emi.emi.mixin.accessor.PotionBrewingMixAccessor;
import dev.emi.emi.mixin.accessor.ShovelItemAccessor;
import dev.emi.emi.platform.EmiClient;
import dev.emi.emi.recipe.EmiAnvilRecipe;
import dev.emi.emi.recipe.EmiBrewingRecipe;
import dev.emi.emi.recipe.EmiCompostingRecipe;
import dev.emi.emi.recipe.EmiCookingRecipe;
import dev.emi.emi.recipe.EmiFuelRecipe;
import dev.emi.emi.recipe.EmiGrindstoneRecipe;
import dev.emi.emi.recipe.EmiSmithingRecipe;
import dev.emi.emi.recipe.EmiStonecuttingRecipe;
import dev.emi.emi.platform.EmiAgnos;
import dev.emi.emi.recipe.EmiTagRecipe;
import dev.emi.emi.recipe.special.EmiAnvilEnchantRecipe;
import dev.emi.emi.recipe.special.EmiAnvilRepairItemRecipe;
import dev.emi.emi.recipe.special.EmiGrindstoneDisenchantingBookRecipe;
import dev.emi.emi.recipe.special.EmiGrindstoneDisenchantingRecipe;
import dev.emi.emi.registry.EmiRecipeSource;
import dev.emi.emi.registry.EmiRecipeSource.HarvestedRecipe;
import dev.emi.emi.registry.EmiTags;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.runtime.EmiTagKey;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ColorCollection;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluids;

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

	// The original read these from the FluidUnit config enum; the config subsystem is not ported.
	private static final long FLUID_BUCKET = EmiAgnos.isForge() ? 1000 : 81_000;
	private static final long FLUID_BOTTLE = EmiAgnos.isForge() ? 250 : 27_000;

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
		WORLD_INTERACTION = new EmiRecipeCategory(EmiPort.id("emi:world_interaction"),
			EmiStack.of(Items.GRASS_BLOCK), EmiStack.of(Items.GRASS_BLOCK), EmiRecipeSorting.none());
		FUEL = new EmiRecipeCategory(EmiPort.id("emi:fuel"),
			EmiTexture.FULL_FLAME, EmiTexture.FULL_FLAME, EmiRecipeSorting.compareInputThenOutput());
		COMPOSTING = new EmiRecipeCategory(EmiPort.id("emi:composting"),
			EmiStack.of(Items.COMPOSTER), EmiStack.of(Items.COMPOSTER), EmiRecipeSorting.compareInputThenOutput());
		INFO = new EmiRecipeCategory(EmiPort.id("emi:info"),
			EmiStack.of(Items.WRITABLE_BOOK), EmiStack.of(Items.WRITABLE_BOOK), EmiRecipeSorting.none());

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
		registry.addCategory(WORLD_INTERACTION);
		registry.addCategory(FUEL);
		registry.addCategory(COMPOSTING);
		registry.addCategory(INFO);
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

		registry.addRecipeHandler(null, new InventoryRecipeHandler());
		registry.addRecipeHandler(MenuType.CRAFTING, new CraftingRecipeHandler());
		registry.addRecipeHandler(MenuType.FURNACE, new CookingRecipeHandler<FurnaceMenu>(SMELTING));
		registry.addRecipeHandler(MenuType.BLAST_FURNACE, new CookingRecipeHandler<BlastFurnaceMenu>(BLASTING));
		registry.addRecipeHandler(MenuType.SMOKER, new CookingRecipeHandler<SmokerMenu>(SMOKING));
		registry.addRecipeHandler(MenuType.STONECUTTER, new StonecuttingRecipeHandler());

		Comparison potionComparison = Comparison.compareData(stack -> stack.get(DataComponents.POTION_CONTENTS));

		registry.setDefaultComparison(Items.POTION, potionComparison);
		registry.setDefaultComparison(Items.SPLASH_POTION, potionComparison);
		registry.setDefaultComparison(Items.LINGERING_POTION, potionComparison);
		registry.setDefaultComparison(Items.TIPPED_ARROW, potionComparison);
		registry.setDefaultComparison(Items.ENCHANTED_BOOK, EmiPort.compareStrict());

		for (HarvestedRecipe entry : EmiRecipeSource.recipes) {
			try {
				addRecipe(registry, entry);
			} catch (Exception e) {
				EmiLog.warn("Failed to map recipe display for " + entry.id(), e);
			}
		}

		safely("repair", () -> addRepair(registry));
		safely("brewing", () -> addBrewing(registry));
		safely("world interaction", () -> addWorldInteraction(registry));
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
		// classes are gone. Enchantments are a dynamic registry, so recipes carry holders.
		List<Holder<Enchantment>> targetedEnchantments = Lists.newArrayList();
		List<Holder<Enchantment>> universalEnchantments = Lists.newArrayList();
		for (Holder.Reference<Enchantment> enchantment : EmiPort.getRegistryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT).listElements().toList()) {
			try {
				if (enchantment.value().canEnchant(ItemStack.EMPTY)) {
					universalEnchantments.add(enchantment);
					continue;
				}
			} catch (Throwable t) {
			}
			targetedEnchantments.add(enchantment);
		}
		for (Item i : EmiPort.getItemRegistry()) {
			try {
				if (i.components().getOrDefault(DataComponents.MAX_DAMAGE, 0) > 0) {
					Repairable repairable = i.components().get(DataComponents.REPAIRABLE);
					if (repairable != null && repairable.items().size() > 0) {
						Item material = repairable.items().get(0).value();
						Identifier id = synthetic("anvil/repairing/material", EmiUtil.subId(i) + "/" + EmiUtil.subId(material));
						registry.addRecipe(new EmiAnvilRecipe(EmiStack.of(i),
							EmiIngredient.of(Ingredient.of(repairable.items())), id));
					}
					registry.addRecipe(new EmiAnvilRepairItemRecipe(i, synthetic("anvil/repairing/tool", EmiUtil.subId(i))));
					registry.addRecipe(new EmiGrindstoneRecipe(i, synthetic("grindstone/repairing", EmiUtil.subId(i))));
				}
			} catch (Throwable t) {
				EmiLog.warn("Exception thrown registering repair recipes for " + EmiUtil.subId(i), t);
			}
			try {
				ItemStack defaultStack = i.getDefaultInstance();
				int acceptableEnchantments = 0;
				Consumer<Holder<Enchantment>> consumer = e -> {
					int max = e.value().getMaxLevel();
					addRecipeSafe(registry, () -> new EmiAnvilEnchantRecipe(i, e, max,
						synthetic("anvil/enchanting", EmiUtil.subId(i) + "/" + EmiUtil.subId(e.unwrapKey().get().identifier()) + "/" + max)));
				};
				for (Holder<Enchantment> e : targetedEnchantments) {
					// The original also consulted Item.isEnchantable(stack); on 26.2 enchantability
					// is the ENCHANTABLE component, covered by ItemStack.isEnchantable().
					if (e.value().canEnchant(defaultStack) && defaultStack.isEnchantable()
							&& EmiAgnos.isEnchantable(defaultStack, e)) {
						consumer.accept(e);
						acceptableEnchantments++;
					}
				}
				if (acceptableEnchantments > 0) {
					for (Holder<Enchantment> e : universalEnchantments) {
						if (e.value().canEnchant(defaultStack)) {
							consumer.accept(e);
							acceptableEnchantments++;
						}
					}
					addRecipeSafe(registry, () -> new EmiGrindstoneDisenchantingRecipe(i, synthetic("grindstone/disenchanting/tool", EmiUtil.subId(i))));
				}
			} catch (Throwable t) {
				EmiLog.warn("Exception thrown registering enchantment recipes for " + EmiUtil.subId(i), t);
			}
			if (i instanceof BlockItem bi && bi.getBlock() instanceof TallFlowerBlock tf && EmiPort.canTallFlowerDuplicate(tf)) {
				addRecipeSafe(registry, () -> basicWorld(EmiStack.of(bi).setRemainder(EmiStack.of(bi)), EmiStack.of(Items.BONE_MEAL), EmiStack.of(i),
						synthetic("world/flower_duping", EmiUtil.subId(i)), false));
			}
		}

		for (Holder.Reference<Enchantment> e : EmiPort.getRegistryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT).listElements().toList()) {
			if (!e.is(EnchantmentTags.CURSE)) {
				int max = Math.min(10, e.value().getMaxLevel());
				int min = e.value().getMinLevel();
				while (min <= max) {
					int level = min;
					addRecipeSafe(registry, () -> new EmiGrindstoneDisenchantingBookRecipe(e, level,
						synthetic("grindstone/disenchanting/book", EmiUtil.subId(e.unwrapKey().get().identifier()) + "/" + level)));
					min++;
				}
			}
		}
	}

	private static void addWorldInteraction(EmiRegistry registry) {
		EmiStack concreteWater = EmiStack.of(Fluids.WATER);
		concreteWater.setRemainder(concreteWater);
		// On 26.2 the 16 colored constants collapsed into ColorCollection fields.
		ColorCollection.zipApply(Blocks.CONCRETE_POWDER, Blocks.CONCRETE,
			(powder, concrete) -> addConcreteRecipe(registry, powder, concreteWater, concrete));

		EmiIngredient axes = damagedTool(getPreferredTag(List.of(
				"minecraft:axes", "c:axes", "c:tools/axes", "fabric:axes", "forge:tools/axes"
			), EmiStack.of(Items.IRON_AXE)), 1);
		for (Map.Entry<Block, Block> entry : AxeItemAccessor.getStrippables().entrySet()) {
			Identifier id = synthetic("world/stripping", EmiUtil.subId(entry.getKey()));
			addRecipeSafe(registry, () -> basicWorld(EmiStack.of(entry.getKey()), axes, EmiStack.of(entry.getValue()), id));
		}
		for (Map.Entry<Block, Block> entry : WeatheringCopper.PREVIOUS_BY_BLOCK.get().entrySet()) {
			Identifier id = synthetic("world/stripping", EmiUtil.subId(entry.getKey()));
			addRecipeSafe(registry, () -> basicWorld(EmiStack.of(entry.getKey()), axes, EmiStack.of(entry.getValue()), id));
		}
		for (Map.Entry<Block, Block> entry : HoneycombItem.WAX_OFF_BY_BLOCK.get().entrySet()) {
			Identifier id = synthetic("world/stripping", EmiUtil.subId(entry.getKey()));
			addRecipeSafe(registry, () -> basicWorld(EmiStack.of(entry.getKey()), axes, EmiStack.of(entry.getValue()), id));
		}

		EmiIngredient shears = damagedTool(EmiStack.of(Items.SHEARS), 1);
		addRecipeSafe(registry, () -> EmiWorldInteractionRecipe.builder()
			.id(synthetic("world/shearing", "minecraft/pumpkin"))
			.leftInput(EmiStack.of(Items.PUMPKIN))
			.rightInput(shears, true)
			.output(EmiStack.of(Items.PUMPKIN_SEEDS, 4))
			.output(EmiStack.of(Items.CARVED_PUMPKIN))
			.build());
		EmiIngredient hoes = damagedTool(getPreferredTag(List.of(
				"minecraft:hoes", "c:hoes", "c:tools/hoes", "fabric:hoes", "forge:tools/hoes"
			), EmiStack.of(Items.IRON_HOE)), 1);
		for (Map.Entry<Block, Pair<Predicate<UseOnContext>, Consumer<UseOnContext>>> entry
				: HoeItemAccessor.getTillables().entrySet()) {
			Consumer<UseOnContext> consumer = entry.getValue().getSecond();
			if (EmiClient.HOE_ACTIONS.containsKey(consumer)) {
				Block b = entry.getKey();
				Identifier id = synthetic("world/tilling", EmiUtil.subId(b));
				List<EmiStack> list = EmiClient.HOE_ACTIONS.get(consumer).stream().map(EmiStack::of).toList();
				if (list.size() == 1) {
					addRecipeSafe(registry, () -> basicWorld(EmiStack.of(b), hoes, list.get(0), id));
				} else if (list.size() == 2) {
					addRecipeSafe(registry, () -> EmiWorldInteractionRecipe.builder()
						.id(id)
						.leftInput(EmiStack.of(b))
						.rightInput(hoes, true)
						.output(list.get(0))
						.output(list.get(1))
						.build());
				} else {
					EmiLog.warn("Encountered hoe action of peculiar size " + list.size() + ", skipping.");
				}
			}
		}

		EmiIngredient shovels = damagedTool(getPreferredTag(List.of(
				"minecraft:shovels", "c:shovels", "c:tools/shovels", "fabric:shovels", "forge:tools/shovels"
			), EmiStack.of(Items.IRON_SHOVEL)), 1);
		for (Map.Entry<Block, BlockState> entry : ShovelItemAccessor.getFlattenables().entrySet()) {
			Block result = entry.getValue().getBlock();
			Identifier id = synthetic("world/flattening", EmiUtil.subId(entry.getKey()));
			addRecipeSafe(registry, () -> basicWorld(EmiStack.of(entry.getKey()), shovels, EmiStack.of(result), id));
		}

		EmiIngredient honeycomb = EmiStack.of(Items.HONEYCOMB);
		for (Map.Entry<Block, Block> entry : HoneycombItem.WAXABLES.get().entrySet()) {
			Identifier id = synthetic("world/waxing", EmiUtil.subId(entry.getKey()));
			addRecipeSafe(registry, () -> basicWorld(EmiStack.of(entry.getKey()), honeycomb, EmiStack.of(entry.getValue()), id, false));
		}

		// The original iterated ItemTags.DYEABLE, removed on 26.2; CAULDRON_CAN_REMOVE_DYE is the
		// exact cauldron-washing item set.
		for (Item i : EmiTagKey.of(ItemTags.CAULDRON_CAN_REMOVE_DYE).getList()) {
			EmiStack cauldron = EmiStack.of(Items.CAULDRON);
			EmiStack waterThird = EmiStack.of(Fluids.WATER, FLUID_BOTTLE);
			int uniq = EmiUtil.RANDOM.nextInt();
			addRecipeSafe(registry, () -> EmiWorldInteractionRecipe.builder()
				.id(synthetic("world/cauldron_washing", EmiUtil.subId(i)))
				.leftInput(EmiStack.EMPTY, s -> new GeneratedSlotWidget(r -> {
					ItemStack stack = i.getDefaultInstance();
					stack.set(DataComponents.DYED_COLOR, new DyedItemColor(r.nextInt(0xFFFFFF + 1)));
					return EmiStack.of(stack);
				}, uniq, s.getBounds().x(), s.getBounds().y()))
				.rightInput(cauldron, true)
				.rightInput(waterThird, false)
				.output(EmiStack.of(i))
				.supportsRecipeTree(false)
				.build());
		}

		EmiStack water = EmiStack.of(Fluids.WATER, FLUID_BUCKET);
		EmiStack lava = EmiStack.of(Fluids.LAVA, FLUID_BUCKET);
		EmiStack waterCatalyst = water.copy().setRemainder(water);
		EmiStack lavaCatalyst = lava.copy().setRemainder(lava);

		addRecipeSafe(registry, () -> EmiWorldInteractionRecipe.builder()
			.id(synthetic("world/fluid_spring", "minecraft/water"))
			.leftInput(waterCatalyst)
			.rightInput(waterCatalyst, false)
			.output(EmiStack.of(Fluids.WATER, FLUID_BUCKET))
			.build());
		addRecipeSafe(registry, () -> EmiWorldInteractionRecipe.builder()
			.id(synthetic("world/fluid_interaction", "minecraft/cobblestone"))
			.leftInput(waterCatalyst)
			.rightInput(lavaCatalyst, false)
			.output(EmiStack.of(Items.COBBLESTONE))
			.build());
		addRecipeSafe(registry, () -> EmiWorldInteractionRecipe.builder()
			.id(synthetic("world/fluid_interaction", "minecraft/stone"))
			.leftInput(waterCatalyst)
			.rightInput(lavaCatalyst, false)
			.output(EmiStack.of(Items.STONE))
			.build());
		addRecipeSafe(registry, () -> EmiWorldInteractionRecipe.builder()
			.id(synthetic("world/fluid_interaction", "minecraft/obsidian"))
			.leftInput(lava)
			.rightInput(waterCatalyst, false)
			.output(EmiStack.of(Items.OBSIDIAN))
			.build());

		EmiStack soulSoil = EmiStack.of(Items.SOUL_SOIL);
		soulSoil.setRemainder(soulSoil);
		EmiStack blueIce = EmiStack.of(Items.BLUE_ICE);
		blueIce.setRemainder(blueIce);

		addRecipeSafe(registry, () -> EmiWorldInteractionRecipe.builder()
			.id(synthetic("world/fluid_interaction", "minecraft/basalt"))
			.leftInput(lavaCatalyst)
			.rightInput(soulSoil, false, s -> s.appendTooltip(EmiPort
				.translatable("tooltip.emi.fluid_interaction.basalt.soul_soil", ChatFormatting.GREEN)))
			.rightInput(blueIce, false, s -> s.appendTooltip(EmiPort
				.translatable("tooltip.emi.fluid_interaction.basalt.blue_ice", ChatFormatting.GREEN)))
			.output(EmiStack.of(Items.BASALT))
			.build());

		EmiPort.getFluidRegistry().stream().forEach(fluid -> {
			Item bucket = fluid.getBucket();
			if (fluid.isSource(fluid.defaultFluidState()) && !fluid.defaultFluidState().createLegacyBlock().isAir()
					&& bucket != Items.AIR && fluid instanceof FlowingFluid) {
				addRecipeSafe(registry, () -> basicWorld(EmiStack.of(Items.BUCKET), EmiStack.of(fluid, FLUID_BUCKET), EmiStack.of(bucket),
					synthetic("emi", "bucket_filling/" + EmiUtil.subId(fluid)), false));
			}
		});

		addRecipeSafe(registry, () -> basicWorld(EmiStack.of(Items.GLASS_BOTTLE), water,
			EmiStack.of(PotionContents.createItemStack(Items.POTION, Potions.WATER)),
			synthetic("world/unique", "minecraft/water_bottle")));

		EmiStack waterBottle = EmiStack.of(PotionContents.createItemStack(Items.POTION, Potions.WATER))
			.setRemainder(EmiStack.of(Items.GLASS_BOTTLE));
		EmiStack mud = EmiStack.of(Items.MUD);
		addRecipeSafe(registry, () -> basicWorld(EmiStack.of(Items.DIRT), waterBottle, mud, synthetic("world/unique", "minecraft/mud"), false));
	}

	private static EmiIngredient damagedTool(EmiIngredient tool, int damage) {
		for (EmiStack stack : tool.getEmiStacks()) {
			ItemStack is = stack.getItemStack().copy();
			is.setDamageValue(1);
			stack.setRemainder(EmiStack.of(is));
		}
		return tool;
	}

	private static EmiIngredient getPreferredTag(List<String> candidates, EmiIngredient fallback) {
		for (String id : candidates) {
			EmiIngredient potential = EmiIngredient.of(TagKey.create(EmiPort.getItemRegistry().key(), EmiPort.id(id)));
			if (!potential.isEmpty()) {
				return potential;
			}
		}
		return fallback;
	}

	private static void addConcreteRecipe(EmiRegistry registry, Block powder, EmiStack water, Block result) {
		addRecipeSafe(registry, () -> basicWorld(EmiStack.of(powder), water, EmiStack.of(result),
			synthetic("world/concrete", EmiUtil.subId(result))));
	}

	private static EmiRecipe basicWorld(EmiIngredient left, EmiIngredient right, EmiStack output, Identifier id) {
		return basicWorld(left, right, output, id, true);
	}

	private static EmiRecipe basicWorld(EmiIngredient left, EmiIngredient right, EmiStack output, Identifier id, boolean catalyst) {
		return EmiWorldInteractionRecipe.builder()
			.id(id)
			.leftInput(left)
			.rightInput(right, catalyst)
			.output(output)
			.build();
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

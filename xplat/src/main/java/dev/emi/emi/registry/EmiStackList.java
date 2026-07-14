package dev.emi.emi.registry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiRegistryAdapter;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.data.EmiData;
import dev.emi.emi.data.IndexStackData;
import dev.emi.emi.runtime.EmiHidden;
import dev.emi.emi.runtime.EmiLog;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * The item/fluid index. Built post-world-load (see {@code EmiReloadManager}) so that the {@code new ItemStack}
 * calls it makes are legal on 26.1+ (ItemStackTemplate lifecycle).
 *
 * <p>Stage 3 scope: the config-driven index sources and EmiHidden filtering from the original are
 * dropped — this builds the full registry + creative-tab + fluid set, deduped, then applies the
 * datapack {@code index/stacks} edits.
 */
public class EmiStackList {
	private static final TagKey<Item> ITEM_HIDDEN = TagKey.create(EmiPort.getItemRegistry().key(), EmiTags.HIDDEN_FROM_RECIPE_VIEWERS);
	private static final TagKey<Block> BLOCK_HIDDEN = TagKey.create(EmiPort.getBlockRegistry().key(), EmiTags.HIDDEN_FROM_RECIPE_VIEWERS);
	private static final TagKey<Fluid> FLUID_HIDDEN = TagKey.create(EmiPort.getFluidRegistry().key(), EmiTags.HIDDEN_FROM_RECIPE_VIEWERS);
	public static List<Predicate<EmiStack>> invalidators = Lists.newArrayList();
	// Published complete and never mutated in place: search workers and the render thread read these
	// while a reload is rebuilding, and must only ever observe a finished index (old or new).
	public static volatile List<EmiStack> stacks = List.of();
	public static volatile List<EmiStack> filteredStacks = List.of();
	private static volatile Object2IntMap<EmiStack> strictIndices = new Object2IntOpenCustomHashMap<>(new StrictHashStrategy());
	private static volatile Object2IntMap<Object> keyIndices = new Object2IntOpenHashMap<>();

	public static void clear() {
		invalidators = Lists.newArrayList();
		stacks = List.of();
		strictIndices = new Object2IntOpenCustomHashMap<>(new StrictHashStrategy());
		keyIndices = new Object2IntOpenHashMap<>();
	}

	public static void reload() {
		FeatureFlagSet flags = FeatureFlags.REGISTRY.allFlags();
		CreativeModeTab.ItemDisplayParameters params = new CreativeModeTab.ItemDisplayParameters(flags, true, EmiPort.getRegistryAccess());
		List<IndexGroup> groups = Lists.newArrayList();
		Map<String, IndexGroup> namespaceGroups = new LinkedHashMap<>();

		for (Item item : EmiPort.getItemRegistry()) {
			String itemName = "null";
			try {
				itemName = item.toString();
				EmiStack stack = EmiStack.of(item);
				namespaceGroups.computeIfAbsent(stack.getId().getNamespace(), (k) -> new IndexGroup()).stacks.add(stack);
			} catch (Exception e) {
				EmiLog.error("Item " + itemName + " threw while EMI was attempting to construct the index, items may be missing.", e);
			}
		}

		// Creative tab population stays on the render thread even though the rest of the reload runs
		// on the worker: CreativeModeTab.buildContents mutates the tab's shared display sets, which
		// JEI (on recipe sync) and vanilla's creative screen also rebuild on the render thread — a
		// concurrent build corrupts the sets for both sides (fastutil AIOOBE, missing tabs).
		Minecraft client = Minecraft.getInstance();
		if (client.isSameThread()) {
			groups.addAll(buildCreativeTabGroups(params));
		} else {
			groups.addAll(client.submit(() -> {
				long tabStart = System.currentTimeMillis();
				List<IndexGroup> tabGroups = buildCreativeTabGroups(params);
				EmiLog.info("Creative tab contents built on the render thread in "
					+ (System.currentTimeMillis() - tabStart) + "ms");
				return tabGroups;
			}).join());
		}

		groups.addAll(namespaceGroups.values());

		IndexGroup fluidGroup = new IndexGroup();
		for (Fluid fluid : EmiPort.getFluidRegistry()) {
			String fluidName = null;
			try {
				fluidName = fluid.toString();
				if (fluid.defaultFluidState().isSource() || (fluid instanceof FlowingFluid ff && ff.getSource() == Fluids.EMPTY)) {
					fluidGroup.stacks.add(EmiStack.of(fluid));
				}
			} catch (Exception e) {
				EmiLog.error("Fluid " + fluidName + " threw while EMI was attempting to construct the index, stack may be missing.", e);
			}
		}
		groups.add(fluidGroup);

		Set<EmiStack> added = new ObjectOpenCustomHashSet<>(new StrictHashStrategy());

		List<EmiStack> built = Lists.newLinkedList();
		for (IndexGroup group : groups) {
			if (group.shouldDisplay()) {
				for (EmiStack stack : group.stacks) {
					if (!added.contains(stack)) {
						built.add(stack);
						added.add(stack);
					}
				}
			}
		}
		stacks = built;
	}

	// Both loaders patch CreativeModeTab.buildContents (Fabric's creative-tab API and NeoForge's
	// BuildCreativeModeTabContentsEvent), so this single vanilla path picks up modded items on both.
	private static List<IndexGroup> buildCreativeTabGroups(CreativeModeTab.ItemDisplayParameters params) {
		List<IndexGroup> groups = Lists.newArrayList();
		for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
			if (tab.getType() == CreativeModeTab.Type.HOTBAR || tab.getType() == CreativeModeTab.Type.INVENTORY) {
				continue;
			}
			try {
				tab.buildContents(params);
				IndexGroup ig = new IndexGroup();
				for (ItemStack stack : tab.getSearchTabDisplayItems()) {
					ig.stacks.add(EmiStack.of(stack));
				}
				groups.add(ig);
			} catch (Exception e) {
				EmiLog.error("Creative tab " + tab + " threw while EMI was attempting to construct the index, items may be missing.", e);
			}
		}
		return groups;
	}

	@SuppressWarnings({"unchecked"})
	private static <T> boolean isHiddenFromRecipeViewers(T key) {
		if (key instanceof Item i) {
			if (i instanceof BlockItem bi && bi.getBlock().defaultBlockState().is(BLOCK_HIDDEN)) {
				return true;
			} else if (i.builtInRegistryHolder().is(ITEM_HIDDEN)) {
				return true;
			}
		} else if (key instanceof Fluid f) {
			if (f.builtInRegistryHolder().is(FLUID_HIDDEN)) {
				return true;
			}
		} else {
			EmiRegistryAdapter<T> adapter = (EmiRegistryAdapter<T>) EmiTags.ADAPTERS_BY_CLASS.get(key.getClass());
			if (adapter != null) {
				return adapter.getRegistry().wrapAsHolder(key).is(TagKey.create(adapter.getRegistry().key(), EmiTags.HIDDEN_FROM_RECIPE_VIEWERS));
			}
		}
		return false;
	}

	public static void bake() {
		List<EmiStack> stacks = Lists.newArrayList(EmiStackList.stacks);
		stacks.removeIf(s -> {
			try {
				if (s.isEmpty()) {
					return true;
				}
				for (Predicate<EmiStack> invalidator : invalidators) {
					if (invalidator.test(s)) {
						return true;
					}
				}
				if (isHiddenFromRecipeViewers(s.getKey())) {
					return true;
				}
				return false;
			} catch (Throwable t) {
				EmiLog.error("Stack threw error while baking", t);
				return true;
			}
		});
		for (Supplier<IndexStackData> supplier : EmiData.stackData) {
			IndexStackData ssd = supplier.get();
			if (!ssd.removed().isEmpty()) {
				Set<EmiStack> removed = Sets.newHashSet();
				for (EmiIngredient invalidator : ssd.removed()) {
					for (EmiStack stack : invalidator.getEmiStacks()) {
						removed.add(stack.copy().comparison(c -> EmiPort.compareStrict()));
					}
				}
				stacks.removeAll(removed);
			}
			if (!ssd.filters().isEmpty()) {
				stacks.removeIf(s -> {
					String id = "" + s.getId();
					for (IndexStackData.Filter filter : ssd.filters()) {
						if (filter.filter().test(id)) {
							return true;
						}
					}
					return false;
				});
			}
			for (IndexStackData.Added added : ssd.added()) {
				if (added.added().isEmpty()) {
					continue;
				}
				if (added.after().isEmpty()) {
					stacks.add(added.added().getEmiStacks().get(0));
				} else {
					int i = stacks.indexOf(added.after());
					if (i == -1) {
						i = stacks.size() - 1;
					}
					stacks.add(i + 1, added.added().getEmiStacks().get(0));
				}
			}
		}
		stacks = stacks.stream().filter(stack -> {
			String name = "Unknown";
			String id = "unknown";
			try {
				if (stack.isEmpty()) {
					return false;
				}
				name = stack.toString();
				id = stack.getId().toString();
				if (name != null && stack.getKey() != null && stack.getName() != null) {
					return true;
				}
				EmiLog.warn("Hiding stack " + name + " with id " + id + " from index due to returning dangerous values");
				return false;
			} catch (Throwable t) {
				EmiLog.error("Hiding stack " + name + " with id " + id + " from index due to throwing errors", t);
				return false;
			}
		}).toList();
		Object2IntMap<EmiStack> strictIndices = new Object2IntOpenCustomHashMap<>(new StrictHashStrategy());
		Object2IntMap<Object> keyIndices = new Object2IntOpenHashMap<>();
		for (int i = 0; i < stacks.size(); i++) {
			EmiStack stack = stacks.get(i);
			strictIndices.put(stack, i);
			keyIndices.put(stack.getKey(), i);
		}
		EmiStackList.stacks = stacks;
		EmiStackList.strictIndices = strictIndices;
		EmiStackList.keyIndices = keyIndices;
		bakeFiltered();
	}

	public static void bakeFiltered() {
		filteredStacks = stacks.stream().filter(s -> !EmiHidden.isDisabled(s) && !EmiHidden.isHidden(s)).toList();
	}

	public static int getIndex(EmiIngredient ingredient) {
		EmiStack stack = ingredient.getEmiStacks().get(0);
		int ret = strictIndices.getOrDefault(stack, Integer.MAX_VALUE);
		if (ret == Integer.MAX_VALUE) {
			ret = keyIndices.getOrDefault(stack, ret);
		}
		return ret;
	}

	public static class IndexGroup {
		public List<EmiStack> stacks = Lists.newArrayList();
		public Set<IndexGroup> suppressedBy = com.google.common.collect.Sets.newHashSet();

		public boolean shouldDisplay() {
			for (IndexGroup suppressor : suppressedBy) {
				if (suppressor.shouldDisplay()) {
					return false;
				}
			}
			return true;
		}
	}

	public static class StrictHashStrategy implements Hash.Strategy<EmiStack> {

		@Override
		public boolean equals(EmiStack a, EmiStack b) {
			if (a == b) {
				return true;
			} else if (a == null || b == null) {
				return false;
			} else if (a.isEmpty() && b.isEmpty()) {
				return true;
			}
			return a.isEqual(b, EmiPort.compareStrict());
		}

		@Override
		public int hashCode(EmiStack stack) {
			if (stack != null) {
				DataComponentPatch changes = stack.getComponentChanges();
				int i = 31 + stack.getKey().hashCode();
				return 31 * i + changes.hashCode();
			}
			return 0;
		}
	}

	public static class ComparisonHashStrategy implements Hash.Strategy<EmiStack> {

		@Override
		public boolean equals(EmiStack a, EmiStack b) {
			if (a == b) {
				return true;
			} else if (a == null || b == null) {
				return false;
			} else if (a.isEmpty() && b.isEmpty()) {
				return true;
			}
			return a.isEqual(b, EmiComparisonDefaults.get(a.getKey()));
		}

		@Override
		public int hashCode(EmiStack stack) {
			if (stack != null) {
				int i = 31 + stack.getKey().hashCode();
				return 31 * i + EmiComparisonDefaults.get(stack.getKey()).getHash(stack);
			}
			return 0;
		}
	}
}

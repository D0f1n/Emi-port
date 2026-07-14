package dev.emi.emi.registry;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiRegistryAdapter;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.ListEmiIngredient;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.data.TagExclusions;
import dev.emi.emi.platform.EmiAgnos;
import dev.emi.emi.runtime.EmiTagKey;
import dev.emi.emi.util.InheritanceMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Tag resolution side of the Stage 3 layer. Datapack exclusions ({@code tag/exclusions}) filter
 * out junk tags before consolidation, as in the original.
 *
 * <p>Synthetic tag icons: the original registered {@code models/tag/**} through the Model Loading
 * API and rendered the baked models directly — both halves of that path are gone on 26.2. Instead
 * the same tag models are shipped alongside client item definitions ({@code items/tag/**}), which
 * vanilla loads for arbitrary ids by directory scan; {@link #MODELED_TAGS} maps tags to those item
 * model ids and {@code TagEmiIngredient} renders them through the ordinary item path with the
 * {@code minecraft:item_model} component.
 */
public class EmiTags {
	public static final InheritanceMap<EmiRegistryAdapter<?>> ADAPTERS_BY_CLASS = new InheritanceMap<>(Maps.newHashMap());
	public static final Map<Registry<?>, EmiRegistryAdapter<?>> ADAPTERS_BY_REGISTRY = Maps.newHashMap();
	// Written by EmiTagExclusionsLoader on resource reload, read by the reload worker.
	public static volatile TagExclusions exclusions = new TagExclusions();
	public static final Identifier HIDDEN_FROM_RECIPE_VIEWERS = EmiPort.id("c", "hidden_from_recipe_viewers");
	// Tag -> client item definition id ("<ns>:tag/<registry>/<path>") for tags with a synthetic icon.
	// Swapped whole on reload: the render thread reads it for tag icons while the reload thread rebuilds.
	public static volatile Map<TagKey<?>, Identifier> MODELED_TAGS = Map.of();
	private static final Map<Set<?>, List<EmiTagKey<?>>> CACHED_TAGS = Maps.newHashMap();
	private static final Map<EmiTagKey<?>, List<?>> TAG_VALUES = Maps.newHashMap();
	private static final Map<Identifier, List<EmiTagKey<?>>> SORTED_TAGS = Maps.newHashMap();
	public static final List<EmiTagKey<?>> TAGS = Lists.newArrayList();

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static <T> List<EmiStack> getValues(EmiTagKey<T> key) {
		if (TAG_VALUES.containsKey(key)) {
			EmiRegistryAdapter adapter = ADAPTERS_BY_REGISTRY.get(key.registry());
			if (adapter != null) {
				List<T> values = (List<T>) TAG_VALUES.getOrDefault(key, List.of());
				return values.stream().map(t -> adapter.of(t, EmiPort.emptyExtraData(), 1)).toList();
			}
		}
		return List.of();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static <T> List<EmiStack> getRawValues(EmiTagKey<T> key) {
		if (key.isOf(EmiPort.getBlockRegistry())) {
			return key.stream().map(e -> EmiStack.of((Block) e)).toList();
		}
		EmiRegistryAdapter adapter = ADAPTERS_BY_REGISTRY.get(key.registry());
		if (adapter != null) {
			return key.stream().map(t -> adapter.of(t, EmiPort.emptyExtraData(), 1)).toList();
		}
		return List.of();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static <T> EmiIngredient getIngredient(Class<T> clazz, List<EmiStack> stacks, long amount) {
		Map<T, EmiStack> map = Maps.newHashMap();
		for (EmiStack stack : stacks) {
			if (!stack.isEmpty()) {
				EmiStack existing = map.getOrDefault(stack.getKey(), null);
				if (existing != null && !stack.equals(existing)) {
					return new ListEmiIngredient(stacks, amount);
				}
				map.put((T) stack.getKey(), stack);
			}
		}
		if (map.size() == 0) {
			return EmiStack.EMPTY;
		} else if (map.size() == 1) {
			return map.values().stream().toList().get(0).copy().setAmount(amount);
		}
		EmiRegistryAdapter<T> adapter = (EmiRegistryAdapter<T>) ADAPTERS_BY_CLASS.get(clazz);
		if (adapter == null) {
			return new ListEmiIngredient(stacks, amount);
		}
		Registry<T> registry = adapter.getRegistry();
		List<EmiTagKey<T>> keys = (List<EmiTagKey<T>>) (List) CACHED_TAGS.get(map.keySet());

		if (keys != null) {
			for (EmiTagKey<T> key : keys) {
				List<T> values = key.getList();
				values.forEach(map::remove);
			}
		} else {
			keys = Lists.newArrayList();
			Set<T> original = new HashSet<>(map.keySet());
			for (EmiTagKey<T> key : getTags(registry)) {
				List<T> values = key.getList();
				if (values.size() < 2) {
					continue;
				}
				if (map.keySet().containsAll(values)) {
					values.forEach(map::remove);
					keys.add(key);
				}
				if (map.isEmpty()) {
					break;
				}
			}
			CACHED_TAGS.put((Set) original, (List) keys);
		}

		if (keys == null || keys.isEmpty()) {
			return new ListEmiIngredient(stacks.stream().toList(), amount);
		} else if (map.isEmpty()) {
			if (keys.size() == 1) {
				return tagIngredient(keys.get(0), amount);
			} else {
				return new ListEmiIngredient(keys.stream().map(k -> tagIngredient(k, 1)).toList(), amount);
			}
		} else {
			return new ListEmiIngredient(List.of(map.values().stream().map(i -> i.copy().setAmount(1)).toList(),
					keys.stream().map(k -> tagIngredient(k, 1)).toList())
				.stream().flatMap(a -> a.stream()).toList(), amount);
		}
	}

	private static EmiIngredient tagIngredient(EmiTagKey<?> key, long amount) {
		List<?> list = TAG_VALUES.get(key);
		if (list == null || list.isEmpty()) {
			return EmiStack.EMPTY;
		} else if (list.size() == 1) {
			return new TagEmiIngredient(key, amount).getEmiStacks().get(0).copy().setAmount(amount);
		} else {
			return new TagEmiIngredient(key, amount);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static <T> List<EmiTagKey<T>> getTags(Registry<T> registry) {
		return (List<EmiTagKey<T>>) (List) SORTED_TAGS.getOrDefault(registry.key().identifier(), List.of());
	}

	public static void reload() {
		EmiTagKey.reload();
		TAGS.clear();
		SORTED_TAGS.clear();
		TAG_VALUES.clear();
		CACHED_TAGS.clear();
		reloadTagModels();
		for (Registry<?> registry : ADAPTERS_BY_REGISTRY.keySet()) {
			reloadTags(registry);
		}
	}

	/**
	 * The 26.2 equivalent of the original {@code registerTagModels}: scans {@code items/tag/**} client
	 * item definitions (resource packs can add their own, like the original's {@code models/tag/**})
	 * and maps each to the tag it depicts. Path layout matches the original:
	 * {@code items/tag/<registry>/<namespaced tag path>.json}.
	 */
	private static void reloadTagModels() {
		Map<TagKey<?>, Identifier> modeledTags = Maps.newHashMap();
		Map<Identifier, ?> resources = Minecraft.getInstance().getResourceManager()
			.listResources("items/tag", i -> i.getPath().endsWith(".json"));
		for (Identifier id : resources.keySet()) {
			String path = id.getPath();
			// items/tag/item/ingots.json -> tag/item/ingots
			path = path.substring(6, path.length() - 5);
			String[] parts = path.split("/");
			if (parts.length > 2) {
				TagKey<?> key = TagKey.create(ResourceKey.createRegistryKey(EmiPort.id("minecraft", parts[1])),
					EmiPort.id(id.getNamespace(), path.substring(5 + parts[1].length())));
				modeledTags.put(key, EmiPort.id(id.getNamespace(), path));
			}
		}
		MODELED_TAGS = modeledTags;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <T> void reloadTags(Registry<T> registry) {
		Set<T> hidden = EmiTagKey.of(registry, HIDDEN_FROM_RECIPE_VIEWERS).getSet();
		Identifier rid = registry.key().identifier();
		List<EmiTagKey<T>> tags = EmiTagKey.fromRegistry(registry)
			.filter(key -> !exclusions.contains(rid, key.id()) && !hidden.containsAll(key.getList()))
			.toList();
		tags = consolodateTags(tags);
		for (EmiTagKey<T> key : tags) {
			TAG_VALUES.put(key, key.getList());
		}
		EmiTags.TAGS.addAll(tags.stream().sorted((a, b) -> a.id().toString().compareTo(b.id().toString())).toList());
		tags = tags.stream()
			.sorted((a, b) -> Long.compare(b.stream().count(), a.stream().count()))
			.toList();
		EmiTags.SORTED_TAGS.put(registry.key().identifier(), (List) tags);
	}

	private static <T> List<EmiTagKey<T>> consolodateTags(List<EmiTagKey<T>> tags) {
		Map<Set<T>, EmiTagKey<T>> map = Maps.newHashMap();
		for (int i = 0; i < tags.size(); i++) {
			EmiTagKey<T> key = tags.get(i);
			Set<T> values = key.getSet();
			EmiTagKey<T> original = map.get(values);
			if (original != null) {
				map.put(values, betterTag(key, original));
			} else {
				map.put(values, key);
			}
		}
		return map.values().stream().toList();
	}

	private static <T> EmiTagKey<T> betterTag(EmiTagKey<T> a, EmiTagKey<T> b) {
		if (a.hasTranslation() != b.hasTranslation()) {
			return a.hasTranslation() ? a : b;
		}
		if (a.hasCustomModel() != b.hasCustomModel()) {
			return a.hasCustomModel() ? a : b;
		}
		String an = a.id().getNamespace();
		String bn = b.id().getNamespace();
		if (!an.equals(bn)) {
			if (an.equals("minecraft")) {
				return a;
			} else if (bn.equals("minecraft")) {
				return b;
			} else if (an.equals("c")) {
				return a;
			} else if (bn.equals("c")) {
				return b;
			} else if (an.equals("fabric")) {
				return EmiAgnos.isModLoaded("forge") ? b : a;
			} else if (bn.equals("fabric")) {
				return EmiAgnos.isModLoaded("forge") ? a : b;
			} else if (an.equals("forge")) {
				return EmiAgnos.isModLoaded("forge") ? a : b;
			} else if (bn.equals("forge")) {
				return EmiAgnos.isModLoaded("forge") ? b : a;
			}
		}
		return a.id().toString().length() <= b.id().toString().length() ? a : b;
	}
}

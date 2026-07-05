package dev.emi.emi.runtime;

import java.util.List;
import java.util.function.Consumer;

import com.google.common.collect.Lists;

import dev.emi.emi.EmiPort;
import dev.emi.emi.VanillaPlugin;
import dev.emi.emi.jemi.JemiPlugin;
import dev.emi.emi.platform.EmiAgnos;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiRegistryAdapter;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.FluidEmiStack;
import dev.emi.emi.api.stack.ItemEmiStack;
import dev.emi.emi.api.stack.ListEmiIngredient;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import dev.emi.emi.registry.EmiIngredientSerializers;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.registry.EmiRecipeSource;
import dev.emi.emi.registry.EmiRecipes;
import dev.emi.emi.registry.EmiRegistryImpl;
import dev.emi.emi.registry.EmiStackList;
import dev.emi.emi.registry.EmiTags;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.stack.serializer.FluidEmiStackSerializer;
import dev.emi.emi.stack.serializer.ItemEmiStackSerializer;
import dev.emi.emi.stack.serializer.ListEmiIngredientSerializer;
import dev.emi.emi.stack.serializer.TagEmiIngredientSerializer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

/**
 * The reload driver: registers the vanilla registry adapters, builds the stack index, then runs the
 * recipe phase (harvest the 26.2 recipe displays, run the plugins, bake the recipe indices). All of
 * it runs <b>after world load</b> so the {@code new ItemStack} calls are legal under the 26.1+
 * ItemStackTemplate lifecycle. The full {@code EmiReloadManager} (search worker, reload screen)
 * returns in later rounds.
 */
public class EmiReload {
	// Plugin discovery via loader entrypoints returns with a later round; the built-in vanilla
	// plugin plus (when JEI is installed) the jemi bridge.
	private static List<EmiPlugin> plugins() {
		List<EmiPlugin> plugins = Lists.newArrayList(new VanillaPlugin());
		if (EmiAgnos.isModLoaded("jei")) {
			// JemiPlugin may only be classloaded behind this gate: it implements JEI interfaces.
			plugins.add(new JemiPlugin());
		}
		return plugins;
	}

	private static boolean adaptersRegistered = false;
	private static boolean serializersRegistered = false;
	private static volatile boolean reloading = false;

	private static volatile boolean scheduled = false;

	/**
	 * Called from the loaders' client world-join events and from the jemi bridge when the JEI
	 * runtime arrives; defers the build onto the client thread. Back-to-back requests (world join
	 * racing JEI's own reload) coalesce into a single rebuild.
	 */
	public static void scheduleReload() {
		if (scheduled) {
			return;
		}
		scheduled = true;
		Minecraft.getInstance().execute(() -> {
			scheduled = false;
			run();
		});
	}

	public static void run() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			EmiLog.warn("Skipping index reload: world not ready (level/player null)");
			return;
		}
		if (reloading) {
			return;
		}
		reloading = true;
		try {
			registerAdapters();
			registerSerializers();
			long start = System.currentTimeMillis();
			EmiLog.info("Building stack index on world load...");
			EmiStackList.clear();
			EmiTags.reload();
			EmiStackList.reload();
			EmiStackList.bake();
			EmiScreenManager.reset();
			EmiLog.info("EmiStackList: " + EmiStackList.stacks.size() + " stacks (built in "
				+ (System.currentTimeMillis() - start) + "ms after world load)");
			reloadRecipes();
		} catch (Throwable t) {
			EmiLog.error("EMI failed to build the stack index", t);
		} finally {
			reloading = false;
		}
	}

	private static void reloadRecipes() {
		long start = System.currentTimeMillis();
		EmiRecipes.clear();
		EmiRecipeFiller.clear();
		EmiRecipeSource.clear();
		EmiRecipeSource.harvest();
		EmiRegistryImpl registry = new EmiRegistryImpl();
		for (EmiPlugin plugin : plugins()) {
			try {
				plugin.register(registry);
			} catch (Throwable t) {
				EmiLog.error("EMI plugin " + plugin.getClass().getName() + " failed to register", t);
			}
		}
		for (Consumer<Consumer<EmiRecipe>> late : List.copyOf(EmiRecipes.lateRecipes)) {
			try {
				late.accept(EmiRecipes::addRecipe);
			} catch (Throwable t) {
				EmiLog.error("EMI deferred recipes failed to register", t);
			}
		}
		EmiRecipes.bake();
		for (EmiRecipeCategory category : EmiRecipes.manager.getCategories()) {
			EmiLog.info("  " + category.getId() + ": " + EmiRecipes.manager.getRecipes(category).size() + " recipes");
		}
		EmiLog.info("Recipe phase done in " + (System.currentTimeMillis() - start) + "ms");
	}

	private static void registerAdapters() {
		if (adaptersRegistered) {
			return;
		}
		EmiTags.ADAPTERS_BY_CLASS.map().clear();
		EmiTags.ADAPTERS_BY_REGISTRY.clear();
		addAdapter(EmiRegistryAdapter.simple(Item.class, EmiPort.getItemRegistry(), EmiStack::of));
		addAdapter(EmiRegistryAdapter.simple(Fluid.class, EmiPort.getFluidRegistry(), EmiStack::of));
		adaptersRegistered = true;
	}

	private static void addAdapter(EmiRegistryAdapter<?> adapter) {
		EmiTags.ADAPTERS_BY_CLASS.map().put(adapter.getBaseClass(), adapter);
		EmiTags.ADAPTERS_BY_REGISTRY.put(adapter.getRegistry(), adapter);
	}

	private static void registerSerializers() {
		if (serializersRegistered) {
			return;
		}
		EmiIngredientSerializers.clear();
		addSerializer(ItemEmiStack.class, new ItemEmiStackSerializer());
		addSerializer(FluidEmiStack.class, new FluidEmiStackSerializer());
		addSerializer(TagEmiIngredient.class, new TagEmiIngredientSerializer());
		addSerializer(ListEmiIngredient.class, new ListEmiIngredientSerializer());
		serializersRegistered = true;
	}

	private static void addSerializer(Class<?> clazz, EmiIngredientSerializer<?> serializer) {
		EmiIngredientSerializers.BY_CLASS.put(clazz, serializer);
		EmiIngredientSerializers.BY_TYPE.put(serializer.getType(), serializer);
	}
}

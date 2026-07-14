package dev.emi.emi.runtime;

import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import com.google.common.collect.Lists;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiRegistryAdapter;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.FluidEmiStack;
import dev.emi.emi.api.stack.ItemEmiStack;
import dev.emi.emi.api.stack.ListEmiIngredient;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import dev.emi.emi.jemi.JemiPlugin;
import dev.emi.emi.platform.EmiAgnos;
import dev.emi.emi.registry.EmiComparisonDefaults;
import dev.emi.emi.registry.EmiIngredientSerializers;
import dev.emi.emi.registry.EmiPluginContainer;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.registry.EmiRecipeSource;
import dev.emi.emi.registry.EmiRecipes;
import dev.emi.emi.registry.EmiRegistryImpl;
import dev.emi.emi.registry.EmiStackList;
import dev.emi.emi.registry.EmiStackProviders;
import dev.emi.emi.registry.EmiTags;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.search.EmiSearch;
import dev.emi.emi.stack.serializer.FluidEmiStackSerializer;
import dev.emi.emi.stack.serializer.ItemEmiStackSerializer;
import dev.emi.emi.stack.serializer.ListEmiIngredientSerializer;
import dev.emi.emi.stack.serializer.TagEmiIngredientSerializer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

/**
 * The reload driver, back on the original's threading model: the whole rebuild (stack index →
 * recipe phase → search bake) runs on a background {@link ReloadWorker}, so joining a world no
 * longer stalls the render thread. A reload requested while the worker is alive restarts the same
 * worker via the restart flag instead of spawning a second pass — a dedicated server's recipe sync
 * landing mid-reload folds into the running rebuild. All rebuilt structures are published complete
 * (never mutated in place, see EmiStackList/EmiSearch), the one GUI step is marshalled onto the
 * render thread at the end, and the EMI UI stays hidden until {@link #isLoaded()}.
 */
public class EmiReloadManager {
	private static volatile boolean clear = false, restart = false;
	// 0 - empty, 1 - reloading, 2 - loaded, -1 - error
	private static volatile int status = 0;
	private static Thread thread;
	public static volatile Component reloadStep = EmiPort.literal("");
	public static volatile long reloadWorry = Long.MAX_VALUE;

	public static void clear() {
		synchronized (EmiReloadManager.class) {
			clear = true;
			status = 0;
			reloadWorry = Long.MAX_VALUE;
			if (thread != null && thread.isAlive()) {
				restart = true;
			} else {
				thread = new Thread(new ReloadWorker());
				thread.setDaemon(true);
				thread.start();
			}
		}
	}

	public static void reload() {
		synchronized (EmiReloadManager.class) {
			step(EmiPort.literal("Starting Reload"));
			status = 1;
			if (thread != null && thread.isAlive()) {
				restart = true;
			} else {
				clear = false;
				// Non-daemon: an exiting JVM must not tear the worker down mid-publish.
				thread = new Thread(new ReloadWorker());
				thread.setDaemon(false);
				thread.start();
			}
		}
	}

	public static void step(Component text) {
		step(text, 5_000);
	}

	public static void step(Component text, long worry) {
		EmiLog.info(text.getString());
		reloadStep = text;
		reloadWorry = System.currentTimeMillis() + worry;
	}

	public static boolean isLoaded() {
		return status == 2 && (thread == null || !thread.isAlive());
	}

	public static int getStatus() {
		return status;
	}

	/**
	 * The plugins to load: discovered through the loader (the {@code "emi"} entrypoint on Fabric,
	 * the {@code EmiEntrypoint} annotation scan on NeoForge), EMI's own first, plus the jemi
	 * bridge when JEI is installed. Each is attributed to the mod that provided it, so a broken
	 * third-party plugin fails with its own mod id and the reload continues.
	 */
	private static List<EmiPluginContainer> plugins() {
		List<EmiPluginContainer> plugins = Lists.newArrayList(EmiAgnos.getPlugins().stream()
			.sorted((a, b) -> Integer.compare(entrypointPriority(a), entrypointPriority(b))).toList());
		if (EmiAgnos.isModLoaded("jei")) {
			// JemiPlugin may only be classloaded behind this gate: it implements JEI interfaces.
			plugins.add(new EmiPluginContainer(new JemiPlugin(), "jemi"));
		}
		return plugins;
	}

	private static int entrypointPriority(EmiPluginContainer container) {
		return container.id().equals("emi") ? 0 : 1;
	}

	private static boolean adaptersRegistered = false;
	private static boolean serializersRegistered = false;

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

	private static class ReloadWorker implements Runnable {

		@Override
		public void run() {
			int retries = 3;
			outer:
			do {
				try {
					if (!clear) {
						EmiLog.info("Starting EMI reload...");
					}
					long reloadStart = System.currentTimeMillis();
					restart = false;
					step(EmiPort.literal("Clearing data"));
					EmiRecipes.clear();
					EmiStackList.clear();
					EmiRecipeFiller.clear();
					EmiStackProviders.clear();
					EmiRecipeSource.clear();
					if (clear) {
						clear = false;
						continue;
					}
					Minecraft client = Minecraft.getInstance();
					if (client.level == null || client.player == null) {
						EmiLog.warn("Skipping index reload: world not ready (level/player null)");
						break;
					}
					registerAdapters();
					registerSerializers();
					step(EmiPort.literal("Processing tags"));
					EmiTags.reload();
					step(EmiPort.literal("Constructing index"));
					long indexStart = System.currentTimeMillis();
					EmiStackList.reload();
					if (restart) {
						continue;
					}
					step(EmiPort.literal("Baking index"));
					EmiStackList.bake();
					EmiLog.info("EmiStackList: " + EmiStackList.stacks.size() + " stacks (built in "
						+ (System.currentTimeMillis() - indexStart) + "ms after world load)");
					if (restart) {
						continue;
					}
					long recipeStart = System.currentTimeMillis();
					EmiRecipeSource.harvest();
					EmiComparisonDefaults.comparisons = new HashMap<>();
					EmiRegistryImpl registry = new EmiRegistryImpl();
					// The original also runs an initialize(EmiInitRegistry) pre-registration phase
					// over the containers; that registry surface returns with the plugin-API round.
					for (EmiPluginContainer container : plugins()) {
						step(EmiPort.literal("Loading plugin from " + container.id()), 10_000);
						long start = System.currentTimeMillis();
						try {
							container.plugin().register(registry);
						} catch (Throwable t) {
							// Catches Throwable on purpose: a plugin calling API this port has not
							// restored yet dies with a LinkageError, not an Exception, and one broken
							// plugin must not take down the reload.
							EmiLog.error("Exception loading plugin provided by " + container.id(), t);
							if (restart) {
								continue outer;
							}
							continue;
						}
						EmiLog.info("Reloaded plugin from " + container.id() + " in "
							+ (System.currentTimeMillis() - start) + "ms");
						if (restart) {
							continue outer;
						}
					}
					step(EmiPort.literal("Registering late recipes"), 10_000);
					for (Consumer<Consumer<EmiRecipe>> late : List.copyOf(EmiRecipes.lateRecipes)) {
						try {
							late.accept(EmiRecipes::addRecipe);
						} catch (Throwable t) {
							EmiLog.error("EMI deferred recipes failed to register", t);
							if (restart) {
								continue outer;
							}
						}
					}
					step(EmiPort.literal("Baking recipes"), 15_000);
					EmiRecipes.bake();
					// After bake so favorite recipe ids can resolve against the recipe manager, as the original
					// loads persistent data at the end of its reload.
					EmiPersistentData.load();
					for (EmiRecipeCategory category : EmiRecipes.manager.getCategories()) {
						EmiLog.info("  " + category.getId() + ": " + EmiRecipes.manager.getRecipes(category).size() + " recipes");
					}
					EmiLog.info("Recipe phase done in " + (System.currentTimeMillis() - recipeStart) + "ms");
					if (restart) {
						continue;
					}
					step(EmiPort.literal("Baking search"), 15_000);
					long searchStart = System.currentTimeMillis();
					EmiSearch.bake();
					EmiLog.info("Search index baked in " + (System.currentTimeMillis() - searchStart) + "ms");
					step(EmiPort.literal("Finishing up"));
					// The one main-thread step: EmiScreenManager.reset touches GUI state (search box,
					// panel pages). Everything above published complete structures the render thread can
					// already see, so the render-thread cost of a reload is only this apply.
					client.execute(() -> {
						long applyStart = System.currentTimeMillis();
						EmiScreenManager.reset();
						EmiLog.info("Applied reload results on the render thread in "
							+ (System.currentTimeMillis() - applyStart) + "ms");
					});
					EmiLog.info("Reloaded EMI in " + (System.currentTimeMillis() - reloadStart) + "ms on the reload worker");
					status = 2;
				} catch (Throwable e) {
					EmiLog.error("Critical error occured during reload:", e);
					status = -1;
					if (retries-- > 0) {
						restart = true;
					}
				}
			} while (restart);
			thread = null;
		}
	}
}

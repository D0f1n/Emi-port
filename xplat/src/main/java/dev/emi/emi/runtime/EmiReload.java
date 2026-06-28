package dev.emi.emi.runtime;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.stack.EmiRegistryAdapter;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.FluidEmiStack;
import dev.emi.emi.api.stack.ItemEmiStack;
import dev.emi.emi.api.stack.ListEmiIngredient;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import dev.emi.emi.registry.EmiIngredientSerializers;
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
 * Minimal Stage 3 reload driver. The full {@code EmiReloadManager} (plugins, recipes, search, background
 * worker) returns in later rounds. Here we only register the vanilla registry adapters and build the
 * stack index, and crucially we do it <b>after world load</b> so the {@code new ItemStack} calls inside
 * {@link EmiStackList#reload()} are legal under the 26.1+ ItemStackTemplate lifecycle.
 */
public class EmiReload {
	private static boolean adaptersRegistered = false;
	private static boolean serializersRegistered = false;
	private static volatile boolean reloading = false;

	/** Called from the loaders' client world-join events; defers the build onto the client thread. */
	public static void scheduleReload() {
		Minecraft.getInstance().execute(EmiReload::run);
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
		} catch (Throwable t) {
			EmiLog.error("EMI failed to build the stack index", t);
		} finally {
			reloading = false;
		}
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

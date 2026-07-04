package dev.emi.emi.registry;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import dev.emi.emi.EmiPort;
import dev.emi.emi.network.RecipeSyncS2CPacket;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.runtime.EmiSyncedRecipes;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

/**
 * The 26.2 recipe reading model. Client-side {@code RecipeManager} enumeration was removed in
 * 1.21.2; recipes are server-side and reach the client as {@link RecipeDisplay} data. EMI reads
 * them through two paths that feed the same display-to-recipe mapping layer:
 *
 * <ul>
 * <li><b>Integrated server (primary):</b> in singleplayer the full server {@code RecipeManager} is
 * in-process, so every {@link RecipeHolder} is enumerated directly and its {@code Recipe#display()}
 * data is used — no networking involved.</li>
 * <li><b>EMI server sync:</b> on a dedicated server with EMI, the server pushes the full recipe
 * set (the same {@code Recipe#display()} data) over EMI's own channel on join.</li>
 * <li><b>Client recipe book (fallback):</b> on a dedicated server without EMI, only the unlocked
 * recipes synced into the client recipe book are visible.</li>
 * </ul>
 */
public class EmiRecipeSource {

	/**
	 * One harvested display. {@code type} is the vanilla recipe type when known (integrated server
	 * path); null on the fallback path, where category mapping falls back to the display's
	 * crafting station.
	 */
	public record HarvestedRecipe(Identifier id, RecipeDisplay display, @Nullable RecipeType<?> type) {
	}

	public static List<HarvestedRecipe> recipes = List.of();
	private static Map<Identifier, RecipeHolder<?>> byId = Map.of();
	/** Whether the full server recipe set is visible (integrated server), or only unlocked recipes. */
	public static boolean fullView = false;

	public static void clear() {
		recipes = List.of();
		byId = Map.of();
		fullView = false;
	}

	public static void harvest() {
		List<HarvestedRecipe> list = Lists.newArrayList();
		Map<Identifier, RecipeHolder<?>> ids = Maps.newHashMap();
		Minecraft client = Minecraft.getInstance();
		MinecraftServer server = client.getSingleplayerServer();
		String source;
		if (server != null) {
			fullView = true;
			source = "integrated server";
			for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
				Identifier id = holder.id().identifier();
				ids.put(id, holder);
				try {
					List<RecipeDisplay> displays = holder.value().display();
					for (int i = 0; i < displays.size(); i++) {
						// Recipes with several displays (e.g. special crafting variants) get unique
						// synthetic ids past the first, per EMI's synthetic id convention.
						Identifier displayId = i == 0 ? id
							: EmiPort.id("emi", "/" + id.getNamespace() + "/" + id.getPath() + "/" + i);
						list.add(new HarvestedRecipe(displayId, displays.get(i), holder.value().getType()));
					}
				} catch (Exception e) {
					EmiLog.warn("Failed to read displays for recipe " + id, e);
				}
			}
		} else if (EmiSyncedRecipes.hasSync()) {
			// A dedicated server with EMI pushed the full recipe set over EMI's own channel.
			fullView = true;
			source = "server sync";
			for (RecipeSyncS2CPacket.Entry entry : EmiSyncedRecipes.get()) {
				Identifier id = entry.id();
				RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(entry.typeId())
					.map(Holder.Reference::value).orElse(null);
				List<RecipeDisplay> displays = entry.displays();
				for (int i = 0; i < displays.size(); i++) {
					Identifier displayId = i == 0 ? id
						: EmiPort.id("emi", "/" + id.getNamespace() + "/" + id.getPath() + "/" + i);
					list.add(new HarvestedRecipe(displayId, displays.get(i), type));
				}
			}
		} else if (client.player != null) {
			fullView = false;
			source = "client recipe book";
			IntSet seen = new IntOpenHashSet();
			for (RecipeCollection collection : client.player.getRecipeBook().getCollections()) {
				for (RecipeDisplayEntry entry : collection.getRecipes()) {
					if (!seen.add(entry.id().index())) {
						continue;
					}
					// No stable recipe id reaches the client; use a synthetic id from the display id.
					Identifier id = EmiPort.id("emi", "/synced/" + entry.id().index());
					list.add(new HarvestedRecipe(id, entry.display(), null));
				}
			}
		} else {
			source = "nothing (world not ready)";
		}
		recipes = list;
		byId = ids;
		EmiLog.info("Harvested " + list.size() + " recipe displays from the " + source);
	}

	/** The vanilla recipe for an id, when the full server view is available. */
	public static @Nullable RecipeHolder<?> getRecipe(@Nullable Identifier id) {
		if (id == null) {
			return null;
		}
		return byId.get(id);
	}
}

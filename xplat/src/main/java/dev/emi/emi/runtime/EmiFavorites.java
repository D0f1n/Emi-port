package dev.emi.emi.runtime;

import java.util.AbstractList;
import java.util.List;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.ItemEmiStack;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import net.minecraft.util.GsonHelper;

public class EmiFavorites {
	public static List<EmiFavorite> favorites = Lists.newArrayList();
	// Populated by the BoM crafting mode in the original; stays empty until BoM is ported. TODO(bom)
	public static List<EmiFavorite> syntheticFavorites = Lists.newArrayList();
	public static List<EmiFavorite> favoriteSidebar = new CompoundList<>(favorites, syntheticFavorites);

	public static JsonArray save() {
		JsonArray arr = new JsonArray();
		for (EmiFavorite fav : favorites) {
			JsonElement stack = EmiIngredientSerializer.getSerialized(fav.getStack());
			if (stack != null) {
				JsonObject obj = new JsonObject();
				obj.add("stack", stack);
				if (fav.getRecipe() != null && fav.getRecipe().getId() != null) {
					obj.addProperty("recipe", fav.getRecipe().getId().toString());
				}
				arr.add(obj);
			}
		}
		return arr;
	}

	public static void load(JsonArray arr) {
		favorites.clear();
		for (JsonElement el : arr) {
			if (el.isJsonObject()) {
				JsonObject json = el.getAsJsonObject();
				EmiRecipe recipe = null;
				if (GsonHelper.isStringValue(json, "recipe")) {
					recipe = EmiApi.getRecipeManager().getRecipe(EmiPort.id(GsonHelper.getAsString(json, "recipe")));
				}
				if (GsonHelper.isValidNode(json, "stack")) {
					EmiIngredient ingredient = EmiIngredientSerializer.getDeserialized(json.get("stack"));
					if (ingredient.isEmpty()) {
						continue;
					}
					if (ingredient instanceof EmiStack es) {
						ingredient = es.copy();
					}
					favorites.add(new EmiFavorite(ingredient, recipe));
				}
			}
		}
	}

	public static boolean canFavorite(EmiIngredient stack, EmiRecipe recipe) {
		stack = EmiIngredientSerializer.getDeserialized(EmiIngredientSerializer.getSerialized(stack));
		if (stack.isEmpty()) {
			return false;
		}
		if (recipe != null) {
			return recipe.getId() != null;
		}
		return true;
	}

	private static int indexOf(EmiIngredient stack) {
		for (int i = 0; i < favorites.size(); i++) {
			if (favorites.get(i).strictEquals(stack) && favorites.get(i).getRecipe() == EmiApi.getRecipeContext(stack)) {
				return i;
			}
		}
		return -1;
	}

	public static boolean removeFavorite(EmiIngredient stack) {
		int index = indexOf(stack);
		if (index != -1) {
			favorites.remove(index);
			return true;
		}
		return false;
	}

	public static void addFavorite(EmiIngredient stack) {
		addFavorite(stack, null);
	}

	public static void addFavoriteAt(EmiIngredient stack, int offset) {
		// The original bails on EmiFavorite.Synthetic here; synthetics return with BoM. TODO(bom)
		if (stack instanceof EmiFavorite.Craftable craftable) {
			stack = craftable.stack;
		}
		EmiFavorite favorite;
		if (stack instanceof EmiFavorite fav) {
			int original = indexOf(stack);
			if (original != -1) {
				if (original < offset) {
					offset--;
				}
				favorites.remove(original);
			}
			favorite = fav;
		} else {
			stack = EmiIngredientSerializer.getDeserialized(EmiIngredientSerializer.getSerialized(stack));
			if (stack.isEmpty()) {
				return;
			}
			for (int i = 0; i < favorites.size(); i++) {
				EmiFavorite fav = favorites.get(i);
				if (fav.getRecipe() == null && fav.strictEquals(stack)) {
					favorites.remove(i--);
				}
			}
			favorite = new EmiFavorite(stack, null);
		}
		if (offset < 0) {
			offset = 0;
		}
		if (offset >= favorites.size()) {
			favorites.add(favorite);
		} else {
			favorites.add(offset, favorite);
		}
		EmiPersistentData.save();
	}

	public static void addFavorite(EmiIngredient stack, EmiRecipe context) {
		// The original bails on EmiFavorite.Synthetic here; synthetics return with BoM. TODO(bom)
		if (stack instanceof EmiFavorite.Craftable craftable) {
			stack = craftable.stack;
		}
		if (stack instanceof EmiFavorite f) {
			if (!removeFavorite(f)) {
				favorites.add(f);
			}
		} else {
			stack = EmiIngredientSerializer.getDeserialized(EmiIngredientSerializer.getSerialized(stack));
			if (stack instanceof EmiStack es && context != null && context.getId() != null) {
				es = es.copy();
				if (es instanceof ItemEmiStack ies) {
					ies.getItemStack().setCount(1);
				}
				if (!es.isEmpty()) {
					for (int i = 0; i < favorites.size(); i++) {
						EmiFavorite fav = favorites.get(i);
						if (fav.getRecipe() == context && fav.strictEquals(es)) {
							return;
						}
					}
					favorites.add(new EmiFavorite(es, context));
				}
			} else {
				if (stack.isEmpty()) {
					return;
				}
				for (int i = 0; i < favorites.size(); i++) {
					EmiFavorite fav = favorites.get(i);
					if (fav.getRecipe() == null && fav.strictEquals(stack)) {
						return;
					}
				}
				favorites.add(new EmiFavorite(stack, null));
			}
		}
		EmiPersistentData.save();
	}

	// The original recalculates BoM-derived synthetic favorites from the player inventory here;
	// no-op until the recipe tree is ported. TODO(bom)
	public static void updateSynthetic() {
		syntheticFavorites.clear();
	}

	private static class CompoundList<T> extends AbstractList<T> {
		private List<? extends T> a, b;

		public CompoundList(List<? extends T> a, List<? extends T> b) {
			this.a = a;
			this.b = b;
		}

		@Override
		public T get(int index) {
			if (index >= a.size()) {
				return b.get(index - a.size());
			}
			return a.get(index);
		}

		@Override
		public int size() {
			return a.size() + b.size();
		}
	}
}

package dev.emi.emi.data;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.google.common.collect.Maps;
import com.google.gson.JsonObject;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeSorting;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import net.minecraft.util.GsonHelper;

/**
 * Static registry for datapack-driven EMI data, loaded from {@code assets/emi/**} by the
 * reload listeners constructed in {@link #init}.
 *
 * <p>Unlike the original, the fields are volatile: the resource reload publishes them from its
 * apply thread while the EMI reload worker reads them, so publication must be safe. Values are
 * always replaced wholesale with effectively-immutable contents.
 */
public class EmiData {
	public static volatile Map<String, EmiRecipeCategoryProperties> categoryPriorities = Map.of();
	public static volatile List<Predicate<EmiRecipe>> recipeFilters = List.of();
	public static volatile List<Supplier<EmiRecipe>> recipes = List.of();

	public static void init(Consumer<EmiResourceReloadListener> register) {
		register.accept(new EmiTagExclusionsLoader());
		register.accept(
			new EmiDataLoader<Map<String, EmiRecipeCategoryProperties>>(
				EmiPort.id("emi:category_properties"), "category/properties", Maps::newHashMap,
				(map, json, id) -> {
					for (String k : json.keySet()) {
						if (GsonHelper.isObjectNode(json, k)) {
							EmiRecipeCategoryProperties props = map.computeIfAbsent(k, s -> new EmiRecipeCategoryProperties());
							JsonObject val = json.getAsJsonObject(k);
							if (GsonHelper.isNumberValue(val, "order")) {
								props.order = val.get("order").getAsInt();
							}
							if (GsonHelper.isObjectNode(val, "icon")) {
								JsonObject icon = val.getAsJsonObject("icon");
								if (GsonHelper.isStringValue(icon, "texture")) {
									props.icon = () -> new EmiTexture(EmiPort.id(GsonHelper.getAsString(icon, "texture")), 0, 0, 16, 16, 16, 16, 16, 16);
								} else if (GsonHelper.isStringValue(icon, "stack")) {
									props.icon = () -> EmiIngredientSerializer.getDeserialized(icon.get("stack"));
								}
							}
							if (GsonHelper.isObjectNode(val, "simplified_icon")) {
								JsonObject icon = val.getAsJsonObject("simplified_icon");
								if (GsonHelper.isStringValue(icon, "texture")) {
									props.simplified = () -> new EmiTexture(EmiPort.id(GsonHelper.getAsString(icon, "texture")), 0, 0, 16, 16, 16, 16, 16, 16);
								} else if (GsonHelper.isStringValue(icon, "stack")) {
									props.simplified = () -> EmiIngredientSerializer.getDeserialized(icon.get("stack"));
								}
							}
							if (GsonHelper.isStringValue(val, "sort")) {
								switch (GsonHelper.getAsString(val, "sort")) {
									case "none":
										props.sort = EmiRecipeSorting.none();
										break;
									case "input_then_output":
										props.sort = EmiRecipeSorting.compareInputThenOutput();
										break;
									case "output_then_input":
										props.sort = EmiRecipeSorting.compareOutputThenInput();
										break;
									case "identifier":
										props.sort = EmiRecipeSorting.identifier();
										break;
								}
							}
						}
					}
				}, map -> categoryPriorities = map));
	}
}

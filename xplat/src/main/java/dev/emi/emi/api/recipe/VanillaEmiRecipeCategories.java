package dev.emi.emi.api.recipe;

/**
 * The vanilla recipe categories EMI ships. Populated by the built-in vanilla plugin during
 * registration (post world load, so category icons can create item stacks legally under the
 * 26.1+ ItemStackTemplate lifecycle).
 */
public class VanillaEmiRecipeCategories {
	public static EmiRecipeCategory CRAFTING;
	public static EmiRecipeCategory SMELTING;
	public static EmiRecipeCategory BLASTING;
	public static EmiRecipeCategory SMOKING;
	public static EmiRecipeCategory CAMPFIRE_COOKING;
	public static EmiRecipeCategory STONECUTTING;
	public static EmiRecipeCategory SMITHING;
	public static EmiRecipeCategory ANVIL_REPAIRING;
	public static EmiRecipeCategory GRINDING;
	public static EmiRecipeCategory BREWING;
	public static EmiRecipeCategory WORLD_INTERACTION;
	public static EmiRecipeCategory FUEL;
	public static EmiRecipeCategory COMPOSTING;
	public static EmiRecipeCategory INFO;
}

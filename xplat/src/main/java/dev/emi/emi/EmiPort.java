package dev.emi.emi;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiRecipeSource;
import dev.emi.emi.runtime.EmiLog;

/**
 * Multiversion quarantine, to avoid excessive git pain.
 *
 * <p>Data side (Stage 3) plus the recipe layer for 26.2: slot display resolution and recipe
 * lookup against the harvested server recipes. Render/shader/buffer helpers from the original
 * remain with the render rounds.
 */
public final class EmiPort {

	public static MutableComponent literal(String s) {
		return Component.literal(s);
	}

	public static MutableComponent literal(String s, ChatFormatting formatting) {
		return Component.literal(s).withStyle(formatting);
	}

	public static MutableComponent literal(String s, ChatFormatting... formatting) {
		return Component.literal(s).withStyle(formatting);
	}

	public static MutableComponent literal(String s, Style style) {
		return Component.literal(s).setStyle(style);
	}

	public static MutableComponent translatable(String s) {
		return Component.translatable(s);
	}

	public static MutableComponent translatable(String s, ChatFormatting formatting) {
		return Component.translatable(s).withStyle(formatting);
	}

	public static MutableComponent translatable(String s, Object... objects) {
		return Component.translatable(s, objects);
	}

	public static MutableComponent append(MutableComponent text, Component appended) {
		return text.append(appended);
	}

	public static FormattedCharSequence ordered(Component text) {
		return text.getVisualOrderText();
	}

	public static Registry<Item> getItemRegistry() {
		return BuiltInRegistries.ITEM;
	}

	public static Registry<Block> getBlockRegistry() {
		return BuiltInRegistries.BLOCK;
	}

	public static Registry<Fluid> getFluidRegistry() {
		return BuiltInRegistries.FLUID;
	}

	/**
	 * The dynamic registry access for the loaded world. Only valid post-world-load.
	 */
	public static RegistryAccess getRegistryAccess() {
		return Minecraft.getInstance().level.registryAccess();
	}

	public static Comparison compareStrict() {
		return Comparison.compareComponents();
	}

	public static DataComponentPatch emptyExtraData() {
		return DataComponentPatch.EMPTY;
	}

	public static Identifier id(String id) {
		return Identifier.parse(id);
	}

	public static Identifier id(String namespace, String path) {
		return Identifier.fromNamespaceAndPath(namespace, path);
	}

	// --- recipe layer (26.2) ---

	/**
	 * The vanilla recipe backing an EMI recipe id, when the full server view is available
	 * (integrated server; a dedicated server without EMI only syncs displays).
	 */
	public static @Nullable RecipeHolder<?> getRecipe(@Nullable Identifier id) {
		return EmiRecipeSource.getRecipe(id);
	}

	/**
	 * Converts a 26.2 {@link SlotDisplay} into an EMI ingredient. This is the single mapping layer
	 * shared by the integrated-server path and the synced-display fallback. Known display shapes
	 * convert structurally (items, stacks, tags, composites, remainders); anything else falls back
	 * to vanilla's own resolution against the level context.
	 */
	public static EmiIngredient ofSlotDisplay(SlotDisplay display) {
		return switch (display) {
			case SlotDisplay.Empty unused -> EmiStack.EMPTY;
			case SlotDisplay.ItemSlotDisplay d -> EmiStack.of(d.item().value());
			case SlotDisplay.ItemStackSlotDisplay d -> EmiStack.of(d.stack().create());
			case SlotDisplay.TagSlotDisplay d -> EmiIngredient.of(d.tag());
			case SlotDisplay.WithRemainder d -> {
				EmiIngredient inner = ofSlotDisplay(d.input());
				EmiIngredient remainder = ofSlotDisplay(d.remainder());
				if (!remainder.isEmpty()) {
					EmiStack remainderStack = remainder.getEmiStacks().get(0);
					for (EmiStack stack : inner.getEmiStacks()) {
						stack.setRemainder(remainderStack);
					}
				}
				yield inner;
			}
			case SlotDisplay.Composite d ->
				EmiIngredient.of(d.contents().stream().map(EmiPort::ofSlotDisplay).toList());
			// Fuel slots are drawn by EMI as the flame animation, not as an item cycle.
			case SlotDisplay.AnyFuel unused -> EmiStack.EMPTY;
			default -> resolveSlotDisplay(display);
		};
	}

	/** The first stack of a converted slot display; used for recipe results. */
	public static EmiStack ofSlotDisplayStack(SlotDisplay display) {
		return ofSlotDisplay(display).getEmiStacks().get(0);
	}

	private static EmiIngredient resolveSlotDisplay(SlotDisplay display) {
		Level level = Minecraft.getInstance().level;
		if (level == null) {
			return EmiStack.EMPTY;
		}
		try {
			List<ItemStack> stacks = display.resolveForStacks(SlotDisplayContext.fromLevel(level));
			return EmiIngredient.of(stacks.stream().map(EmiStack::of).toList());
		} catch (Exception e) {
			EmiLog.warn("Failed to resolve slot display " + display, e);
			return EmiStack.EMPTY;
		}
	}
}

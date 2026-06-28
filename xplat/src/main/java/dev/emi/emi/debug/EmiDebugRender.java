package dev.emi.emi.debug;

import java.util.ArrayList;
import java.util.List;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiDrawContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Temporary debug render harness — proves the render primitives draw on screen (item icons incl. a modded
 * one, the tag-icon fallback, and a sample tooltip) and, since the mixin round, that they can be positioned
 * relative to the GUI via the container-screen accessor. Triggered from {@code AbstractContainerScreenMixin}
 * (a column drawn beside the inventory). Self-contained: the screen round removes it by deleting
 * {@code dev.emi.emi.debug} and the call from the screen mixin.
 *
 * <p>TODO(screen): remove debug render harness.
 */
public final class EmiDebugRender {

	private EmiDebugRender() {
	}

	/** Draws the sample column with its top-left at (baseX, baseY) — fed the GUI geometry by the mixin. */
	public static void renderDebug(GuiGraphicsExtractor draw, int baseX, int baseY) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		EmiDrawContext context = EmiDrawContext.wrap(draw);
		List<EmiIngredient> samples = pickSamples();
		int y = baseY;
		for (EmiIngredient sample : samples) {
			context.drawStack(sample, baseX, y, EmiIngredient.RENDER_ICON | EmiIngredient.RENDER_AMOUNT
				| EmiIngredient.RENDER_INGREDIENT);
			y += 20;
		}
		// Sample tooltip for the first stack, drawn immediately (the deferred setTooltipForNextFrame
		// mechanism is not reliably flushed inside the screen). Uses EmiStack#getTooltip(), exercising
		// the ClientTooltipComponent path on top of getTooltipText().
		if (!samples.isEmpty()) {
			List<ClientTooltipComponent> tip = samples.get(0).getEmiStacks().get(0).getTooltip();
			if (!tip.isEmpty()) {
				draw.tooltip(client.font, tip, baseX, y + 2, DefaultTooltipPositioner.INSTANCE, null);
			}
		}
	}

	private static List<EmiIngredient> pickSamples() {
		List<EmiIngredient> list = new ArrayList<>();
		list.add(EmiStack.of(Items.DIAMOND));
		list.add(EmiStack.of(Items.GOLD_INGOT));
		list.add(EmiStack.of(Items.OAK_LOG));
		// A modded item, whichever content mod is present (Farmer's Delight on Fabric / Ecologics on
		// NeoForge): first registered item outside the minecraft/emi namespaces. Skipped if none.
		for (Item item : BuiltInRegistries.ITEM) {
			Identifier id = BuiltInRegistries.ITEM.getKey(item);
			String ns = id.getNamespace();
			if (!ns.equals("minecraft") && !ns.equals("emi")) {
				list.add(EmiStack.of(item));
				break;
			}
		}
		// A tag ingredient, to exercise the tag-icon fallback (first stack + corner overlay).
		list.add(EmiIngredient.of(ItemTags.PLANKS));
		return list;
	}
}

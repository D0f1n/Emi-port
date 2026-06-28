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
 * Temporary debug render harness — proves the Stage 4 render primitives draw on screen (item icons incl.
 * a modded one, the tag-icon fallback, and a sample tooltip). It is intentionally self-contained in this
 * package: the screen round removes it by deleting {@code dev.emi.emi.debug}, the two loader hook classes
 * ({@code EmiDebugHudFabric}/{@code EmiDebugHudNeoForge}), and the single marked call line in
 * {@code EmiClientFabric}.
 *
 * <p>TODO(screen): remove debug render harness.
 */
public final class EmiDebugRender {

	private EmiDebugRender() {
	}

	public static void renderDebug(GuiGraphicsExtractor draw) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		EmiDrawContext context = EmiDrawContext.wrap(draw);
		List<EmiIngredient> samples = pickSamples();
		int x = 8;
		int y = 8;
		for (EmiIngredient sample : samples) {
			context.drawStack(sample, x, y, EmiIngredient.RENDER_ICON | EmiIngredient.RENDER_AMOUNT
				| EmiIngredient.RENDER_INGREDIENT);
			x += 20;
		}
		// Sample tooltip for the first stack, drawn immediately (the deferred setTooltipForNextFrame
		// mechanism is not reliably flushed in the in-game HUD). Uses EmiStack#getTooltip(), exercising
		// the ClientTooltipComponent path on top of getTooltipText().
		if (!samples.isEmpty()) {
			List<ClientTooltipComponent> tip = samples.get(0).getEmiStacks().get(0).getTooltip();
			if (!tip.isEmpty()) {
				draw.tooltip(client.font, tip, 8, 34, DefaultTooltipPositioner.INSTANCE, null);
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

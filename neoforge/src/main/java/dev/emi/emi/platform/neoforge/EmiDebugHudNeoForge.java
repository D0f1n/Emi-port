package dev.emi.emi.platform.neoforge;

import dev.emi.emi.debug.EmiDebugRender;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Temporary debug render harness — NeoForge HUD hook. Auto-registers via {@link EventBusSubscriber}, so
 * removal needs no edit to working code: just delete this class. TODO(screen): remove debug render harness.
 */
@EventBusSubscriber(modid = "emi", value = Dist.CLIENT)
public final class EmiDebugHudNeoForge {

	private EmiDebugHudNeoForge() {
	}

	@SubscribeEvent
	public static void onRenderGui(RenderGuiEvent.Post event) {
		EmiDebugRender.renderDebug(event.getGuiGraphics());
	}
}

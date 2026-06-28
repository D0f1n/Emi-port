package dev.emi.emi.platform.fabric;

import dev.emi.emi.EmiPort;
import dev.emi.emi.debug.EmiDebugRender;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

/**
 * Temporary debug render harness — Fabric HUD hook. TODO(screen): remove debug render harness (delete
 * this class + the call in {@link EmiClientFabric}).
 */
public final class EmiDebugHudFabric {

	private EmiDebugHudFabric() {
	}

	public static void register() {
		HudElementRegistry.addLast(EmiPort.id("emi", "debug_render"),
			(graphics, delta) -> EmiDebugRender.renderDebug(graphics));
	}
}

package dev.emi.emi.platform.fabric;

import dev.emi.emi.platform.EmiClient;
import dev.emi.emi.runtime.EmiReload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class EmiClientFabric implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		EmiClient.init();
		// Build the stack index on world join (post-world-load), mirroring REI's "entries built on world
		// join" timing. The actual build is deferred onto the client thread inside EmiReload.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> EmiReload.scheduleReload());
		EmiDebugHudFabric.register(); // TODO(screen): remove debug render harness
	}
}

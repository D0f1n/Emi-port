package dev.emi.emi.platform.fabric;

import dev.emi.emi.network.EmiNetwork;
import dev.emi.emi.network.EmiPacket;
import dev.emi.emi.platform.EmiClient;
import dev.emi.emi.runtime.EmiReload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class EmiClientFabric implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		EmiClient.init();
		EmiNetwork.initClient(packet -> {
			if (ClientPlayNetworking.canSend(packet.type())) {
				ClientPlayNetworking.send(packet);
			}
		});
		registerClientReceiver(EmiNetwork.PING);
		registerClientReceiver(EmiNetwork.SYNC_RECIPES);
		// Build the stack index on world join (post-world-load), mirroring REI's "entries built on world
		// join" timing. The actual build is deferred onto the client thread inside EmiReload.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> EmiReload.scheduleReload());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> EmiClient.onDisconnect());
	}

	private static <T extends EmiPacket> void registerClientReceiver(CustomPacketPayload.Type<T> type) {
		ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
			context.client().execute(() -> payload.apply(context.player())));
	}
}

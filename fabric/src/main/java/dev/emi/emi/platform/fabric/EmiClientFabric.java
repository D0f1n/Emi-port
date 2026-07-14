package dev.emi.emi.platform.fabric;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import dev.emi.emi.data.EmiData;
import dev.emi.emi.network.EmiNetwork;
import dev.emi.emi.network.EmiPacket;
import dev.emi.emi.platform.EmiClient;
import dev.emi.emi.runtime.EmiReloadManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

public class EmiClientFabric implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		EmiClient.init();
		EmiData.init(reloader -> {
			ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new IdentifiableResourceReloadListener() {
				@Override
				public CompletableFuture<Void> reload(SharedState state, Executor prepareExecutor,
						PreparationBarrier barrier, Executor applyExecutor) {
					return reloader.reload(state, prepareExecutor, barrier, applyExecutor);
				}

				@Override
				public String getName() {
					return reloader.getName();
				}

				@Override
				public Identifier getFabricId() {
					return reloader.getEmiId();
				}
			});
		});
		EmiNetwork.initClient(packet -> {
			if (ClientPlayNetworking.canSend(packet.type())) {
				ClientPlayNetworking.send(packet);
			}
		});
		registerClientReceiver(EmiNetwork.PING);
		registerClientReceiver(EmiNetwork.COMMAND);
		registerClientReceiver(EmiNetwork.SYNC_RECIPES);
		// Build the stack index on world join (post-world-load), mirroring REI's "entries built on world
		// join" timing. The build runs on the reload worker thread inside EmiReloadManager.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> EmiReloadManager.reload());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> EmiClient.onDisconnect());
	}

	private static <T extends EmiPacket> void registerClientReceiver(CustomPacketPayload.Type<T> type) {
		ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
			context.client().execute(() -> payload.apply(context.player())));
	}
}

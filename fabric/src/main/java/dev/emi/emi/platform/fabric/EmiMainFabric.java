package dev.emi.emi.platform.fabric;

import dev.emi.emi.network.CommandS2CPacket;
import dev.emi.emi.network.CreateItemC2SPacket;
import dev.emi.emi.network.EmiNetwork;
import dev.emi.emi.network.EmiPacket;
import dev.emi.emi.network.FillRecipeC2SPacket;
import dev.emi.emi.network.PingS2CPacket;
import dev.emi.emi.network.RecipeSyncS2CPacket;
import dev.emi.emi.platform.EmiMain;
import dev.emi.emi.platform.EmiServer;
import dev.emi.emi.registry.EmiCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class EmiMainFabric implements ModInitializer {

	@Override
	public void onInitialize() {
		EmiMain.init();
		EmiNetwork.initServer(ServerPlayNetworking::send);
		PayloadTypeRegistry.serverboundPlay().register(EmiNetwork.FILL_RECIPE, FillRecipeC2SPacket.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(EmiNetwork.CREATE_ITEM, CreateItemC2SPacket.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(EmiNetwork.PING, PingS2CPacket.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(EmiNetwork.COMMAND, CommandS2CPacket.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(EmiNetwork.SYNC_RECIPES, RecipeSyncS2CPacket.CODEC);
		registerServerReceiver(EmiNetwork.FILL_RECIPE);
		registerServerReceiver(EmiNetwork.CREATE_ITEM);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> EmiServer.onPlayerJoin(handler.player));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment)
			-> EmiCommands.registerCommands(dispatcher));
	}

	private static <T extends EmiPacket> void registerServerReceiver(CustomPacketPayload.Type<T> type) {
		ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
			context.server().execute(() -> payload.apply(context.player())));
	}
}

package dev.emi.emi.platform.neoforge;

import dev.emi.emi.network.CreateItemC2SPacket;
import dev.emi.emi.network.EmiNetwork;
import dev.emi.emi.network.EmiPacket;
import dev.emi.emi.network.FillRecipeC2SPacket;
import dev.emi.emi.network.PingS2CPacket;
import dev.emi.emi.network.RecipeSyncS2CPacket;
import dev.emi.emi.runtime.EmiLog;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class EmiPacketHandler {

	public static void init(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("emi").optional();
		registrar.playToServer(EmiNetwork.FILL_RECIPE, FillRecipeC2SPacket.CODEC, EmiPacketHandler::handleServerbound);
		registrar.playToServer(EmiNetwork.CREATE_ITEM, CreateItemC2SPacket.CODEC, EmiPacketHandler::handleServerbound);
		registrar.playToClient(EmiNetwork.PING, PingS2CPacket.CODEC, EmiPacketHandler::handleClientbound);
		registrar.playToClient(EmiNetwork.SYNC_RECIPES, RecipeSyncS2CPacket.CODEC, EmiPacketHandler::handleClientbound);
	}

	private static void handleServerbound(EmiPacket packet, IPayloadContext context) {
		if (context.flow() != PacketFlow.SERVERBOUND) {
			EmiLog.warn("Ignoring EMI packet " + packet.type().id() + " sent in the wrong direction");
			return;
		}
		Player player = context.player();
		context.enqueueWork(() -> packet.apply(player));
	}

	private static void handleClientbound(EmiPacket packet, IPayloadContext context) {
		if (context.flow() != PacketFlow.CLIENTBOUND) {
			EmiLog.warn("Ignoring EMI packet " + packet.type().id() + " sent in the wrong direction");
			return;
		}
		Player player = context.player();
		context.enqueueWork(() -> packet.apply(player));
	}
}

package dev.emi.emi.platform.neoforge;

import dev.emi.emi.network.EmiNetwork;
import dev.emi.emi.platform.EmiMain;
import dev.emi.emi.platform.EmiServer;
import dev.emi.emi.runtime.EmiLog;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod("emi")
public class EmiNeoForge {

	public EmiNeoForge(IEventBus modEventBus) {
		EmiMain.init();
		EmiNetwork.initServer((player, packet) -> {
			if (player.connection.hasChannel(packet.type())) {
				PacketDistributor.sendToPlayer(player, packet);
			} else {
				EmiLog.warn("Can't send EMI packet to " + player.getName().getString() + " as they're missing the channel");
			}
		});
		modEventBus.addListener(EmiPacketHandler::init);
		NeoForge.EVENT_BUS.addListener(EmiNeoForge::onPlayerLoggedIn);
	}

	private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			EmiServer.onPlayerJoin(player);
		}
	}
}

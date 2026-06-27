package dev.emi.emi.platform.neoforge;

import dev.emi.emi.platform.EmiClient;
import dev.emi.emi.runtime.EmiReload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid = "emi", value = Dist.CLIENT)
public class EmiClientNeoForge {
	private static volatile boolean pendingReload = false;

	@SubscribeEvent
	public static void clientInit(FMLClientSetupEvent event) {
		EmiClient.init();
		NeoForge.EVENT_BUS.addListener(EmiClientNeoForge::onLogin);
		NeoForge.EVENT_BUS.addListener(EmiClientNeoForge::onClientTick);
	}

	// NeoForge fires LoggingIn from inside the login-packet handler, where Minecraft.getInstance() is
	// momentarily null. So we only set a flag here and let the next client tick (where the instance and
	// world are ready) trigger the actual index build.
	private static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
		pendingReload = true;
	}

	private static void onClientTick(ClientTickEvent.Post event) {
		if (pendingReload) {
			Minecraft client = Minecraft.getInstance();
			if (client != null && client.level != null && client.player != null) {
				pendingReload = false;
				EmiReload.scheduleReload();
			}
		}
	}
}

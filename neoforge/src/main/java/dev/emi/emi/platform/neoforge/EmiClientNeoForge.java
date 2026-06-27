package dev.emi.emi.platform.neoforge;

import dev.emi.emi.platform.EmiClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = "emi", value = Dist.CLIENT)
public class EmiClientNeoForge {

	@SubscribeEvent
	public static void clientInit(FMLClientSetupEvent event) {
		EmiClient.init();
	}
}

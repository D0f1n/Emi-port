package dev.emi.emi.platform.fabric;

import dev.emi.emi.platform.EmiClient;
import net.fabricmc.api.ClientModInitializer;

public class EmiClientFabric implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		EmiClient.init();
	}
}

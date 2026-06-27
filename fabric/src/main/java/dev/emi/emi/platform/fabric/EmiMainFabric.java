package dev.emi.emi.platform.fabric;

import dev.emi.emi.platform.EmiMain;
import net.fabricmc.api.ModInitializer;

public class EmiMainFabric implements ModInitializer {

	@Override
	public void onInitialize() {
		EmiMain.init();
	}
}

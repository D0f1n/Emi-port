package dev.emi.emi.platform.neoforge;

import dev.emi.emi.platform.EmiMain;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod("emi")
public class EmiNeoForge {

	public EmiNeoForge(IEventBus modEventBus) {
		EmiMain.init();
	}
}

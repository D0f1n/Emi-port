package dev.emi.emi.platform.neoforge;

import dev.emi.emi.platform.EmiAgnos;
import net.neoforged.fml.ModList;

public class EmiAgnosNeoForge extends EmiAgnos {

	static {
		EmiAgnos.delegate = new EmiAgnosNeoForge();
	}

	@Override
	protected String getLoaderNameAgnos() {
		return "neoforge";
	}

	@Override
	protected boolean isModLoadedAgnos(String id) {
		return ModList.get().isLoaded(id);
	}
}

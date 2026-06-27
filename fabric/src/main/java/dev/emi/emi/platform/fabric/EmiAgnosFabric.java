package dev.emi.emi.platform.fabric;

import dev.emi.emi.platform.EmiAgnos;
import net.fabricmc.loader.api.FabricLoader;

public class EmiAgnosFabric extends EmiAgnos {

	static {
		EmiAgnos.delegate = new EmiAgnosFabric();
	}

	@Override
	protected String getLoaderNameAgnos() {
		return "fabric";
	}

	@Override
	protected boolean isModLoadedAgnos(String id) {
		return FabricLoader.getInstance().isModLoaded(id);
	}
}

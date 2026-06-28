package dev.emi.emi.platform.neoforge;

import java.util.Optional;

import dev.emi.emi.platform.EmiAgnos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;

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

	@Override
	protected Component getFluidNameAgnos(Fluid fluid, DataComponentPatch componentChanges) {
		return new FluidStack(fluid, 1000, componentChanges).getHoverName();
	}

	@Override
	protected String getModNameAgnos(String namespace) {
		if (namespace.equals("c")) {
			return "Common";
		}
		if (namespace.equals("minecraft")) {
			return "Minecraft";
		}
		Optional<? extends ModContainer> container = ModList.get().getModContainerById(namespace);
		if (container.isPresent()) {
			return container.get().getModInfo().getDisplayName();
		}
		container = ModList.get().getModContainerById(namespace.replace('_', '-'));
		if (container.isPresent()) {
			return container.get().getModInfo().getDisplayName();
		}
		return namespace;
	}
}

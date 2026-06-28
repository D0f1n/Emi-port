package dev.emi.emi.platform.fabric;

import dev.emi.emi.platform.EmiAgnos;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;

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

	@Override
	protected Component getFluidNameAgnos(Fluid fluid, DataComponentPatch componentChanges) {
		return FluidVariantAttributes.getName(FluidVariant.of(fluid, componentChanges));
	}
}

package dev.emi.emi.api.stack;

import org.jetbrains.annotations.ApiStatus;

import dev.emi.emi.EmiPort;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;

@ApiStatus.Internal
public class FluidEmiStack extends EmiStack {
	private final Fluid fluid;
	private final DataComponentPatch componentChanges;

	public FluidEmiStack(Fluid fluid) {
		this(fluid, DataComponentPatch.EMPTY);
	}

	public FluidEmiStack(Fluid fluid, DataComponentPatch componentChanges) {
		this(fluid, componentChanges, 0);
	}

	public FluidEmiStack(Fluid fluid, DataComponentPatch componentChanges, long amount) {
		this.fluid = fluid;
		this.componentChanges = componentChanges;
		this.amount = amount;
	}

	@Override
	public EmiStack copy() {
		EmiStack e = new FluidEmiStack(fluid, componentChanges, amount);
		e.setChance(chance);
		e.setRemainder(getRemainder().copy());
		e.comparison = comparison;
		return e;
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public DataComponentPatch getComponentChanges() {
		return componentChanges;
	}

	@Override
	public Object getKey() {
		return fluid;
	}

	@Override
	public Identifier getId() {
		return EmiPort.getFluidRegistry().getKey(fluid);
	}

	@Override
	public Component getName() {
		// TODO(render): original delegates to EmiAgnos.getFluidName(fluid, components) (loader-specific
		// fluid rendering metadata). Placeholder uses the fluid id so the index has a non-null name.
		return EmiPort.literal(getId().toString());
	}
}

package dev.emi.emi.api.stack;

import java.util.List;

import org.jetbrains.annotations.ApiStatus;

import com.google.common.collect.Lists;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.render.EmiRender;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.platform.EmiAgnos;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
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
	public void render(GuiGraphicsExtractor draw, int x, int y, float delta, int flags) {
		if ((flags & RENDER_ICON) != 0) {
			// The original pushed the icon 100 units forward; the two-phase 26.2 context has no Z axis.
			EmiAgnos.renderFluid(this, draw, x, y, delta);
		}
		if ((flags & RENDER_REMAINDER) != 0) {
			EmiRender.renderRemainderIcon(this, draw, x, y);
		}
	}

	@Override
	public List<Component> getTooltipText() {
		// TODO(render): the original returns EmiAgnos.getFluidTooltip(fluid, components) (loader metadata).
		return List.of(getName());
	}

	@Override
	public List<ClientTooltipComponent> getTooltip() {
		List<ClientTooltipComponent> list = Lists.newArrayList(super.getTooltip());
		if (EmiConfig.appendModId) {
			String namespace = EmiPort.getFluidRegistry().getKey(fluid).getNamespace();
			list.add(ClientTooltipComponent.create(EmiPort.ordered(
				EmiPort.literal(EmiUtil.getModName(namespace), ChatFormatting.BLUE, ChatFormatting.ITALIC))));
		}
		return list;
	}

	@Override
	public Component getName() {
		return EmiAgnos.getFluidName(fluid, componentChanges);
	}
}

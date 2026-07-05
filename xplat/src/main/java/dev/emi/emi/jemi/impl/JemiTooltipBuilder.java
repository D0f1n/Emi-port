package dev.emi.emi.jemi.impl;

import java.util.Collection;
import java.util.List;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Either;

import dev.emi.emi.runtime.EmiLog;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiKeyMapping;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * Collects JEI tooltip lines into EMI's client tooltip components. JEI hands out
 * {@link FormattedText}; only actual {@link Component}s can be converted losslessly, which in
 * practice is everything mods pass.
 */
public class JemiTooltipBuilder implements ITooltipBuilder {
	public final List<ClientTooltipComponent> tooltip = Lists.newArrayList();
	private final List<Either<FormattedText, TooltipComponent>> lines = Lists.newArrayList();

	@Override
	public void add(FormattedText component) {
		lines.add(Either.left(component));
		if (component instanceof Component text) {
			tooltip.add(ClientTooltipComponent.create(text.getVisualOrderText()));
		}
	}

	@Override
	public void addAll(Collection<? extends FormattedText> components) {
		for (FormattedText v : components) {
			add(v);
		}
	}

	@Override
	public void add(TooltipComponent data) {
		lines.add(Either.right(data));
		try {
			tooltip.add(ClientTooltipComponent.create(data));
		} catch (Exception e) {
			EmiLog.error("Error converting TooltipComponent", e);
		}
	}

	@Override
	public void addKeyUsageComponent(String translationKey, IJeiKeyMapping keyMapping) {
		// EMI renders its own key hints; JEI-style key usage lines are not shown.
	}

	@Override
	public void setIngredient(ITypedIngredient<?> typedIngredient) {
		// EMI's methods bypass the vanilla tooltip render which accepts a stack, so this will do nothing
	}

	@Override
	public void clearIngredient() {
	}

	@Override
	public void clear() {
		// EMI does not support tooltip removal, this will only clear the user's additions
	}

	@Override
	public List<Either<FormattedText, TooltipComponent>> getLines() {
		return lines;
	}
}

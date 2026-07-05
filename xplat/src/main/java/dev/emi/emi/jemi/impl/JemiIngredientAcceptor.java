package dev.emi.emi.jemi.impl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Either;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.jemi.JemiStack;
import dev.emi.emi.jemi.JemiUtil;
import dev.emi.emi.jemi.impl.JemiRecipeSlot.IngredientRenderer;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;

/**
 * Accumulates everything a JEI recipe feeds into a slot and converts it to EMI stacks on the fly.
 * Vanilla-shaped inputs (item stacks, ingredients, 26.2 slot displays) map through EMI's own
 * conversions; typed JEI ingredients go through {@link JemiUtil}.
 */
public class JemiIngredientAcceptor implements IIngredientAcceptor<JemiIngredientAcceptor> {
	public static final Pattern FLUID_END = Pattern.compile("(^|\\s)([\\d,]+)\\s*mB$");
	public final RecipeIngredientRole role;
	public final List<EmiStack> stacks = Lists.newArrayList();

	public JemiIngredientAcceptor(RecipeIngredientRole role) {
		this.role = role;
	}

	/**
	 * JEI reports fluid amounts through slot tooltips ("123 mB"); mirror them onto the EMI stacks
	 * so the tank/slot amounts match what JEI would have displayed.
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	public void coerceStacks(IRecipeSlotRichTooltipCallback richTooltipCallback, Map<IIngredientType<?>, IngredientRenderer<?>> renderers) {
		if (richTooltipCallback == null && renderers == null) {
			return;
		}
		for (EmiStack stack : stacks) {
			ITypedIngredient typed = JemiUtil.getTyped(stack).orElse(null);
			if (typed != null && (stack instanceof JemiStack || stack.getKey() instanceof Fluid)) {
				List<Component> base = Lists.newArrayList();
				if (renderers != null && renderers.containsKey(typed.getType())) {
					base.addAll(((IngredientRenderer) renderers.get(typed.getType())).renderer().getTooltip(typed.getIngredient(), TooltipFlag.Default.NORMAL));
				}
				if (base.isEmpty()) {
					if (richTooltipCallback == null) {
						continue;
					}
					base.add(stack.getName());
					base.add(EmiPort.literal(""));
				}
				if (richTooltipCallback != null) {
					try {
						JemiRecipeSlot jsr = new JemiRecipeSlot(role, stack);
						JemiTooltipBuilder builder = new JemiTooltipBuilder();
						richTooltipCallback.onRichTooltip(jsr, builder);
						for (Either<FormattedText, TooltipComponent> line : builder.getLines()) {
							line.left().ifPresent(t -> {
								if (t instanceof Component c) {
									base.add(c);
								}
							});
						}
					} catch (Exception e) {
					}
				}
				for (int i = 0; i < base.size(); i++) {
					Component t = base.get(i);
					if (t != null) {
						Matcher m = FLUID_END.matcher(t.getString());
						if (m.find()) {
							long amount = Long.parseLong(m.group(2).replace(",", ""));
							if (amount != stack.getAmount()) {
								stack.setAmount(amount);
							}
						}
					}
				}
			}
		}
	}

	public EmiIngredient build() {
		return EmiIngredient.of(stacks);
	}

	private JemiIngredientAcceptor addStack(EmiStack stack) {
		if (!stack.isEmpty()) {
			stacks.add(stack);
		}
		return this;
	}

	private JemiIngredientAcceptor addEmiIngredient(EmiIngredient ingredient) {
		for (EmiStack stack : ingredient.getEmiStacks()) {
			addStack(stack);
		}
		return this;
	}

	@Override
	public JemiIngredientAcceptor add(SlotDisplay display) {
		return addEmiIngredient(EmiPort.ofSlotDisplay(display));
	}

	@Override
	public <I> JemiIngredientAcceptor add(IIngredientType<I> type, SlotDisplay display) {
		// Typed slot displays beyond items/fluids have no EMI equivalent; fall back to the display.
		return add(display);
	}

	@Override
	public JemiIngredientAcceptor add(ItemStack stack) {
		return addStack(EmiStack.of(stack));
	}

	@Override
	public JemiIngredientAcceptor add(ItemLike item) {
		return addStack(EmiStack.of(item));
	}

	@Override
	public JemiIngredientAcceptor add(ItemStackTemplate template) {
		return addStack(EmiStack.of(template.create()));
	}

	@Override
	public JemiIngredientAcceptor add(Fluid fluid) {
		return addStack(EmiStack.of(fluid, JemiUtil.getFluidHelper().bucketVolume()));
	}

	@Override
	public JemiIngredientAcceptor add(Fluid fluid, long amount) {
		return addStack(EmiStack.of(fluid, amount));
	}

	@Override
	public JemiIngredientAcceptor add(Fluid fluid, long amount, DataComponentPatch componentChanges) {
		return addStack(EmiStack.of(fluid, componentChanges, amount));
	}

	@Override
	public JemiIngredientAcceptor add(Ingredient ingredient) {
		return addEmiIngredient(EmiIngredient.of(ingredient));
	}

	@Override
	public <I> JemiIngredientAcceptor add(IIngredientType<I> type, Ingredient ingredient) {
		return add(ingredient);
	}

	@Override
	public <I> JemiIngredientAcceptor add(ITypedIngredient<I> ingredient) {
		return addStack(JemiUtil.getStack(ingredient));
	}

	@Override
	public <I> JemiIngredientAcceptor add(IIngredientType<I> type, I ingredient) {
		return addStack(JemiUtil.getStack(type, ingredient));
	}

	@Override
	public <I> JemiIngredientAcceptor addIngredients(IIngredientType<I> type, List<@Nullable I> ingredients) {
		for (I i : ingredients) {
			if (i != null) {
				add(type, i);
			}
		}
		return this;
	}

	@Override
	public JemiIngredientAcceptor addIngredientsUnsafe(List<?> ingredients) {
		for (Object o : ingredients) {
			addStack(JemiUtil.getStack(o));
		}
		return this;
	}

	@Override
	public JemiIngredientAcceptor addTypedIngredients(List<ITypedIngredient<?>> ingredients) {
		for (ITypedIngredient<?> i : ingredients) {
			add(i);
		}
		return this;
	}

	@Override
	public JemiIngredientAcceptor addOptionalTypedIngredients(List<Optional<ITypedIngredient<?>>> ingredients) {
		for (Optional<ITypedIngredient<?>> opt : ingredients) {
			opt.ifPresent(this::add);
		}
		return this;
	}

	@Override
	public JemiIngredientAcceptor addItemStacks(List<ItemStack> stacks) {
		for (ItemStack stack : stacks) {
			add(stack);
		}
		return this;
	}

	private static final ContextMap EMPTY_CONTEXT = new ContextMap.Builder().create(new ContextKeySet.Builder().build());

	@Override
	public ContextMap getContextMap() {
		return EMPTY_CONTEXT;
	}
}

package dev.emi.emi.jemi;

import java.util.List;
import java.util.Optional;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.IPlatformFluidHelper;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/**
 * Conversion between JEI's typed ingredients and EMI stacks. Item and fluid ingredients map onto
 * EMI's native stacks; everything else is wrapped in a {@link JemiStack} driven by JEI's helper
 * and renderer. Fluids go through {@link IPlatformFluidHelper}, so no platform-specific JEI API
 * is referenced here.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class JemiUtil {

	public static EmiIngredient getIngredient(List<ITypedIngredient<?>> ingredients) {
		if (ingredients.isEmpty()) {
			return EmiStack.EMPTY;
		}
		return EmiIngredient.of(ingredients.stream().map(JemiUtil::getStack).filter(i -> !i.isEmpty()).toList());
	}

	public static EmiStack getStack(Object ingredient) {
		Optional<IIngredientType> optional = (Optional<IIngredientType>) (Optional) JemiPlugin.runtime.getIngredientManager()
			.getIngredientTypeChecked(ingredient);
		if (optional.isPresent()) {
			return getStack(optional.get(), ingredient);
		}
		return EmiStack.EMPTY;
	}

	public static EmiStack getStack(ITypedIngredient<?> ingredient) {
		return getStack(ingredient.getType(), ingredient.getIngredient());
	}

	public static EmiStack getStack(IIngredientType<?> type, Object ingredient) {
		if (type == VanillaTypes.ITEM_STACK) {
			return EmiStack.of((ItemStack) ingredient);
		} else if (type == getFluidType()) {
			IPlatformFluidHelper helper = getFluidHelper();
			IIngredientHelper<Object> ingredientHelper = JemiPlugin.runtime.getIngredientManager().getIngredientHelper((IIngredientType<Object>) type);
			Object base = ((mezz.jei.api.ingredients.IIngredientTypeWithSubtypes<Fluid, Object>) type).getBase(ingredient);
			if (base instanceof Fluid fluid) {
				long amount = ingredientHelper.getAmount(ingredient);
				return EmiStack.of(fluid, amount <= 0 ? helper.bucketVolume() : amount);
			}
			return EmiStack.EMPTY;
		} else {
			IIngredientManager im = JemiPlugin.runtime.getIngredientManager();
			IIngredientHelper helper = im.getIngredientHelper(type);
			if (helper.isValidIngredient(ingredient)) {
				return new JemiStack(type, helper, im.getIngredientRenderer(type), ingredient);
			}
		}
		return EmiStack.EMPTY;
	}

	public static Optional<ITypedIngredient<?>> getTyped(EmiStack stack) {
		if (stack.isEmpty()) {
			return Optional.empty();
		} else if (stack.getKey() instanceof Fluid f) {
			Object fluidIngredient = getFluidHelper().create(EmiPort.getFluidRegistry().wrapAsHolder(f),
				stack.getAmount() <= 0 ? getFluidHelper().bucketVolume() : stack.getAmount(), stack.getComponentChanges());
			return (Optional) JemiPlugin.runtime.getIngredientManager().createTypedIngredient(getFluidType(), fluidIngredient, false);
		} else if (stack instanceof JemiStack js) {
			return JemiPlugin.runtime.getIngredientManager().getIngredientTypeChecked(js.ingredient)
				.map(t -> (Optional) JemiPlugin.runtime.getIngredientManager().createTypedIngredient(t, js.ingredient, false))
				.orElse(Optional.empty());
		}
		return (Optional) JemiPlugin.runtime.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, stack.getItemStack(), false);
	}

	public static IPlatformFluidHelper getFluidHelper() {
		return JemiPlugin.runtime.getJeiHelpers().getPlatformFluidHelper();
	}

	public static IIngredientType getFluidType() {
		return getFluidHelper().getFluidIngredientType();
	}
}

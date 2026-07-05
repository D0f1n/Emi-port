package dev.emi.emi.jemi.runtime;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.jemi.JemiUtil;
import dev.emi.emi.screen.EmiScreenManager;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientListOverlay;

/**
 * JEI's ingredient list overlay api backed by EMI's index panel. Hover introspection is
 * TODO(polish) until the screen manager exposes hovered stacks again.
 */
public class JemiIngredientListOverlay implements IIngredientListOverlay {

	@Override
	public Optional<ITypedIngredient<?>> getIngredientUnderMouse() {
		return Optional.empty();
	}

	@Override
	public <T> @Nullable T getIngredientUnderMouse(IIngredientType<T> ingredientType) {
		return null;
	}

	@Override
	public boolean isListDisplayed() {
		return EmiApi.getHandledScreen() != null;
	}

	@Override
	public boolean hasKeyboardFocus() {
		return EmiScreenManager.isSearchFocused();
	}

	@Override
	public <T> List<T> getVisibleIngredients(IIngredientType<T> ingredientType) {
		return EmiScreenManager.searchedStacks.stream()
			.filter(i -> !i.getEmiStacks().isEmpty())
			.map(i -> JemiUtil.getTyped(i.getEmiStacks().get(0)))
			.filter(Optional::isPresent).map(Optional::get)
			.map(i -> i.getIngredient(ingredientType))
			.filter(Optional::isPresent).map(Optional::get)
			.toList();
	}
}

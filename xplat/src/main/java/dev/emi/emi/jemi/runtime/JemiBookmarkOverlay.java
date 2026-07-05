package dev.emi.emi.jemi.runtime;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IBookmarkOverlay;

/** TODO(polish): EMI favorites are not ported yet; the bookmark overlay reports nothing. */
public class JemiBookmarkOverlay implements IBookmarkOverlay {

	@Override
	public Optional<ITypedIngredient<?>> getIngredientUnderMouse() {
		return Optional.empty();
	}

	@Override
	public <T> @Nullable T getIngredientUnderMouse(IIngredientType<T> ingredientType) {
		return null;
	}
}

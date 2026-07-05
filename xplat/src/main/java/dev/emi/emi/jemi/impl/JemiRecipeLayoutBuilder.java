package dev.emi.emi.jemi.impl;

import java.util.List;

import com.google.common.collect.Lists;

import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;

/** Captures a JEI recipe layout (slots + loose ingredients) for conversion into EMI widgets. */
public class JemiRecipeLayoutBuilder implements IRecipeLayoutBuilder {
	public final List<JemiIngredientAcceptor> ingredients = Lists.newArrayList();
	public final List<JemiRecipeSlotBuilder> slots = Lists.newArrayList();
	public boolean shapeless = false;

	@Override
	public IRecipeSlotBuilder addSlot(RecipeIngredientRole role) {
		JemiRecipeSlotBuilder builder = new JemiRecipeSlotBuilder(role, 0, 0);
		ingredients.add(builder.acceptor);
		slots.add(builder);
		return builder;
	}

	@Override
	public IIngredientAcceptor<?> addInvisibleIngredients(RecipeIngredientRole role) {
		JemiIngredientAcceptor acceptor = new JemiIngredientAcceptor(role);
		ingredients.add(acceptor);
		return acceptor;
	}

	@Override
	public void moveRecipeTransferButton(int posX, int posY) {
	}

	@Override
	public void setShapeless() {
		shapeless = true;
	}

	@Override
	public void setShapeless(int posX, int posY) {
		shapeless = true;
	}

	@Override
	public void createFocusLink(IIngredientAcceptor<?>... slots) {
	}
}

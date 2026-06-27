package dev.emi.emi.api.stack;

import java.util.List;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class ListEmiIngredient implements EmiIngredient {
	private final List<? extends EmiIngredient> ingredients;
	private final List<EmiStack> fullList;
	private long amount;
	private float chance = 1;

	@ApiStatus.Internal
	public ListEmiIngredient(List<? extends EmiIngredient> ingredients, long amount) {
		this.ingredients = ingredients;
		this.fullList = ingredients.stream().flatMap(i -> i.getEmiStacks().stream()).toList();
		if (fullList.isEmpty()) {
			throw new IllegalArgumentException("ListEmiIngredient cannot be empty");
		}
		this.amount = amount;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof ListEmiIngredient other) {
			return other.getEmiStacks().equals(this.getEmiStacks());
		}
		return false;
	}

	@Override
	public int hashCode() {
		return fullList.hashCode();
	}

	@Override
	public EmiIngredient copy() {
		EmiIngredient stack = new ListEmiIngredient(ingredients, amount);
		stack.setChance(chance);
		return stack;
	}

	@Override
	public String toString() {
		return "Ingredient" + getEmiStacks();
	}

	@Override
	public List<EmiStack> getEmiStacks() {
		return fullList;
	}

	@Override
	public long getAmount() {
		return amount;
	}

	@Override
	public EmiIngredient setAmount(long amount) {
		this.amount = amount;
		return this;
	}

	@Override
	public float getChance() {
		return chance;
	}

	@Override
	public EmiIngredient setChance(float chance) {
		this.chance = chance;
		return this;
	}

	@ApiStatus.Internal
	public List<? extends EmiIngredient> getIngredients() {
		return ingredients;
	}
}

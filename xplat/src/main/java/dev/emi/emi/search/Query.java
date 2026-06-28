package dev.emi.emi.search;

import dev.emi.emi.api.stack.EmiStack;

public abstract class Query {
	public boolean negated;

	public abstract boolean matches(EmiStack stack);

	/**
	 * The "unbaked" test path, used when stacks aren't indexed in a {@code SuffixArray}. This port never
	 * bakes (the SuffixArray is a pure speed optimisation), so this is the only path used; queries that
	 * don't override it fall back to {@link #matches}.
	 */
	public boolean matchesUnbaked(EmiStack stack) {
		return matches(stack);
	}
}

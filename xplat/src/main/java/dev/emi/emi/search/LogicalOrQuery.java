package dev.emi.emi.search;

import java.util.List;

import dev.emi.emi.api.stack.EmiStack;

public class LogicalOrQuery extends Query {
	private final List<Query> queries;

	public LogicalOrQuery(List<Query> queries) {
		this.queries = queries;
	}

	@Override
	public boolean matches(EmiStack stack) {
		for (Query q : queries) {
			if (q.matches(stack) == !q.negated) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean matchesUnbaked(EmiStack stack) {
		for (Query q : queries) {
			if (q.matchesUnbaked(stack) == !q.negated) {
				return true;
			}
		}
		return false;
	}
}

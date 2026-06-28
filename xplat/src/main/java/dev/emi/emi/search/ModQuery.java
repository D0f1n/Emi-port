package dev.emi.emi.search;

import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.stack.EmiStack;

public class ModQuery extends Query {
	private final String name;

	public ModQuery(String name) {
		this.name = name.toLowerCase();
	}

	@Override
	public boolean matches(EmiStack stack) {
		return EmiUtil.getModName(stack.getId().getNamespace()).toLowerCase().contains(name)
			|| stack.getId().getNamespace().toLowerCase().contains(name);
	}
}

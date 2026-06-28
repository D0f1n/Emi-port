package dev.emi.emi.search;

import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.network.chat.Component;

public class NameQuery extends Query {
	private final String name;

	public NameQuery(String name) {
		this.name = name.toLowerCase();
	}

	@Override
	public boolean matches(EmiStack stack) {
		return getText(stack).getString().toLowerCase().contains(name);
	}

	public static Component getText(EmiStack stack) {
		return stack.getName();
	}
}

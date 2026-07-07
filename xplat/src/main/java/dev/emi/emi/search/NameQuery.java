package dev.emi.emi.search;

import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class NameQuery extends Query {
	private final String name;

	public NameQuery(String name) {
		this.name = name.toLowerCase();
	}

	@Override
	public boolean matches(EmiStack stack) {
		// The original bakes both the localized name and the id path into the names index, so plain
		// terms match either one regardless of locale; this is the unbaked equivalent.
		if (getText(stack).getString().toLowerCase().contains(name)) {
			return true;
		}
		Identifier id = stack.getId();
		return id != null && id.getPath().toLowerCase().contains(name);
	}

	public static Component getText(EmiStack stack) {
		return stack.getName();
	}
}

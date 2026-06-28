package dev.emi.emi.search;

import java.util.List;

import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.network.chat.Component;

public class TooltipQuery extends Query {
	private final String name;

	public TooltipQuery(String name) {
		this.name = name.toLowerCase();
	}

	@Override
	public boolean matches(EmiStack stack) {
		for (Component text : getText(stack)) {
			if (text.getString().toLowerCase().contains(name)) {
				return true;
			}
		}
		return false;
	}

	public static List<Component> getText(EmiStack stack) {
		List<Component> lines = stack.getTooltipText();
		if (lines.isEmpty()) {
			return lines;
		}
		return lines.subList(1, lines.size());
	}
}

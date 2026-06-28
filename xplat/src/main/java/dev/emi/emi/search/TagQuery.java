package dev.emi.emi.search;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiTagKey;

public class TagQuery extends Query {
	private final Set<Object> valid;

	public TagQuery(String name) {
		String lowerName = name.toLowerCase();
		valid = Stream.<EmiTagKey<?>>concat(
			EmiTagKey.fromRegistry(EmiPort.getItemRegistry()),
			EmiTagKey.fromRegistry(EmiPort.getBlockRegistry())
		).filter(t -> {
			if (t.hasTranslation() && t.getTagName().getString().toLowerCase().contains(lowerName)) {
				return true;
			}
			return t.id().toString().contains(lowerName);
		}).flatMap(v -> v.stream()).collect(Collectors.toSet());
	}

	@Override
	public boolean matches(EmiStack stack) {
		return valid.contains(stack.getKey());
	}
}

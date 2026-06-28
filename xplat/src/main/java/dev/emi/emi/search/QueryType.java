package dev.emi.emi.search;

import java.util.function.Function;

/**
 * The EMI search query prefixes. Trimmed of the original's syntax-highlight colours (no highlighting yet);
 * the prefix + constructors are what the compiler needs.
 */
public enum QueryType {
	DEFAULT("", NameQuery::new, RegexNameQuery::new),
	MOD("@", ModQuery::new, RegexModQuery::new),
	TOOLTIP("$", TooltipQuery::new, RegexTooltipQuery::new),
	TAG("#", TagQuery::new, RegexTagQuery::new),
	;

	public final String prefix;
	public final Function<String, Query> queryConstructor, regexQueryConstructor;

	QueryType(String prefix, Function<String, Query> queryConstructor, Function<String, Query> regexQueryConstructor) {
		this.prefix = prefix;
		this.queryConstructor = queryConstructor;
		this.regexQueryConstructor = regexQueryConstructor;
	}

	public static QueryType fromString(String s) {
		for (int i = QueryType.values().length - 1; i >= 0; i--) {
			QueryType type = QueryType.values()[i];
			if (s.startsWith(type.prefix)) {
				return type;
			}
		}
		return DEFAULT;
	}
}

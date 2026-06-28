package dev.emi.emi.search;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.Lists;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.screen.EmiScreenManager;

/**
 * The EMI search engine, trimmed for this round. Keeps the query compiler and the full prefix syntax
 * ({@code @}mod, {@code $}tooltip, {@code #}tag, {@code /regex/}, {@code |} OR, {@code -} negate) but drops
 * the {@code SuffixArray} fast-index and aliases — every stack is tested directly via {@code matchesUnbaked}.
 *
 * <p>Filtering runs synchronously on the caller thread. At dev scale (~2000 stacks) this is sub-frame; in
 * large modpacks it would freeze typing — see the release-blocker note: re-add the background SearchWorker.
 */
public class EmiSearch {
	public static final Pattern TOKENS = Pattern.compile(
		"-?[@#$]?"
		+ "("
			+ "\\/(\\\\.|[^\\\\\\/])+\\/"
			+ "|"
			+ "\\\"(\\.|[^\\\"])+\\\""
			+ "|"
			+ "[^\\s|]+"
			+ "|"
			+ "\\|"
			+ "|"
			+ "\\&"
		+ ")");

	public static volatile List<? extends EmiIngredient> stacks = List.of();
	public static volatile CompiledQuery compiledQuery;

	public static void search(String query) {
		CompiledQuery compiled = new CompiledQuery(query);
		compiledQuery = compiled;
		List<? extends EmiIngredient> source = EmiScreenManager.getSearchSource();
		if (compiled.isEmpty()) {
			apply(source);
			return;
		}
		List<EmiIngredient> result = Lists.newArrayList();
		for (EmiIngredient stack : source) {
			List<EmiStack> ess = stack.getEmiStacks();
			if (ess.size() == 1 && compiled.test(ess.get(0))) {
				result.add(stack);
			}
		}
		apply(List.copyOf(result));
	}

	private static void apply(List<? extends EmiIngredient> s) {
		stacks = s;
		EmiScreenManager.setSearchedStacks(s);
	}

	public static class CompiledQuery {
		public final Query fullQuery;

		public CompiledQuery(String query) {
			List<Query> full = Lists.newArrayList();
			List<Query> queries = Lists.newArrayList();
			Matcher matcher = TOKENS.matcher(query);
			while (matcher.find()) {
				String q = matcher.group();
				boolean negated = q.startsWith("-");
				if (negated) {
					q = q.substring(1);
				}
				if (q.isEmpty() || q.equals("&")) {
					continue;
				}
				if (q.equals("|")) {
					if (!queries.isEmpty()) {
						full.add(new LogicalAndQuery(queries));
						queries = Lists.newArrayList();
					}
					continue;
				}
				QueryType type = QueryType.fromString(q);
				Function<String, Query> constructor = type.queryConstructor;
				Function<String, Query> regexConstructor = type.regexQueryConstructor;
				addQuery(q.substring(type.prefix.length()), negated, queries, constructor, regexConstructor);
			}
			if (!queries.isEmpty()) {
				full.add(new LogicalAndQuery(queries));
			}
			fullQuery = full.isEmpty() ? null : new LogicalOrQuery(full);
		}

		public boolean isEmpty() {
			return fullQuery == null;
		}

		public boolean test(EmiStack stack) {
			return fullQuery == null || fullQuery.matchesUnbaked(stack);
		}

		private static void addQuery(String s, boolean negated, List<Query> queries,
				Function<String, Query> normal, Function<String, Query> regex) {
			Query q;
			if (s.length() > 1 && s.startsWith("/") && s.endsWith("/")) {
				q = regex.apply(s.substring(1, s.length() - 1));
			} else if (s.length() > 1 && s.startsWith("\"") && s.endsWith("\"")) {
				q = normal.apply(s.substring(1, s.length() - 1));
			} else {
				q = normal.apply(s);
			}
			q.negated = negated;
			queries.add(q);
		}
	}
}

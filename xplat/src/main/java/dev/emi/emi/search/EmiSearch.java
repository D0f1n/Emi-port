package dev.emi.emi.search;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.Lists;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.screen.EmiScreenManager;

/**
 * The EMI search engine. Keeps the query compiler and the full prefix syntax ({@code @}mod,
 * {@code $}tooltip, {@code #}tag, {@code /regex/}, {@code |} OR, {@code -} negate); aliases and the
 * {@code SuffixArray} fast-index are still to come — every stack is tested via {@code matchesUnbaked}.
 *
 * <p>Each search runs on its own daemon worker thread; a newer search supersedes the running one, which
 * notices via {@code currentWorker} and bails. Results are published into the volatile {@link #stacks},
 * picked up by the render thread in {@code EmiScreenManager.render}.
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

	private static volatile SearchWorker currentWorker = null;
	public static volatile Thread searchThread = null;
	public static volatile List<? extends EmiIngredient> stacks = List.of();
	public static volatile CompiledQuery compiledQuery;

	public static void search(String query) {
		synchronized (EmiSearch.class) {
			SearchWorker worker = new SearchWorker(query, EmiScreenManager.getSearchSource());
			currentWorker = worker;

			searchThread = new Thread(worker);
			searchThread.setDaemon(true);
			searchThread.start();
		}
	}

	public static void apply(SearchWorker worker, List<? extends EmiIngredient> stacks) {
		synchronized (EmiSearch.class) {
			if (worker == currentWorker) {
				EmiSearch.stacks = stacks;
				currentWorker = null;
				searchThread = null;
			}
		}
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
				if (type == QueryType.DEFAULT) {
					List<Function<String, Query>> constructors = Lists.newArrayList();
					List<Function<String, Query>> regexConstructors = Lists.newArrayList();
					constructors.add(constructor);
					regexConstructors.add(regexConstructor);

					if (EmiConfig.searchTooltipByDefault) {
						constructors.add(QueryType.TOOLTIP.queryConstructor);
						regexConstructors.add(QueryType.TOOLTIP.regexQueryConstructor);
					}
					if (EmiConfig.searchModNameByDefault) {
						constructors.add(QueryType.MOD.queryConstructor);
						regexConstructors.add(QueryType.MOD.regexQueryConstructor);
					}
					if (EmiConfig.searchTagsByDefault) {
						constructors.add(QueryType.TAG.queryConstructor);
						regexConstructors.add(QueryType.TAG.regexQueryConstructor);
					}
					// TODO(alias): the original also joins AliasQuery once aliases are ported.
					if (constructors.size() > 1) {
						constructor = name -> new LogicalOrQuery(constructors.stream().map(c -> c.apply(name)).toList());
						regexConstructor = name -> new LogicalOrQuery(regexConstructors.stream().map(c -> c.apply(name)).toList());
					}
				}
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

	private static class SearchWorker implements Runnable {
		private final String query;
		private final List<? extends EmiIngredient> source;

		public SearchWorker(String query, List<? extends EmiIngredient> source) {
			this.query = query;
			this.source = source;
		}

		@Override
		public void run() {
			try {
				CompiledQuery compiled = new CompiledQuery(query);
				compiledQuery = compiled;
				if (compiled.isEmpty()) {
					apply(this, source);
					return;
				}
				List<EmiIngredient> stacks = Lists.newArrayList();
				int processed = 0;
				for (EmiIngredient stack : source) {
					if (processed++ >= 1024) {
						processed = 0;
						if (this != currentWorker) {
							return;
						}
					}
					List<EmiStack> ess = stack.getEmiStacks();
					// TODO properly support ingredients?
					if (ess.size() == 1) {
						EmiStack es = ess.get(0);
						if (compiled.test(es)) {
							stacks.add(stack);
						}
					}
				}
				apply(this, List.copyOf(stacks));
			} catch (Exception e) {
				EmiLog.error("Error when attempting to search:", e);
			}
		}
	}
}

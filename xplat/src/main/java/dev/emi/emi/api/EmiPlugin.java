package dev.emi.emi.api;

/**
 * The primary method of communicating with EMI.
 * Plugins are loaded at runtime to provide information like stacks and recipes.
 *
 * <p>Port note: plugin discovery (the "emi" entrypoint on Fabric/Quilt, the {@code EmiEntrypoint}
 * annotation on NeoForge) and the {@code initialize} pre-registration phase return with the jemi
 * round; for now the built-in vanilla plugin is registered internally.
 */
public interface EmiPlugin {

	/**
	 * The core method through which information is registered for EMI.
	 * This includes recipe categories, recipes, recipe handlers.
	 * @see {@link EmiRegistry}
	 */
	void register(EmiRegistry registry);
}

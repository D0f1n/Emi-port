package dev.emi.emi.platform;

/**
 * Loader-agnostic platform abstraction. Each loader provides a concrete subclass
 * ({@code EmiAgnosFabric} / {@code EmiAgnosNeoForge}) which registers itself into
 * {@link #delegate} from a static initializer. The static block below force-loads
 * whichever subclass is present on the current loader.
 *
 * <p>Skeleton scope: only enough surface to prove the abstraction resolves on both
 * loaders. EMI's full agnostic surface is reintroduced in later rounds.
 */
public abstract class EmiAgnos {
	public static EmiAgnos delegate;

	static {
		try {
			Class.forName("dev.emi.emi.platform.fabric.EmiAgnosFabric");
		} catch (Throwable t) {
		}
		try {
			Class.forName("dev.emi.emi.platform.neoforge.EmiAgnosNeoForge");
		} catch (Throwable t) {
		}
	}

	public static String getLoaderName() {
		return delegate == null ? "unknown" : delegate.getLoaderNameAgnos();
	}

	protected abstract String getLoaderNameAgnos();

	public static boolean isModLoaded(String id) {
		return delegate != null && delegate.isModLoadedAgnos(id);
	}

	protected abstract boolean isModLoadedAgnos(String id);
}

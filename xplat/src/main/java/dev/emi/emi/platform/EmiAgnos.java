package dev.emi.emi.platform;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.material.Fluid;

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

	public static Component getFluidName(Fluid fluid, DataComponentPatch componentChanges) {
		return delegate.getFluidNameAgnos(fluid, componentChanges);
	}

	protected abstract Component getFluidNameAgnos(Fluid fluid, DataComponentPatch componentChanges);

	public static String getModName(String namespace) {
		return delegate.getModNameAgnos(namespace);
	}

	protected abstract String getModNameAgnos(String namespace);

	/** Whether the player's client declared the given EMI channel (i.e. has EMI installed). */
	public static boolean canSendToPlayer(ServerPlayer player, CustomPacketPayload.Type<?> type) {
		return delegate != null && delegate.canSendToPlayerAgnos(player, type);
	}

	protected abstract boolean canSendToPlayerAgnos(ServerPlayer player, CustomPacketPayload.Type<?> type);
}

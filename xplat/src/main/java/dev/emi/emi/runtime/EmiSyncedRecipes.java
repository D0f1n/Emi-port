package dev.emi.emi.runtime;

import java.util.List;

import com.google.common.collect.Lists;

import dev.emi.emi.network.RecipeSyncS2CPacket;
import net.minecraft.client.Minecraft;

/**
 * Client-side store for the full recipe set a dedicated server with EMI pushes over
 * {@link RecipeSyncS2CPacket}. Batches accumulate in {@code pending} and become visible atomically
 * when the final batch arrives, so a harvest can never observe a half-received set.
 */
public class EmiSyncedRecipes {
	private static final List<RecipeSyncS2CPacket.Entry> pending = Lists.newArrayList();
	// Written on the client thread, read by the reload worker during a harvest.
	private static volatile List<RecipeSyncS2CPacket.Entry> recipes = List.of();

	public static void receive(boolean reset, boolean last, List<RecipeSyncS2CPacket.Entry> entries) {
		if (reset) {
			pending.clear();
		}
		pending.addAll(entries);
		if (last) {
			recipes = Lists.newArrayList(pending);
			pending.clear();
			EmiLog.info("Received " + recipes.size() + " synced recipes from the server");
			// In singleplayer the harvest reads the integrated server directly; the synced copy is
			// only the source of truth on a remote server, so only then is a re-harvest needed. If
			// the join reload is still running this restarts its worker instead of adding a pass.
			if (Minecraft.getInstance().getSingleplayerServer() == null) {
				EmiReloadManager.reload();
			}
		}
	}

	public static List<RecipeSyncS2CPacket.Entry> get() {
		return recipes;
	}

	public static boolean hasSync() {
		return !recipes.isEmpty();
	}

	public static void clear() {
		pending.clear();
		recipes = List.of();
	}
}

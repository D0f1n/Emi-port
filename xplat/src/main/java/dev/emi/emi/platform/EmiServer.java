package dev.emi.emi.platform;

import java.util.List;

import com.google.common.collect.Lists;

import dev.emi.emi.network.EmiNetwork;
import dev.emi.emi.network.PingS2CPacket;
import dev.emi.emi.network.RecipeSyncS2CPacket;
import dev.emi.emi.runtime.EmiLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;

/**
 * Server-side join hook, called by both loaders from their player-join events: announces EMI to
 * the client (ping) and pushes the full recipe set in batches for the dedicated-server case.
 */
public class EmiServer {
	private static final int SYNC_BATCH_SIZE = 100;

	public static void onPlayerJoin(ServerPlayer player) {
		// Clients without EMI never declare the channel; skip them entirely so no bandwidth is
		// wasted on a sync they cannot decode.
		if (!EmiAgnos.canSendToPlayer(player, EmiNetwork.PING)) {
			return;
		}
		EmiNetwork.sendToClient(player, new PingS2CPacket());
		sendRecipeSync(player);
	}

	// TODO(release-blocker): on large modpacks this is hundreds of packets in one burst on join;
	// needs pacing/compression before public release (tracked alongside the SearchWorker debt).
	private static void sendRecipeSync(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		int sent = 0;
		List<RecipeSyncS2CPacket.Entry> batch = Lists.newArrayList();
		boolean first = true;
		for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
			try {
				List<RecipeDisplay> displays = holder.value().display();
				if (displays.isEmpty()) {
					continue;
				}
				batch.add(new RecipeSyncS2CPacket.Entry(holder.id().identifier(),
					BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()), displays));
				sent++;
			} catch (Exception e) {
				EmiLog.warn("Failed to read displays for recipe " + holder.id().identifier(), e);
				continue;
			}
			if (batch.size() >= SYNC_BATCH_SIZE) {
				EmiNetwork.sendToClient(player, new RecipeSyncS2CPacket(first, false, batch));
				batch = Lists.newArrayList();
				first = false;
			}
		}
		EmiNetwork.sendToClient(player, new RecipeSyncS2CPacket(first, true, batch));
		EmiLog.info("Synced " + sent + " recipes to " + player.getName().getString());
	}
}

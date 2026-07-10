package dev.emi.emi.platform;

import java.util.List;

import com.google.common.collect.Lists;

import dev.emi.emi.network.EmiNetwork;
import dev.emi.emi.network.PingS2CPacket;
import dev.emi.emi.network.RecipeSyncS2CPacket;
import dev.emi.emi.runtime.EmiLog;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;

/**
 * Server-side join hook, called by both loaders from their player-join events: announces EMI to
 * the client (ping) and pushes the full recipe set in batches for the dedicated-server case.
 */
public class EmiServer {
	// Batches are cut by encoded size, not entry count: entry sizes vary wildly between recipe
	// types, and a count cap either wastes packets on small entries or risks the payload limit on
	// big ones. 512 KiB sits well below vanilla's 1 MiB custom-payload cap even before the
	// connection-level compression dedicated servers apply.
	private static final int SYNC_BATCH_BYTES = 512 * 1024;

	public static void onPlayerJoin(ServerPlayer player) {
		// Clients without EMI never declare the channel; skip them entirely so no bandwidth is
		// wasted on a sync they cannot decode.
		if (!EmiAgnos.canSendToPlayer(player, EmiNetwork.PING)) {
			return;
		}
		EmiNetwork.sendToClient(player, new PingS2CPacket());
		sendRecipeSync(player);
	}

	private static void sendRecipeSync(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		int sent = 0;
		int packets = 0;
		long totalBytes = 0;
		long batchBytes = 0;
		// Every entry is pre-encoded here with the exact packet logic, so batch sizes are accounted
		// in real wire bytes and entries that cannot encode are skipped up front instead of failing
		// the whole packet later on the network thread.
		RegistryFriendlyByteBuf scratch = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
		List<RecipeSyncS2CPacket.Entry> batch = Lists.newArrayList();
		boolean first = true;
		for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
			RecipeSyncS2CPacket.Entry entry;
			int entrySize;
			scratch.clear();
			try {
				List<RecipeDisplay> displays = holder.value().display();
				if (displays.isEmpty()) {
					continue;
				}
				entry = new RecipeSyncS2CPacket.Entry(holder.id().identifier(),
					BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()), displays);
				RecipeSyncS2CPacket.writeEntry(scratch, entry);
				entrySize = scratch.writerIndex();
			} catch (Exception e) {
				EmiLog.warn("Failed to read displays for recipe " + holder.id().identifier(), e);
				continue;
			}
			// Flush before adding, so no packet exceeds the budget; an entry that alone exceeds it
			// still ships, as its own oversized packet.
			if (!batch.isEmpty() && batchBytes + entrySize > SYNC_BATCH_BYTES) {
				EmiNetwork.sendToClient(player, new RecipeSyncS2CPacket(first, false, batch));
				packets++;
				totalBytes += batchBytes;
				batch = Lists.newArrayList();
				batchBytes = 0;
				first = false;
			}
			batch.add(entry);
			batchBytes += entrySize;
			sent++;
		}
		EmiNetwork.sendToClient(player, new RecipeSyncS2CPacket(first, true, batch));
		packets++;
		totalBytes += batchBytes;
		scratch.release();
		EmiLog.info("Synced " + sent + " recipes to " + player.getName().getString()
			+ " in " + packets + " packets (" + (totalBytes / 1024) + " KiB)");
	}
}

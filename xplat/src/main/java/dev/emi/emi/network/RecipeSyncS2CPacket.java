package dev.emi.emi.network;

import java.util.List;

import com.google.common.collect.Lists;

import dev.emi.emi.runtime.EmiSyncedRecipes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.display.RecipeDisplay;

/**
 * One batch of the full recipe set a server with EMI pushes to joining clients. The 1.21.2+ recipe
 * rework keeps the full {@code RecipeManager} server-side and only syncs unlocked recipe displays,
 * so on dedicated servers EMI ships the complete set itself over this channel. Entries carry the
 * same vanilla {@link RecipeDisplay} data the integrated-server harvest reads, so the client feeds
 * both through one mapping layer.
 *
 * <p>The set is split into batches to stay well below the payload size limit; {@code reset} marks
 * the first batch of a sync, {@code last} the final one.
 */
public class RecipeSyncS2CPacket implements EmiPacket {
	public static final StreamCodec<RegistryFriendlyByteBuf, RecipeSyncS2CPacket> CODEC
		= CustomPacketPayload.codec(RecipeSyncS2CPacket::write, RecipeSyncS2CPacket::new);

	private final boolean reset, last;
	private final List<Entry> entries;

	public RecipeSyncS2CPacket(boolean reset, boolean last, List<Entry> entries) {
		this.reset = reset;
		this.last = last;
		this.entries = entries;
	}

	public RecipeSyncS2CPacket(RegistryFriendlyByteBuf buf) {
		int flags = buf.readByte();
		reset = (flags & 1) != 0;
		last = (flags & 2) != 0;
		int count = buf.readVarInt();
		entries = Lists.newArrayList();
		for (int i = 0; i < count; i++) {
			Identifier id = Identifier.STREAM_CODEC.decode(buf);
			Identifier typeId = Identifier.STREAM_CODEC.decode(buf);
			int displayCount = buf.readVarInt();
			List<RecipeDisplay> displays = Lists.newArrayList();
			for (int j = 0; j < displayCount; j++) {
				displays.add(RecipeDisplay.STREAM_CODEC.decode(buf));
			}
			entries.add(new Entry(id, typeId, displays));
		}
	}

	@Override
	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeByte((reset ? 1 : 0) | (last ? 2 : 0));
		buf.writeVarInt(entries.size());
		for (Entry entry : entries) {
			writeEntry(buf, entry);
		}
	}

	/**
	 * Encodes one entry. The server also runs this against a scratch buffer while batching, so the
	 * size accounting and the real send stay byte-identical by construction.
	 */
	public static void writeEntry(RegistryFriendlyByteBuf buf, Entry entry) {
		Identifier.STREAM_CODEC.encode(buf, entry.id());
		Identifier.STREAM_CODEC.encode(buf, entry.typeId());
		buf.writeVarInt(entry.displays().size());
		for (RecipeDisplay display : entry.displays()) {
			RecipeDisplay.STREAM_CODEC.encode(buf, display);
		}
	}

	@Override
	public void apply(Player player) {
		EmiSyncedRecipes.receive(reset, last, entries);
	}

	@Override
	public Type<RecipeSyncS2CPacket> type() {
		return EmiNetwork.SYNC_RECIPES;
	}

	/** One recipe: its id, its {@code RecipeType} registry id, and its vanilla displays. */
	public record Entry(Identifier id, Identifier typeId, List<RecipeDisplay> displays) {
	}
}

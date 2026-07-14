package dev.emi.emi.network;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.registry.EmiCommands;
import dev.emi.emi.runtime.EmiLog;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/** Server-to-client dispatch of the /emi command: opens recipes (and BoM views, with BoM) remotely. */
public class CommandS2CPacket implements EmiPacket {
	public static final StreamCodec<RegistryFriendlyByteBuf, CommandS2CPacket> CODEC
		= CustomPacketPayload.codec(CommandS2CPacket::write, CommandS2CPacket::new);
	private final byte type;
	private final Identifier id;

	public CommandS2CPacket(byte type, Identifier id) {
		this.type = type;
		this.id = id;
	}

	public CommandS2CPacket(RegistryFriendlyByteBuf buf) {
		type = buf.readByte();
		if (type == EmiCommands.VIEW_RECIPE || type == EmiCommands.TREE_GOAL || type == EmiCommands.TREE_RESOLUTION) {
			id = buf.readIdentifier();
		} else {
			id = null;
		}
	}

	@Override
	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeByte(type);
		if (type == EmiCommands.VIEW_RECIPE || type == EmiCommands.TREE_GOAL || type == EmiCommands.TREE_RESOLUTION) {
			buf.writeIdentifier(id);
		}
	}

	@Override
	public void apply(Player player) {
		if (type == EmiCommands.VIEW_RECIPE) {
			EmiRecipe recipe = EmiApi.getRecipeManager().getRecipe(id);
			if (recipe != null) {
				EmiApi.displayRecipe(recipe);
			}
		} else if (type == EmiCommands.VIEW_TREE || type == EmiCommands.TREE_GOAL
				|| type == EmiCommands.TREE_RESOLUTION) {
			// The tree opcodes drive the BoM screen, which is not ported yet; the payload is
			// consumed above so the connection stays healthy. TODO(bom)
			EmiLog.info("Ignoring /emi tree command; the recipe tree is not ported yet");
		}
	}

	@Override
	public Type<CommandS2CPacket> type() {
		return EmiNetwork.COMMAND;
	}
}

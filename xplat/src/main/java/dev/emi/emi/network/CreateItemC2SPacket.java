package dev.emi.emi.network;

import dev.emi.emi.runtime.EmiLog;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Cheat-mode item creation. The server-side permission gate below is the only line of defense —
 * any client can send this packet regardless of UI state, so it must hold unconditionally.
 *
 * <p>Port note: the client-side trigger (cheat mode in the index panel) returns with the config
 * subsystem. TODO(polish)
 */
public class CreateItemC2SPacket implements EmiPacket {
	public static final StreamCodec<RegistryFriendlyByteBuf, CreateItemC2SPacket> CODEC
		= CustomPacketPayload.codec(CreateItemC2SPacket::write, CreateItemC2SPacket::new);

	private final int mode;
	private final ItemStack stack;

	public CreateItemC2SPacket(int mode, ItemStack stack) {
		this.mode = mode;
		this.stack = stack;
	}

	public CreateItemC2SPacket(RegistryFriendlyByteBuf buf) {
		this(buf.readByte(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
	}

	@Override
	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeByte(mode);
		ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
	}

	@Override
	public void apply(Player player) {
		if ((player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || player.isCreative())
				&& player.containerMenu != null) {
			if (stack.isEmpty()) {
				if (mode == 1 && !player.containerMenu.getCarried().isEmpty()) {
					EmiLog.info(player.getName().getString() + " deleted " + player.containerMenu.getCarried());
					player.containerMenu.setCarried(stack);
				}
			} else {
				EmiLog.info(player.getName().getString() + " cheated in " + stack);
				if (mode == 0) {
					player.getInventory().placeItemBackInInventory(stack);
				} else if (mode == 1) {
					player.containerMenu.setCarried(stack);
				}
			}
		}
	}

	@Override
	public Type<CreateItemC2SPacket> type() {
		return EmiNetwork.CREATE_ITEM;
	}
}

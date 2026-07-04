package dev.emi.emi.network;

import dev.emi.emi.platform.EmiClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/**
 * Sent by a server with EMI installed when a player joins; flips the client into "server has EMI"
 * mode so craft fills go through {@link FillRecipeC2SPacket} instead of the client-side fallback.
 */
public class PingS2CPacket implements EmiPacket {
	public static final StreamCodec<RegistryFriendlyByteBuf, PingS2CPacket> CODEC
		= CustomPacketPayload.codec(PingS2CPacket::write, PingS2CPacket::new);

	public PingS2CPacket() {
	}

	public PingS2CPacket(RegistryFriendlyByteBuf buf) {
	}

	@Override
	public void write(RegistryFriendlyByteBuf buf) {
	}

	@Override
	public void apply(Player player) {
		EmiClient.onServer = true;
	}

	@Override
	public Type<PingS2CPacket> type() {
		return EmiNetwork.PING;
	}
}

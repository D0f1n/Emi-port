package dev.emi.emi.network;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import dev.emi.emi.EmiPort;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Packet type constants and the send dispatch. Registration and the actual send/receive plumbing
 * are loader-specific (Fabric Networking API / NeoForge payload API); the loaders inject their
 * senders through {@link #initServer} and {@link #initClient}.
 *
 * <p>Port note: the original's {@code CommandS2CPacket} (the /emi command and BoM navigation) and
 * the chess packets return with their subsystems. TODO(polish)
 */
public class EmiNetwork {
	public static final CustomPacketPayload.Type<FillRecipeC2SPacket> FILL_RECIPE = new CustomPacketPayload.Type<>(EmiPort.id("emi:fill_recipe"));
	public static final CustomPacketPayload.Type<CreateItemC2SPacket> CREATE_ITEM = new CustomPacketPayload.Type<>(EmiPort.id("emi:create_item"));
	public static final CustomPacketPayload.Type<PingS2CPacket> PING = new CustomPacketPayload.Type<>(EmiPort.id("emi:ping"));
	public static final CustomPacketPayload.Type<RecipeSyncS2CPacket> SYNC_RECIPES = new CustomPacketPayload.Type<>(EmiPort.id("emi:sync_recipes"));
	private static BiConsumer<ServerPlayer, EmiPacket> clientSender;
	private static Consumer<EmiPacket> serverSender;

	public static void initServer(BiConsumer<ServerPlayer, EmiPacket> sender) {
		clientSender = sender;
	}

	public static void initClient(Consumer<EmiPacket> sender) {
		serverSender = sender;
	}

	public static void sendToClient(ServerPlayer player, EmiPacket packet) {
		clientSender.accept(player, packet);
	}

	public static void sendToServer(EmiPacket packet) {
		serverSender.accept(packet);
	}
}

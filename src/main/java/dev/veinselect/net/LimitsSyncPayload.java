package dev.veinselect.net;

import dev.veinselect.VeinSelect;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → Client: gewünschte Blockanzahl des Spielers (z. B. nach {@code /veinselect max}, damit der Client sie speichert)
 * und das Maximum, das der Server diesem Spieler erlaubt.
 */
public record LimitsSyncPayload(int maxBlocks, int allowed) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<LimitsSyncPayload> TYPE = new CustomPacketPayload.Type<>(VeinSelect.id("limits_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LimitsSyncPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, LimitsSyncPayload::maxBlocks,
			ByteBufCodecs.VAR_INT, LimitsSyncPayload::allowed,
			LimitsSyncPayload::new).cast();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

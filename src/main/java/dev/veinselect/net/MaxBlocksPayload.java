package dev.veinselect.net;

import dev.veinselect.VeinSelect;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → Server: gewünschte maximale Blockanzahl aus den Einstellungen des Spielers. */
public record MaxBlocksPayload(int maxBlocks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<MaxBlocksPayload> TYPE = new CustomPacketPayload.Type<>(VeinSelect.id("max_blocks"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MaxBlocksPayload> CODEC =
			ByteBufCodecs.VAR_INT.map(MaxBlocksPayload::new, MaxBlocksPayload::maxBlocks).cast();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

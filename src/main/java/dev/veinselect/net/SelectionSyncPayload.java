package dev.veinselect.net;

import java.util.List;

import dev.veinselect.VeinSelect;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → Client: aktuell ausgewählte Blöcke (leer = keine Auswahl), nur für die Hervorhebung. */
public record SelectionSyncPayload(List<BlockPos> positions) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SelectionSyncPayload> TYPE = new CustomPacketPayload.Type<>(VeinSelect.id("selection_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SelectionSyncPayload> CODEC =
			BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()).map(SelectionSyncPayload::new, SelectionSyncPayload::positions).cast();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

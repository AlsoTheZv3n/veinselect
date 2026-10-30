package dev.veinselect.net;

import java.util.Optional;

import dev.veinselect.VeinSelect;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → Server: Block, auf den der Spieler schaut (leer = Auswahl aufheben). */
public record SelectVeinPayload(Optional<BlockPos> target) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SelectVeinPayload> TYPE = new CustomPacketPayload.Type<>(VeinSelect.id("select_vein"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SelectVeinPayload> CODEC =
			BlockPos.STREAM_CODEC.apply(ByteBufCodecs::optional).map(SelectVeinPayload::new, SelectVeinPayload::target).cast();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

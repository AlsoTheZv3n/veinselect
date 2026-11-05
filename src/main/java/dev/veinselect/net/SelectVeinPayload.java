package dev.veinselect.net;

import java.util.Optional;

import dev.veinselect.VeinSelect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → Server: Block, auf den der Spieler schaut, und die getroffene Seite (bestimmt die Richtung der Formen).
 * Leeres {@code target} = Auswahl aufheben.
 */
public record SelectVeinPayload(Optional<BlockPos> target, Direction face) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SelectVeinPayload> TYPE = new CustomPacketPayload.Type<>(VeinSelect.id("select_vein"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SelectVeinPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC.apply(ByteBufCodecs::optional), SelectVeinPayload::target,
			Direction.STREAM_CODEC, SelectVeinPayload::face,
			SelectVeinPayload::new).cast();

	public static SelectVeinPayload clear() {
		return new SelectVeinPayload(Optional.empty(), Direction.UP);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

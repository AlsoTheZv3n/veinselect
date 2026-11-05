package dev.veinselect.net;

import dev.veinselect.VeinMode;
import dev.veinselect.VeinSelect;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Beide Richtungen. Client → Server: Modus gewechselt (V + Mausrad, Einstellungen, beim Verbinden).
 * Server → Client: nach {@code /veinselect mode}, damit der Client den Modus speichert.
 */
public record ModePayload(VeinMode mode) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ModePayload> TYPE = new CustomPacketPayload.Type<>(VeinSelect.id("mode"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ModePayload> CODEC =
			ByteBufCodecs.VAR_INT.map(VeinMode::byIndex, VeinMode::ordinal).map(ModePayload::new, ModePayload::mode).cast();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

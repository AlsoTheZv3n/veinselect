package dev.veinselect;

import java.util.Optional;

import net.minecraft.network.chat.Component;

/** Auswahl-Modi in der Reihenfolge, in der V + Mausrad sie durchschaltet. */
public enum VeinMode {
	/** Zusammenhängende Blöcke desselben Typs, auch diagonal. */
	VEIN("vein", "Vein", false),
	/** Gerade 1×1-Linie in Blickrichtung, bis Luft, Flüssigkeit oder Limit. */
	TUNNEL("tunnel", "Tunnel", true),
	/** 3×3-Fläche quer zur Blickrichtung. */
	THREE_BY_THREE("3x3", "3×3", true),
	/** Treppe nach oben mit 3 Blöcken Kopffreiheit. */
	STAIRS_UP("stairs_up", "Stairs up", true),
	/** Treppe nach unten mit 3 Blöcken Kopffreiheit. */
	STAIRS_DOWN("stairs_down", "Stairs down", true);

	private static final VeinMode[] VALUES = values();

	private final String id;
	private final String fallbackName;
	private final boolean shape;

	VeinMode(String id, String fallbackName, boolean shape) {
		this.id = id;
		this.fallbackName = fallbackName;
		this.shape = shape;
	}

	/** Name in Befehlen und in der Client-Einstellung. */
	public String id() {
		return id;
	}

	/** Formen bauen gemischte Blöcke ab, die Ader nur den Blocktyp des Ursprungs. */
	public boolean isShape() {
		return shape;
	}

	public Component displayName() {
		// Fallback für die Server-Konsole, die keine Sprachdateien von Mods lädt
		return Component.translatableWithFallback("mode.veinselect." + id, fallbackName);
	}

	/** {@code steps} Modi weiter (negativ = zurück), am Ende geht es wieder von vorn los. */
	public VeinMode cycle(int steps) {
		return VALUES[Math.floorMod(ordinal() + steps, VALUES.length)];
	}

	/** Für Pakete: unbekannte Werte werden zur Ader, statt die Verbindung zu trennen. */
	public static VeinMode byIndex(int index) {
		return index >= 0 && index < VALUES.length ? VALUES[index] : VEIN;
	}

	public static Optional<VeinMode> byId(String id) {
		for (VeinMode mode : VALUES) {
			if (mode.id.equals(id)) return Optional.of(mode);
		}
		return Optional.empty();
	}
}

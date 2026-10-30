package dev.veinselect;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

/** Server-Einstellungen in {@code config/veinselect.json}, änderbar per {@code /veinselect serverlimit}. */
public final class VeinSelectConfig {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("veinselect.json");

	private static int serverLimit = VeinSelect.DEFAULT_LIMIT;

	private VeinSelectConfig() {}

	/** Höchstwert für alle Spieler außer dem Owner. */
	public static int serverLimit() {
		return serverLimit;
	}

	public static void setServerLimit(int limit) {
		serverLimit = VeinSelect.clampLimit(limit);
		save();
	}

	static void load() {
		serverLimit = VeinSelect.DEFAULT_LIMIT;

		if (Files.exists(FILE)) {
			try {
				JsonObject json = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
				if (json.has("serverLimit")) serverLimit = VeinSelect.clampLimit(json.get("serverLimit").getAsInt());
			} catch (IOException | RuntimeException e) {
				VeinSelect.LOGGER.warn("Konnte {} nicht lesen, verwende Standardwerte", FILE, e);
			}
		}

		// Datei immer (neu) schreiben, damit sie existiert und ungültige Werte korrigiert sind
		save();
	}

	private static void save() {
		JsonObject json = new JsonObject();
		json.addProperty("serverLimit", serverLimit);

		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(json));
		} catch (IOException e) {
			VeinSelect.LOGGER.warn("Konnte {} nicht speichern", FILE, e);
		}
	}
}

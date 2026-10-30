package dev.veinselect.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.veinselect.VeinSelect;

import net.fabricmc.loader.api.FabricLoader;

/** Spieler-Einstellungen in {@code config/veinselect-client.json}. Der Server begrenzt den Wert zusätzlich. */
public final class VeinSelectClientConfig {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("veinselect-client.json");

	private static int maxBlocks = VeinSelect.DEFAULT_LIMIT;

	private VeinSelectClientConfig() {}

	public static int maxBlocks() {
		return maxBlocks;
	}

	public static void setMaxBlocks(int value) {
		int clamped = VeinSelect.clampLimit(value);
		if (clamped == maxBlocks) return;

		maxBlocks = clamped;
		save();
	}

	static void load() {
		if (Files.exists(FILE)) {
			try {
				JsonObject json = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
				if (json.has("maxBlocks")) maxBlocks = VeinSelect.clampLimit(json.get("maxBlocks").getAsInt());
			} catch (IOException | RuntimeException e) {
				VeinSelect.LOGGER.warn("Konnte {} nicht lesen, verwende Standardwerte", FILE, e);
			}
		}

		save();
	}

	private static void save() {
		JsonObject json = new JsonObject();
		json.addProperty("maxBlocks", maxBlocks);

		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(json));
		} catch (IOException e) {
			VeinSelect.LOGGER.warn("Konnte {} nicht speichern", FILE, e);
		}
	}
}

package dev.veinselect.gametest;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.veinselect.VeinMode;
import dev.veinselect.VeinSelect;
import dev.veinselect.VeinSelection;
import dev.veinselect.client.VeinSelectClientConfig;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Hud;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Testet Vein Select im echten Client: Auswahl, Abbau, Moduswechsel per V + Mausrad und die Hervorhebung.
 * Start: {@code gradlew runClientGameTest}. Liegen Iris und Sodium in {@code build/run/clientGameTest/mods}
 * (und ein Shader-Pack laut {@code config/iris.properties}), entstehen zusätzlich Screenshots mit Shadern.
 * Alle Prüfungen laufen durch; Fehler werden am Ende gesammelt gemeldet.
 */
public class VeinSelectClientGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("veinselect-gametest");
	private static final String PLAYER = "Player0";
	/** Testszenen schweben über der flachen Welt, damit Treppen nicht im Boden enden. */
	private static final int Y = -40;

	private final List<String> failures = new ArrayList<>();
	private ClientGameTestContext context;
	private TestServerContext server;
	private TestServerConnection connection;
	private KeyMapping selectKey;

	@Override
	public void runTest(ClientGameTestContext context) {
		this.context = context;
		selectKey = KeyMapping.get("key.veinselect.select");
		check(selectKey != null, "Taste key.veinselect.select ist registriert");

		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(true).create()) {
			server = world.getServer();
			connection = world.getConnection();
			connection.waitForChunksRender();

			server.runCommand("difficulty peaceful");
			server.runCommand("time set noon");
			server.runCommand("gamemode survival " + PLAYER);
			server.runCommand("item replace entity " + PLAYER + " hotbar.0 with minecraft:diamond_pickaxe");
			context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));

			unbreakableOriginIsRejected();
			stairsDownMinesExactlyTheShape();
			scrollSwitchesModeAndRebuilds();
			highlightScreenshots();
		}

		if (!failures.isEmpty()) {
			throw new AssertionError(failures.size() + " Prüfung(en) fehlgeschlagen:\n- " + String.join("\n- ", failures));
		}
		LOGGER.info("VEINSELECT-TEST: alle Prüfungen bestanden");
	}

	/** Sicherheitsfix 1.1.1: 3×3 auf Grundgestein, dann Nachbarblock abbauen, darf das Grundgestein nicht zerstören. */
	private void unbreakableOriginIsRejected() {
		int x = 20;
		server.runCommand(fill(x - 1, Y - 1, 0, x + 1, Y - 1, 2, "stone"));
		server.runCommand(fill(x - 1, Y, -2, x + 1, Y + 2, -2, "deepslate"));
		BlockPos bedrock = new BlockPos(x, Y + 1, -2);
		server.runCommand("setblock " + pos(bedrock) + " bedrock");
		standAt(x, 0);
		setMode(VeinMode.THREE_BY_THREE);

		lookAt(bedrock);
		tapSelect();
		check(selection() == null, "Grundgestein: V im 3×3-Modus wählt nichts aus");

		mine(new BlockPos(x + 1, Y + 1, -2));
		context.waitTicks(10);
		check(blockIs(bedrock, "bedrock"), "Grundgestein steht nach dem Abbau des Nachbarblocks noch");
		int deepslate = count(x - 1, Y, -2, x + 1, Y + 2, -2, "deepslate");
		check(deepslate == 7, "Nur der angeklickte Tiefenschiefer ist weg (übrig: " + deepslate + ", erwartet 7)");
	}

	/** Treppe runter: richtige Form, und genau diese Blöcke werden abgebaut. */
	private void stairsDownMinesExactlyTheShape() {
		buildCube();
		standAt(0, 0);
		setMode(VeinMode.STAIRS_DOWN);

		BlockPos origin = new BlockPos(0, Y + 1, -2);
		lookAt(origin);
		tapSelect();
		VeinSelection selection = selection();
		check(selection != null && selection.mode() == VeinMode.STAIRS_DOWN, "Treppe runter: Auswahl vorhanden");
		if (selection == null) return;

		// Erste Stufe: Ursprung und zwei darunter, zweite Stufe einen weiter und einen tiefer
		for (BlockPos expected : List.of(origin, origin.below(), origin.below(2), origin.north().below(), origin.north().below(2), origin.north().below(3))) {
			check(selection.blocks().containsKey(expected), "Treppe runter enthält " + pos(expected));
		}
		for (BlockPos outside : List.of(origin.above(), origin.east(), origin.north())) {
			check(!selection.blocks().containsKey(outside), "Treppe runter enthält nicht " + pos(outside));
		}
		check(selection.blocks().size() > 20, "Treppe runter geht tief in den Würfel (" + selection.blocks().size() + " Blöcke)");
		checkOverlay(Component.translatable("message.veinselect.selected_shape", VeinMode.STAIRS_DOWN.displayName(), selection.blocks().size()),
				"Kurze Meldung nach V auf Treppe runter");

		mine(origin);
		int waited = server.waitFor(s -> selection.blocks().keySet().stream().allMatch(p -> connection.getServerLevel().getBlockState(p).isAir()), 100);
		check(waited >= 0, "Alle " + selection.blocks().size() + " Treppenblöcke abgebaut");
		for (BlockPos outside : List.of(origin.above(), origin.east(), origin.north())) {
			check(blockIs(outside, "stone"), "Nicht ausgewählter Block " + pos(outside) + " steht noch");
		}
		int mined = 11 * 17 * 13 - count(-5, Y - 12, -14, 5, Y + 4, -2, "stone");
		check(mined == selection.blocks().size(), "Genau die Auswahl abgebaut (" + mined + " von " + selection.blocks().size() + ")");
	}

	/** V halten + Mausrad: Modus wechselt, bestehende Auswahl wird neu berechnet, Hotbar bleibt; Antippen wählt ab. */
	private void scrollSwitchesModeAndRebuilds() {
		buildCube();
		standAt(0, 0);
		setMode(VeinMode.TUNNEL);

		BlockPos origin = new BlockPos(0, Y + 1, -2);
		lookAt(origin);
		tapSelect();
		VeinSelection tunnel = selection();
		check(tunnel != null && tunnel.mode() == VeinMode.TUNNEL && tunnel.blocks().size() == 13,
				"Tunnel: 13 Blöcke durch den Würfel (" + (tunnel == null ? "keine Auswahl" : tunnel.blocks().size()) + ")");
		checkOverlay(Component.translatable("message.veinselect.selected_shape", VeinMode.TUNNEL.displayName(), 13), "Kurze Meldung nach V auf Tunnel");

		int slot = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
		scrollWithSelectKey(-1);
		check(VeinSelectClientConfig.mode() == VeinMode.THREE_BY_THREE, "Mausrad runter: Client-Modus 3×3 (ist " + VeinSelectClientConfig.mode() + ")");
		check(server.computeOnServer(s -> VeinSelect.mode(connection.getServerPlayer())) == VeinMode.THREE_BY_THREE, "Mausrad runter: Server-Modus 3×3");
		VeinSelection rebuilt = selection();
		check(rebuilt != null && rebuilt.mode() == VeinMode.THREE_BY_THREE && rebuilt.blocks().size() == 9,
				"Auswahl beim Scrollen behalten und als 3×3 neu berechnet (" + (rebuilt == null ? "aufgehoben" : rebuilt.mode() + ", " + rebuilt.blocks().size()) + ")");
		check(context.computeOnClient(client -> client.player.getInventory().getSelectedSlot()) == slot, "Hotbar-Slot bleibt beim Scrollen mit V");
		// Nach dem Neuberechnen darf der Server den Namen nicht mit einer langen Meldung überschreiben
		checkOverlay(VeinMode.THREE_BY_THREE.displayName(), "Beim Wechseln steht unten nur der Modusname");

		scrollWithSelectKey(1);
		check(VeinSelectClientConfig.mode() == VeinMode.TUNNEL, "Mausrad hoch: zurück zu Tunnel");
		checkOverlay(VeinMode.TUNNEL.displayName(), "Zurückwechseln zeigt nur den Modusnamen");

		// Weitere Wechsel bis „Treppe hoch“: nur der Name, nichts dahinter
		scrollWithSelectKey(-2);
		check(VeinSelectClientConfig.mode() == VeinMode.STAIRS_UP, "Zwei Rastungen runter: Treppe hoch");
		checkOverlay(VeinMode.STAIRS_UP.displayName(), "Treppe hoch zeigt nur den Namen");
		scrollWithSelectKey(2);
		check(VeinSelectClientConfig.mode() == VeinMode.TUNNEL, "Zwei Rastungen hoch: wieder Tunnel");

		tapSelect();
		check(selection() == null, "V antippen auf die Auswahl hebt sie auf");

		context.getInput().scroll(-1);
		context.waitTick();
		check(context.computeOnClient(client -> client.player.getInventory().getSelectedSlot()) != slot, "Mausrad ohne V wechselt weiter den Hotbar-Slot");
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(slot));
		context.waitTicks(2);
	}

	/** Screenshots einer Treppe runter im Würfel: ohne/mit Verbesserter Transparenz und, falls Iris da ist, mit Shadern. */
	private void highlightScreenshots() {
		buildCube();
		standAt(0, 0);
		setMode(VeinMode.STAIRS_DOWN);
		lookAt(new BlockPos(0, Y + 1, -2));
		tapSelect();
		check(selection() != null, "Treppe für Screenshots ausgewählt");

		// Etwas zurück und schräg, damit die Treppe im Würfel ganz im Bild ist (Plattform, sonst stürzt man in Survival ab)
		server.runCommand(fill(6, Y + 5, 5, 8, Y + 5, 7, "stone"));
		server.runCommand("tp " + PLAYER + " 7.5 " + (Y + 6) + " 6.5");
		context.waitTicks(5);
		lookAt(new BlockPos(0, Y - 3, -7));
		connection.waitForChunksRender();
		context.waitTicks(5);
		logSeeThroughState("Vanilla");
		screenshot("stairs_down");

		context.runOnClient(client -> client.options.improvedTransparency().set(true));
		context.waitTicks(20);
		screenshot("stairs_down_improved_transparency");
		context.runOnClient(client -> client.options.improvedTransparency().set(false));
		context.waitTicks(20);

		if (!FabricLoader.getInstance().isModLoaded("iris")) {
			LOGGER.info("VEINSELECT-TEST: Iris nicht installiert, keine Shader-Screenshots");
			return;
		}

		screenshot("stairs_down_sodium_no_shaders");
		context.runOnClient(client -> setIrisShaders(true));
		context.waitFor(client -> irisShaderPackInUse(), 1200);
		connection.waitForChunksRender();
		context.waitTicks(40);
		logSeeThroughState("Iris mit Shader-Pack");
		screenshot("stairs_down_shaders");
		context.runOnClient(client -> setIrisShaders(false));
		context.waitTicks(20);
	}

	// --- Szene und Eingaben ---

	/** Steinwürfel 11 × 17 × 13 nördlich der Plattform, Vorderseite bei z = -2. */
	private void buildCube() {
		server.runCommand(fill(-5, Y - 12, -14, 5, Y + 4, -2, "stone"));
	}

	/** Plattform bauen, Spieler draufstellen, Blick nach Norden. */
	private void standAt(int x, int z) {
		server.runCommand(fill(x - 1, Y - 1, z, x + 1, Y - 1, z + 2, "stone"));
		server.runCommand("tp " + PLAYER + " " + (x + 0.5) + " " + Y + " " + (z + 0.5) + " 180 0");
		context.waitTicks(5);
		connection.waitForChunksRender();
	}

	private void setMode(VeinMode mode) {
		server.runCommand("execute as " + PLAYER + " run veinselect mode " + mode.id());
		connection.waitForClientboundPackets();
		context.waitTick();
	}

	private void lookAt(BlockPos pos) {
		context.getInput().lookAt(pos);
		context.waitTick();
	}

	/** V kurz drücken und warten, bis der Server geantwortet hat. */
	private void tapSelect() {
		context.getInput().pressKey(selectKey);
		syncPackets();
	}

	/** V halten, Mausrad drehen (positiv = hoch), loslassen. */
	private void scrollWithSelectKey(double amount) {
		TestInput input = context.getInput();
		input.holdKey(selectKey);
		context.waitTick();
		input.scroll(amount);
		context.waitTick();
		input.releaseKey(selectKey);
		context.waitTick();
		syncPackets();
	}

	private void syncPackets() {
		context.waitTick();
		connection.waitForServerboundPackets();
		connection.waitForClientboundPackets();
		context.waitTick();
	}

	/** Block anvisieren und mit der Angriffstaste abbauen, bis er weg ist. */
	private void mine(BlockPos pos) {
		lookAt(pos);
		context.getInput().holdKey(options -> options.keyAttack);
		server.waitFor(s -> connection.getServerLevel().getBlockState(pos).isAir(), 200);
		context.getInput().releaseKey(options -> options.keyAttack);
		context.waitTicks(2);
	}

	private void screenshot(String name) {
		Path path = context.takeScreenshot("veinselect_" + name);
		LOGGER.info("VEINSELECT-TEST: Screenshot {}", path.toAbsolutePath());
	}

	// --- Zustand lesen ---

	/** Aktuelle Auswahl des Spielers auf dem Server, oder {@code null}. */
	@SuppressWarnings("unchecked")
	private VeinSelection selection() {
		return server.computeOnServer(s -> {
			try {
				Field field = VeinSelect.class.getDeclaredField("SELECTIONS");
				field.setAccessible(true);
				Map<UUID, VeinSelection> selections = (Map<UUID, VeinSelection>) field.get(null);
				return selections.get(connection.getServerPlayer().getUUID());
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("VeinSelect.SELECTIONS nicht lesbar", e);
			}
		});
	}

	/** Vergleicht die Meldung über der Hotbar (Action Bar) mit {@code expected} in der Sprache des Clients. */
	private void checkOverlay(Component expected, String description) {
		String[] texts = context.computeOnClient(client -> {
			try {
				Field field = Hud.class.getDeclaredField("overlayMessageString");
				field.setAccessible(true);
				Component shown = (Component) field.get(client.gui.hud);
				return new String[] {expected.getString(), shown == null ? "" : shown.getString()};
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Hud.overlayMessageString nicht lesbar", e);
			}
		});
		check(texts[0].equals(texts[1]), description + ": „" + texts[1] + "“ (erwartet „" + texts[0] + "“)");
	}

	private boolean blockIs(BlockPos pos, String id) {
		return server.computeOnServer(s -> connection.getServerLevel().getBlockState(pos).getBlock() == block(id));
	}

	private int count(int x1, int y1, int z1, int x2, int y2, int z2, String id) {
		return server.computeOnServer(s -> {
			int n = 0;
			for (BlockPos p : BlockPos.betweenClosed(x1, y1, z1, x2, y2, z2)) {
				if (connection.getServerLevel().getBlockState(p).getBlock() == block(id)) n++;
			}
			return n;
		});
	}

	private static net.minecraft.world.level.block.Block block(String id) {
		return switch (id) {
			case "stone" -> Blocks.STONE;
			case "deepslate" -> Blocks.DEEPSLATE;
			case "bedrock" -> Blocks.BEDROCK;
			default -> throw new IllegalArgumentException(id);
		};
	}

	/** Ob die Umrandung durch Blöcke gerade gezeichnet werden kann (aus SeeThroughLines gelesen). */
	private void logSeeThroughState(String label) {
		try {
			Class<?> seeThrough = Class.forName("dev.veinselect.client.SeeThroughLines");
			Field renderType = seeThrough.getDeclaredField("RENDER_TYPE");
			Field shaderReady = seeThrough.getDeclaredField("SHADER_READY");
			renderType.setAccessible(true);
			shaderReady.setAccessible(true);
			boolean available = renderType.get(null) != null;
			LOGGER.info("VEINSELECT-TEST: {}: Pipeline durch Blöcke erzeugt={}, Iris zugeordnet={}", label, available, shaderReady.get(null));
			check(available, label + ": Pipeline für die Umrandung durch Blöcke wurde erzeugt");
			if (FabricLoader.getInstance().isModLoaded("iris")) {
				check((boolean) shaderReady.get(null), label + ": Iris hat die Pipeline als Linien zugeordnet");
			}
		} catch (ReflectiveOperationException e) {
			check(false, label + ": SeeThroughLines nicht lesbar: " + e);
		}
	}

	// --- Iris per Reflection, damit der Test auch ohne Iris kompiliert und läuft ---

	private static Object irisApi() throws ReflectiveOperationException {
		return Class.forName("net.irisshaders.iris.api.v0.IrisApi").getMethod("getInstance").invoke(null);
	}

	/** Muss auf dem Render-Thread laufen (runOnClient). */
	private static void setIrisShaders(boolean enabled) {
		try {
			Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object config = apiClass.getMethod("getConfig").invoke(irisApi());
			Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig").getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, enabled);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Iris-Shader nicht umschaltbar", e);
		}
	}

	private static boolean irisShaderPackInUse() {
		try {
			return (boolean) Class.forName("net.irisshaders.iris.api.v0.IrisApi").getMethod("isShaderPackInUse").invoke(irisApi());
		} catch (ReflectiveOperationException e) {
			return false;
		}
	}

	// --- Hilfen ---

	private void check(boolean ok, String description) {
		LOGGER.info("VEINSELECT-TEST: {} {}", ok ? "OK  " : "FAIL", description);
		if (!ok) failures.add(description);
	}

	private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
		return "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " minecraft:" + block;
	}

	private static String pos(BlockPos p) {
		return p.getX() + " " + p.getY() + " " + p.getZ();
	}
}

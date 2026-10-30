# Vein Select (Fabric, Minecraft 26.3)

Taste drücken (Standard: **V**) → zusammenhängende Blöcke desselben Typs werden ausgewählt und blau umrandet (Standard: bis zu **64**). Einen davon abbauen → alle werden abgebaut.

- Nochmals **V** auf die Auswahl oder ins Leere = Auswahl aufheben
- Abbau läuft über den normalen Vanilla-Weg: Drops, Werkzeug-Haltbarkeit, Glück/Behutsamkeit, Hunger
- Spawn-Schutz, Weltgrenze und Abenteuer-Modus werden für jeden einzelnen Block geprüft
- Bricht das Werkzeug oder wechselt man den Hotbar-Slot, stoppt der Abbau
- Große Auswahlen werden mit 64 Blöcken pro Tick abgebaut, damit der Server nicht hängt
- Taste änderbar unter Optionen → Steuerung → Tastenbelegung → „Vein Select"

## Shader

Die Auswahl wird als Umrandung gezeichnet, genauso wie die Vanilla-Blockumrandung, und ist damit auch mit Sodium/Iris und Shader-Packs sichtbar. Ohne Shader kommt zusätzlich eine halbtransparente Füllung dazu.

## Limit

| Wer | Darf bis |
|---|---|
| Normale Spieler | Server-Limit (Standard 64) |
| Owner (OP-Level 4, im Singleplayer/LAN der Host) | 4096 (technische Obergrenze) |

Jeder Spieler stellt seinen eigenen Wert ein. Wirksam ist der kleinere Wert aus eigener Einstellung und erlaubtem Maximum.

**Spieler:**
- Mod Menu → Vein Select → Einstellungen (wenn [Mod Menu](https://modrinth.com/mod/modmenu) installiert ist), oder
- `/veinselect max <anzahl>`
- `/veinselect` zeigt die aktuellen Werte
- gespeichert in `config/veinselect-client.json`

**Owner:**
- `/veinselect serverlimit <anzahl>` (1–4096), funktioniert auch in der Server-Konsole
- `/veinselect serverlimit` zeigt den aktuellen Wert
- gespeichert in `config/veinselect.json` auf dem Server (kann auch direkt bearbeitet werden, gilt nach Neustart)

## Bauen

Voraussetzung: **JDK 25** (`JAVA_HOME` muss darauf zeigen)

```
gradlew build        (Windows)
./gradlew build      (macOS/Linux)
```

Ergebnis: `build/libs/veinselect-1.0.0.jar`

## Installieren

1. Fabric Loader **0.20.1+** für Minecraft **26.3** installieren
2. In den `mods`-Ordner: `veinselect-1.0.0.jar` + **Fabric API 0.165.0+26.3** (oder neuer für 26.3)
3. Optional: **Mod Menu 21.0.0+** für das Einstellungsmenü
4. Multiplayer: Mod muss auch auf dem Server installiert sein

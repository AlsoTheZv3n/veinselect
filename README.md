# Vein Select (Fabric, Minecraft 26.3)

Taste drücken (Standard: **V**) → bis zu **64** zusammenhängende Blöcke desselben Typs werden ausgewählt und blau hervorgehoben. Einen davon abbauen → alle werden gleichzeitig abgebaut.

- Nochmals **V** auf die Auswahl oder ins Leere = Auswahl aufheben
- Abbau läuft über den normalen Vanilla-Weg: Drops, Werkzeug-Haltbarkeit, Glück/Behutsamkeit, Hunger, Spawn-Schutz
- Bricht das Werkzeug, stoppt der Abbau
- Taste änderbar unter Optionen → Steuerung → Tastenbelegung → „Vein Select"

## Bauen

Voraussetzung: **JDK 25**

```
gradlew build        (Windows)
./gradlew build      (macOS/Linux)
```

Ergebnis: `build/libs/veinselect-1.0.0.jar`

## Installieren

1. Fabric Loader **0.20.1+** für Minecraft **26.3** installieren
2. In den `mods`-Ordner: `veinselect-1.0.0.jar` + **Fabric API 0.165.0+26.3** (oder neuer für 26.3)
3. Multiplayer: Mod muss auch auf dem Server installiert sein

## Anpassen

Maximale Blockanzahl: `MAX_BLOCKS` in `src/main/java/dev/veinselect/VeinSelect.java`

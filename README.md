# Vein Select (Fabric, Minecraft 26.3)

Taste drücken (Standard: **V**) → Blöcke werden je nach Modus ausgewählt und blau umrandet (Standard: bis zu **64**). Einen davon abbauen → alle werden abgebaut.

## Modi

| Modus | Wählt aus |
|---|---|
| **Ader** (Standard) | Zusammenhängende Blöcke desselben Typs, auch diagonal |
| **Tunnel** | Gerade 1×1-Linie in Blickrichtung, bis Luft, Flüssigkeit oder Limit |
| **3×3** | 3×3-Fläche quer zur Blickrichtung (auch nach oben/unten) |
| **Treppe hoch** | Stufen nach oben mit 3 Blöcken Kopffreiheit |
| **Treppe runter** | Stufen nach unten mit 3 Blöcken Kopffreiheit |

Die Richtung ergibt sich aus der Seite des Blocks, auf die man schaut. Treppen laufen immer waagerecht, bei Ober-/Unterseite in Blickrichtung.

Die Formen (alles außer Ader) nehmen gemischte Blöcke mit, aber nie Luft, Flüssigkeiten oder Unzerstörbares. Außerhalb des Kreativmodus nur Blöcke, die mit dem gehaltenen Werkzeug etwas droppen, damit z. B. ein 3×3 mit der Schaufel keinen Stein vernichtet. Bei sofort abbaubaren Blöcken (Fackel, Blume) wählen die Formen nur diesen einen Block.

**Modus wechseln:**
- **V halten + Mausrad** (wie die Hotbar: nach oben = vorheriger Modus). Eine bestehende Auswahl wird sofort mit dem neuen Modus neu berechnet, man muss dafür nicht auf sie schauen.
- `/veinselect mode <vein|tunnel|3x3|stairs_up|stairs_down>`, `/veinselect mode` zeigt den aktuellen
- Mod Menu → Vein Select → Einstellungen
- wird in `config/veinselect-client.json` gespeichert

Die Formen sind an [Liteminer](https://github.com/iamkaf/liteminer) angelehnt.

## Bedienung

- **V** auf einen Block = auswählen (ersetzt eine bestehende Auswahl)
- **V** auf die Auswahl oder ins Leere und loslassen = Auswahl aufheben (außer man hat dabei mit dem Mausrad den Modus gewechselt)
- Abbau läuft über den normalen Vanilla-Weg: Drops, Werkzeug-Haltbarkeit, Glück/Behutsamkeit, Hunger
- Spawn-Schutz, Weltgrenze und Abenteuer-Modus werden für jeden einzelnen Block geprüft
- Bricht das Werkzeug oder wechselt man den Hotbar-Slot, stoppt der Abbau
- Große Auswahlen werden mit 64 Blöcken pro Tick abgebaut, damit der Server nicht hängt
- Taste änderbar unter Optionen → Steuerung → Tastenbelegung → „Vein Select"

## Shader

Die Auswahl wird als Umrandung gezeichnet, genauso wie die Vanilla-Blockumrandung, und ist damit auch mit Sodium/Iris und Shader-Packs sichtbar. Ohne Shader kommt zusätzlich eine halbtransparente Füllung dazu.

Teile der Auswahl, die in oder hinter Blöcken liegen, werden zusätzlich schwach durch die Blöcke hindurch umrandet. So sieht man z. B., wohin eine Treppe oder ein Tunnel führt. Mit Shader-Pack passiert das nur, wenn die Iris-Version eigene Render-Pipelines zuordnen kann (`assignPipeline`). Sonst bleibt es bei der normalen Umrandung.

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

Ergebnis: `build/libs/veinselect-1.1.0.jar`

## Installieren

1. Fabric Loader **0.20.1+** für Minecraft **26.3** installieren
2. In den `mods`-Ordner: `veinselect-1.1.0.jar` + **Fabric API 0.165.0+26.3** (oder neuer für 26.3)
3. Optional: **Mod Menu 21.0.0+** für das Einstellungsmenü
4. Multiplayer: Mod muss auch auf dem Server installiert sein, in derselben Version (1.1.0 ist nicht mit 1.0.0 kompatibel)

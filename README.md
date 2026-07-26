# RankedSMP (Fabric port)

Fabric 1.21.11 port of the [RankedSMP](https://modrinth.com/plugin/ranked-smp) Paper plugin
by Lusik21556. "Kill ranked players to steal their position!"

Assigns up to 20 online players a shuffled rank 1-20 with escalating health, potion
duration, XP gain and extra-inventory-space bonuses. Killing a better-ranked player
steals their rank (configurable). Includes the HierArchy Hammer (a custom Mace with a
dash + 4-hit combo execution), a Dragon Egg locator, and admin GUIs.

This is a **server-only mod** (`"environment": "server"` in fabric.mod.json) - it does
not need to be installed on clients, exactly like the original plugin didn't. Everything
is rendered through vanilla mechanics (scoreboard teams, chest-like GUIs, vanilla items
with custom data components) so unmodified clients connect normally.

## Building

Requires **JDK 21** to run Gradle/Loom (Minecraft 1.20.5+ requires Java 21 at runtime too).

```
JAVA_HOME=/path/to/jdk-21 ./gradlew build
```

The distributable jar is `build/libs/rankedsmp-<version>.jar` - drop it in a Fabric
1.21.11 server's `mods/` folder alongside [Fabric API](https://modrinth.com/mod/fabric-api).

To boot a local test server (accepts the Mojang EULA automatically via `run/eula.txt`):

```
./gradlew runServer
```

## Commands

- `/rankedsmp` (alias `/rsmp`) - `help`, `start`, `reload`, `manage`, `rank set/remove`,
  `give mace`, `extrainventory`. Admin subcommands require permission level 2 (op),
  matching the original's `rankedsmp.admin` (`default: op`).
- `/extrainventory` (alias `/einv`) - open your extra inventory (rank 10 or better).

## What changed vs. the original plugin

- **Data storage**: ranks and extra-inventory contents now ride along with the world
  save (a `PersistentState`) instead of a bundled SQLite database + YAML config; global
  settings (just the `keep-ranks` toggle) live in `config/rankedsmp.json`.
- **Dropped integrations**: the TAB and PlaceholderAPI hooks, AltarSMP protection hook,
  bStats metrics, and the Modrinth update-checker had no Fabric equivalent in scope and
  were removed. Rank display always uses vanilla scoreboard team prefixes now.
- **Dragon Egg locator**: always enabled - the underlying vanilla waypoint attributes
  the original gated behind "1.21.6+" detection are just always present on 1.21.11.
- **Rank management GUI bugfix**: in the original, left-clicking an *empty* slot to
  drop a selected player into a rank gap was unreachable dead code (an outer guard
  required the clicked slot to already hold a player head). This port makes that path
  actually work, matching what the item's own tooltip describes.
- **XP multiplier**: Bukkit's cancellable `PlayerExpChangeEvent` has no Fabric
  equivalent, so it's applied via a small Mixin into `PlayerEntity#addExperience`.
- **HierArchy Hammer name/lore**: the original's per-character legacy-color gradient
  name was simplified to a plain gold/bold display name; the mechanics (dash, combo
  counter, verdict execution) are unchanged.

## Project layout

```
src/main/java/net/lusik21556/rankedsmp/
  RankedSMP.java              mod entrypoint, wires everything together
  config/RankedConfig.java    config/rankedsmp.json (gameplay.keep-ranks)
  data/RankedSaveData.java    per-world PersistentState (ranks + extra inventories)
  rank/RankManager.java       ranks, health scaling, potion/XP multipliers, scoreboard
  inventory/InventoryManager.java  extra-inventory slots, persistence, resizing
  gui/                        vanilla chest-GUI screen handlers (extra inv + rank mgmt)
  item/HierarchyHammer*.java  the Mace item marker + dash/verdict combat handler
  listener/                   join/leave/death/respawn, potion multiplier, egg locator
  command/RankedCommand.java  /rankedsmp, /rsmp, /extrainventory, /einv
  mixin/PlayerEntityMixin.java XP multiplier hook
```

`old-plugin/RankedSMP-2.3.jar` is the original Paper plugin, kept for reference.

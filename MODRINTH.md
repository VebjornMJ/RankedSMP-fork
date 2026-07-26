# RankedSMP

**Kill ranked players to steal their position.**

A Fabric port of the original [RankedSMP](https://modrinth.com/plugin/ranked-smp) Paper plugin by Lusik21556. Up to 20 online players are shuffled into ranks 1–20, each rank granting escalating combat and utility bonuses. Kill someone ranked better than you, and their position becomes yours.

This is a **server-side only** mod — vanilla clients connect with no changes needed. Everything is built on vanilla mechanics: scoreboard teams for rank display, chest-style GUIs, and vanilla items carrying custom data components.

## Features

- **20 competitive ranks** — shuffled across online players, with escalating bonuses the higher you climb:
  - Extra max health
  - Longer potion effect durations
  - Increased XP gain
  - Extra inventory space (unlocked at rank 10 and better)
- **Steal-on-kill** — defeat a better-ranked player and take their rank (configurable)
- **HierArchy Hammer** — a custom Mace with a dash attack and a 4-hit combo execution finisher
- **Dragon Egg locator** — a built-in waypoint pointing toward the Dragon Egg
- **Admin GUIs** — manage ranks and player positions without touching commands
- **Persistent state** — ranks and extra-inventory contents are saved with the world, no external database required

## Commands

| Command | Description |
|---|---|
| `/rankedsmp` (`/rsmp`) | `help`, `start`, `reload`, `manage`, `rank set/remove`, `give mace`, `extrainventory` |
| `/extrainventory` (`/einv`) | Open your extra inventory (rank 10 or better) |

Admin subcommands require permission level 2 (op), matching the original plugin's `rankedsmp.admin` permission.

## Requirements

- Fabric Loader `0.19.3+`
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Minecraft `1.21.11`
- Java `21+`

Install on the **server only** — do not install on clients.

## Configuration

Global settings (currently just the `keep-ranks` toggle) live in `config/rankedsmp.json`. Ranks and extra-inventory contents ride along with the world save, so no external database setup is needed.

## Credits

Original concept, design, and Paper plugin by **Lusik21556**. This is an unofficial Fabric port bringing the same gameplay to Fabric servers.

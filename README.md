# Upgrader

Gamble your way up. The **Upgrader** is a Forge 1.20.1 tool that prices any item through a layered
value pipeline and lets you risk it against a richer target item on a spinning chance compass.

| | |
|---|---|
| **Minecraft** | 1.20.1 |
| **Forge** | 47.4.23 |
| **Mappings** | Official (Mojang) |
| **Java** | 17 |
| **Mod id** | `upgradermod` |
| **Author** | Popipok |

---

## How it plays

1. Craft/receive the **Upgrader** (it appears in the *Tools & Utilities* creative tab) and right
   click to open its screen.
2. Drop the item you want to consume into the **INPUT** slot.
3. Press **Pick target** (or `V`) to open the searchable catalogue and choose the item you want
   to win. You can also drop an item into the **TARGET** slot directly.
4. Set the **BET** multiplier with `-` / `+`. The multiplier divides the target value in the chance
   formula (a bigger bet is a harder spin) and, on a win, consumes and rewards that many items.
5. Press **SPIN** (or `R`). The compass spins, the needle lands on the rolled angle and you either
   get the target items or nothing.

Everything random and every inventory mutation happens **server side**. The client only receives
finished numbers.

## Value pipeline

Providers run in descending priority; the first positive result wins.

| Priority | Provider | Source |
|---|---|---|
| 1000 | `OverrideValueProvider` | `config/upgradermod/overrides.json` |
| 900 | `ProjectEValueProvider` | ProjectE EMC through **reflection only** (no compile dependency), active only when `projecte` is loaded |
| 700 | `RecipeValueProvider` | `Σ(quantity × ingredient price) × depthMultiplier`, cheapest matching recipe wins |
| 500 | `TagValueProvider` | `MAX` over the values configured for the item's tags |
| 300 | `AnalogyValueProvider` | modded id → vanilla id (exact path, then longest `_`-separated suffix) |
| 100 | `HeuristicValueProvider` | stack size / durability / rarity rules |

Depth multipliers: `1.0, 1.0, 1.2, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0` (index = recursion
depth, capped at 10). A `Set<Item> visited` set on the recursion context breaks circular recipes.

Heuristic rules: base `1`; `+4` when the max stack size is 16; `+49` when the max stack size is 1
and the item is undamageable; `+maxDamage/10` when damageable; rarity bonus `UNCOMMON +2`,
`RARE +9`, `EPIC +29`, `default +5`.

## Chance formula

```
ratio  = inputValue / (targetValue * multiplier)
chance = clamp(ratio * 100, 1e-21, 90.0)
result = random.nextDouble() < (chance / 100)
```

## Pre-spin checks

A spin is cancelled when **any** condition is true:

| Code | Condition |
|---|---|
| C1 | input or target slot empty |
| C2 | `inputValue <= 0` or `targetValue <= 0` |
| C3 | `ItemStack.isSameItemSameTags(input, target)` |
| C4 | player is creative and `targetValue >= 1_000_000` |
| C5 | `inputValue > targetValue * 100` |
| C6 | `inputValue > 1_000_000_000` — additionally appended to `logs/upgradermod_suspicious.log` |

C6 both logs and refuses the spin, following the specification's "CANCEL_SPIN if any condition is
TRUE".

## Configuration

Created on first launch in `config/upgradermod/`:

* **`overrides.json`** — `{"modid:item": value}` hard overrides (keys starting with `_` or `#` are
  comments).
* **`tags.json`** — `{"namespace:tag/path": value}` tag values.

Both files are re-read on datapack reload.

## Building

```bash
./gradlew build
```

The jar lands in `build/libs/`. A GitHub Actions workflow (`.github/workflows/build.yml`) runs the
same command with Temurin JDK 17 on every push.

## Project layout

```
com.example.upgradermod
├── UpgraderMod / UpgraderConstants
├── config      UpgraderConfig                      (JSON tables)
├── registry    ModItems, ModMenus                  (DeferredRegister)
├── item        UpgraderItem
├── menu        UpgraderMenu                        (server authoritative container)
├── logic       ValueProvider, ValueProviderRegistry, ValueCalculator, ChanceCalculator,
│               ItemRegistryCache, SpinValidator, SuspiciousLogger
│   └── providers  Override, ProjectE, Recipe, Tag, Analogy, Heuristic
├── network     NetworkHandler, SpinPacket, SpinResultPacket, SetTargetPacket, SetMultiplierPacket
└── client      ClientSetup, KeyBindings, UpgraderScreen, CatalogScreen
```

Network: channel `upgradermod:main`, protocol `"1"`, `SimpleChannel`.

## Notes

* Every provider call, every packet handler and `doSpin()` are wrapped in `try-catch(Throwable)`; a
  broken mod item degrades the value, it never crashes the server.
* Logging uses `org.slf4j.Logger` (via `com.mojang.logging.LogUtils`), never `System.out`.
* The GUI is drawn procedurally in the specified dark theme — no texture atlases are needed beyond
  the single item icon.

# Tower Defense Mod

<p align="center">
  <strong>A tower defense mod for Minecraft — PvP, Solo vs AI, or Test mode</strong>
</p>

Two teams face off in an arena: defend your Nexus, build towers and walls, spawn mobs to attack the enemy, and use spells to turn the tide. The first team to lose their Nexus loses the game. Supports multiplayer PvP, solo play against an AI opponent, and single-player test mode.

---

## ✨ Features

- **PvP or Solo** — Two-team arena PvP, or solo mode vs AI with economic bonuses
- **10 tower types** — From basic arrows to chain lightning and AOE explosions
- **10 mob spawners** — Zombies, skeletons, ravagers, witches, iron golems, and more
- **3 income generators** — Passive gold over time
- **5 spells** — Fireball, Freeze, Heal Nexus, Lightning, Shield
- **Wall blocks** — Wool, Oak, Cobblestone (4-block pillars, fireball-resistant)
- **Minimap** — Live top-left HUD showing mobs, paths, towers, walls, spawners, generators, and both Nexuses. Your current money is shown bottom-right.
- **Per-player sidebar** — Scoreboard shows "You" / "Opponent" HP and money, tailored to each player
- **Lobby system** — Host creates, players join, manual start when ready
- **Solo mode** — Play alone vs AI (villager avatar, resource allocation strategy, economic bonuses)
- **Test mode** — One player controls both sides; crossing the midline auto-switches teams
- **Configurable** — JSON config + web UI for balancing

---

## 📋 Requirements

| Requirement | Version |
|-------------|---------|
| Minecraft | 1.21.4 |
| Fabric Loader | Latest |
| Fabric API | Latest |

---

## 📥 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.4
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Download the mod JAR from [Releases](https://github.com/pamartn/tower-defense-mod/releases) or build it yourself (see Build & Deploy below)
4. Place the JAR in your `.minecraft/mods/` folder

---

## 🎮 Commands

All commands use the `/td` prefix.

### Lobby & Game

| Command | Description |
|---------|-------------|
| `/td start` | **Create lobby** — Host creates a lobby and is auto-assigned to Team 1. When 2+ players are in, host runs again to **start the game**. When alone, host runs again to **start solo mode** (vs AI). |
| `/td join` | **Quick join** — Join the lobby (auto-assigned to the smaller team). Only works when game is in lobby. |
| `/td join 1` / `/td join 2` | **Switch team** — Change teams (lobby or prep phase only). |
| `/td join <player>` | **Request join** — Request to join a game. Target uses `/td accept` to approve. |
| `/td accept` | **Accept** — Accept a join request or an invite. |
| `/td invite <player>` | **Invite** — Invite a player to the lobby (host only). |
| `/td stop` | **Stop game** — End the current game. Host only in lobby. |
| `/td status` | **Status** — Show game state and team sizes. |
| `/td shop` | **Shop** — Open the Tower Shop (towers, walls, spawners, spells, upgrades). |
| `/td test` | **Test mode** — Start single-player test mode from lobby (host only). Cross the midline to switch teams. |

### Game Flow

```
1. Host: /td start          → Lobby created, host in Team 1
2. Others: /td join         → Auto-join smaller team
   (or: /td invite <p> + /td accept)
3. Host: /td start          → Game begins (2+ players) or Solo mode (1 player)
4. Prep phase               → Build defenses on your half
5. Playing                  → Waves spawn, mobs attack enemy Nexus
6. Victory                  → First team to lose Nexus loses
```

### Solo Mode

When the host is alone in the lobby and runs `/td start` again, solo mode launches: you vs AI. The AI is represented by a villager that wanders on its half. The AI has economic bonuses (higher starting money, passive income, generator output) and allocates resources: ~15% income, ~35% defense (towers, walls), ~50% attack (spawners, spells). Config: `soloModeStartingMultiplier`, `soloModeIncomeMultiplier`, `soloModeGeneratorMultiplier` in `towerdefense.json`.

### Test Mode

Run `/td start` to create a lobby (alone), then `/td test` to enter test mode. One player controls both sides — crossing the midline automatically switches your active team. Useful for testing layouts without a second player.

---

## 🏪 Shop

Open with `/td shop`. All items require you to be in a game.

### Towers

Place the block on your half to build. Towers auto-target enemy mobs.

| Tower | Block | Attack | Tier |
|-------|-------|--------|------|
| Shotgun Tower | Gravel | Triple Arrow | 1 |
| Archer Tower | Oak Log | Double Arrow | 1 |
| Cannon Tower | Stone | Explosive Cannonball | 1 |
| Fire Tower | Netherrack | Fire | 1 |
| Slow Tower | Blue Ice | Slowness | 1 |
| Poison Tower | Slime Block | Poison | 1 |
| Laser Tower | Diamond Block | Laser Beam | 2 |
| Sniper Tower | Copper Block | Sniper Shot | 2 |
| Lightning Tower | Lightning Rod | Chain Lightning | 2 |
| AOE Tower | TNT | Area Explosion | 2 |

**Sell:** Break your tower to get 50% refund.

### Walls

Place on your half. **Creates a 4-block-high pillar** (1 block placed = 4 blocks). Resistant to Fireball spell (tier = hits to destroy).

| Wall | Block | Price | Fireball HP |
|------|-------|-------|-------------|
| Wool Wall | White Wool | 4 | 1 hit |
| Oak Planks Wall | Oak Planks | 6 | 2 hits |
| Cobblestone Wall | Cobblestone | 10 | 3 hits |

### Spawners

Place on your half. Spawns mobs that path to the enemy Nexus.

| Spawner | Mob | Tier |
|---------|-----|------|
| Baby Zombie Spawner | Baby Zombie | 1 |
| Zombie Spawner | Zombie | 1 |
| Skeleton Spawner | Skeleton | 1 |
| Spider Spawner | Spider | 1 |
| Creeper Spawner | Creeper | 2 |
| Enderman Spawner | Enderman | 2 |
| Witch Spawner | Witch | 2 |
| Ravager Spawner | Ravager | 2 |
| Iron Golem Spawner | Iron Golem | 3 |
| Boss Spawner | Boss | 3 |

### Income Generators

Passive gold over time.

| Generator | Block | Tier |
|-----------|-------|------|
| Basic | Gold Block | 1 |
| Advanced | Emerald Block | 2 |
| Elite | Netherite Block | 3 |

### Spells

Buy in shop, then **hold and use** the item (right-click) to cast.

| Spell | Item | Effect |
|-------|------|--------|
| Fireball | Fire Charge | Destroys enemy towers, spawners, generators, and wall blocks in explosion radius |
| Freeze Bomb | Snowball | Slows all enemy mobs for 5s |
| Heal Nexus | Golden Apple | Restores 15 HP to your Nexus |
| Lightning | Trident | Damages enemy mobs in area |
| Shield | Shield | Nexus immune to damage for 10s |

### Weapons

| Weapon | Item | Tier |
|--------|------|------|
| Wood Sword | Wooden Sword | 1 |
| Iron Sword | Iron Sword | 2 |
| Diamond Sword | Diamond Sword | 2 |
| Netherite Sword | Netherite Sword | 3 |
| Ench. Netherite | Netherite Sword (Sharpness V) | 3 |

---

## 🔧 Build & Deploy

### Build

```bash
cd tower-defense-mod
./gradlew build
```

Output: `build/libs/tower-defense-<version>.jar`

### Deploy (local instance + server)

```bash
./build_and_deploy.sh
```

The script builds the mod, copies the JAR to your local Minecraft instance and your server mods folder, and restarts the server. On first run it will prompt for the paths and save them to `.deploy.conf`.

```bash
./build_and_deploy.sh --background   # non-blocking, streams output
./build_and_deploy.sh --no-server    # skip server restart
```

---

## ⚙️ Configuration

Config file: `towerdefense.json` in the server config directory.

**Web UI:** When the server is running, open `http://localhost:8765/` to edit config in the browser (prices, tower stats, spell effects, mob stats, etc.).

---

## 📁 Project Structure

```
tower-defense-mod/
├── src/main/java/com/towerdefense/
│   ├── ai/             # SoloAI — AI opponent logic
│   ├── arena/          # Arena builder, Nexus, WallBlockManager, constants
│   ├── command/        # /td commands
│   ├── config/         # ConfigManager, TDConfig, ConfigWebServer
│   ├── game/           # GameManager, LobbyManager, HudManager, PlayerState, MoneyManager
│   ├── handler/        # Block placement (towers, walls, spawners)
│   ├── mob/            # Ravager breach behavior
│   ├── network/        # MinimapPayload and shop networking packets
│   ├── shop/           # Shop items (WallShopItem, etc.)
│   ├── spell/          # SpellManager, SpellType
│   ├── tower/          # TowerManager, TowerRegistry, TowerConstants
│   └── wave/           # SpawnerManager, WaveSpawner, MobType
├── src/client/java/com/towerdefense/client/
│   ├── MinimapRenderer.java   # Live minimap HUD
│   └── ShopScreen.java        # Client-side shop UI
├── build_and_deploy.sh # Build + deploy to instance & server
└── README.md
```

---

## 📄 License

See repository root for license information.

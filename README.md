# EssenceOfDoom

An RPG system for Minecraft inspired by **He Who Fights With Monsters**.

You gather **essences**, you find **awakening stones**, and the combination decides what you become. Hold **dark**, **sin** and **blood** at the same time and they fuse into **Doom** — the confluence — which is its own power entirely.

Paper **1.21.11** · Java **21+**

| File | Goes in |
|---|---|
| `dist/EssenceOfDoom.jar` | `<server>/plugins/` |
| `dist/EssenceOfDoom-ResourcePack.zip` | client `resourcepacks/` **and/or** server `resource-pack=` |

---

## The essences

Four of them, each a vial you hold:

| Essence | Theme | Awakening it grants |
|---|---|---|
| **Dark** | Shadow, concealment, the space between things | Night vision, resistance |
| **Sin** | Appetite, transgression, taking what is not offered | Strength |
| **Blood** | Vitality, sacrifice, the price paid in flesh | Regeneration |
| **Doom** | *The confluence of all three.* What ends things | Absorption II, Strength II |

**Doom is not found. It is earned.** Hold dark, sin and blood together and awaken a stone — the three fuse, and the confluence opens. From then on you carry Doom itself, and it grants a passive 5% damage reduction on top of its own ability.

---

## How awakening works

1. Find an **Awakening Stone** (16 kinds — see below).
2. Hold an **essence** in your inventory.
3. **Right-click with the stone.**

The stone reads which essence you brought and awakens the ability that essence unlocks *for that stone*. Hold several essences at once and it awakens them all in one go. The stone is consumed, and you receive a token for each ability you gained.

**Every stone teaches every essence.** Whichever of the four you bring, the stone has something to give — you can never waste a stone by bringing the "wrong" essence. Bring all three base essences to a stone and you awaken three abilities *and* open the confluence.

Abilities arrive as **tokens** you can right-click:

- **Active** — right-click to cast, on a cooldown
- **Aura** — always on while you carry it
- **Familiar** — right-click to summon, right-click again to dismiss
- **Passive / Utility** — always on

---

## The 16 awakening stones

Each covers all four essences across five ability kinds — one aura, one familiar, two actives, one passive and one utility.

| Stone | Rarity | Leans toward |
|---|---|---|
| Umbral | rare | Dark |
| Crimson | rare | Blood |
| Gluttonous | rare | Sin |
| Predator's | epic | Sin |
| Void-Touched | epic | Dark |
| Executioner's | epic | Doom |
| Nightfall | epic | Dark |
| Sanguine | epic | Blood |
| Wrathful | rare | Sin |
| Steadfast | uncommon | Doom |
| Abyssal | epic | Dark |
| Feathered | uncommon | Dark |
| Twin Fang | epic | Blood |
| Apex | epic | Doom |
| Insatiable | epic | Sin |
| **Doomsday** | **legendary** | **Doom** |

---

## The 57 abilities

**12 passives** — Shadow Meld (25% chance to avoid an attack while sneaking), Predator's Eye (bonus damage under half health), Crimson Vitality (+8 max health), Iron Hide (flat damage reduction), Void-Touched (heal on kill), Bloodthirst (lifesteal), Executioner (finish the wounded), Shade Step, Brutal Swing, Undying Heart, Night Predator, Sanguine Armour.

**5 auras** — Dread (slow + weaken), Gluttony (mining fatigue), Vitality (regeneration for you and nearby allies), Doom (wither + slow), the Void (blindness + slow).

**18 actives** — Shadow Dash, Blink Strike, Ruin, Soul Nova, Blood Pact (spend health to heal more), Siphon, Gluttony, Wave of Terror, Bulwark, Earthen Quake, Sever (execute), Mark of Doom, Blood Storm, Void Leap, Purity of Sin, Chain of Damnation, Life Drain, Shadow Step.

**6 familiars** — Sin Imp, Blood Hound, Void Wisp, Swarm of Night, Shade Servant, and the **Herald of Doom**. Familiars are real mobs: they path, fight and follow using vanilla AI, never attack you, and never despawn.

**16 utilities** — permanent night vision, water breathing, fire resistance, slow falling, haste, strength, regeneration, resistance, speed, absorption, true sight (glow nearby enemies), endless hunger, and attribute boosts to breath, reach, jump and fall damage.

---

## Commands

| Command | What it does |
|---|---|
| `/essence essences` | List the four essences |
| `/essence stones` | List all 16 stones |
| `/essence abilities` | Your essences, abilities and cooldowns |
| `/essence stone <id> [player]` | Give a specific stone |
| `/essence doom [player]` | Grant dark, sin and blood |
| `/essence awaken [player]` | Grant the three essences and a starter stone |
| `/essence reset <player>` | Wipe a player's essences |
| `/essence reload` | Reload config |

Aliases: `/ess`, `/hwfwm`. Tab-completes stone ids and player names.

Permissions: `essence.use` (everyone), `essence.admin` (op).

---

## Config

```yaml
allow-pvp: false                 # can abilities hurt other players
announce-awakening: true
doom-requires-all-three: true    # dark + sin + blood for the confluence
passive-refresh-seconds: 5
grant-starter-stone: true
ability-damage-multiplier: 1.0   # scale every ability's damage
```

---

## How it's built

- **Data-driven.** `essences.json` holds the essences, all 57 abilities and all 16 stone pools. Adding an ability is a JSON edit unless it needs a genuinely new effect type.
- **Explicit essence tags.** Every ability declares which essence it belongs to, so a stone's pool is never guessed from its name.
- **Validated at build time.** The generator refuses to emit data where a stone has no ability for an essence, or references an unknown ability.
- **No dependencies.** The jar ships its own JSON parser, so nothing is shaded and it loads on any Paper server.
- **Native behaviour.** Familiars are ordinary mobs; damage goes through the server's own pipeline so armour and i-frames work correctly.
- **Passives use real attribute modifiers** with stable keys, so re-applying never stacks and removal is clean.

## Files

| File | Purpose |
|---|---|
| `plugin/src/main/java/com/essence/` | Plugin source (9 classes) |
| `essences.json` | All content: essences, abilities, stone pools |
| `tools/gen_data.py` | Builds and validates `essences.json` |
| `tools/gen_pack.py` | Draws the 77 resource-pack textures |

## Build

```bash
python tools/gen_data.py          # regenerate + validate content
python tools/gen_pack.py          # redraw the resource pack
cd plugin && mvn clean package    # -> target/EssenceOfDoom.jar
```

---

## Known limits

- **Awakening needs a real player.** Right-clicking with a stone is a player action, so it can't be driven from the server console.
- **Offhand stones aren't read.** Hold the stone in your main hand; essences can be anywhere in your inventory.
- **Familiars use vanilla AI.** They follow and fight, but they won't use abilities of their own.
- **Attribute passives** apply on awaken and refresh every few seconds; they persist through death because they're re-derived from your saved profile.
- Ability damage is tuned for a solo player. For PvP servers, drop `ability-damage-multiplier` to about `0.7` and keep `allow-pvp: false` unless you want fights decided by cooldowns.

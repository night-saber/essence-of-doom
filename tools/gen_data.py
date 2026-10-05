"""Generate essences.json - the content data for the HWFWM plugin.

Structure
---------
essences : dark, sin, blood + the doom confluence (granted when all three are held)
stones   : 16 awakening stones. Each stone holds a pool of awakening abilities,
           one per essence it can awaken. Which essence you awaken with decides
           which ability you get, so a stone has up to 4 possible outcomes.
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# ---------------------------------------------------------------- essences
ESSENCES = {
    "dark": {
        "name": "Dark Essence",
        "colour": "#4a3a6b",
        "hint": "Shadow, concealment, the space between things.",
        "onAwaken": {"effects": [["NIGHT_VISION", 6000, 0], ["RESISTANCE", 200, 0]]},
    },
    "sin": {
        "name": "Sin Essence",
        "colour": "#8a1f3d",
        "hint": "Appetite, transgression, taking what is not offered.",
        "onAwaken": {"effects": [["STRENGTH", 400, 0]]},
    },
    "blood": {
        "name": "Blood Essence",
        "colour": "#a01b1b",
        "hint": "Vitality, sacrifice, the price paid in flesh.",
        "onAwaken": {"effects": [["REGENERATION", 300, 0]]},
    },
    "doom": {
        "name": "Doom Essence",
        "colour": "#7b2ff7",
        "confluence": True,
        "hint": "The confluence of dark, sin and blood. What ends things.",
        "onAwaken": {"effects": [["ABSORPTION", 1200, 2], ["STRENGTH", 600, 1]]},
    },
}

# ------------------------------------------------------- ability catalogue
# Each ability: id, name, kind, desc, and a mechanical payload.
# kind: passive | aura | active | familiar | utility
def P(aid, name, desc, attrs=None, on_hit=None, on_kill=None, on_hurt=None):
    a = {"id": aid, "name": name, "kind": "passive", "desc": desc}
    if attrs:
        a["attrs"] = attrs
    if on_hit:
        a["onHit"] = on_hit
    if on_kill:
        a["onKill"] = on_kill
    if on_hurt:
        a["onHurt"] = on_hurt
    return a


def A(aid, name, desc, cooldown, payload):
    return {"id": aid, "name": name, "kind": "active", "desc": desc,
            "cooldown": cooldown, "payload": payload}


def AU(aid, name, desc, effects, radius=8, particles="SOUL"):
    return {"id": aid, "name": name, "kind": "aura", "desc": desc,
            "radius": radius, "effects": effects, "particles": particles}


def F(aid, name, desc, mob, count=1, speed=0.35, health=14, damage=4):
    return {"id": aid, "name": name, "kind": "familiar", "desc": desc,
            "mob": mob, "count": count, "speed": speed, "health": health, "damage": damage}


def U(aid, name, desc, payload):
    return {"id": aid, "name": name, "kind": "utility", "desc": desc, "payload": payload}


# payload types used by the runtime:
#   dash, blink, nova, heal, lifesteal_burst, fear, shield, summon, storm,
#   chain_bolt, execute, blood_pact, ruin, siphon, leap, quake, mark, cleanse
ABILITIES = {
    # ------------------------------------------------------------- passives
    "shadow_meld": P("shadow_meld", "Shadow Meld",
                     "While sneaking you are near-invisible and harder to hit.",
                     on_hurt={"cancel_chance": 0.25}),
    "predator": P("predator", "Predator's Eye",
                  "You deal more damage to enemies below half health.",
                  on_hit={"low_health_bonus": 2.0}),
    "crimson_vitality": P("crimson_vitality", "Crimson Vitality",
                          "Your blood runs hot. Maximum health increases.",
                          attrs={"MAX_HEALTH": 8.0}),
    "iron_hide": P("iron_hide", "Iron Hide",
                   "Your skin thickens with sin. Flat damage reduction.",
                   on_hurt={"flat_reduction": 1.5}),
    "void_touched": P("void_touched", "Void-Touched",
                      "The dark feeds you. Killing restores health.",
                      on_kill={"heal": 4.0}),
    "bloodthirst": P("bloodthirst", "Bloodthirst",
                     "Wounds you open feed you. Hitting steals a little health.",
                     on_hit={"lifesteal": 0.15}),
    "executioner": P("executioner", "Executioner",
                     "Your strikes finish the wounded. Bonus damage to low targets.",
                     on_hit={"execute_threshold": 0.3, "execute_bonus": 6.0}),
    "shade_step": P("shade_step", "Shade Step",
                    "The dark quickens you. Movement speed up.",
                    attrs={"MOVEMENT_SPEED": 0.04}),
    "brutal_swing": P("brutal_swing", "Brutal Swing",
                      "Sin lends weight to your arm. Attack damage up.",
                      attrs={"ATTACK_DAMAGE": 3.0}),
    "undying_heart": P("undying_heart", "Undying Heart",
                       "Your heart refuses to stop. Knockback resistance and toughness.",
                       attrs={"KNOCKBACK_RESISTANCE": 0.3, "ARMOR_TOUGHNESS": 4.0}),
    "night_predator": P("night_predator", "Night Predator",
                        "In darkness you are stronger.",
                        on_hit={"night_bonus": 3.0}),
    "sanguine_armour": P("sanguine_armour", "Sanguine Armour",
                         "Congealed blood plates your body.",
                         attrs={"ARMOR": 4.0}),

    # --------------------------------------------------------------- auras
    "aura_dread": AU("aura_dread", "Aura of Dread",
                     "Nearby enemies are slowed and weakened.", [
                         {"target": "enemy", "effect": "SLOWNESS", "ticks": 60, "amp": 0},
                         {"target": "enemy", "effect": "WEAKNESS", "ticks": 60, "amp": 0},
                     ], radius=8, particles="SOUL"),
    "aura_sin": AU("aura_sin", "Aura of Gluttony",
                   "Your presence saps the will of those nearby.", [
                       {"target": "enemy", "effect": "MINING_FATIGUE", "ticks": 80, "amp": 1},
                   ], radius=7, particles="WITCH"),
    "aura_blood": AU("aura_blood", "Aura of Vitality",
                     "Your blood essence mends you and nearby allies.", [
                         {"target": "ally", "effect": "REGENERATION", "ticks": 60, "amp": 0},
                         {"target": "self", "effect": "REGENERATION", "ticks": 60, "amp": 0},
                     ], radius=8, particles="HEART"),
    "aura_doom": AU("aura_doom", "Aura of Doom",
                    "Everything near you begins to end.", [
                        {"target": "enemy", "effect": "WITHER", "ticks": 60, "amp": 0},
                        {"target": "enemy", "effect": "SLOWNESS", "ticks": 60, "amp": 1},
                    ], radius=9, particles="SCULK_SOUL"),
    "aura_void": AU("aura_void", "Aura of the Void",
                    "Light dies around you. Enemies are blinded and slowed.", [
                        {"target": "enemy", "effect": "BLINDNESS", "ticks": 60, "amp": 0},
                        {"target": "enemy", "effect": "SLOWNESS", "ticks": 80, "amp": 0},
                    ], radius=8, particles="REVERSE_PORTAL"),

    # ------------------------------------------------------------- actives
    "act_shadow_dash": A("act_shadow_dash", "Shadow Dash",
                         "Step through the dark to where you are looking.", 8,
                         {"type": "dash", "distance": 8, "particles": "SOUL"}),
    "act_blink_strike": A("act_blink_strike", "Blink Strike",
                          "Teleport behind your target and strike hard.", 14,
                          {"type": "blink", "distance": 12, "damage": 10}),
    "act_ruin": A("act_ruin", "Ruin",
                  "A wave of ending erupts around you, damaging and withering.", 25,
                  {"type": "nova", "radius": 7, "damage": 12, "effect": ["WITHER", 100, 1]}),
    "act_soul_nova": A("act_soul_nova", "Soul Nova",
                       "Dark fire bursts outward, knocking enemies back.", 18,
                       {"type": "nova", "radius": 6, "damage": 8,
                        "knockback": 1.2, "particles": "SOUL_FIRE_FLAME"}),
    "act_blood_pact": A("act_blood_pact", "Blood Pact",
                        "Spend your own health to heal far more.", 30,
                        {"type": "blood_pact", "cost": 6, "heal": 16}),
    "act_siphon": A("act_siphon", "Siphon",
                    "Tear life from every enemy around you and take it as your own.", 22,
                    {"type": "siphon", "radius": 8, "damage": 6, "heal_per_target": 4}),
    "act_gluttony": A("act_gluttony", "Gluttony",
                      "Devour the strength of those nearby.", 26,
                      {"type": "steal_buff", "radius": 7, "effect": ["STRENGTH", 300, 1]}),
    "act_fear": A("act_fear", "Wave of Terror",
                  "Enemies flee, blinded and slowed.", 20,
                  {"type": "fear", "radius": 8, "effect": ["BLINDNESS", 120, 0]}),
    "act_iron_shield": A("act_iron_shield", "Bulwark",
                         "Harden your flesh into a shield of sin.", 28,
                         {"type": "shield", "absorption": 16, "seconds": 12}),
    "act_quake": A("act_quake", "Earthen Quake",
                   "Slam the ground, launching everything nearby upward.", 24,
                   {"type": "quake", "radius": 7, "damage": 6, "launch": 1.1}),
    "act_execute": A("act_execute", "Sever",
                     "Instantly slay a badly wounded target in front of you.", 40,
                     {"type": "execute", "range": 4, "threshold": 0.35}),
    "act_mark": A("act_mark", "Mark of Doom",
                  "Brand a target: it takes far more damage from you.", 30,
                  {"type": "mark", "range": 16, "seconds": 12, "multiplier": 1.5}),
    "act_storm": A("act_storm", "Blood Storm",
                   "Call down a storm of blood around you.", 45,
                   {"type": "storm", "radius": 8, "damage": 4, "duration": 8,
                    "particles": "DUST_PLUME"}),
    "act_leap": A("act_leap", "Void Leap",
                  "Launch yourself high and land without harm.", 16,
                  {"type": "leap", "power": 1.6, "slow_fall": 200}),
    "act_cleanse": A("act_cleanse", "Purity of Sin",
                     "Burn away every negative effect on you.", 35,
                     {"type": "cleanse"}),
    "act_chain": A("act_chain", "Chain of Damnation",
                   "Dark bolts arc between nearby enemies.", 18,
                   {"type": "chain_bolt", "radius": 8, "targets": 5, "damage": 7}),
    "act_lifedrain": A("act_lifedrain", "Life Drain",
                       "Drain a single target's health over time into yourself.", 26,
                       {"type": "lifesteal_burst", "range": 12, "damage": 10, "heal": 10}),
    "act_shadowstep": A("act_shadowstep", "Shadow Step",
                        "Vanish, then reappear further away, unseen.", 20,
                        {"type": "blink", "distance": 16, "invisible": 100}),

    # ----------------------------------------------------------- familiars
    "fam_imp": F("fam_imp", "Sin Imp", "A lesser sin made flesh. Fights beside you.",
                 "VEX", count=1, speed=0.4, health=16, damage=5),
    "fam_blood_hound": F("fam_blood_hound", "Blood Hound",
                         "A hound of your own blood. Fast and vicious.",
                         "WOLF", count=2, speed=0.45, health=18, damage=5),
    "fam_void_wisp": F("fam_void_wisp", "Void Wisp",
                       "A mote of the dark that follows and burns.",
                       "ALLAY", count=1, speed=0.5, health=14, damage=4),
    "fam_bat_swarm": F("fam_bat_swarm", "Swarm of Night",
                       "A cloud of dark wings that harasses your enemies.",
                       "BAT", count=4, speed=0.6, health=8, damage=2),
    "fam_shade": F("fam_shade", "Shade Servant",
                   "A shadow bound to your will.",
                   "VEX", count=2, speed=0.38, health=20, damage=6),
    "fam_doom_herald": F("fam_doom_herald", "Herald of Doom",
                         "An omen given form. It fights like the end of things.",
                         "VEX", count=1, speed=0.42, health=30, damage=9),

    # ------------------------------------------------------------ utilities
    "util_night_eye": U("util_night_eye", "Eyes of the Dark",
                        "Permanent night vision.", {"type": "perm_effect", "effect": "NIGHT_VISION"}),
    "util_water_breath": U("util_water_breath", "Breath of the Deep",
                           "Permanent water breathing.", {"type": "perm_effect", "effect": "WATER_BREATHING"}),
    "util_fire_ward": U("util_fire_ward", "Ember Ward",
                        "Permanent fire resistance.", {"type": "perm_effect", "effect": "FIRE_RESISTANCE"}),
    "util_feather": U("util_feather", "Feather of Night",
                      "Permanent slow falling.", {"type": "perm_effect", "effect": "SLOW_FALLING"}),
    "util_haste": U("util_haste", "Unerring Purpose",
                    "Permanent haste.", {"type": "perm_effect", "effect": "HASTE"}),
    "util_strength": U("util_strength", "Might of Sin",
                       "Permanent strength I.", {"type": "perm_effect", "effect": "STRENGTH"}),
    "util_regen": U("util_regen", "Blood Renewal",
                    "Permanent regeneration I.", {"type": "perm_effect", "effect": "REGENERATION"}),
    "util_resistance": U("util_resistance", "Unyielding",
                         "Permanent resistance I.", {"type": "perm_effect", "effect": "RESISTANCE"}),
    "util_speed": U("util_speed", "Quickened",
                    "Permanent speed I.", {"type": "perm_effect", "effect": "SPEED"}),
    "util_absorption": U("util_absorption", "Second Skin",
                         "Permanent absorption.", {"type": "perm_effect", "effect": "ABSORPTION"}),
    "util_vision": U("util_vision", "True Sight",
                     "Permanent glowing on nearby enemies.",
                     {"type": "glow_enemies", "radius": 16}),
    "util_saturation": U("util_saturation", "Endless Hunger",
                         "You never go hungry.", {"type": "no_hunger"}),
    "util_breath": U("util_breath", "Tireless Lungs",
                     "Bonus oxygen.", {"type": "attr", "attr": "OXYGEN_BONUS", "amount": 4.0}),
    "util_range": U("util_range", "Reaching Doom",
                    "Extended reach.", {"type": "attr", "attr": "BLOCK_INTERACTION_RANGE", "amount": 2.0}),
    "util_jump": U("util_jump", "Leaping Sin",
                   "Higher jumps.", {"type": "attr", "attr": "JUMP_STRENGTH", "amount": 0.35}),
    "util_fall": U("util_fall", "Unbroken Landing",
                   "Greatly reduced fall damage.", {"type": "attr", "attr": "SAFE_FALL_DISTANCE", "amount": 8.0}),
}


# ---------------------------------------------------- ability -> essence map
# Explicit, so the plugin never has to guess which essence an ability belongs to.
ESSENCE_OF = {
    # passives
    "shadow_meld": "dark", "void_touched": "dark", "shade_step": "dark",
    "night_predator": "dark",
    "predator": "sin", "iron_hide": "sin", "executioner": "sin",
    "brutal_swing": "sin",
    "crimson_vitality": "blood", "bloodthirst": "blood", "sanguine_armour": "blood",
    "undying_heart": "doom",
    # auras
    "aura_dread": "dark", "aura_void": "dark",
    "aura_sin": "sin",
    "aura_blood": "blood",
    "aura_doom": "doom",
    # actives
    "act_shadow_dash": "dark", "act_blink_strike": "dark", "act_soul_nova": "dark",
    "act_fear": "dark", "act_leap": "dark", "act_shadowstep": "dark",
    "act_chain": "dark",
    "act_gluttony": "sin", "act_iron_shield": "sin", "act_quake": "sin",
    "act_cleanse": "sin",
    "act_blood_pact": "blood", "act_siphon": "blood", "act_lifedrain": "blood",
    "act_storm": "blood",
    "act_ruin": "doom", "act_execute": "doom", "act_mark": "doom",
    # familiars
    "fam_void_wisp": "dark", "fam_bat_swarm": "dark", "fam_shade": "dark",
    "fam_imp": "sin",
    "fam_blood_hound": "blood",
    "fam_doom_herald": "doom",
    # utilities
    "util_night_eye": "dark", "util_water_breath": "dark",
    "util_feather": "dark", "util_haste": "dark", "util_vision": "dark",
    "util_breath": "dark", "util_range": "dark", "util_fall": "dark",
    "util_fire_ward": "sin", "util_strength": "sin", "util_speed": "sin",
    "util_jump": "sin",
    "util_regen": "blood", "util_saturation": "blood",
    "util_resistance": "doom", "util_absorption": "doom",
}

# --------------------------------------------------- the 16 awakening stones
# `pool` lists one ability per essence: [dark, sin, blood, doom].
# Whichever essence you awaken with, the stone has something to teach.
STONES = [
    {
        "id": "umbral", "name": "Umbral Awakening Stone", "colour": "#4a3a6b",
        "rarity": "rare",
        "desc": "Cold to the touch. It drinks the light around it.",
        "pool": ["aura_dread", "fam_shade", "act_iron_shield", "act_storm", "undying_heart", "util_jump"],
    },
    {
        "id": "crimson", "name": "Crimson Awakening Stone", "colour": "#a01b1b",
        "rarity": "rare",
        "desc": "Warm, and faintly wet. It beats like something alive.",
        "pool": ["aura_blood", "fam_blood_hound", "act_leap", "act_cleanse", "undying_heart", "util_vision"],
    },
    {
        "id": "gluttonous", "name": "Gluttonous Awakening Stone", "colour": "#8a1f3d",
        "rarity": "rare",
        "desc": "It is never full. It is never satisfied.",
        "pool": ["aura_sin", "fam_imp", "act_shadow_dash", "act_lifedrain", "undying_heart", "util_water_breath"],
    },
    {
        "id": "predator", "name": "Predator's Awakening Stone", "colour": "#5c1a2e",
        "rarity": "epic",
        "desc": "It smells of blood and appetite. Something has already fed.",
        "pool": ["aura_sin", "fam_imp", "act_shadowstep", "act_siphon", "undying_heart", "util_breath"],
    },
    {
        "id": "void", "name": "Void-Touched Awakening Stone", "colour": "#3a1a5c",
        "rarity": "epic",
        "desc": "There is a hole in it. Not a crack - a hole, in the world.",
        "pool": ["aura_dread", "fam_void_wisp", "act_iron_shield", "act_storm", "undying_heart", "util_jump"],
    },
    {
        "id": "executioner", "name": "Executioner's Awakening Stone", "colour": "#3d0a14",
        "rarity": "epic",
        "desc": "It knows what it is for. It has been waiting to be used.",
        "pool": ["aura_doom", "fam_doom_herald", "act_blink_strike", "act_cleanse", "bloodthirst", "util_feather"],
    },
    {
        "id": "nightfall", "name": "Nightfall Awakening Stone", "colour": "#241a3d",
        "rarity": "epic",
        "desc": "The moment the sun gives up, held in stone.",
        "pool": ["aura_dread", "fam_shade", "act_cleanse", "act_lifedrain", "undying_heart", "util_strength"],
    },
    {
        "id": "sanguine", "name": "Sanguine Awakening Stone", "colour": "#7a0f1a",
        "rarity": "epic",
        "desc": "Blood has been spilled on this stone. Recently.",
        "pool": ["aura_blood", "fam_blood_hound", "act_fear", "act_iron_shield", "undying_heart", "util_night_eye"],
    },
    {
        "id": "wrathful", "name": "Wrathful Awakening Stone", "colour": "#8f1d0f",
        "rarity": "rare",
        "desc": "Hot to the touch, and getting hotter.",
        "pool": ["aura_sin", "fam_imp", "act_leap", "act_storm", "undying_heart", "util_range"],
    },
    {
        "id": "steadfast", "name": "Steadfast Awakening Stone", "colour": "#8a6a2a",
        "rarity": "uncommon",
        "desc": "Heavy, dull, and utterly immovable.",
        "pool": ["aura_doom", "fam_doom_herald", "act_shadow_dash", "act_cleanse", "crimson_vitality", "util_vision"],
    },
    {
        "id": "abyssal", "name": "Abyssal Awakening Stone", "colour": "#0d2030",
        "rarity": "epic",
        "desc": "Pulled from somewhere very deep, and very dark, and very cold.",
        "pool": ["aura_dread", "fam_void_wisp", "act_cleanse", "act_lifedrain", "undying_heart", "util_strength"],
    },
    {
        "id": "feathered", "name": "Feathered Awakening Stone", "colour": "#5aa8c8",
        "rarity": "uncommon",
        "desc": "Lighter than it has any right to be.",
        "pool": ["aura_void", "fam_bat_swarm", "act_gluttony", "act_siphon", "undying_heart", "util_fire_ward"],
    },
    {
        "id": "twin_fang", "name": "Twin Fang Awakening Stone", "colour": "#6b1f1f",
        "rarity": "epic",
        "desc": "Two marks, side by side, like a bite that went all the way through.",
        "pool": ["aura_blood", "fam_blood_hound", "act_blink_strike", "act_quake", "undying_heart", "util_fall"],
    },
    {
        "id": "apex", "name": "Apex Awakening Stone", "colour": "#6b5a1f",
        "rarity": "epic",
        "desc": "Every predator that ever lived, compressed into a single point.",
        "pool": ["aura_doom", "fam_doom_herald", "act_chain", "act_cleanse", "sanguine_armour", "util_feather"],
    },
    {
        "id": "insatiable", "name": "Insatiable Awakening Stone", "colour": "#4a0f2a",
        "rarity": "epic",
        "desc": "It has eaten other awakening stones. You can feel them in there.",
        "pool": ["aura_sin", "fam_imp", "act_fear", "act_lifedrain", "undying_heart", "util_haste"],
    },
    {
        "id": "doomsday", "name": "Doomsday Awakening Stone", "colour": "#7b2ff7",
        "rarity": "legendary",
        "desc": "It does not glow. It does not hum. Everything near it simply starts to end.",
        "pool": ["aura_doom", "fam_doom_herald", "act_leap", "act_iron_shield", "crimson_vitality", "util_night_eye"],
    },
]

def build_pools():
    """Give every stone at least one ability for each of the four essences.

    A stone is a theme, not a filter: whichever essence you awaken with, the
    stone must have something to teach. Six abilities per essence-agnostic
    stone means 6 picks, drawn to fit the stone's flavour where possible.
    """
    by_essence = {}
    for aid, ess in ESSENCE_OF.items():
        by_essence.setdefault(ess, []).append(aid)

    # stable ordering so the output is deterministic
    for k in by_essence:
        by_essence[k].sort()

    return by_essence

def main():
    # validate every pool entry exists and is tagged with an essence
    missing, untagged = [], []
    for s in STONES:
        for aid in s["pool"]:
            if aid not in ABILITIES:
                missing.append((s["id"], aid))
            elif aid not in ESSENCE_OF:
                untagged.append(aid)
    if missing:
        raise SystemExit("unknown abilities referenced: " + repr(missing))
    if untagged:
        raise SystemExit("abilities with no essence tag: " + repr(sorted(set(untagged))))

    # every stone must cover all four essences
    gaps = []
    for s in STONES:
        have = {ESSENCE_OF[a] for a in s["pool"]}
        for ess in ("dark", "sin", "blood", "doom"):
            if ess not in have:
                gaps.append((s["id"], ess))
    if gaps:
        raise SystemExit("stones missing an essence: " + repr(gaps))

    # how many of each kind does each stone offer?
    report = {}
    for s in STONES:
        kinds = {}
        for aid in s["pool"]:
            k = ABILITIES[aid]["kind"]
            kinds[k] = kinds.get(k, 0) + 1
        report[s["id"]] = kinds

    data = {
        "namespace": "essence",
        "essences": ESSENCES,
        "abilities": {k: dict(v, essence=ESSENCE_OF[k]) for k, v in ABILITIES.items()},
        "stones": STONES,
    }
    out = os.path.join(ROOT, "essences.json")
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=1)
        fh.write("\n")

    print("wrote", out)
    print("essences:", list(ESSENCES))
    print("abilities:", len(ABILITIES))
    print("stones:", len(STONES))
    from collections import Counter
    print("ability kinds:", dict(Counter(a["kind"] for a in ABILITIES.values())))
    print("\nper-stone kind mix:")
    for k, v in report.items():
        print(f"  {k:12} {v}")


if __name__ == "__main__":
    main()

# Water and fire effects

**Use Fire Resistance for fire-tagged damage, and Water Breathing or Conduit Power for ordinary underwater air protection.** Breath of the Nautilus is applied by a ridden Nautilus, but this MattMC revision does not connect it to the player's air rules. Keep actual breathing protection when riding underwater. [Fire damage gate][fire-gate] · [Recognized breathing effects][breathing-helper] · [Rider effect][breath-grant] · [Air handling][air-gate]

This guide compares the effects themselves. [Conduit](../blocks/Conduit.md) owns frame construction and range, [Nautilus](../mobs/Nautilus.md) owns finding, taming, and riding the animal, and [Brewing](../brewing/Brewing.md) owns the stand and bottle workflow. Times below assume **20 game ticks per second**; they are source values, not measured expedition times.

## Fire Resistance

Effect ID: `minecraft:fire_resistance`.

The ordinary living-entity damage handler rejects a damage event when the effect is present and the damage type is in **`is_fire`**. The bundled tag includes burning, fire, campfire, lava, hot-floor damage, and the two fireball-impact damage types. This is a presence check: raising the amplifier does not strengthen that rejection. It is distinct from the percentage-based [Resistance effect](CombatEffects.md#resistance). [Registration][register-fire-water] · [Damage check][fire-gate] · [Exact fire tag][fire-tag]

**Fire Resistance does not cover every attack that looks fiery.** The tag does not include ordinary melee or explosion damage. A large fireball has a separate direct fireball hit and an explosion; rejecting the first does not promise protection from the second. See [Magma Cream](../items/MagmaCream.md#fire-resistance-brewing) for the related Magma Cube combat caution. [Fire tag][fire-tag] · [Separate impact and explosion callbacks][fireball]

It also **does not extinguish you**. Burning has its own timer, which still counts down independently. The automatic entity-type fire-immunity check does not consult Fire Resistance. Fire damage can resume after the effect ends if a burning timer or hazardous contact remains. Move to safety before drinking Milk or letting the timer expire. [Burning tick][burning] · [Type immunity][fire-immunity] · [Lava damage call][lava] · [Ignition and clearing][fire-clear]

### Fire Resistance sources

- **Brewed potion:** I for **3 minutes**, or **8 minutes** after Redstone, using the [recipe table below](#brewing-and-delivery). [Potion definitions][fire-potions]
- **[Piglin bartering](../mobs/Piglin.md):** completed adult barters can select a drinkable or splash Fire Resistance potion. These are possible outcomes, not an item you can choose for every Gold Ingot. [Adult completion][piglin-complete] · [Loaded barter table][piglin-loot] · [Potion entries][piglin-table] · [Potion component function][set-potion]
- **[Enchanted Golden Apple](../items/EnchantedGoldenApple.md):** eating it grants Fire Resistance I for **6,000 ticks / 5 minutes**, alongside its other effects. [Item binding][apple-item] · [Effect list][apple] · [Consumption callback][eat]
- **[Totem of Undying](../items/TotemOfUndying.md#activation):** a qualifying activation grants Fire Resistance I for **800 ticks / 40 seconds** after clearing prior effects. It is not a benefit from merely carrying a Totem in an ordinary inventory slot. [Hand and lethal-hit check][totem-gate] · [Replacement effects][totem]
- **Allium [Suspicious Stew](../items/SuspiciousStew.md):** the recipe stores Fire Resistance I for only **60 ticks / 3 seconds**. This very short serving is unsuitable as a long lava-crossing plan. [Exact recipe][allium-recipe] · [Stored-effect consumption][stew-effect] · [Level I entries][stew-level]

### Lava fog and burning visuals

The current renderer selects a less restrictive **lava-fog range** for a non-spectator living camera entity with Fire Resistance: its start/end parameters are **0 / 5**, compared with **0.25 / 1** without the effect. The active Rust terrain path consumes those fog parameters. These are rendering inputs, not a measured promise that everything five blocks away becomes visible. Shader packs and rendering hooks can alter the final image. [Lava-fog choice][lava-fog] · [Environment selection][fog-select] · [Active Rust collection][fog-collect] · [Mesh fog inputs][fog-header] · [Terrain fog blend][fog-fragment]

**The burning overlay can remain visible.** The current first-person overlay path checks whether the player is on fire; it does not exclude a player with Fire Resistance. A fiery screen therefore does not, by itself, prove the effect has failed. [Active overlay conditions][overlay] · [Burning state][burning]

## Water Breathing

Effect ID: `minecraft:water_breathing`.

Water Breathing prevents the ordinary underwater air-loss branch. If air is below maximum, that branch instead restores **4 air units per living-entity tick**, capped at the normal maximum. The breathing check uses the effect's presence, so higher levels do not add more protection in this consumer. [Registration][register-fire-water] · [Breathing helper][breathing-helper] · [Active underwater branch][air-gate] · [Air recovery][air-change]

This protection concerns **ordinary underwater air depletion**, not every possible damage source. It does not stop suffocation inside blocks, supply Fire Resistance, or promise immunity for a water-sensitive mob whose own handler deals damage while wet. It also does not supply the Conduit mining bonus or [Dolphin's Grace](MovementEffects.md#dolphins-grace) water-movement effect. [Ordinary air gate][air-gate] · [Separate water-sensitive damage][water-sensitive] · [Digging helper][conduit-mining] · [Water movement][water-motion]

Acquire it through:

- **Brewing:** ordinary Water Breathing I lasts **3 minutes**, extended to **8 minutes** with Redstone. Use a loose [Pufferfish item](../items/Pufferfish.md), not a Bucket of Pufferfish, in the recipe below. [Recipe][brew-water] · [Potion definitions][water-potions]
- **[Turtle Shell](../items/TurtleShell.md#diving):** when correctly equipped and your eyes are outside water, it refreshes Water Breathing I for **200 ticks / 10 seconds**. Submerging your eyes stops the refresh. Return your head to air to prepare another short dive; the linked item guide owns its broken-equipment caveat. [Tick condition][helmet-tick] · [Effect and matching-slot check][helmet-effect]
- **[Buried Treasure](../structures/BuriedTreasure.md#loot-and-why-to-collect-it):** the assigned chest table has a dedicated pool that can generate **0–2 ordinary Potions of Water Breathing**. A pool-level function sets their potion contents, so selected bottles use the ordinary **3-minute Water Breathing I** definition. The pool can produce zero; finding a chest does not guarantee a bottle. [Chest-table assignment][treasure-placement] · [Potion pool][treasure-pool] · [Pool-function decoding][pool-codec] · [Pool-function execution][pool-functions] · [Potion assignment][set-potion] · [Registered duration][water-potions] · [Drink scale][drink]

Eating a Pufferfish does **not** grant Water Breathing. Its food consumption applies Poison, Hunger, and Nausea. Use it as the brewing ingredient instead. [Food effects][puffer-food]

Water Breathing also has **no special visibility check** in the inspected current water-vision, water-fog, or lightmap consumers. Normal underwater visual adaptation still occurs, and Conduit Power has an additional lighting input described next. Do not treat a Water Breathing bottle as Night Vision. [Water-vision calculation][water-vision] · [Water-fog calculation][water-fog] · [Lightmap choice][conduit-light]

## Conduit Power

Effect ID: `minecraft:conduit_power`.

Receive Conduit Power from an active [Conduit](../blocks/Conduit.md#player-range-and-refresh). The server checks its water/frame structure every **40 ticks** and grants **Conduit Power I for 260 ticks / 13 seconds** to eligible players in range who are in water or exposed to rain. The frame needs at least 16 valid positions and the inner water volume; use the Conduit guide for the actual layout and range limits. [Block ticker][conduit-block-tick] · [Refresh dispatch][conduit-dispatch] · [Activation checks][conduit-shape] · [Player conditions and duration][conduit-grant]

Leaving range, losing the wet condition, or disabling the structure stops later refreshes. An effect already applied can continue for its remaining duration. The wet condition is a condition for receiving a refresh, not an extra requirement in every consumer of an effect you already have. [Grant conditions][conduit-grant] · [Effect timer][effect-tick] · [Digging helper][conduit-mining]

Conduit Power supplies three checked benefits:

- **Ordinary underwater air protection and recovery:** it uses the same helper as Water Breathing. Having both effects does not double that air-recovery rate. [Recognized effects][breathing-helper] · [Air branch][air-gate] · [Recovery][air-change]
- **Mining assistance:** with no stronger Haste, level I multiplies the shared digging-speed result at that stage by **1.2**. A higher level would contribute `1 + 0.2 × level`; with Haste, the helper uses the higher amplifier rather than adding them. Mining Fatigue, the submerged-mining attribute, the off-ground penalty, tool suitability, and the remaining calculation still apply. This is not a tested 20% reduction in the time to break every block. See [Mining](../mechanics/Mining.md). [Amplifier selection][conduit-mining] · [Active mining stages and harvest check][mining]
- **Underwater lighting input:** when the player's eyes are in water, Conduit Power can supply the water-vision value to the current lightmap's night-vision factor. Night Vision takes precedence in that selection. A separate active path also supplies this value to the shader-pack `nightVision` input. Its strength can depend on ongoing water-vision adaptation; it does not directly extend ordinary water-fog distance or make adaptation instantaneous. [Adaptation value][water-vision] · [Lightmap selection][conduit-light] · [Active environment inputs][lightmap-state] · [Rust lightmap computation][lightmap-build] · [Terrain sampling][lightmap-sample] · [Pack semantic helper][nightvision-semantic] · [Pack uniform input][nightvision-uniform] · [Separate water fog][water-fog]

The lighting description follows the active whole-frame renderer and its Rust consumers, not a measured visibility test. The effect does not add a swimming-speed or damage-resistance modifier. Attacks from a complete Conduit frame are a separate structure action, not a damage aura attached to a player with Conduit Power. [Current rendering entry][render-entry] · [Primitive-frame caller][render-call] · [Whole-frame environment submission][render-submit] · [Plain effect registration][register-conduit] · [Separate structure attack dispatch][conduit-dispatch]

## Breath of the Nautilus

Effect ID: `minecraft:breath_of_the_nautilus`.

A [ridden Nautilus](../mobs/Nautilus.md#riding-and-equipment) grants this status to its first player passenger on the server, at **level I for 60 ticks / 3 seconds**. With the player continuously in that position, the post-decrement refresh counter produces another grant every **41 qualifying AI updates**. Dismounting stops further grants; an already applied status can remain for its remaining duration. [Mount interaction][nautilus-mount] · [Duration and counter constants][breath-timing] · [Active passenger callback][breath-grant] · [Duration ticking][effect-tick]

**In this revision, the effect does not provide underwater air protection.** It is registered as a plain beneficial effect with no attribute modifier or active effect-tick behavior. The player's air consumer recognizes Water Breathing and Conduit Power, and does not check Breath of the Nautilus. The Nautilus itself has an underwater-breathing override; that does not give its passenger the same ability. [Plain registration][register-breath] · [Base effect behavior][effect-base] · [Recognized breathing effects][breathing-helper] · [Player air branch][air-gate] · [Mount's own breathing][breath-grant]

Keep Water Breathing, an active Conduit's coverage, or access to air when riding. The checked source also provides no Breath-specific fog or lightmap consumer. This is a source-reviewed integration limitation, not an in-game drowning reproduction or a claim about another edition's Nautilus. [Water-fog logic][water-fog] · [Lightmap selection][conduit-light]

## Brewing and delivery

Both brewable effects use **Awkward Potion**. The server initializes the mix registry used by the active Brewing Stand; use [Brewing](../brewing/Brewing.md) for fuel, bottles, Nether Wart, and operation. [Server registry][brew-bootstrap] · [Stand execution][brew-stand]

| Effect | Add to Awkward Potion | Ordinary drinkable result | Add Redstone to that result |
| --- | --- | --- | --- |
| Fire Resistance | [Magma Cream](../items/MagmaCream.md) | I, 3:00 | I, 8:00 |
| Water Breathing | [Pufferfish](../items/Pufferfish.md) | I, 3:00 | I, 8:00 |

The registered list has **no Glowstone upgrade** for either effect and no ordinary potion type or brewing recipe for Conduit Power or Breath of the Nautilus. Magma Cream added directly to Water makes Mundane Potion. Pufferfish has its specific Awkward input; do not treat a named ingredient as a recipe for every base liquid. [Fire Resistance mixes][brew-fire] · [Start-mix handling][brew-start] · [Water Breathing mixes][brew-water] · [Complete mix list][brew-all] · [Potion registry][potion-types]

Gunpowder converts a drinkable potion to splash, and Dragon's Breath converts splash to lingering. Splash duration depends on impact distance, so the whole table duration is not guaranteed to every nearby target. Ordinary lingering and tipped-arrow items apply shorter durations through their item scales. See [movement-effect delivery](MovementEffects.md#delivery-changes-duration) for the shared delivery rules. [Container mixes][brew-all] · [Splash handler][splash] · [Item scales][scales]

The ordinary listed potion variants and source items are also available through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Creative**. That insertion route is separate from collecting ingredients, bartering, chest loot, or brewing. Potion category entries enumerate enabled registered types; a registered effect alone does not create a Conduit Power or Nautilus-breath bottle. [Potion listing][browser-potions] · [Type enumeration][browser-types] · [Registered potions][potion-types]

## Refresh, removal, and commands

Different effects can coexist. Reapplying the same effect does not add its levels or simply add durations: stronger effects take precedence, and equal-level longer applications replace remaining time. Finite hidden durations keep counting down; infinite duration remains infinite. See [shared effect timing](CombatEffects.md#duration-clearing-and-commands). [Per-effect map][effect-apply] · [Replacement][effect-update] · [Hidden timers][effect-tick] · [Infinite duration][effect-infinite]

**Milk removes these beneficial effects too.** Clearing Fire Resistance during a fire hazard or clearing your breathing protection underwater can restore the ordinary danger. Honey only removes Poison. A continuing source, such as a valid Conduit, can apply its effect again; removing an effect does not disable that source. [Milk definition][milk] · [Clear callback][milk-clear] · [Effect removal][clear] · [Honey specificity][honey] · [Conduit refresh][conduit-dispatch]

With command permission level 2, for example:

```mcfunction
/effect give @s minecraft:water_breathing 60 0
/effect give @s minecraft:fire_resistance 60 0
/effect clear @s minecraft:water_breathing
```

Here `60` is seconds and amplifier `0` means level I. Permission-gated commands are separate from ordinary browser access. Higher levels do not add a missing consumer: they cannot make the checked Breath of the Nautilus status supply air. [Permission][command-gate] · [Arguments][command-args] · [Seconds conversion][command-seconds] · [Specific clearing][command-clear] · [Breathing helper][breathing-helper]

## Related pages

- [Status effects](Effects.md), [Movement effects](MovementEffects.md), and [Combat effects](CombatEffects.md)
- [Conduit](../blocks/Conduit.md), [Nautilus](../mobs/Nautilus.md), and [Turtle Shell](../items/TurtleShell.md)
- [Water and Lava](../blocks/WaterAndLava.md), [Mining](../mechanics/Mining.md), and [Brewing](../brewing/Brewing.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. Checked active damage/air/mining handlers, registered effects and potions, described item/loot/mount/Conduit callers, and current Java-to-Rust rendering consumers. **No in-game damage, drowning, visibility, potion, chest, riding, or timing test was run.** Listed benefits and limits describe these source paths; modified data, item components, attributes, other entities' handlers, shader packs, and server timing can change outcomes.

[register-fire-water]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffects.java#L60-L61
[register-conduit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffects.java#L105-L107
[register-breath]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffects.java#L127
[fire-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1147-L1154
[fire-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/damage_type/is_fire.json#L1-L11
[fireball]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/LargeFireball.java#L30-L49
[burning]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Entity.java#L508-L531
[fire-immunity]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Entity.java#L1418-L1420
[lava]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Entity.java#L575-L590
[fire-clear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Entity.java#L597-L618
[fire-potions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L26-L31
[water-potions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L47-L52
[apple]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L29-L40
[apple-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1412-L1418
[eat]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumable.java#L77-L91
[totem]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/DeathProtection.java#L25-L40
[totem-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1349-L1375
[allium-recipe]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_allium.json#L1-L23
[stew-effect]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/SuspiciousStewEffects.java#L38-L42
[stew-level]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/SuspiciousStewEffects.java#L58-L72
[piglin-table]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/gameplay/piglin_bartering.json#L29-L49
[piglin-complete]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L373-L379
[piglin-loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L440-L445
[set-potion]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/functions/SetPotionFunction.java#L14-L35
[lava-fog]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/fog/environment/LavaFogEnvironment.java#L25-L44
[fog-select]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/fog/FogRenderer.java#L248-L287
[fog-collect]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L2816-L2832
[fog-header]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/rust/render/worldrender/geometry/batching.rs#L2188-L2223
[fog-fragment]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/rust/render/shaderpack/programs/builtin/glsl/minimal_terrain_material_fragment_direct.glsl#L83-L89
[overlay]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/ScreenEffectRenderer.java#L80-L123
[breathing-helper]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectUtil.java#L43-L45
[air-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L424-L443
[air-change]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L569-L583
[water-sensitive]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2863-L2868
[mining]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L623-L657
[water-motion]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2313-L2342
[helmet-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L292-L294
[helmet-effect]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L339-L353
[treasure-placement]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/levelgen/structure/structures/BuriedTreasurePieces.java#L71-L73
[treasure-pool]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/chests/buried_treasure.json#L184-L202
[pool-codec]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L30-L38
[pool-functions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L93-L100
[drink]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionContents.java#L232-L235
[water-vision]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/player/LocalPlayer.java#L1033-L1047
[water-fog]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/fog/environment/WaterFogEnvironment.java#L28-L45
[conduit-dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L93-L110
[conduit-shape]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L128-L162
[conduit-grant]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L165-L180
[conduit-block-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/ConduitBlock.java#L53-L58
[conduit-mining]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectUtil.java#L25-L40
[conduit-light]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/LightTexture.java#L223-L245
[lightmap-state]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L2699-L2750
[lightmap-build]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/rust/render/shaderpack/vanilla/lightmap.rs#L113-L154
[lightmap-sample]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/rust/render/shaderpack/programs/builtin/glsl/minimal_terrain_material_vertex.glsl#L108-L121
[nightvision-semantic]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L2990-L3006
[nightvision-uniform]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/rust/render/worldrender/frame/requests.rs#L718-L750
[render-entry]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/Minecraft.java#L1355-L1370
[render-call]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L830-L837
[render-submit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java#L933-L984
[breath-timing]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/nautilus/AbstractNautilus.java#L60-L62
[breath-grant]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/nautilus/AbstractNautilus.java#L237-L249
[nautilus-mount]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/nautilus/AbstractNautilus.java#L138-L179
[effect-base]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffect.java#L82-L95
[brew-fire]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L151-L152
[brew-water]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L168-L169
[brew-all]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L133-L190
[brew-start]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L230-L234
[brew-bootstrap]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/MinecraftServer.java#L336-L340
[brew-stand]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L170-L183
[puffer-food]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L49-L57
[splash]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/ThrownSplashPotion.java#L38-L68
[scales]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L2242-L2255
[browser-potions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1756-L1770
[browser-types]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2207-L2213
[potion-types]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L10-L80
[effect-update]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L123-L170
[effect-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L214-L260
[effect-infinite]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L168-L188
[effect-apply]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L982-L1000
[milk]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L64
[milk-clear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/consume_effects/ClearAllStatusEffectsConsumeEffect.java#L20-L22
[clear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L943-L953
[honey]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L16-L20
[command-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L33-L48
[command-args]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L70-L90
[command-seconds]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L153-L176
[command-clear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L226-L232

# Sculk Sensors and calibration

Use a **Sculk Sensor** to turn eligible nearby game events into a redstone pulse. Use a **Calibrated Sculk Sensor** to listen farther away, recover sooner, and optionally accept only one event frequency. **Output strength measures distance; comparator output identifies the event frequency.** These are different values. [Sensor listener][sensor-user] · [Calibration filter][filter] · [Strength formula][frequency] · [Comparator callback][sensor-comparator]

## Sculk Sensor

**Sculk Sensor** (`minecraft:sculk_sensor`) listens within an **8-block radius**, becomes active for **30 game ticks**, then spends **10 ticks** cooling down. It accepts new ordinary vibrations only while inactive. The range check uses the distance between event and sensor block positions. [Range and activation][sensor-user] · [Range dispatch][range] · [Active and cooldown states][sensor-output] · [Reset tick][sensor-stop]

Find it through [Deep Dark/Ancient City generation, city chests, or catalyst growth](Sculk.md#finding-and-collecting-the-family). Collect it with Silk Touch; without Silk Touch, ordinary mining gives **5 XP and no Sensor item**. Its hardness and blast resistance are **1.5**, and its emitted light is **1**. [Registration][blocks] · [Loot][loot-sculk_sensor] · [XP callback][sensor-comparator]

## Calibrated Sculk Sensor

**Calibrated Sculk Sensor** (`minecraft:calibrated_sculk_sensor`) has a **16-block radius**, **10 active ticks** and the same **10-tick cooldown**. It inherits the ordinary Sensor's hardness, light, waterlogging and comparator behavior. Craft it using [the established Amethyst recipe](Amethyst.md#shard-recipes-and-other-uses). Moving the crafted block still needs **Silk Touch**; breaking it normally gives **5 XP and no item**. [Registration][blocks] · [Radius and filter][filter] · [Active timing][calibrated] · [Recipe][recipe] · [Loot][loot-calibrated_sculk_sensor] · [Inherited XP][sensor-comparator]

Feed a redstone strength into the **back input side**, opposite the block's `facing` direction. Placement sets `facing` to the direction the placer is looking, so the back is toward the placer. Input **0** leaves the sensor unfiltered; **1–15** accepts only matching frequencies. For example, input **1** selects listed movement events, while **12** selects block-destroy and fluid-pickup events. This is an exact equality test, not a minimum-strength threshold. [Placement][calibrated] · [Input lookup and filter][filter] · [Frequency mapping][frequency]

Its normal redstone output excludes that back input side. Other sides carry distance-based power, and the block beneath can receive the inherited direct signal. The comparator reading still reports the selected event's frequency. The input filter is checked when the vibration is accepted; the arrival callback does not reread the input, so changing it does not cancel an already travelling accepted event. [Directional output][calibrated] · [Signal-direction convention][signal-directions] · [Filter timing][filter] · [Arrival activation][sensor-user] · [Comparator][sensor-comparator]

## Detection, travel and cooldown

Sensors detect **registered game events**, not arbitrary audible sounds. The normal listener checks the vibration tag, current phase and event frequency, excludes its own placement/destruction at the sensor's position, then checks signal occlusion. Its block entity is registered as a listener and ticked on the server; these are active callbacks, not unused helpers. [Event tag][vibrations] · [Sensor-specific filter][sensor-user] · [Listener][listener] · [Listener registration][listeners] · [Ordinary ticker][sensor-tick] · [Calibrated ticker][calibrated]

An accepted event first becomes a candidate. Among events competing in the same game tick, the selector prefers the nearer event; equal-distance candidates favor the higher frequency. Selection occurs on a later game tick. Travel delay is **the floor of the exact event-to-listener distance in blocks**, then the sensor enters its active phase on delivery. Ordinary events are ignored while one vibration is travelling. Delivery also waits for the sensor's surrounding **3 × 3 chunks** to be present and ticking. This makes **30 + 10** and **10 + 10** the active/cooldown periods, not complete event-to-ready latencies. [Candidate selection][selection] · [Travel scheduling][travel] · [Travel-time rule][valid] · [Busy listener][listener] · [Chunk gate][delivery]

The direct **step-on** callback is a separate route: an inactive sensor can force-schedule a Step event for an entity standing on it, except a Warden. It still applies the calibrated frequency check, but bypasses the ordinary listener's sneaking and occlusion filters. Do not rely on crouching while standing directly on a Sensor to leave it inactive. [Ground-contact dispatch][step-dispatch] · [Direct Sensor callback][sensor-placement] · [Forced scheduling][listener] · [Calibration check][filter]

## Wool, sneaking and water

For the ordinary vibration-listener route:

- Spectator sources and entities that dampen vibrations are rejected
- Careful/sneaking movement suppresses only the events in the sneaking-ignore tag, including Step, Swim, Hit Ground and Projectile Shoot; it does not silence every action
- An event whose affected block belongs to the dampening tag is rejected; the bundled tag includes Wool and Wool Carpet
- **Wool blocks** can occlude the signal between event and listener; Carpet is not in the signal-occlusion tag

Occlusion casts six slightly offset lines between the two block centers and rejects the event only when all six meet an occluding block. A nearby piece of Wool is not an all-direction shield. See [Wool and Carpet](WoolAndCarpet.md#vibrations) for the canonical dampening/occlusion guide. [Event filtering][valid] · [Sneaking-ignore events][sneak] · [Affected-block dampening][dampens] · [Occluders][occludes] · [Line tests][listener]

Both sensors can be waterlogged. Water suppresses their ordinary activation and reset clicking sounds, but **does not stop detection, redstone output, the tendril-clicking game event, or adjacent-Amethyst resonance**. That game event can still reach an eligible [Shrieker](SculkShrieker.md#what-triggers-a-shriek). [Placement fluid state][sensor-placement] · [Activation and resonance][sensor-water] · [Reset sound condition][sensor-stop]

## Redstone and comparator output

While active, the ordinary Sensor outputs **1–15** redstone strength on all sides; both variants supply direct power downward, subject to the calibrated back-side exception above for normal output. Distance strength is **max(1, 15 − floor(15 × distance ÷ radius))**, using the event and sensor block-position distance at delivery. At distance **4**, an ordinary Sensor gives **8**, while a Calibrated Sensor gives **12**. The same event can therefore produce a different signal strength on the two devices. [Signal callbacks][sensor-output] · [Strength calculation][frequency] · [Delivery distance][delivery] · [Direction convention][signal-directions]

A [Comparator](RedstoneComparator.md) reading an active sensor reports the **frequency below**, independent of distance. It returns **0** outside the active phase, even though the block entity retains its last frequency internally. Normal output also drops to 0 at the beginning of cooldown. [Comparator reading][sensor-comparator] · [Cooldown transition][sensor-output]

### Frequencies

These are the exact bundled event groups accepted by the normal vibration system. Names describe event IDs; a particular interaction must actually emit that event to be detected. [Frequency map][frequency] · [Listenable tag][vibrations]

| Frequency | Event IDs, with the `minecraft:` prefix omitted |
| ---: | --- |
| 1 | `step`, `swim`, `flap` |
| 2 | `projectile_land`, `hit_ground`, `splash` |
| 3 | `item_interact_finish`, `projectile_shoot`, `instrument_play` |
| 4 | `entity_action`, `elytra_glide`, `unequip` |
| 5 | `entity_dismount`, `equip` |
| 6 | `entity_interact`, `shear`, `entity_mount` |
| 7 | `entity_damage` |
| 8 | `drink`, `eat` |
| 9 | `container_close`, `block_close`, `block_deactivate`, `block_detach` |
| 10 | `container_open`, `block_open`, `block_activate`, `block_attach`, `prime_fuse`, `note_block_play` |
| 11 | `block_change` |
| 12 | `block_destroy`, `fluid_pickup` |
| 13 | `block_place`, `fluid_place` |
| 14 | `entity_place`, `lightning_strike`, `teleport` |
| 15 | `entity_die`, `explode` |

`resonate_1` through `resonate_15` use their matching frequencies. On activation a sensor checks its six adjacent blocks for the vibration-resonator tag and emits the matching resonance event at each match. The bundled resonator is the ordinary [Block of Amethyst](Amethyst.md#placing-crystals-and-making-sound), not Budding Amethyst or a cluster. [Resonance caller][sensor-water] · [Resonator tag][resonators] · [Resonance frequencies][frequency]

## Sources and verification

Source-reviewed on **2026-10-02** at `099b1184d1a7115915d880b436c8f58c1ed5a422`. Checked registrations, item/recipe and loot routes, active listeners/tickers, candidate selection, travel and chunk gates, exact event map, calibration input, output direction, comparator timing, occlusion, direct stepping and waterlogging. No in-game circuit, timing, waterlogging, chunk-boundary or vibration test was run. Game ticks are simulation ticks: normally 20 per second, with no fixed wall-clock guarantee under lag.

Related: [Sculk and harvesting](Sculk.md) · [Shriekers](SculkShrieker.md) · [Sensor item](../items/SculkSensor.md) · [Calibrated Sensor item](../items/CalibratedSculkSensor.md) · [Redstone](../redstone/Redstone.md) · [Blocks](Blocks.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/Blocks.java#L6062-L6096
[calibrated]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/CalibratedSculkSensorBlock.java#L35-L85
[dampens]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/tags/block/dampens_vibrations.json#L1-L6
[delivery]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/gameevent/vibrations/VibrationSystem.java#L319-L350
[filter]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/entity/CalibratedSculkSensorBlockEntity.java#L24-L43
[frequency]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/gameevent/vibrations/VibrationSystem.java#L53-L120
[listener]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/gameevent/vibrations/VibrationSystem.java#L200-L255
[listeners]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L697-L719
[loot-calibrated_sculk_sensor]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/blocks/calibrated_sculk_sensor.json#L1-L33
[loot-sculk_sensor]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/blocks/sculk_sensor.json#L1-L33
[occludes]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/tags/block/occludes_vibration_signals.json#L1-L5
[range]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/gameevent/EuclideanGameEventListenerRegistry.java#L114-L122
[recipe]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/recipe/crafting/calibrated_sculk_sensor.json#L1-L16
[resonators]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/tags/block/vibration_resonators.json#L1-L5
[selection]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/gameevent/vibrations/VibrationSelector.java#L26-L59
[sensor-comparator]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SculkSensorBlock.java#L261-L291
[sensor-output]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SculkSensorBlock.java#L176-L215
[sensor-placement]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SculkSensorBlock.java#L65-L108
[sensor-stop]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SculkSensorBlock.java#L83-L94
[sensor-tick]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SculkSensorBlock.java#L151-L168
[sensor-user]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/entity/SculkSensorBlockEntity.java#L76-L133
[sensor-water]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SculkSensorBlock.java#L210-L238
[signal-directions]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/SignalGetter.java#L13-L39
[sneak]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/tags/game_event/ignore_vibrations_sneaking.json#L1-L10
[step-dispatch]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/Entity.java#L847-L853
[travel]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/gameevent/vibrations/VibrationSystem.java#L258-L295
[valid]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/gameevent/vibrations/VibrationSystem.java#L353-L403
[vibrations]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/tags/game_event/vibrations.json#L1-L59

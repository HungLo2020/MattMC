# Movement and controls

Use **WASD** to move and **Space** to jump. In MattMC's defaults, **Left Ctrl is Sneak and Left Shift is Sprint**. Check your bindings before following a control tip: these actions are configurable. This guide covers ordinary on-foot movement; [vehicles](Transport.md), [Elytra](../items/Elytra.md), and [climbing](../blocks/Ladder.md#climbing-and-descent) have their own rules. [Default bindings][bindings] · [Letter keys][letter-keys] · [Key names][key-names] · [Live keyboard input][keyboard]

## Choosing your controls

| Action | Default key |
| --- | --- |
| Forward / backward | W / S |
| Strafe left / right | A / D |
| Jump | Space |
| Sneak / crouch | Left Ctrl |
| Sprint | Left Shift |

Open **Controls → Key Binds** to change an action's key. The Controls screen also exposes **Sneak**, **Sprint**, and **Sprint Window** settings. Sneak and Sprint both default to **Hold**: their input follows whether the key is pressed. **Toggle** flips that input on each press. These are input settings; an active sprint still has the start/stop rules below. [Controls menu][controls] · [Rebinding][rebind] · [Hold/Toggle defaults][options] · [Toggle handling][toggle]

**Sprint Window** controls double-tapping Forward. It defaults to **7 ticks**, accepts **0–10**, and **0 / Off** disables that route. The first eligible forward press starts the countdown; a fresh forward press while it remains positive can start sprinting. Sneak input, ordinary on-foot item use, or Backward clears the pending window. The Sprint key remains an alternative when the window is Off. [Window setting][window] · [Input transitions and sprint requests][sprint-tick]

## Starting and stopping a sprint

For an ordinary run, move forward and press Sprint, or double-tap Forward within the configured window. The checked start conditions require:

- **Forward input** and no active [Blindness](../effects/VisionEffects.md#blindness)
- **More than 6 hunger points** in ordinary on-foot play; see [Hunger](Hunger.md#exhaustion-and-sprinting). The ability to fly bypasses this food check
- **No active item use**. Eating or another held-use action prevents a new sprint
- **No crouching/crawling slowdown or Elytra glide**, unless underwater
- **No shallow-water state** while not flying: touching water with your eyes out of it differs from being underwater

Riding bypasses the food check but has an additional vehicle-sprint eligibility check; it does not make every vehicle sprint. [Complete start/food/vehicle checks][sprint-start] · [Blindness restriction][blindness] · [Water-state distinction][water-state]

An existing **run sprint** stops when forward input ends, sprint eligibility fails, or you hit a horizontal obstruction that the movement check does not classify as minor. **Releasing the Sprint key or toggling its request off is not itself a run-stop condition.** Release Forward to end the ordinary run sprint; momentum and terrain still affect the subsequent motion. Crouching and item use can slow input without being separate entries in this run-stop check. [Run and swim stop checks][sprint-stop] · [Active dispatch][sprint-tick] · [Input slowdown][slow-input] · [Air movement][air-movement]

## Crouching, crawling, and edges

Sneak requests a crouched stance during ordinary non-flying play. Low headroom can also keep you crouched after you release the key. If neither the desired stance nor crouching fits, but the low swimming-shaped pose does, collision handling uses that lower pose. On dry land this counts as **visual crawling**. Pressing Sneak in open space does not independently request a crawl. [Crouch input conditions][crouch] · [Pose and clearance checks][pose] · [Visual crawling][crawl]

Crouching and visual crawling apply the **sneaking-speed input factor**, normally **0.3**. Ordinary on-foot item use separately multiplies input by **0.2**; both can apply together. These are input factors, not measured travel speeds. The common input adjustment, diagonal handling, movement attributes, friction and existing velocity still matter. [Swift Sneak](../enchanting/MobilityEnchantments.md#swift-sneak-crouching-and-crawling) changes the sneaking factor; [Speed and Slowness](../effects/MovementEffects.md#speed) affect a different attribute. [Base factor][sneak-attribute] · [Player attribute][player-attributes] · [Input processing][slow-input] · [Movement consumers][air-movement] [ground-movement]

**Keep Sneak active when edging along a ledge**, but leave a safe margin. Its edge adjustment trims horizontal movement only when you are not flying, are not moving upward, and are on the ground or pass the nearby-support check based on step height and accumulated fall distance. It handles the ordinary self/player movement types. A low-looking pose alone does not activate this protection; the check uses Sneak input. It is not protection against every fall or push, and does not cancel fall damage. [Sneak intent][sneak-intent] · [Conditional edge adjustment][edge] · [Movement callsite][edge-call] · [Fall-damage path][fall-damage]

## Swimming and getting out of a low passage

To enter swimming, get your **eyes underwater** and use Forward plus Sprint. The actual swimming transition requires sprinting, underwater state, water-tagged fluid at your body position, and no passenger state. Active flight disables swimming. Once entered, swimming can continue with your eyes above water while you remain in water, sprinting, and not riding. [Swimming entry and continuation][swim-state] · [Underwater detection][water-state] · [Flight exception][player-swim]

Use **Jump to rise** in ordinary deeper-water movement and **Sneak to descend**. Looking up or down also influences vertical motion while swimming, with surface/Jump checks affecting ascent. Swim sprint has its own stop rule: losing Forward stops it when you are also off the ground and not pressing Sneak; leaving water or losing sprint eligibility also stops it. [Upward input][jump-input] · [Downward input][water-down] · [Vertical motion][water-vertical] · [Swimming direction][swim-direction] · [Swim stop rule][sprint-stop]

**Crawling through a dry low passage is not actual swimming.** It shares the low pose, but the visual-crawl check and water-travel state are separate. If you cannot stand after leaving a passage, release Sneak or toggle it off and move to a space where your full standing shape fits. [Pose selection][pose] · [Crawl distinction][crawl] · [Travel selection][travel]

Swimming supplies no breathing exemption by itself. [Air-supply check][breathing] Plan access to air using [water and breathing guidance](../blocks/WaterAndLava.md#light-fire-and-breathing). Water equipment belongs with [Depth Strider](../enchanting/MobilityEnchantments.md#depth-strider-water-movement) and [Dolphin's Grace](../effects/MovementEffects.md#dolphins-grace).

## Quick troubleshooting

- **Sprint will not start:** check the configured key/window, forward input, hunger, Blindness, held item use, low stance, and shallow water using the conditions above
- **Jump behaves differently:** ordinary ground jumping needs suitable ground/fluid conditions and clearance affects the result. [Jump Boost](../effects/MovementEffects.md#jump-boost), [Elytra](../items/Elytra.md#starting-and-controlling-a-glide), and [Elevators](../blocks/Elevator.md#controls-and-destination-search) have separate behavior; do not assume a fixed jump height. [Jump input][jump-input] · [Jump calculation][ground-jump] · [Elevator hook][elevator-jump]
- **Sneak descends instead of holding position:** water and [Scaffolding](../blocks/Scaffolding.md#climbing-and-collision) use descent rules. Follow [Ladders](../blocks/Ladder.md#climbing-and-descent) or [Vines](../blocks/Vines.md#trimming-climbing-and-water) for their climbing controls

## Sources and verification

Source-reviewed on **2026-10-04** at MattMC commit `cc140840a21e5c6c932c23abf34124418d6506b0`. Checked configurable bindings, the installed keyboard-input path, local sprint/slow-input handling, player pose/edge/swimming checks, and the active living-entity jump/travel consumers. [Input installation][input-install] [input-respawn] · [Movement tick][movement-tick] · [Player travel][swim-direction]

No in-game controls, timing, collision, fall, swimming, movement-speed or jump-height test was run. The input factors and conditions describe this source snapshot, not measured travel performance or parity with another game build. Equipment, effects, terrain and later changes can alter results.

Related: [Mechanics](Mechanics.md) · [Hunger](Hunger.md) · [Movement effects](../effects/MovementEffects.md) · [Mobility enchantments](../enchanting/MobilityEnchantments.md) · [Transport](Transport.md)

[bindings]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/Options.java#L555-L561
[letter-keys]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/blaze3d/platform/InputConstants.java#L371-L393
[key-names]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/blaze3d/platform/InputConstants.java#L455-L459
[keyboard]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/player/KeyboardInput.java#L17-L40
[controls]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/options/controls/ControlsScreen.java#L17-L40
[rebind]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/options/controls/KeyBindsScreen.java#L62-L85
[options]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/Options.java#L516-L523
[toggle]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/ToggleKeyMapping.java#L29-L38
[window]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/Options.java#L530-L540
[sprint-tick]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/player/LocalPlayer.java#L650-L711
[sprint-start]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/player/LocalPlayer.java#L1009-L1031
[blindness]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/player/Player.java#L1919-L1921
[water-state]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/Entity.java#L1456-L1462
[sprint-stop]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/player/LocalPlayer.java#L804-L810
[slow-input]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/player/LocalPlayer.java#L563-L625
[air-movement]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2288-L2310
[ground-movement]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2458-L2503
[crouch]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/player/LocalPlayer.java#L660-L669
[pose]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/player/Player.java#L359-L390
[crawl]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/Entity.java#L2577-L2590
[sneak-attribute]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L78-L80
[sneak-intent]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/player/Player.java#L308-L318
[edge]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/player/Player.java#L910-L969
[edge-call]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/Entity.java#L692-L705
[fall-damage]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/player/Player.java#L1342-L1373
[swim-state]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/Entity.java#L1479-L1485
[player-swim]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/player/Player.java#L1324-L1331
[jump-input]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2776-L2813
[water-down]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/player/LocalPlayer.java#L741-L744
[water-vertical]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2231-L2237
[swim-direction]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/player/Player.java#L1295-L1317
[travel]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2257-L2265
[ground-jump]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2204-L2228
[elevator-jump]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/player/Player.java#L510-L523
[input-install]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/multiplayer/ClientPacketListener.java#L545-L549
[input-respawn]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/multiplayer/ClientPacketListener.java#L1337-L1341
[movement-tick]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2773-L2831
[breathing]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L423-L447
[player-attributes]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/player/Player.java#L220-L235

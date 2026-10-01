# Buttons

Buttons provide temporary redstone power. Stone and Oak Buttons both emit signal **15** while pressed, but have different pulse times and arrow behavior.

## Stone and Oak comparison

| Property | Stone Button | Oak Button |
| --- | --- | --- |
| Recipe | One Stone, shapeless | One Oak Planks, shapeless |
| Ordinary press time | 20 game ticks | 30 game ticks |
| At 20 game ticks per second | 1 second | 1.5 seconds |
| Arrow activation | Disabled by its block-set type | Enabled |
| Item ID | `minecraft:stone_button` | `minecraft:oak_button` |

Game ticks are not redstone ticks. The table reports the actual scheduled game-tick delays in the registrations.

## Placement and operation

Attach a button to a supported floor, wall, or ceiling face. Interact with an unpressed button to power it and schedule its release check. Interacting while already pressed does not restart the ordinary press method in the checked path.

The button supplies direct power in its connected direction and updates neighbors when changing state. Certain trigger-capable explosions can press an unpowered button as well.

## Arrow detail

For Oak, the release check searches for an `AbstractArrow` inside the button's current shape. If one remains there, the button stays powered and schedules another check. Therefore arrow-held activation can last longer than the ordinary 30-tick click pulse.

This is an arrow-class check, not a promise that every thrown item or projectile activates wooden buttons. Stone's block-set type disables that check.

## Other materials

Other buttons have their own registrations. [Pewen](Pewen.md) uses a 30-tick cherry-type button, but its recipe has a documented integration problem. A registered button's timing does not prove that its crafting route works.

## Related pages

- [Stone Button item](../items/StoneButton.md)
- [Oak Button item](../items/OakButton.md)
- [Lever](Lever.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No circuit simulation or in-game timing test was run; feature flags can select different wire evaluators.

- [Button state and arrow checks](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/ButtonBlock.java)
- [Stone timing](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/Blocks.java#L1915)
- [Oak timing](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/Blocks.java#L2724)
- [Block-set arrow properties](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/state/properties/BlockSetType.java)
- [Stone recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/stone_button.json)
- [Oak recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/oak_button.json)
- [Support rules](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/FaceAttachedHorizontalDirectionalBlock.java)

# Lever

A Lever is a redstone switch that stays in its selected state. Use it when a circuit should remain on until deliberately switched off, rather than sending a short button pulse.

## Crafting and placement

Place one Stick above one **Cobblestone** to craft one Lever. The recipe names Cobblestone specifically; it does not use the broader stone-crafting-materials tag.

Levers attach to supported floor, wall, or ceiling faces. Their attached-block survival check must remain valid; removing the support can remove the lever.

## Switching

Interact to toggle its powered state. When on, its signal is **15**; when off, **0**. The implementation also supplies direct power in its connected direction and updates the lever's neighbors and attached block.

Unlike a button, ordinary activation does not schedule an automatic release. Certain explosions that are allowed to trigger blocks can also toggle a lever, so an exposed switch is not guaranteed to remain unchanged during every nearby event.

## Choosing a control

- Choose a Lever for sustained power, such as keeping a test circuit enabled.
- Choose a [Button](Buttons.md) for a timed pulse.
- Use [Redstone Dust](RedstoneDust.md) to carry a signal over a supported path, checking connections and distance.

## Related pages

- [Lever item](../items/Lever.md)
- [Redstone basics](../redstone/Redstone.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No circuit simulation or in-game timing test was run; feature flags can select different wire evaluators.

- [Crafting recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/lever.json)
- [Toggle and signal behavior](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/LeverBlock.java)
- [Attachment and support](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/FaceAttachedHorizontalDirectionalBlock.java)

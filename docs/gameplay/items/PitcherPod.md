# Pitcher Pod

Pitcher Pod is a plantable item registered as `minecraft:pitcher_pod`. In MattMC it also activates the conversion of an ordinary Nether portal into a **Primordial Caves portal**.

## Obtaining

The Sniffer digging implementation calls a loot table with one roll between **Torchflower Seeds and Pitcher Pod**, at equal default weights. That gives a **50% Pitcher Pod choice per execution of that loot pool**. It is not a fixed digging-time guarantee or a complete guide to acquiring the first Sniffer.

Breaking the lower half of an immature Pitcher crop (ages 0–3) returns one pod under the checked loot table. At mature age 4, it returns a **Pitcher Plant** instead. Growing the pod is therefore not a verified way to multiply pods for portal conversion.

## Portal conversion

Drop **one pod**, separated from the rest of the stack, into a complete active Nether portal. MattMC's portal code attempts to convert the portal blocks and then discards the **whole dropped stack**. An incomplete portal check can fail without preserving the dropped item, so do not experiment with a valuable stack.

The [Primordial Caves guide](../dimensions/PrimordialCaves.md) explains the destination and return route. Conversion changes the portal; it does not keep its ordinary Nether destination alongside the new one.

## Planting

The crop uses Farmland support and grows to age 4, with an upper half as it becomes tall. Its crop class is separate from ordinary Wheat; do not assume the same automatic right-click or hoe area-harvesting methods apply.

Bone meal can advance its growth by one stage when the crop's growth/space checks allow it. Keep room above it for the tall stages.

## Related pages

- [Primordial Caves](../dimensions/PrimordialCaves.md)
- [Sniffer](../mobs/Sniffer.md)
- [Farmland](../blocks/Farmland.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game portal round trip, generation, or Sniffer-digging test was run. World presets and data packs can change the definitions.

- [Sniffer drop path](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L274-L284)
- [Digging loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/gameplay/sniffer_digging.json)
- [Crop age/drop distinction](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/pitcher_crop.json)
- [Crop support and growth](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/PitcherCropBlock.java)
- [Portal conversion](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/NetherPortalBlock.java#L110-L172)
- [Item registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java)

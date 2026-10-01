# Torch

Torch is the inventory item for standing and wall-mounted [Torches](../blocks/Torch.md). Its item ID is `minecraft:torch`.

## Obtaining and use

Craft four Torches with one Coal or Charcoal above one Stick. Place one on a suitable supporting top or side face to create a light-level-14 torch.

The same item selects the standing or wall form during placement. Neither form uses a fuel slot or refueling timer in the checked implementation. For exact support requirements and comparisons with Soul, Copper, and Redstone Torches, use the block guide.

## Related pages

- [Torch block](../blocks/Torch.md)
- [Coal](Coal.md), [Charcoal](Charcoal.md)
- [Items](Items.md)

## Sources and verification

Reviewed at source `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game placement, sleeping, respawn, or mining test was performed.

- [Recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/torch.json)
- [Standing/wall item](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java#L464-L468)
- [Light registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/Blocks.java#L1193-L1202)

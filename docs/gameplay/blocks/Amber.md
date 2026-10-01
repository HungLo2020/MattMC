# Amber

Amber is a transparent decorative block integrated from Alex's Caves. It passes skylight and can be used for warm-colored windows or display cases. Unlike [Ambersol](Ambersol.md), its registration does not give it light emission or a downward light column.

## Obtaining

Amber is available in the Creative inventory as `minecraft:amber`. A natural-generation route or crafting recipe has not been established from the active MattMC sources reviewed here.

The block loot table contains one Amber item, subject to surviving an explosion, but normal player harvesting also requires a correct tool. Amber is absent from the checked mining-tool tags, so a reliable Survival harvesting tool is **not verified**. Do not assume the block will drop just because a loot entry exists.

## Building with Amber

The block uses transparent-block behavior: it lets skylight pass downward and does not visually darken its surface through the ordinary shade-brightness setting. Its collision is still that of a solid block; transparency does not mean entities can walk through it.

Adjacent blocks of the same type hide their shared faces. Exact visual appearance, textures, and lighting should be checked in the running build before committing to a large design.

| Property | Registered value |
| --- | --- |
| ID | `minecraft:amber` |
| Hardness | 0.3 |
| Blast resistance | 2 |
| Sound set | Glass |
| Map color | Orange |

## Related pages

- [Amber item](../items/Amber.md)
- [Ambersol](Ambersol.md)
- [Blocks](Blocks.md)

## Sources and verification

Reviewed against source snapshot `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. These are source-defined rules, not an in-game test.

- [Registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/Blocks.java#L5958-L5961)
- [Transparency and skylight](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/TransparentBlock.java)
- [Shared-face behavior](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/HalfTransparentBlock.java)
- [Loot table](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/amber.json)
- [Creative inventory](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L833-L835)
- [Mining-tool tags](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json)

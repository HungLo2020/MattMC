# Roadrunner Feather

Roadrunner Feather is a special material registered as `minecraft:roadrunner_feather`. It is not registered as food and is distinct from the ordinary Feather item.

## Obtaining

Keep a living adult [Roadrunner](../mobs/Roadrunner.md) in ticking range. Its periodic feather timer runs for 24,000–47,999 game ticks, roughly 20–40 minutes at 20 ticks per second, and drops one feather before resetting. The item is also listed in Creative inventory.

No dedicated death-loot table was found for Roadrunner in the checked data. The verified source is the live bird's timer, not an assumed kill drop.

## Uses and limits

The item uses a plain item registration. No recipe consuming `minecraft:roadrunner_feather` was found in the current bundled recipe data reviewed here. Upstream equipment recipes should not be imported as working MattMC instructions without active recipe evidence.

## Related pages

- [Roadrunner](../mobs/Roadrunner.md)
- [Items](Items.md)

## Sources and verification

Reviewed against `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. Source-defined behavior only; no in-game capture, breeding, combat, or persistence test.

- [Periodic production](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexsmobs/entity/EntityRoadrunner.java)
- [Integrated item reference](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexsmobs/item/AMItemRegistry.java#L43)
- [Item registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java#L1611)
- [Creative listing](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1809)

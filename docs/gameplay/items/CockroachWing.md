# Cockroach Wing

**Cockroach Wing** is a registered material item with **no bundled crafting use or verified Survival acquisition route** in the checked MattMC snapshot. It is not flight equipment. [Registration][items] · [Bundled data][data]

## Obtaining

The **Ingredients** Creative category lists the wing. No bundled recipe, loot table, trade or Cockroach interaction awards it in the reviewed source and data. [Creative entry][creative] · [Bundled data][data] · [Cockroach implementation][roach]

Do not kill or shear [Cockroaches](../mobs/Cockroach.md) expecting wings. Their periodic item production awards [Cockroach Ootheca](CockroachOotheca.md), and their shearing callback changes the headless state without dropping a wing. The normal and Maraca-specific Cockroach death-loot tables referenced by the mob are absent from bundled data. [Adult production][production] · [Shearing][shear] · [Loot selection][loot] · [Bundled loot][loot-data]

## Usage

No bundled recipe consumes wings or converts [Cockroach Wing Fragments](CockroachWingFragment.md) into a wing. The two names identify separate items, not an implemented crafting progression. [Registration][items] · [Bundled data][data]

## Behavior

Default wings stack to **64** and have no durability, food or equipment component. Using a plain wing has no special action. [Registration][items] · [Common components][components] · [Ordinary item use][use]

## Notes

Registered as `minecraft:cockroach_wing`.

Related: [Cockroach](../mobs/Cockroach.md) · [Cockroach Ootheca](CockroachOotheca.md) · [Cockroach Wing Fragment](CockroachWingFragment.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Checked registrations, all Java/data references to the wing, Cockroach rewards, recipes and loot. No in-game acquisition or use test was run. Server data packs and custom item components can change these defaults.

[items]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1821-L1824
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1775-L1819
[roach]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityCockroach.java
[production]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L326-L333
[shear]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L385-L401
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L177-L183
[data]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data
[loot-data]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/entities
[components]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Item.java#L164-L192

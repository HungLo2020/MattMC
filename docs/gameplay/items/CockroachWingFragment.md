# Cockroach Wing Fragment

**Cockroach Wing Fragment** is a separate registered material item from [Cockroach Wing](CockroachWing.md). The checked MattMC snapshot provides neither a bundled fragment-to-wing recipe nor a verified Survival source for fragments. [Registration][items] · [Bundled data][data]

## Obtaining

Fragments appear in the **Ingredients** Creative category. No bundled recipe, loot table, trade or Cockroach reward was found that supplies them. [Creative entry][creative] · [Bundled data][data] · [Cockroach behavior][roach]

[Cockroaches](../mobs/Cockroach.md) do not have an implemented wing-fragment shedding or shearing reward. Adults periodically drop [ootheca](CockroachOotheca.md); shearing changes the headless state without awarding fragments. The mob's referenced normal and Maraca-specific death-loot tables are missing from bundled data, so a Cockroach death is not an established fragment source either. [Adult production][production] · [Shearing][shear] · [Loot selection][loot] · [Bundled loot][loot-data]

## Usage

No bundled recipe consumes fragments, combines them into wings or breaks wings down into fragments. A Creative listing alone does not establish one of those recipes. [Registration][items] · [Bundled data][data]

## Behavior

Default fragments stack to **64**, have no durability and provide no food, equipment or special use action. [Registration][items] · [Common components][components] · [Ordinary item use][use]

## Notes

Registered as `minecraft:cockroach_wing_fragment`.

Related: [Cockroach](../mobs/Cockroach.md) · [Cockroach Wing](CockroachWing.md) · [Cockroach Ootheca](CockroachOotheca.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Checked registrations, all Java/data references to the fragment, Cockroach rewards, recipes and loot. No in-game acquisition or use test was run. Server data packs and custom item components can change these defaults.

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

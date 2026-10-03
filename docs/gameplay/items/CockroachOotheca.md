# Cockroach Ootheca

A **Cockroach Ootheca** is a throwable egg case laid by adult [Cockroaches](../mobs/Cockroach.md). Each impact can produce **zero, one or two babies**. It stacks to **16** and is registered as `minecraft:cockroach_ootheca`. [Registration][items] · [Hatching][impact]

## Obtaining

A living adult Cockroach drops one ootheca after **24,000–47,999 adult ticks**, then starts another timer in the same range. That is approximately **20–40 minutes at 20 ticks per second**. Babies do not advance this timer. No feeding, mating, Maraca or nest is required for the drop. [Adult production][production]

This produces more eggs once you already have a Cockroach; it does not establish a natural way to find the first one. The [Cockroach guide](../mobs/Cockroach.md#obtaining) explains the missing natural-spawn integration. The item also appears in the **Ingredients** Creative category. No bundled crafting or loot-table source was found. [Creative entry][creative] · [Bundled data][data]

## Usage

Use the held item to throw it. Each throw consumes one in Survival; Creative's unlimited-materials behavior preserves the stack. It is not food or a placeable egg block. [Item use][use] · [Consumption][consume]

On impact, the server selects **0, 1 or 2 babies with equal probability** and removes the projectile. A throw therefore has a **one-third chance of producing none**. Babies appear at the impact position and begin at age -24,000 ticks, approximately **20 minutes of ticking time** before adulthood. Unlike the offspring created through Cockroach breeding, this hatching path does not set the special calm "breaded" state. Prepare an enclosure and see the mob's [care and persistence advice](../mobs/Cockroach.md#behavior). [Impact handling][impact] · [Growth][growth] · [Breeding offspring][offspring]

## Behavior

A [Dispenser](../blocks/DispenserAndDropper.md) **ejects the ootheca as an item**. Although the item provides a projectile factory, the checked Dispenser registrations do not connect it to projectile dispensing. Dropping or dispensing the inventory item does not run the thrown projectile's hatching code. [Projectile factory][use] · [Dispenser registrations][dispense] · [Fallback selection][fallback] · [Item ejection][ejection]

## Notes

Related: [Cockroach](../mobs/Cockroach.md) · [Cockroach Spawn Egg](CockroachSpawnEgg.md) · [Cockroach Wing](CockroachWing.md) · [Cockroach Wing Fragment](CockroachWingFragment.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Checked active item/entity registrations, adult production, projectile impact, consumption, Dispenser dispatch and bundled data. No in-game production, throwing, hatching or dispensing test was run. Server data packs can change acquisition.

[items]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1821-L1824
[production]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L326-L333
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1775-L1819
[data]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data
[use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/item/CockroachOothecaItem.java#L18-L44
[consume]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1080
[impact]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityCockroachEgg.java#L40-L57
[growth]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/AgeableMob.java#L130-L164
[offspring]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L375-L383
[dispense]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java
[fallback]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/DispenserBlock.java#L102-L113
[ejection]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/dispenser/DefaultDispenseItemBehavior.java#L21-L46

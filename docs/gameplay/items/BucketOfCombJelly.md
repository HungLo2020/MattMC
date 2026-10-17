# Bucket of Comb Jelly

**Bucket of Comb Jelly** (`minecraft:comb_jelly_bucket`) uses the same incomplete creature-release class as the Catfish buckets. **Do not rely on it to return a captured creature to the world.** The missing insertion is tracked in [#801](https://github.com/HungLo2020/MattMC/issues/801). [Item binding][item] · [Release implementation][release]

## Obtaining

Use a **Water Bucket** on a living [Comb Jelly](../mobs/CombJelly.md) to capture it. The active interaction produces this filled item and removes the creature; an empty Bucket does not qualify. Capture is not proof that the later release works. [Species interaction][capture] · [Filled-item selection][selection] · [Registry alias][alias] · [Shared capture][pickup]

## Usage

Ordinary use runs water handling and then the custom creature callback. That callback creates and configures a Comb Jelly but **never adds it to the server level**. A successful water action can still return an empty Bucket in Survival. Creative's infinite-material handling preserves the filled item but does not repair the missing entity insertion. [Custom callback][release] · [Bucket use and result][use] · [Creation versus addition][creation]

## Behavior

The filled item stacks to **one** and is bound to the Water fluid. Saved creature data does not establish a successful capture-and-release round trip; the tracked repair needs to preserve the appropriate creature state as well as actually insert it. See the [shared Catfish bucket explanation](BucketOfSmallCatfish.md#usage) for the caller distinction. [Registration][item] · [Issue scope](https://github.com/HungLo2020/MattMC/issues/801)

## Notes

This is a source-reviewed correction at `2eb6ad396dd26dadffc33336088870d7068a9835`, 2026-10-02. No capture, release, loss or multiplayer test was performed. The [Comb Jelly mob guide](../mobs/CombJelly.md) remains a separate coverage task.

[item]: https://github.com/HungLo2020/MattMC/blob/2eb6ad396dd26dadffc33336088870d7068a9835/src/main/java/net/minecraft/world/item/Items.java#L1826-L1830
[release]: https://github.com/HungLo2020/MattMC/blob/2eb6ad396dd26dadffc33336088870d7068a9835/src/main/java/net/alexsmobs/item/ItemModFishBucket.java#L39-L70
[capture]: https://github.com/HungLo2020/MattMC/blob/2eb6ad396dd26dadffc33336088870d7068a9835/src/main/java/net/alexsmobs/entity/EntityCombJelly.java#L247-L249
[selection]: https://github.com/HungLo2020/MattMC/blob/2eb6ad396dd26dadffc33336088870d7068a9835/src/main/java/net/alexsmobs/entity/EntityCombJelly.java#L145-L152
[alias]: https://github.com/HungLo2020/MattMC/blob/2eb6ad396dd26dadffc33336088870d7068a9835/src/main/java/net/alexsmobs/item/AMItemRegistry.java#L34
[pickup]: https://github.com/HungLo2020/MattMC/blob/2eb6ad396dd26dadffc33336088870d7068a9835/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L76-L94
[use]: https://github.com/HungLo2020/MattMC/blob/2eb6ad396dd26dadffc33336088870d7068a9835/src/main/java/net/minecraft/world/item/BucketItem.java#L72-L92
[creation]: https://github.com/HungLo2020/MattMC/blob/2eb6ad396dd26dadffc33336088870d7068a9835/src/main/java/net/minecraft/world/entity/EntityType.java#L1736-L1776

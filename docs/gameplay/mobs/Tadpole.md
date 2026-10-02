# Tadpole

**Tadpoles** (`minecraft:tadpole`) are the aquatic young stage that grows into a [Frog](Frog.md). Keep them in water and use a [Water Bucket](../items/WaterBucket.md) to move them before they mature. Their registered health is **6 points (3 hearts)**. [Entity registration][entity] · [Active attributes][attributes] · [Tadpole attributes and growth][tadpole]

## Getting tadpoles

Breed Frogs and let their [Frogspawn](../blocks/Frogspawn.md#hatching) hatch. That is the verified Survival production route; the bundled biome spawn tables do not list natural Tadpole groups. A [Bucket of Tadpole](../items/BucketOfTadpole.md) releases an existing Tadpole; Creative players also have the [Tadpole Spawn Egg](../items/TadpoleSpawnEgg.md). [Hatching][spawn] · [Bucket release][mob-bucket] · [Spawn-egg registration][egg]

## Water care and transport

Keep a contained pool available. Tadpoles inherit the fish's flopping behavior on dry ground and the water-animal air callback, which causes drowning-type damage when they remain out of water. They cannot be leashed through that water-animal behavior; a Water Bucket is the intended checked transport route. [Fish movement][fish] · [Water and leash rules][water-animal] · [Bucket capture](../items/BucketOfTadpole.md#capturing-a-tadpole)

Tadpoles report bucket-style persistence even when they were hatched normally, so ordinary distance-based despawning is disabled by the inherited fish checks. Frogspawn-created Tadpoles are additionally marked persistent. Persistence does not protect them from environmental damage. [Persistence callbacks][tadpole] · [Fish persistence][fish] · [Hatching][spawn]

## Growth and feeding

A new Tadpole starts at age zero and becomes a Frog after **24,000 server-side growth ticks**, approximately **20 minutes at 20 ticks per second** while ticking. This timer begins after hatching; it is separate from the Frogspawn hatch delay. [Age increment and conversion][tadpole]

Feed **[Slimeballs](../items/Slimeball.md)** to speed growth. Each feeding consumes one in Survival and advances growth by approximately **10% of the remaining time**, rounded down to whole seconds. The bundled frog-food item tag contains only Slimeball, and both the feeding and temptation behavior use that food family. Near adulthood, rounding can make the growth gain zero. [Food tag][food] · [Feeding callback][tadpole] · [Speed-up rounding][feeding] · [Tadpole behavior][ai]

Capturing a Tadpole preserves its age and health. Growth resumes from the saved age after release; see [Bucket of Tadpole](../items/BucketOfTadpole.md#what-the-bucket-preserves).

## Choosing the adult variant

Move the Tadpole to a suitable pool in the target biome before maturation. The conversion creates a Frog, runs the Frog's current spawn-finalization callback, and chooses a variant using the **biome at the maturation position**. The parents' variants and the original hatching location do not determine that selection. The resulting Frog is marked persistent. [Conversion callback][tadpole] · [Variant selection][frog]

Use the [Frog variant guide](Frog.md#variants-and-where-to-grow-tadpoles) for verified biome examples and the connection to Froglights. Once it has grown into a Frog, simply moving it to another biome does not repeat this Tadpole conversion.

## Drops

The bundled Tadpole loot table has no item pools, and Tadpole's experience-drop callback returns false. Raising or transporting it is the useful lifecycle route; killing it provides no ordinary Tadpole loot or experience. [Loot][loot] · [Experience override][tadpole]

Related: [Frog](Frog.md) · [Frogspawn](../blocks/Frogspawn.md) · [Bucket of Tadpole](../items/BucketOfTadpole.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. No in-game hatching, growth, feeding, bucket transfer, variant selection, or water care test was run. Data packs can change tags, variant selection, and loot; the values above describe bundled source behavior.

[entity]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L1353-L1355
[attributes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L258
[tadpole]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/frog/Tadpole.java
[spawn]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/FrogspawnBlock.java
[mob-bucket]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/MobBucketItem.java
[egg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1970
[fish]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/AbstractFish.java
[water-animal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java
[food]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/frog_food.json
[feeding]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/AgeableMob.java#L166-L168
[ai]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/frog/TadpoleAi.java
[frog]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/frog/Frog.java#L296-L303
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/entities/tadpole.json

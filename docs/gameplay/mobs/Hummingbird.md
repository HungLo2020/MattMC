# Hummingbird

The **Hummingbird** (`minecraft:hummingbird`) is a small flying animal with **4 health points, or 2 hearts**. The mob and its spawn egg are registered, but its bundled food, pollination, feeder, and natural-spawn integrations are incomplete. Use it as a source-confirmed Creative animal; do not plan a crop farm or feeding setup from the imported class names alone. [Mob and goals][bird] · [Entity registration][entity] · [Attributes][attributes]

## Availability

No Hummingbird entry was found in the bundled biome spawn tables, and its light/surface helper has no active spawn-placement registration. Its helper permits an appropriate tagged surface or air and brightness above 8, but that does not establish a naturally populated biome. The checked access route is the [Hummingbird Spawn Egg](../items/HummingbirdSpawnEgg.md) or an administrator-provided mob. [Spawn registrations][placements] · [Spawn helper][bird] · [Egg registration][egg]

Its registered body is **0.4 blocks wide and 0.4 blocks high**. Give a display enclosure enough room to fly; those dimensions are not a tested cage specification. [Entity dimensions][entity]

## Flight and appearance

The active tick forces flight and disables gravity. The bird has a flight movement controller, flying navigation, wandering, parent-following, and floating goals. It has no registered attack or target-selection goals, and its base attack attribute is zero. This describes its own goal list, not immunity to predators or environmental damage. [Controller, goals, and tick][bird]

Normal spawn initialization chooses one of **three variants with equal random chances**. The renderer uses a separate texture for each variant, and the selected variant is saved. The offspring factory creates a fresh bird without copying either parent's variant; do not assume that using an egg on a bird inherits its appearance. [Variant initialization and save][bird] · [Variant renderer][renderer]

## Food and breeding limits

Both the direct food check and food-following goal use `minecraft:hummingbird_breedables`. The corresponding bundled item tag is absent. Consequently, there is no supplied item that triggers ordinary food attraction, love mode, or food-assisted baby growth through those checks. Sugar, flowers, and Honey Bottles should not be presented as working Hummingbird food here. [Tag definition][tags] · [Food and goals][bird] · [Shared feeding][animal]

A breeding goal and Hummingbird offspring factory exist, but their presence does not supply the missing food trigger. Using the matching spawn egg on an existing Hummingbird is a separate baby-creation path, explained on the [egg page](../items/HummingbirdSpawnEgg.md#using-on-a-hummingbird). No ordinary taming, owner-follow, player-riding, or sit-command interaction is implemented in the checked class. [Offspring and goal list][bird] · [Egg offspring path][egg-class]

## Pollination and feeder limits

The pollination goal selects targets through `minecraft:hummingbird_pollinates`, whose bundled block tag is absent. Without eligible targets it cannot establish an active crop-boosting route. Its conditional growth helper applies Bone Meal only to a qualifying, not-fully-grown `CropBlock`; that dormant branch is not evidence that every plant can be fertilized. [Target and growth checks][pollination] · [Tag definition][tags]

A Hummingbird Feeder class and feeding goal remain in source, but no matching feeder block/item registration was found in the current block and item registries. The feeder point-of-interest holder explicitly remains a stub key. There is therefore no checked craft/place/fill feeder workflow to document in this snapshot. Server modifications could complete those integrations, but the ordinary bundled setup does not. [Feeder goal][bird] · [Feeder class][feeder] · [Point-of-interest stub][poi] · [Block registry][blocks] · [Item registry][items]

## Persistence and drops

The inherited animal rule prevents ordinary distance-based despawning. Variant, pollination count/cooldown, and remembered feeder position are saved, but persistence does not keep a flying bird inside its enclosure or protect its low health. [Save fields][bird] · [Animal persistence][animal]

No dedicated bundled Hummingbird death-loot table was found. Its default entity loot key therefore uses the empty-table fallback unless server data supplies a table; no unique feather or nectar reward is established. An eligible adult death can still use the inherited animal **1–3 XP** reward under ordinary player-credit and mob-loot conditions. [Default key][loot-key] · [Fallback][fallback] · [Animal XP][animal] · [Death conditions][death]

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked entity/attribute/egg registration, bundled biome/tag/loot absence, active goals and tick, feeder registrations and POI stub, variants, offspring, shared feeding, and persistence. No gameplay test of spawning, flight, enclosure, feeding, breeding, pollination, or drops was run. Data packs and code changes can alter the documented integration limits.

Related: [Hummingbird Spawn Egg](../items/HummingbirdSpawnEgg.md) · [Bee](Bee.md) · [Mobs](Mobs.md)

[bird]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityHummingbird.java
[entity]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L751-L757
[attributes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L183
[placements]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[egg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1894
[renderer]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/client/render/RenderHummingbird.java
[tags]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L37-L39
[animal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/Animal.java
[egg-class]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L184
[pollination]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/ai/HummingbirdAIPollinate.java
[feeder]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/block/BlockHummingbirdFeeder.java
[poi]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/misc/AMPointOfInterestRegistry.java
[blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java
[loot-key]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[fallback]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L121
[death]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1527

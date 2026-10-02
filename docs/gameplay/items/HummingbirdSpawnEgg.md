# Hummingbird Spawn Egg

**Hummingbird Spawn Egg** (`minecraft:hummingbird_spawn_egg`) creates a [Hummingbird](../mobs/Hummingbird.md) through the registered spawn-egg system. It is a Creative setup route, not evidence that the bird naturally populates the current biomes. [Registration](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1894)

## Spawning a bird

Use the egg on a suitable block face with space for the bird. Normal individual initialization randomly selects one of three visual variants. The shared egg placement path still checks its target and entity creation; this is not a guaranteed safe enclosure layout. [Placement](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L50-L106) · [Variant selection](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityHummingbird.java#L205-L209)

## Using on a Hummingbird

Use the matching egg directly on an existing Hummingbird to invoke its offspring factory and create a baby. That factory creates a new Hummingbird rather than copying the parent's variant. This route is distinct from ordinary food breeding, whose bundled food tag is missing. [Baby helper](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L184) · [Offspring factory](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityHummingbird.java#L310-L315)

Read the [mob guide](../mobs/Hummingbird.md) before planning feeding, crop pollination, or a feeder: the corresponding source classes do not establish complete usable integrations in this snapshot.

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Registration, egg dispatch, variant initialization, and the offspring factory were checked. No egg, baby, flight, or enclosure gameplay test was run.

Related: [Hummingbird](../mobs/Hummingbird.md) · [Items](Items.md)

# Compass

An ordinary **Compass** (`minecraft:compass`) points toward the server's **global default spawn destination** when that destination is in your current dimension. The normal destination is the world's spawn, rather than your personal bed location. Binding the item to a Lodestone changes it to follow that saved location instead. [Client target selection][angle] · [Server default-spawn data][server] · [Binding][compass]

## Crafting

Place **one Redstone Dust in the center** of a 3 × 3 grid and **four Iron Ingots above, below, left, and right** to craft one Compass. [Recipe][recipe]

A Compass is also the center ingredient of the [Empty Map recipe](EmptyMap.md#crafting-and-first-use). Decide whether you need navigation or a fresh survey before spending it in crafting.

## Reading the needle

The client receives the global spawn destination from the server. The item's model selects that target unless the stack has a Lodestone Tracker component. A valid target must be in the same dimension as the item owner; otherwise the needle uses a spinning animation. Standing essentially at the target also fails the direction calculation's normal validity check. [Item model][model] · [Target validity and spinning][angle] · [Spawn packet][packet]

This is a direction aid, not a pathfinder: it does not route around walls, identify a safe arrival height, or transport the player. In a normal world, an unbound Compass taken into another dimension cannot point through a portal toward the Overworld target. If a server changes the global spawn destination or its dimension, the supplied target can change too. [Global spawn update][server]

## Lodestone binding

Use a Compass on a placed **Lodestone** to save that block's position and dimension. With a single held Compass in Survival, the existing item is bound in place. From a larger stack, the interaction consumes one and places the bound result in inventory or drops it when full. The bound form uses a distinct display name and glint while keeping the same Compass item type. [Binding, naming, and glint][compass]

The bundled Lodestone recipe uses **one Iron Ingot surrounded by eight Chiseled Stone Bricks**. It does not require a Netherite Ingot in this snapshot. [Lodestone recipe][lodestone]

A bound Compass can guide you in any dimension **matching its saved target dimension**. Taking it to a different dimension produces the no-valid-target behavior. Its inventory tick checks the target's Lodestone point of interest while in that target dimension; if the tracked block is gone or outside bounds, the target is cleared. Replace the Lodestone and bind the Compass again rather than assuming it will keep a reliable old direction. [Tracker checks][tracker] · [Inventory tick][compass] · [Lodestone point-of-interest registration][poi]

## Sources and verification

Source-reviewed on 2026-10-02 at `3e85592c4c78ebb420302360667a6c230dc0318d`. No in-game needle, dimension, binding, target-removal, or crafting test was run. Server spawn settings and custom tracker components can alter particular compasses.

Related: [Empty Map](EmptyMap.md) · [Map](Map.md) · [Lodestone](Lodestone.md) · [Items](Items.md)

[angle]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/client/renderer/item/properties/numeric/CompassAngleState.java
[server]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/MinecraftServer.java#L1870-L1889
[compass]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CompassItem.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/compass.json
[model]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/assets/minecraft/items/compass.json
[packet]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/client/multiplayer/ClientPacketListener.java#L1159
[lodestone]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/lodestone.json
[tracker]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/component/LodestoneTracker.java
[poi]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L140

# Turtle Scute

A **Turtle Scute** is the growth reward from a baby [Turtle](../mobs/Turtle.md), used to craft and repair the wearable Turtle Shell helmet. Its ID is `minecraft:turtle_scute`. It is separate from Armadillo Scute and the Turtle Shell item. [Registration][shell-items] · [Growth reward][turtle-scute]

## Obtaining

A baby Turtle normally drops **one Scute when it becomes an adult**, provided **mob loot is enabled**. The active age transition calls the Turtle growth gift table; it is not a periodic adult drop. Feeding Seagrass can speed baby growth. Follow [raising baby Turtles](../mobs/Turtle.md#raising-babies-for-scutes) for the practical route. [Age transition][age] · [Growth callback][turtle-scute] · [Gift-table mapping][scute-key] · [One-item table][scute-loot] · [Feeding][animal]

Killing an adult Turtle does not provide Scutes: its death table has Seagrass and a conditional lightning-death Bowl. Terrapin is not a substitute Scute-producing animal. [Turtle death table][turtle-loot] · [Terrapin implementation][terrapin]

Scutes are also ordinary category-listed items in the [inventory browser](../mechanics/InventoryBrowser.md), available through insertion in Creative. This is separate from raising a baby. [Listing][scute-list] · [Client][browser-client] · [Server][browser-server]

## Usage

Use **five Scutes** for the [Turtle Shell recipe](TurtleShell.md#obtaining). Scutes are also the accepted material for repairing that helmet at an [Anvil](../mechanics/AnvilMechanics.md#repairing-durability); follow the anvil guide for costs and preservation rules. [Helmet recipe][helmet-recipe] · [Repair material][repair-tag] · [Equipment repair component][armor-factory] · [Active repair check][repair-check]

## Behavior

This is a material item, not wearable armor. The **Turtle Shell** item grants the helmet's armor and temporary Water Breathing benefit when equipped; keeping a loose Scute in inventory does not provide those effects. [Separate registrations][shell-items] · [Equipped-item check][helmet-effect]

## Notes

Mob loot controls the normal growth gift even though the Turtle survives the event. [Growth gate][turtle-scute]

Related: [Turtle](../mobs/Turtle.md) · [Turtle Shell](TurtleShell.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `384aa3dfa1473af7753759569de95012d5bdc46f`. Checked the growth callback and loaded gift-table ID separately from death loot, ingredient/repair wiring and ordinary browser listing. No in-game test was run. Timing assumes 20 ticks per second; data packs can change the listed tags, recipes, biome entries and loot.

[shell-items]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/item/Items.java#L1281-L1282
[turtle-scute]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/animal/Turtle.java#L259-L265
[age]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/AgeableMob.java#L97-L104
[scute-key]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/level/storage/loot/BuiltInLootTables.java#L104-L104
[scute-loot]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/resources/data/minecraft/loot_table/gameplay/turtle_grow.json
[animal]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L158
[turtle-loot]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/resources/data/minecraft/loot_table/entities/turtle.json
[terrapin]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/alexsmobs/entity/EntityTerrapin.java
[scute-list]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1833-L1833
[browser-client]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1928
[helmet-recipe]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/resources/data/minecraft/recipe/crafting/turtle_helmet.json
[repair-tag]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/resources/data/minecraft/tags/item/repairs_turtle_helmet.json
[armor-factory]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/item/Item.java#L459-L466
[repair-check]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/item/ItemStack.java#L1106-L1108
[helmet-effect]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/player/Player.java#L339-L353

# Turtle Shell

The **Turtle Shell** is a helmet that grants **2 armor points** and prepares a short Water Breathing effect before a dive. Equip it in the head slot; carrying it in inventory is not enough. Its ID is `minecraft:turtle_helmet`. [Item and armor properties][shell-items] · [Material][helmet-material] · [Slot attributes][armor-attributes] · [Equipped check][helmet-effect]

## Obtaining

Craft **five [Turtle Scutes](TurtleScute.md)** at a Crafting Table: fill all three slots of the top row, then the left and right slots of the next row. The recipe returns **one Turtle Shell**. [Exact recipe][helmet-recipe]

It is also an ordinary category-listed item in the [inventory browser](../mechanics/InventoryBrowser.md), available through insertion in Creative. This is a separate route from raising Turtles for Scutes and crafting. [Listing][helmet-list] · [Client][browser-client] · [Server][browser-server]

## Usage

### Diving

While the helmet is correctly equipped and your **eyes are outside water**, the player's active tick refreshes **Water Breathing I for 200 ticks**, about **10 seconds**. Submerging your eyes stops that refresh and lets the effect count down. Return your head to air before another dive to replenish it. This is a short diving buffer, not an unlimited underwater-air supply. [Refresh condition][helmet-tick] · [Effect and equipment check][helmet-effect] · [Underwater air handling][air-tick]

### Brewing

Add a Turtle Shell to **Awkward Potions** to brew **Turtle Master**. The base potion provides **Slowness IV and Resistance III for 400 ticks**, about 20 seconds; it is not the helmet's Water Breathing effect. Use the [Brewing guide](../brewing/Brewing.md) for the stand, fuel and potion workflow. [Active mixture][helmet-brew] · [Potion effects][turtle-master]

## Behavior

An ordinary new helmet has **275 durability**, **2 armor points**, no added toughness and no added knockback resistance. Those armor attributes apply in the matching equipment slot while functional; they are not extra hearts or a fixed damage-reduction percentage. [Material values][helmet-material] · [Helmet durability multiplier][armor-slots] · [Property assembly][armor-factory] · [Attributes][armor-attributes] · [Armor mechanics](../mechanics/Armor.md)

Repair it with **Turtle Scutes** at an [Anvil](../mechanics/AnvilMechanics.md#repairing-durability). Each accepted Scute repairs up to **68 durability** under the quarter-maximum, rounded-down rule; taking the result consumes the materials used and pays the normal anvil cost. [Repair tag][repair-tag] · [Repair eligibility][repair-check] · [Material repair loop][anvil]

### Fully damaged helmets

MattMC retains a fully damaged shell as a broken stack, and ordinary armor attributes stop applying. **The separate Water Breathing refresh does not check the broken flag:** a retained shell still in its correct equipment slot can continue refreshing the effect while your eyes are out of water. Repair it to restore ordinary armor function. This distinction is source-reviewed, not an in-game wear-out test. The effect-policy follow-up is tracked in [#800](https://github.com/HungLo2020/MattMC/issues/800); no fix or universal passive-effect policy is implied. [Retained broken stack][broken-item] · [Armor guard][broken-armor] · [Break callback][break-callback] · [Refresh condition][helmet-tick] · [Equipment and effect check][helmet-effect] · [Durability guide](../mechanics/Durability.md)

## Notes

A loose Turtle Scute is the ingredient; the completed Turtle Shell is the wearable helmet and the Turtle Master brewing ingredient.

Related: [Turtle](../mobs/Turtle.md) · [Turtle Scute](TurtleScute.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `384aa3dfa1473af7753759569de95012d5bdc46f`. Checked the shaped recipe, armor material/slot/durability assembly, Player tick refresh condition, broken-stack guard distinction, repair tag and caller, potion mixture and browser listing. No in-game test was run. Timing assumes 20 ticks per second; data packs can change the listed tags, recipes, biome entries and loot.

[shell-items]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/item/Items.java#L1281-L1282
[helmet-material]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/item/equipment/ArmorMaterials.java#L27-L38
[armor-attributes]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/item/equipment/ArmorMaterial.java#L25-L40
[helmet-effect]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/player/Player.java#L339-L353
[helmet-recipe]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/resources/data/minecraft/recipe/crafting/turtle_helmet.json
[helmet-list]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1638-L1638
[browser-client]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1928
[helmet-tick]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/player/Player.java#L292-L294
[air-tick]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L424-L442
[helmet-brew]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L160-L160
[turtle-master]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L38-L45
[armor-slots]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/item/equipment/ArmorType.java#L7-L30
[armor-factory]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/item/Item.java#L459-L466
[repair-tag]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/resources/data/minecraft/tags/item/repairs_turtle_helmet.json
[repair-check]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/item/ItemStack.java#L1106-L1108
[anvil]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L133-L151
[broken-item]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/item/ItemStack.java#L441-L485
[broken-armor]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2668-L2682

[break-callback]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3540-L3550

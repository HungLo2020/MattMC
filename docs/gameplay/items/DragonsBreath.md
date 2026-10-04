# Dragon's Breath

**Collect Dragon's Breath during an [Ender Dragon](../mobs/EnderDragon.md) encounter, then use it to turn Splash Potions into Lingering Potions.** Bring empty [Glass Bottles](GlassBottle.md); this ingredient is collected from a live dragon-owned cloud. [Collection][bottle] · [Brewing conversion][mix]

## Obtaining

Use a Glass Bottle near a live breath cloud owned by an Ender Dragon. The bottle searches the player's bounding box expanded by **2 blocks in every direction**, chooses the first matching cloud, and reduces its radius by **0.5 block**. That search is a box intersection, not a promise that a cloud's center must be within two blocks. Each successful ordinary Survival collection consumes **one Glass Bottle** and produces **one Dragon's Breath**. [Cloud selection][bottle] · [Filled-bottle result][filled]

If that was your last empty bottle, the filled bottle replaces it in your hand. Otherwise it goes into your inventory, or drops beside you if it cannot fit. In Creative, the empty bottle is retained; the helper only attempts to add a Dragon's Breath when your inventory does not already contain a matching one. Creative collection still shrinks the cloud. [Filled-bottle handling][filled] · [Infinite-materials flag][creative]

Both the dragon's perched breath and its fireball impacts create dragon-owned clouds. Ordinary potion clouds do not qualify just because they look similar. Those dragon clouds carry Instant Damage, so approaching to collect them remains hazardous; use the [encounter guide](../mobs/EnderDragon.md#collecting-dragons-breath) to plan the collection. [Perched cloud][perched] · [Fireball cloud][fireball] · [Ownership filter][bottle]

## Usage

Put **Dragon's Breath in a fueled Brewing Stand's ingredient slot** and **1–3 Splash Potions in its bottle slots**. A completed batch converts each matching Splash Potion into a Lingering Potion and consumes **one Dragon's Breath for the entire batch**, so filling all three slots uses the ingredient more efficiently. Use ordinary brewed potions with potion contents; this conversion preserves their registered potion type. Follow [Brewing](../brewing/Brewing.md) for preparing the splash stage and using the result. [Container recipe and conversion][mix] · [Batch processing][brew-complete]

In this source snapshot, a completed batch drops **one Glass Bottle at the stand only when some Dragon's Breath remains in the ingredient stack afterward**. Using the last one returns **no Glass Bottle** and leaves the ingredient slot empty. This stand consumption has no Creative-player exemption. [Registered remainder][registration] · [Consumption before remainder lookup][brew-complete] · [Empty-stack item lookup][empty-stack] · [Crafting remainder lookup][craft-remainder]

## Behavior

Dragon's Breath stacks to **64** and has **Uncommon** rarity in the bundled registration. It is an ingredient, with no ordinary drinking or eating action; use the Brewing Stand rather than trying to consume the bottle in your hand. [Registration][registration] · [Default stack limit][stack] · [Ordinary item use][plain-use]

## Notes

* Registered item: `minecraft:dragon_breath`
* Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. The active server item-use dispatcher, cloud-producing paths, brewing registry and Brewing Stand ticker were checked. No in-game collection, brewing, inventory-overflow or Creative test was run. [Server admission][use-admission] · [Item dispatch][item-use] · [Dragon phase dispatch][dragon-tick] · [Brewing bootstrap][bootstrap] · [Stand ticker][ticker]
* These counts describe the bundled item and brewing behavior; modified item components or later builds can differ

Related: [Ender Dragon](../mobs/EnderDragon.md) · [Glass Bottle](GlassBottle.md) · [Brewing](../brewing/Brewing.md) · [Items](Items.md)

[bottle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BottleItem.java#L29-L46
[mix]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L102-L138
[filled]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemUtils.java#L16-L43
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1212-L1215
[perched]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/boss/enderdragon/phases/DragonSittingFlamingPhase.java#L55-L92
[fireball]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/DragonFireball.java#L27-L63
[brew-complete]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L170-L193
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2241-L2241
[empty-stack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L322-L324
[craft-remainder]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L262-L264
[stack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L390
[plain-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L164-L191
[use-admission]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1303-L1325
[item-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L300-L338
[dragon-tick]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/boss/enderdragon/EnderDragon.java#L188-L199
[bootstrap]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/MinecraftServer.java#L338-L338
[ticker]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/BrewingStandBlock.java#L54-L60

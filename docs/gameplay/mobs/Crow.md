# Crow

Crows can become flying companions by eating Pumpkin Seeds that a player drops. A tamed crow can follow, sit, carry a single item, or gather loose items into marked containers. Wild adults can also damage crops, so keep them away from a farm you want to preserve.

## At a glance

- **Entity ID:** `minecraft:crow`
- **Health:** 8 points (4 hearts)
- **Base attack attribute:** 1 point (half a heart)
- **Registered adult size:** 0.45 blocks wide × 0.45 blocks tall

## Obtaining

Use a [Crow Spawn Egg](../items/CrowSpawnEgg.md), explicitly available in Creative, or `/summon minecraft:crow` with command permission.

**Natural spawning is not established.** The reviewed active biome spawn data, biome-building code, and spawn-placement registrations contain no Crow entry. Its standalone brightness predicate and spawn-roll check are not a verified natural habitat.

## Taming and food

**Pumpkin Seeds** are the only bundled crow taming food. One Pumpkin crafts into **4 Pumpkin Seeds**.

1. Drop the seeds yourself near a wild crow
2. Give it room to approach and collect one. Its pickup goal considers dropped items more than 40 game ticks old
3. Wait for it to eat the carried seed after more than 60 ticks of holding it, about three seconds at 20 ticks per second
4. Each qualifying meal has a **30% taming chance**. Stay nearby and online so the remembered player can be found

Successful taming assigns the seed thrower as owner and switches the crow to **follow**. Directly using seeds on a wild crow does not run this taming path. Nearby Carved Pumpkins and Jack o'Lanterns can scare wild crows away, so choose a clear taming spot.

Crows eat items with a food component, plus **Eggs, Sugar, Wheat Seeds, Beetroot Seeds, Melon Seeds, and Pumpkin Seeds**. Eating heals up to **4 health points**. A wild crow eats carried food even at full health; a tamed one waits until injured. Thus a healthy tame crow can carry food, but an injured one may eat the item you wanted delivered.

## Commands and shoulder riding

With the crow's beak empty, its owner can interact empty-handed to cycle:

**Wander (0) → Follow (1) → Sit (2) → Gather (3) → Wander**

If it is already carrying something, interaction first makes it drop that item instead. That item-return branch is not restricted to the owner. Giving an eligible edible item directly to an empty-beaked owned crow also advances its command, so use an empty hand when you only want to change orders.

In follow mode, it flies around its owner and can settle onto a shoulder; the goal allows up to **two riding crows**. It can try to teleport closer when far behind. Sneaking once its boarding cooldown has expired, beginning elytra flight, or acquiring a combat target makes it dismount. Taking damage also makes it dismount and can knock the carried item out of its beak.

## Gathering into containers

For a simple source-supported setup:

1. Put an Item Frame on a [Chest](../blocks/Chest.md) or another block entity implementing a container
2. Display the item type you want deposited in that frame
3. Leave suitable free space in the container
4. Set an owned crow to **Gather** and leave loose items within its working area

A tamed crow can pick up any loose item type, **one item at a time**, while its beak is empty and it is neither sitting, riding, nor fighting. Pickup is not exclusive to Gather mode; Gather additionally enables container delivery.

The delivery goal searches a box extending **16 blocks in each direction** from the crow for a matching frame, then deposits when within 2 blocks of it. It checks the container directly behind the frame. The frame matches the item type; adding to an existing stack also requires matching item components. A full container may leave it carrying the item.

A **Hay Bale** underneath a tamed crow records a saved perch and heals 1 point at roughly 50-tick intervals. Gather-mode idle flight uses a remembered perch as its center. The source's later perch-validity check is unreachable after the shared cooldown is reset, so **remove or relocate a saved perch cautiously**: automatic invalid-perch cleanup is not established.

This implementation writes directly into container slots and has not been tested here with every storage block. Try a simple Chest setup before trusting valuable items to a larger sorting system.

## Breeding

Feed **Pumpkin Seeds to two tamed adults** to enter love mode. Breeding produces a live crow chick. Its creation path does not copy an owner or tame state, so plan to tame the offspring separately. Babies cannot enter the crow's flying state.

There is an interaction edge case: the handler can call ordinary animal feeding twice. Feeding a tamed chick from a stack can therefore consume a second seed and apply growth twice. Adult breeding also runs before command/item-return logic; clear its beak and check its command afterward. These are source-identified concerns, not in-game test results. The repeated feeding and handled-result fallthrough are tracked in [issue #784](https://github.com/HungLo2020/MattMC/issues/784); the separate perch concern is outside that issue.

## Wild behavior and combat

Wild adults circle and peck **Wheat, Carrots, Potatoes, Beetroots, Pumpkin Stems, and Melon Stems**. With `mobGriefing` enabled and the bundled crop-stealing option enabled, a peck reduces an ordinary crop's growth stage by one, destroys a stage-zero crop, or breaks a targeted stem. Tamed crows do not run this crop-raiding goal. Carved Pumpkins and Jack o'Lanterns activate the wild-crow avoidance behavior.

Owned crows have owner-defense and owner-attack target goals. Their melee goal is disabled while sitting or gathering. Its peck requests **4 damage points against undead-tagged targets**, otherwise **1 point**, before damage handling. The current damage method also makes crows immune to fall, suffocation-in-block, and cactus damage; other hazards still matter.

## Drops and verification

No dedicated Crow death-loot table or unique Crow resource item was found. Carried items can be returned by interaction or dropped when a successful hit damages the bird; this does not establish a species-specific death reward.

Source-reviewed on **2026-10-01** at `b81c01943c9f3254e713c365a1dd633392929cb2`. No in-game spawning, taming, breeding, riding, gathering, combat, or persistence test was run.

## Related pages

- [Blue Jay](BlueJay.md)
- [Chest](../blocks/Chest.md)
- [Wheat farming](../blocks/Wheat.md)
- [Mobs](Mobs.md)

## Sources

- [Stats, goals, damage, interactions, feeding, perch, saves, and offspring](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityCrow.java#L94-L480)
- [Food selection and dropped-item ownership](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityCrow.java#L575-L601)
- [Pumpkin avoidance and item pickup](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityCrow.java#L836-L981)
- [Perch-centered idle flight](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityCrow.java#L674-L729)
- [Marked-container selection and deposit](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityCrow.java#L985-L1113)
- [Follow and shoulder-riding goal](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/ai/CrowAIFollowOwner.java#L48-L177)
- [Crop-raiding goal and gamerule checks](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/ai/CrowAICircleCrops.java)
- [Crop-stealing configuration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/config/AMConfig.java#L15-L16)
- [Melee damage and command restrictions](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/ai/CrowAIMelee.java#L23-L73)
- [Active damage wrapper](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Entity.java#L1772-L1784)
- [Shared dropped-item pickup](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/ai/CreatureAITargetItems.java#L53-L145)
- [Taming food](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/crow_tameables.json)
- [Breeding food](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/crow_breedables.json)
- [Additional edible items](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/crow_foodstuffs.json)
- [Perch block](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/crow_home_blocks.json)
- [Scare blocks](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/crow_fears.json)
- [Crop targets](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/crow_foodblocks.json)
- [Pumpkin Seeds recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/pumpkin_seeds.json)
- [Ordinary animal feeding and offspring finalization](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Animal.java#L134-L227)
- [Entity registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L455-L457)
- [Attribute wiring](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L149)
- [Spawn egg registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1850)
- [Creative listing](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2004)
- [Spawn placements](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java)
- [Biome spawn data](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome)
- [Biome-building code](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/biome)
- [Entity loot tables](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities)

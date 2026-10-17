# Slime and Honey Blocks

Use **Slime Block** for a bouncing landing surface and **Honey Block** for a slow surface or a wall to slide down. Both can carry eligible neighboring blocks in a piston mechanism, but they do not adhere to each other. Start with a small mechanism and a safe drop: collision, entity type, and the shared piston limits still matter. [Slime behavior][slime] · [Honey behavior][honey] · [Sticky pairing][pairing]

## Crafting and collecting

| Make | Ingredients and arrangement | Result |
| --- | --- | --- |
| Slime Block | Nine [Slimeballs](../items/Slimeball.md), filling a 3 × 3 crafting grid | One Slime Block |
| Unpack Slime Block | One Slime Block, shapeless | Nine Slimeballs |
| Honey Block | Four [Honey Bottles](../items/HoneyBottle.md), in a 2 × 2 square | One Honey Block, with four empty Glass Bottle remainders |
| Re-bottle honey | One Honey Block and four [Glass Bottles](../items/GlassBottle.md), shapeless | Four Honey Bottles |

Honey Block crafting fits the inventory grid. Re-bottling needs **five occupied ingredient slots**, so use a [Crafting Table](CraftingTable.md); four bottles stacked in one slot do not satisfy four separate ingredients. Slime Block packing also needs the table. [Slime packing][slime-recipe] · [Slime unpacking][slime-unpack] · [Honey packing][honey-recipe] · [Honey bottling][honey-unpack] · [Shapeless matching][shapeless]

The empty bottles are real crafting remainders: manual crafting leaves them in emptied input slots, otherwise tries the inventory and then drops them if it is full. A Crafter sends the result and remainders through its output handling. Keep bottle collection in mind when automating Honey Blocks. For the ingredient supply, use [Slimeball acquisition](../items/Slimeball.md#obtaining) and [Bee housing harvesting](BeeHousing.md#harvesting). [Bottle registration][bottle-item] · [Remainder calculation][remainders] · [Manual handling][result-slot] · [Crafter output][crafter]

Both blocks have **zero registered hardness and blast resistance**, and ordinary Survival breaking can return **one matching block item by hand**. Neither requires a correct tool, Silk Touch, or a special harvest tool. Their loot tables contain no Fortune multiplier and do include an explosion-survival condition. Stop a moving mechanism before recovering its blocks. [Registrations][slime-registration] · [Honey registration][honey-registration] · [Property defaults][defaults] · [Harvest eligibility][tool-gate] · [Mining dispatch][mining] · [Slime loot][slime-loot] · [Honey loot][honey-loot]

## Slime Block

**Slime Block** is `minecraft:slime_block`, with an item of the same ID. Its ordinary collision shape is a full cube. It has a registered friction value of **0.8**, and its ground-contact handler further reduces horizontal motion when absolute vertical speed is below 0.1 and the entity is not stepping carefully. These factors are not a measured walking speed. [Registration][slime-registration] · [Item registration][block-items] · [Inherited full shape][shape] · [Slime contact][slime] · [Ground friction caller][friction]

On a downward landing through the normal movement callback, Slime reverses vertical velocity: the factor is **1.0 for living entities** and **0.8 for other entities reaching that callback**. The fall callback supplies a zero damage multiplier when bounce is enabled. Bounce height depends on the incoming motion and later movement; it is not a fixed-height jump. [Slime landing][slime] · [Movement dispatch][movement] · [Fall calculation][fall-calculation]

**Hold Sneak/Crouch to suppress the bounce.** Careful stepping also skips Slime's additional ground-contact slowdown. In this MattMC snapshot, the crouching Slime landing handler makes **no fall-damage call**, and the ordinary on-foot fall-check chain resets the recorded fall distance afterward. Thus this source path does not restore ordinary landing damage when you crouch. This has not been tested in game; it is not protection from landing on a different block, missing the pad, or another damage source. [Sneak flags][sneak] · [Slime callbacks][slime] · [Stopped vertical motion][base-landing] · [Fall dispatch/reset][fall-dispatch] · [Server-player fall check][server-fall] · [Living fall check][living-fall]

Slime Block also brews **Awkward Potion into Potion of Oozing**. The same ingredient added directly to Water makes Mundane Potion. See [Brewing](../brewing/Brewing.md#a-first-batch) for the stand, fuel, and base-potion workflow. [Registered mix][brewing] · [Starting-potion rule][start-mix]

## Honey Block

**Honey Block** is `minecraft:honey_block`, with an item of the same ID. Its collision is **14/16 of a block wide and deep, centered in the block, and 15/16 high**. The inset sides let an entity overlap the block's cell while sliding beside its solid surface. [Registration][honey-registration] · [Item registration][block-items] · [Collision definition][honey-shape] · [Centered column helper][column]

Honey applies a **0.4 horizontal speed factor** and **0.5 block jump factor**. The jump factor affects the base jump impulse before the separate Jump Boost addition; it does not mean half the jump height. Living entities can blend the movement slowdown toward 1 through movement efficiency. Sneaking does not switch off these Honey factors. [Honey properties][honey-registration] · [Factor selection][factors] · [Horizontal application][movement] · [Movement efficiency][efficiency] · [Jump calculation][jump]

Landing on top requests fall damage with a **0.2 multiplier**, so sufficiently large falls can still hurt. Honey does not supply Slime's upward bounce. Damage rounding, attributes, and other protections affect the final health loss. [Honey landing][honey] · [Default post-landing motion][base-landing] · [Damage calculation][fall-calculation]

To slide, contact the side while airborne and descending, with your feet below the top of the collision surface. The callback checks side position and downward motion, slows the descent, reduces horizontal motion for faster falls, and **resets fall distance while it is applying the slide**. Standing on top or merely being near the wall does not qualify. Leaving the wall starts a new fall, so provide a safe landing below. [Slide checks and movement][honey-slide] · [Active block-contact dispatch][contact]

The slide's random sound/particle effects are limited to living entities, minecarts, primed TNT, and boats; that list does **not** restrict the slide-motion handler itself. Whether another entity slides still depends on its own movement and block-contact path. [Effect filter and callback order][honey] · [Contact eligibility][contact-gate]

## Pistons and neighboring blocks

Both blocks attach eligible face-adjacent blocks to a moving group. Same-material sticky blocks can attach; **Slime and Honey do not attach to each other**. That separation does not prevent one from physically pushing the other when it occupies the movement path. Use it to separate adjacent machine sections, while keeping their forward paths clear. [Pairing][pairing] · [Forward obstruction handling][resolver-line] · [Side branches][resolver-branches]

- The entire moved group shares the **12-block limit**, including the Slime/Honey blocks themselves and all attached branches
- Movable walls, floors, or supports touching the sticky faces can become unwanted load; diagonal proximity alone is not an attachment
- **Glazed Terracotta does not follow a side or trailing sticky attachment**, but can still be pushed directly along the movement path; its facing does not change this
- An immovable block beside the group is not pulled into it; an obstruction directly in the required travel path can stop the mechanism

These are movement-group rules, not an exemption from world boundaries, block-entity restrictions, or other [Piston pushability rules](Pistons.md#what-can-move). An ordinary Piston can push a sticky group; a Sticky Piston is needed for its ordinary pull-back cycle. The existing [Piston guide](Pistons.md#facing-and-power) owns power and short-pulse behavior. [Resolver][resolver-line] · [Branch directions][resolver-branches] · [Pushability and retraction][piston] · [Glazed Terracotta details](Terracotta.md#pistons-and-sticky-blocks)

## Moving entities and projectile limits

A moving Slime Block has an additional entity-launch path that sets velocity along the piston movement axis. It skips entities with an `IGNORE` piston reaction and explicitly skips the server-side player object in that Slime handling. This is not enough evidence to promise a tested player launcher or a launch distance. [Moving collision handling][piston-entities]

A **horizontally moving Honey Block** additionally carries qualifying entities standing on top: they must be on the ground, have a normal piston reaction, and meet its support/position check. This is separate from wall sliding and from the ordinary collision push; it is not universal adhesion to every entity on every face. [Honey carry conditions][honey-carry] · [Active moving-block tick][piston-tick] · [Ticker registration][piston-ticker]

Do not assume every projectile bounces off a stationary Slime Block. Arrows and throwable projectiles use their own traced-hit/set-position paths rather than the normal landing-bounce sequence, although they do invoke block-contact effects. Their eventual impact behavior and Honey contact therefore need to be assessed separately. This review does not establish a universal projectile launcher or catcher. [Arrow movement][arrows] · [Throwable movement][throwables] · [Ordinary landing sequence][movement]

Related: [Slime Block item](../items/SlimeBlock.md) · [Honey Block item](../items/HoneyBlock.md) · [Pistons](Pistons.md) · [Bee housing](BeeHousing.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `beb4335362d5983b867ef84d66a74ce668b6ef7d`. Checked registrations, recipes, manual/Crafter remainders, ordinary mining/loot, active fall/contact and piston callers, and representative projectile paths. No in-game crafting, mining, damage, bounce, sliding, speed, projectile, or machine test was run. Collision-box extraction can use a native helper for sufficiently large shapes; this review did not test native execution or client/server motion synchronization. Data packs can replace recipes and loot. [Recipe loading][recipe-load] · [Collision extraction boundary][voxel-boxes]

[slime]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/SlimeBlock.java#L25-L58
[honey]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/HoneyBlock.java#L24-L125
[pairing]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/piston/PistonStructureResolver.java#L62-L72
[slime-recipe]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipe/crafting/slime_block.json#L1-L16
[slime-unpack]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipe/crafting/slime_ball.json#L1-L11
[honey-recipe]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipe/crafting/honey_block.json#L1-L15
[honey-unpack]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipe/crafting/honey_bottle.json#L1-L15
[shapeless]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L65
[bottle-item]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/Items.java#L2477-L2480
[remainders]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/crafting/CraftingRecipe.java#L18-L31
[result-slot]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L72-L112
[crafter]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/CrafterBlock.java#L146-L175
[slime-registration]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/Blocks.java#L3118-L3120
[honey-registration]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/Blocks.java#L5758-L5762
[defaults]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L992-L1018
[tool-gate]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[mining]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[slime-loot]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/loot_table/blocks/slime_block.json#L1-L21
[honey-loot]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/loot_table/blocks/honey_block.json#L1-L21
[block-items]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/Items.java#L1003-L1006
[shape]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[friction]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2288-L2310
[movement]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/entity/Entity.java#L737-L772
[fall-calculation]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1710-L1735
[sneak]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/entity/Entity.java#L2541-L2555
[base-landing]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/Block.java#L456-L462
[fall-dispatch]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/entity/Entity.java#L1389-L1415
[server-fall]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1260-L1270
[living-fall]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L350-L381
[brewing]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L127-L145
[start-mix]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L230-L235
[honey-shape]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/HoneyBlock.java#L24-L48
[column]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/Block.java#L158-L183
[factors]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/entity/Entity.java#L973-L986
[efficiency]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L495-L498
[jump]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2204-L2229
[honey-slide]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/HoneyBlock.java#L62-L114
[contact]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/entity/Entity.java#L1175-L1205
[contact-gate]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/entity/Entity.java#L847-L877
[resolver-line]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/piston/PistonStructureResolver.java#L73-L153
[resolver-branches]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/piston/PistonStructureResolver.java#L169-L182
[piston]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L193-L270
[piston-entities]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/piston/PistonMovingBlockEntity.java#L115-L181
[honey-carry]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/piston/PistonMovingBlockEntity.java#L187-L219
[piston-tick]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/piston/PistonMovingBlockEntity.java#L312-L352
[piston-ticker]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/piston/MovingPistonBlock.java#L62-L66
[arrows]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L233-L270
[throwables]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/entity/projectile/ThrowableProjectile.java#L45-L64
[recipe-load]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L89
[voxel-boxes]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/phys/shapes/VoxelShape.java#L129-L136

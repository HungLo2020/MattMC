# Fire Charge

A **Fire Charge** (`minecraft:fire_charge`) is a consumable ignition item, Small Fireball ammunition for a Dispenser, and a crafting ingredient. Matching charges stack to **64**. For repeated manual lighting, [Flint and Steel](FlintAndSteel.md) uses durability instead. [Registration][charge-registration] · [Default stack limit][default-stack] · [Ignition and projectile behavior][charge-use] · [Projectile creation][charge-projectile]

## Obtaining

### Crafting

Combine **one [Gunpowder](Gunpowder.md), one [Blaze Powder](BlazePowder.md), and one Coal or Charcoal** in three separate slots to make **three Fire Charges**. The recipe is shapeless and fits the inventory's 2 × 2 grid. It accepts those two named fuel items, not any arbitrary Furnace fuel. Taking the result consumes one of each ingredient. [Recipe][charge-recipe] · [Recipe loading][recipe-loader] · [Input consumption][crafting-consumption]

### Loot, bartering and Creative access

- **Ruined Portal chests** can select one Fire Charge as a loot entry. It is a possible weighted result, not a guaranteed chest supply. Actual portal templates bind that chest table, and those templates are selected by the ruined-portal generator. [Loot entry][portal-loot] · [Chest template][portal-template] · [Template selection][portal-generation] · [Structure set][portal-set]
- An eligible adult **[Piglin barter](../mobs/Piglin.md#bartering)** can return **one Fire Charge**. The barter result is thrown into the world; offering Gold Ingots does not let you choose it. The Piglin guide owns the full result weights, eligibility and interruption rules. [Charge entry][barter-loot] · [Adult completion][barter-dispatch] · [Loot evaluation and delivery][barter-result]
- **Trial Chamber dispensers** using the chamber loot table can contain **4–8 Fire Charges when that entry is selected**. This is machine inventory to recover, and an active trap can spend it. The dispenser pool includes empty and other template choices, so not every chamber supplies these charges. [Loot entry][chamber-loot] · [Dispenser template][chamber-template] · [Template choices][chamber-pool] · [Machine consumption][projectile-dispense]
- The Creative catalog includes Fire Charges. Use the [inventory browser's Creative request route](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item); a visible Survival entry is not server-accepted item supply. [Catalog entry][creative]

The Fire Charges selected for overhead **[ominous-trial hazards](../blocks/TrialSpawner.md#becoming-ominous)** are released as projectiles, not loose Fire Charge pickups. Do not count those attacks as the same acquisition route as a dispenser's recoverable inventory. [Projectile-versus-item release][ominous-release]

## Usage

### Manual ignition

Use a charge on a dry, unlit **[Campfire](../blocks/Campfires.md#lighting-and-extinguishing)** or **[Candle group](../blocks/Candles.md#lighting-and-extinguishing)**, or on an unlit candle on Cake, to light that block. Otherwise the item tries to place fire in the air just outside the clicked face, provided fire can survive there or activate a valid portal. A successful ordinary Survival action consumes **one charge**; failed placement consumes none. Waterlogged lighting blocks reject relighting but may still lead to the adjacent-fire attempt. [Item branches][charge-use] · [Campfire eligibility][campfire-light] · [Candle eligibility][candle-light] · [Candle-cake eligibility][cake-eligible] · [Fire-placement check][fire-placement]

The supporting floor selects **Soul Fire** on Soul Sand or Soul Soil and ordinary **Fire** elsewhere. Use [Fire and Soul Fire](../blocks/Fire.md#support-persistence-and-spread) for spread, support and extinguishing, and [Nether portal activation](../blocks/NetherPortals.md#building-and-activating) for the frame and Overworld/Nether restriction. Direct ignition still works when `doFireTick` is disabled. [Fire selection][fire-selection] · [Soul Fire bases][soul-tag] · [Portal check][fire-lifecycle] · [Direct ignition][charge-use] · [Fire-tick gate][fire-tick]

A charge also directly primes **[TNT](../blocks/TNT.md#priming-routes)**, subject to `tntExplodes`, or ignites a **[Creeper](../mobs/Creeper.md#charged-and-manually-ignited-creepers)**. Successful ordinary Survival use consumes one charge. On TNT, use the normal interaction: secondary use can skip its direct handler and try adjacent fire instead. A disabled TNT rule does not disable that separate fire-placement path. Manual Creeper ignition keeps the fuse advancing after the target escapes. [TNT use][tnt] · [Block-first ordering][server-block-use] · [Active mob dispatch][mob-dispatch] · [Creeper handler][creeper-use] · [Igniter tag][creeper-tag] · [Fuse behavior][creeper-fuse]

**Using a Fire Charge in open air does not throw it.** Its projectile factory is used by automation; ordinary air use inherits the base item's no-action result for this default stack. [Item behavior][charge-projectile] · [Inherited air use][item-use]

### Fireworks and an attachment recipe

A Fire Charge is the optional **Large Ball** shape ingredient when creating a **[Firework Star](FireworkStar.md)**. Follow [the canonical star recipe](../mechanics/Fireworks.md#make-a-star-and-choose-its-effects) for dyes, shape restrictions and additional effects. Large Ball is a saved effect design, not a damage bonus; full visual appearance has not been verified on the current renderer. [Active special recipe][firework-data] · [Shape matching and assembly][firework-shape]

At the **[TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table)**, **2 Crying Obsidian + 1 Fire Charge + 4 Blaze Rods** make **one [Incendiary Ammo](IncendiaryAmmo.md) attachment**. Look under **Extended Mag**, the group assigned by its bundled index. This is a workbench transaction using carried ingredients; see [workbench controls](../blocks/TaCZWorkbenches.md#using-a-workbench) and their Creative-material requirement. The registered attachment list, packaged recipe loader and allowed menu group connect this recipe to the current table. [Ingredients and output][ammo-recipe] · [Group][ammo-index] · [Attachment definition][ammo-definition] · [Item registration][ammo-registration] · [Active loading][workbench-loader] · [Menu group][workbench-groups] · [Craft check][workbench-menu] · [Consumption][workbench-craft]

## Behavior

### Dispenser projectiles

A **[Dispenser](../blocks/DispenserAndDropper.md#facing-loading-and-activation)** consumes one charge to launch a **Small Fireball** in its facing direction, with randomized spread. It does not carry out the same immediate block-lighting action as dispensed Flint and Steel. Load only charges while checking your setup so another occupied slot is not selected. [Registered action][projectile-registration] · [Bootstrap][bootstrap] · [Launch and consumption][projectile-dispense] · [Fireball creation][charge-projectile] · [Slot selection][dispense-slots]

The Small Fireball ignites itself before impact processing. A hit first reaches the struck block's projectile callback, so eligible Campfires, Candles or TNT can react; their own conditions still apply. Its separate block-hit action attempts to put fire in the empty cell outside the hit face. Unsupported fire may immediately disappear, and a valid portal can form through the shared fire placement lifecycle. [Burning before impact][fireball-burn] · [Block-hit dispatch][projectile-hit] · [Adjacent fire][small-fireball] · [Fire lifecycle][fire-lifecycle]

The normal Dispenser-created fireball has **no owner**. `mobGriefing` therefore does not block its adjacent-fire placement: that particular check applies only to a Mob owner. Disabling `doFireTick` controls later spreading, not this initial placement. TNT's projectile route still has its own [priming and permission rules](../blocks/TNT.md#rules-and-permissions). [Ownerless construction][projectile-owner] · [Fireball owner check][small-fireball] · [Fire ticking][fire-tick] · [Projectile permission][projectile-permission]

An entity hit requests **5 health points of damage** and **5 seconds of burning**, subject to the target's damage handling and burning-time modifiers. If the damage request fails, the old fire timer is restored. The Small Fireball is discarded after a normally resolved impact; its own hit handler does not create a Ghast-style explosive blast, although it can trigger other hazards such as TNT. These are source values, not measured health loss or a safe-distance guarantee. [Impact handling][small-fireball] · [Living-entity burning modifier][burn-time]

## Notes

Creative players retain their charges during the checked manual block and Creeper interactions: the shared caller restores the stack count or uses Creative-aware consumption. A Dispenser still spends its own inventory charge. Block interaction also checks reach, protected areas and game mode; Adventure's ordinary item-use fallback needs a matching **Can Place On** permission. A charge in inventory does not bypass those checks. [Server use and count restoration][server-block-use] · [Client count restoration][client-block-use] · [Entity-use restoration][player-interact] · [TNT consumption][tnt] · [Dispenser consumption][projectile-dispense] · [Server admission][packet-use] · [Adventure guard][stack-use]

### Sources and verification

Source-reviewed on **2026-10-04** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Checked registration, ordinary and packaged workbench recipe loading, loot/template bindings, barter completion, current player/block/entity dispatch, igniter and fire-support tags, Creative count handling, Dispenser launch and projectile hits. No in-game crafting, loot, barter, ignition, projectile, damage, portal, sound or visual test was run. Data packs, components, permissions and game rules can change the result; source wiring does not establish complete native-renderer visuals.

Related: [Flint and Steel](FlintAndSteel.md) · [Fire and Soul Fire](../blocks/Fire.md) · [Firework Star](FireworkStar.md) · [Items](Items.md)

[charge-registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L2025
[default-stack]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L391
[charge-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/FireChargeItem.java#L29-L55
[charge-projectile]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/FireChargeItem.java#L63-L87
[charge-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/fire_charge.json
[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L90
[crafting-consumption]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L82-L106
[portal-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/chests/ruined_portal.json#L54-L64
[portal-template]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/structure/ruined_portal/portal_1.nbt
[portal-generation]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/levelgen/structure/structures/RuinedPortalStructure.java#L103-L110
[portal-set]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/worldgen/structure_set/ruined_portals.json
[barter-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/gameplay/piglin_bartering.json#L159-L163
[barter-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L373-L399
[barter-result]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L413-L445
[chamber-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/dispensers/trial_chambers/chamber.json#L67-L83
[chamber-template]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/structure/trial_chambers/dispensers/chamber.nbt
[chamber-pool]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/dispensers/chamber.json
[projectile-dispense]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/dispenser/ProjectileDispenseBehavior.java#L25-L41
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1453-L1459
[ominous-release]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/OminousItemSpawner.java#L71-L107
[campfire-light]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CampfireBlock.java#L332-L336
[candle-light]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CandleBlock.java#L157-L161
[cake-eligible]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CandleCakeBlock.java#L150-L152
[fire-placement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/BaseFireBlock.java#L190-L218
[fire-selection]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/BaseFireBlock.java#L44-L50
[soul-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/block/soul_fire_base_blocks.json
[fire-lifecycle]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/BaseFireBlock.java#L156-L178
[fire-tick]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/FireBlock.java#L137-L145
[tnt]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/TntBlock.java#L86-L120
[server-block-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L339-L395
[mob-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1073
[creeper-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/monster/Creeper.java#L209-L227
[creeper-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/creeper_igniters.json
[creeper-fuse]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/monster/Creeper.java#L125-L146
[item-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Item.java#L164-L191
[firework-data]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/firework_star.json
[firework-shape]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/FireworkStarRecipe.java#L15-L127
[ammo-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipes/attachments/ammo_mod_i.json
[ammo-index]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/index/attachments/ammo_mod_i.json
[ammo-definition]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L111-L115
[ammo-registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L2724-L2737
[workbench-loader]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L174
[workbench-groups]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L179
[workbench-menu]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L72-L84
[workbench-craft]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L66
[projectile-registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L64-L79
[bootstrap]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/Bootstrap.java#L42-L57
[dispense-slots]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/entity/DispenserBlockEntity.java#L34-L45
[fireball-burn]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/projectile/AbstractHurtingProjectile.java#L78-L91
[projectile-hit]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L288-L290
[small-fireball]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/projectile/SmallFireball.java#L32-L68
[projectile-owner]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/projectile/AbstractHurtingProjectile.java#L33-L43
[projectile-permission]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L336-L345
[burn-time]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3690-L3693
[client-block-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L328-L372
[player-interact]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/player/Player.java#L854-L870
[packet-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1255-L1278
[stack-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L369

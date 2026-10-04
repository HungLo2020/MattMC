# Spectral Arrow

A **Spectral Arrow** is ammunition that can mark a living target with **Glowing I for 200 ticks**, or **10 seconds at 20 ticks per second**, after an accepted hit. It still uses ordinary arrow damage handling; Glowing is the additional effect. See [Glowing sources](../effects/VisibilityEffects.md#glowing-sources) for the status and outline behavior. [Projectile type][item] · [Effect duration][effect] · [Accepted-hit handling][hit]

## Obtaining

At a Crafting Table, put **one ordinary Arrow in the center** and **four Glowstone Dust directly above, below, left and right**, leaving the corners empty. This makes **two Spectral Arrows**. [Recipe][recipe]

An eligible adult [Piglin](../mobs/Piglin.md#bartering) can return **6–12 Spectral Arrows** as one possible result of a completed Gold Ingot barter. It is a weighted random result, not an arrow purchase or guaranteed return. Use the Piglin guide for offer requirements and interruptions. [Barter reward entry][barter-table] · [Adult completion gate][barter-completion] · [Live reward-table lookup][barter-roll]

Spectral Arrows are in the **Combat** Creative catalog. Seeing them in Survival does not bypass the [Inventory Browser's insertion gate](../mechanics/InventoryBrowser.md#mode-and-permission-limits). [Catalog][catalog]

## Usage

Use them in a [Bow](Bow.md), [Crossbow](Crossbow.md), or Dispenser. The ammunition tag accepts Spectral Arrows, and the item creates the spectral projectile. A Dispenser consumes one arrow per shot. [Ammunition tag][ammo-tag] · [Bow acceptance][bow] · [Crossbow acceptance][crossbow] · [Projectile creation][item] · [Dispenser registration][dispenser-register] · [Dispenser firing][dispenser-fire]

**Infinity does not save Spectral Arrows.** Its ammunition-saving condition explicitly matches the ordinary Arrow item. A normal Survival shot therefore consumes spectral ammunition. When fired from a Bow, the arrow can benefit from Power, Punch and Flame; see [Bow enchantments](../enchanting/BowEnchantments.md#infinity-which-ammunition-is-saved) for those rules and ammunition selection. [Infinity condition][infinity] · [Active ammunition use][ammo-use]

## Behavior

### Damage and Glowing

The projectile starts with a **base damage factor of 2**. The active hit calculation uses its speed and the weapon's damage modifiers, rounds up, and can add a critical-hit bonus. This is not a fixed two-health-point hit: Bow draw, critical state, enchantments and target defenses affect the result. [Base factor][base-damage] · [Hit calculation][hit]

Glowing is requested only after the target accepts the arrow's damage call and the target is a living entity. The arrow handler returns early for an Enderman before that callback. The target's effect acceptance and existing-effect rules also apply, so a collision does not guarantee a fresh ten-second status on every entity. [Server damage dispatch][damage-dispatch] · [Hit and callback gates][hit] · [Spectral grant][effect] · [Effect application][effect-acceptance]

### Recovering fired arrows

A normally consumed player-fired Spectral Arrow, or one fired by a Dispenser, allows pickup. Touch it after it is embedded in a block and has stopped shaking; the server must be able to add its stored item to your inventory. The shared pickup path also permits the special no-physics state. [Player ownership and pickup checks][pickup] · [Dispenser pickup setting][item]

After an accepted hit on a non-Enderman target, a non-piercing arrow is discarded, so do not expect to recover that shot from the target. Arrows created without consuming ammunition, including Creative shots and extra projectiles, use **Creative-only pickup**: an infinite-materials player can remove them, but that branch does not add an arrow to the inventory. [Entity-hit removal][hit] · [Intangible ammunition][ammo-use] · [Pickup-mode construction][base-damage] · [Pickup result][pickup]

An ordinary Spectral Arrow item stack holds **64**. [Item registration][registration] · [Default item construction][item-defaults] · [Item properties][item-properties] · [Stack size][stack]

## Notes

- Item ID: `minecraft:spectral_arrow`
- Crafting and Piglin bartering are selected acquisition routes; this page does not exhaustively audit structure-chest loot
- Related: [Arrow](Arrow.md), [Bow](Bow.md), [Crossbow](Crossbow.md), [Glowing](../effects/VisibilityEffects.md#glowing), [Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked recipe and barter loading, ammunition selection/consumption, active arrow damage dispatch, the spectral post-hit callback and pickup rules. No in-game shot, damage, effect, pickup or barter test was run. Data packs, changed components and server rules can alter these defaults. [Recipe loading][recipes]

[item-defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2784-L2797
[item-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L354-L371
[stack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[recipes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L88
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2245-L2245
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/SpectralArrowItem.java#L12-L27
[effect]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/SpectralArrow.java#L16-L50
[hit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L377-L470
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/spectral_arrow.json#L1-L17
[barter-table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/gameplay/piglin_bartering.json#L212-L227
[barter-completion]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L373-L385
[barter-roll]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L440-L446
[catalog]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1668-L1672
[ammo-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/arrows.json#L1-L7
[bow]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BowItem.java#L100-L103
[crossbow]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CrossbowItem.java#L54-L62
[dispenser-register]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L65-L72
[dispenser-fire]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/dispenser/ProjectileDispenseBehavior.java#L25-L41
[infinity]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/enchantment/infinity.json#L6-L20
[ammo-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ProjectileWeaponItem.java#L101-L138
[base-damage]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L67-L114
[damage-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Entity.java#L1773-L1784
[effect-acceptance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L982-L1012
[pickup]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L586-L616

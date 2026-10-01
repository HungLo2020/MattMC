# Crossbow

The **Crossbow** (`minecraft:crossbow`) loads ammunition, stores it on the item, and fires on a later use. It has **465 durability** and stacks to one. Choose it when you want to prepare a shot before an encounter rather than hold a [Bow](Bow.md) drawn. [Registration][item] · [Loading and firing][crossbow]

## Crafting

Craft one Crossbow from **three [Sticks](Stick.md)**, **two [Strings](String.md)**, **one [Iron Ingot](IronIngot.md)**, and **one [Tripwire Hook](TripwireHook.md)**:

```text
Stick Iron Ingot Stick
String Tripwire Hook String
. Stick .
```

This is the active bundled shaped recipe. It establishes a crafting route without claiming every possible trade or mob-drop source. [Recipe][recipe]

## Load now, fire later

Hold the use control with ammunition available until loading finishes, then release. The ordinary charge duration is **25 ticks**, about **1.25 seconds at 20 TPS**. Loading stores the selected projectiles in the item's charged-projectiles component. Releasing too early does not complete that load. [Charge duration and loading callback][crossbow]

Use a loaded Crossbow again to fire its stored ammunition. You can switch away after loading and return to it for the shot; the loaded state belongs to the item stack rather than a held-use animation. Firing empties that stored component before launching the projectiles. These are source-defined item behaviors, not a tested reload demonstration. [Charged-projectile component and firing][crossbow]

**Quick Charge I, II, and III** reduce the normal charge time by 5, 10, and 15 ticks respectively, giving **20, 15, and 10 ticks** under the bundled defaults. The charge duration is calculated from enchantment effects rather than one immutable timer for every custom item. [Quick Charge][quick] · [Duration calculation][crossbow]

## Choosing ammunition

The inventory search accepts the arrow tag: ordinary [Arrows](Arrow.md), Tipped Arrows, and Spectral Arrows. The held-ammunition search additionally accepts **Firework Rockets**. With a Crossbow in the main hand, put a rocket in the **offhand** to choose it; a rocket only sitting in the ordinary inventory is not selected by that fallback search. Held ammunition is checked before inventory arrows. [Supported predicates][crossbow] · [Arrow tag][arrows] · [Held selection][weapon] · [Inventory selection][player]

Rockets and arrows use different projectile behavior. A firework's explosion damage requires explosion data; a plain rocket without explosions does not become a damaging blast merely because the Crossbow fires it. Explosive rockets can harm nearby living entities, so do not fire them point-blank near yourself, pets, or other players. This page does not claim a universal rocket damage value or radius of guaranteed harm. [Rocket creation][crossbow] · [Rocket explosion handling][rocket]

## Multishot and Piercing

**Multishot** adds two projectiles to the default one, for a three-projectile spread. The shared loading path consumes ammunition for the first projectile and creates the additional copies without another ammunition cost. Those free copies are marked intangible; do not count on recovering three ordinary arrows from one consumed arrow. [Multishot count/spread][multi] · [Shared drawing and intangible copies][weapon] · [Arrow pickup][arrow-entity]

**Piercing I–IV** increases an arrow's entity-piercing level. It is an arrow behavior, not a reason to expect a Firework Rocket to pass through a line of targets. Multishot and Piercing belong to the same bundled exclusive enchantment set, so normal enchantment combination treats them as incompatible. [Piercing][piercing] · [Exclusivity tag][exclusive] · [Arrow piercing and hit handling][arrow-entity]

## Wear and repair

The shared firing loop applies durability wear **per launched projectile**:

| Loaded shot | Ordinary total wear |
| --- | ---: |
| One arrow | 1 |
| One Firework Rocket | 3 |
| Three Multishot arrows | 3 |
| Three Multishot Firework Rockets | 9 |

These totals describe a normal functioning item firing the listed projectiles without wear modifiers. Loading itself consumes ammunition into the stored component; wear is applied when the projectiles are fired. Creative and durability effects can alter ordinary processing. [Per-projectile loop][weapon] · [Arrow/rocket wear choice][crossbow]

Fully damaged Crossbows remain as broken stacks in MattMC. Normal use, release, and firing reject a broken Crossbow; do not assume stored ammunition makes a broken weapon usable. Repair methods also differ in which components they retain, so take care with loaded, named, or enchanted equipment. See [Durability and repair](../mechanics/Durability.md) and [Anvil operations](../mechanics/AnvilMechanics.md). [Broken-state guards][crossbow]

## Sources and verification

Source-reviewed on 2026-10-01 at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No gameplay test of charge timing, loaded-item switching, rockets, Multishot, Piercing, wear, or repairs was run. Custom components and enchantments can change the reviewed defaults.

Related: [Bow](Bow.md) · [Arrow](Arrow.md) · [Enchanting](../enchanting/Enchanting.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/Items.java#L2373-L2377
[crossbow]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/CrossbowItem.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/crossbow.json
[quick]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/enchantment/quick_charge.json
[arrows]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/item/arrows.json
[weapon]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/ProjectileWeaponItem.java
[player]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/player/Player.java#L1834-L1854
[rocket]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/projectile/FireworkRocketEntity.java
[multi]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/enchantment/multishot.json
[piercing]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/enchantment/piercing.json
[exclusive]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/enchantment/exclusive_set/crossbow.json
[arrow-entity]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java

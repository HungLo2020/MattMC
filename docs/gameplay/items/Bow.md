# Bow

The **Bow** (`minecraft:bow`) is a draw-and-release ranged weapon with **384 durability**. Unlike a [Crossbow](Crossbow.md), it fires when you release the use control instead of storing a completed load. Keep distance from targets and leave room to draw fully. [Registration][item] · [Use and release][bow]

## Crafting and ammunition

Craft one Bow from **three [Sticks](Stick.md)** and **three [Strings](String.md)**:

```text
. Stick String
Stick . String
. Stick String
```

The shaped recipe also accepts the mirrored arrangement. This is a confirmed crafting route, not an exhaustive list of mob, trade, or loot sources. [Recipe][recipe] · [Pattern matching][pattern]

The bundled ammunition tag contains ordinary [Arrows](Arrow.md), Tipped Arrows, and Spectral Arrows. Held ammunition is selected before inventory ammunition, checking the offhand first. To choose a special arrow reliably, hold it in the offhand rather than depending on inventory order. A Bow does not accept Firework Rockets. [Arrow tag][arrows] · [Held selection][weapon] · [Player inventory fallback][player]

## Drawing and firing

Hold use to draw, aim, then release. The draw-strength calculation reaches its normal maximum at **20 ticks**, about one second at 20 TPS. Holding longer does not increase that strength beyond its cap. Releasing before the strength reaches 0.1 produces no shot; a full draw marks the launched arrow as critical. [Draw formula and release gate][bow] · [Critical flag][weapon]

Arrow damage is calculated using projectile speed, its base damage, enchantment processing, and critical randomness, followed by the target's handling. A full draw is not a guarantee of one fixed damage number at every range or against every target. Target defenses matter: an [Enderman](../mobs/Enderman.md) and a low-health [Wither](../mobs/Wither.md) have special projectile behavior. [Arrow hit calculation][arrow-entity]

An ordinary successful shot consumes one arrow and applies **one durability wear**. Casting no projectile from an underdrawn release does not apply that firing wear. Creative and durability/ammunition effects alter the shared processing where applicable. [Ammo use and projectile wear][weapon]

## Infinity and other enchantments

**Infinity** sets ammunition consumption to zero for the ordinary `minecraft:arrow` item. It does not apply to Tipped or Spectral Arrows. A Survival player still needs an accepted arrow available: projectile selection occurs before Infinity's ammunition-use effect, so Infinity does not create a missing arrow from nothing. [Infinity requirement][infinity] · [Ammo effect evaluation][ammo-helper] · [Projectile selection][player]

Arrows generated without consuming ammunition are marked intangible for pickup and become Creative-only pickups. Infinity therefore does not turn each shot into another freely collectible Survival arrow. [Intangible marking][weapon] · [Arrow pickup handling][arrow-entity]

Other bundled Bow enchantments include **Power** for arrow damage, **Punch** for knockback, and **Flame** for burning projectiles. Use the [Enchanting guide](../enchanting/Enchanting.md) and Anvil rules for obtaining and combining compatible enchantments; a listed enchantment is not a promise that every enchantment can coexist. [Power][power] · [Punch][punch] · [Flame][flame]

## Broken bows and repair

MattMC keeps a fully damaged Bow as a broken item stack. Normal use, release, and projectile firing check that state and refuse further shots. Keeping a bow in inventory does not mean it remains functional after its durability reaches zero. [Broken-use guard][stack] · [Release guard][bow] · [Firing guard][weapon]

Two Bows can be combined using the supported repair systems. Crafting-grid repair loses ordinary enchantments and other non-curse item data; a Grindstone removes non-curse enchantments. Review the output or use an appropriate [Anvil operation](../mechanics/AnvilMechanics.md) for valued equipment. See [Durability and repair](../mechanics/Durability.md) before combining an enchanted or named bow.

## Sources and verification

Source-reviewed on 2026-10-01 at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No gameplay test of draw timing, damage, ammunition selection, Infinity, or repair was run. Tags, components, enchantments, and target conditions can change particular outcomes.

Related: [Arrow](Arrow.md) · [Crossbow](Crossbow.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/Items.java#L1289
[bow]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/BowItem.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/bow.json
[pattern]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java
[arrows]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/item/arrows.json
[weapon]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/ProjectileWeaponItem.java
[player]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/player/Player.java#L1834-L1854
[arrow-entity]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java
[infinity]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/enchantment/infinity.json
[ammo-helper]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L96-L100
[power]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/enchantment/power.json
[punch]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/enchantment/punch.json
[flame]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/enchantment/flame.json
[stack]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/ItemStack.java#L382-L390

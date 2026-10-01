# Wolf Armor

**Wolf Armor** protects a tame adult [Wolf](../mobs/Wolf.md) by spending item durability on eligible incoming damage. Its owner can equip it, repair it with Armadillo Scutes while the wolf sits, and remove it with Shears. Protection has important exceptions, including a source-identified inconsistency when the armor is fully damaged in MattMC. [Wolf interactions][interactions] · [Damage handling][absorption]

## At a glance

- **Item ID:** `minecraft:wolf_armor`
- **Maximum durability:** 64
- **Repair on the wolf:** 8 durability per Armadillo Scute
- **Equipment slot:** body; restricted to Wolves
- **Maximum stack:** one

Durability comes from the body-slot multiplier of 16 and the Armadillo Scute material multiplier of 4. The component also supplies 11 armor points, but the special wolf-armor absorption rule is the main protection described below. [Item registration][registration] · [Armor components][components] · [Material][material] · [Slot durability][slot] · [Repair interaction][interactions]

## Crafting and scutes

Craft one armor from **six [Armadillo Scutes](ArmadilloScute.md)** in a Crafting Table:

- Top row: Scute, empty, empty
- Middle row: Scute, Scute, Scute
- Bottom row: Scute, empty, Scute

This is an active shaped recipe. Creative also lists Wolf Armor. [Recipe][recipe] · [Creative entry][creative]

Scutes are obtained from living adult [Armadillos](../mobs/Armadillo.md): using a [Brush](Brush.md) calls a loot table that produces one, and adults also shed a scute periodically. The brush interaction costs **16 Brush durability** per successful use. The shedding timer resets to 6,000–11,999 ticking game ticks, roughly five to ten minutes at 20 TPS. Killing an Armadillo is not the documented scute route. [Brushing][brushing] · [Brush loot][brush-loot] · [Shedding timer][shedding] · [Shed loot][shed-loot]

## Equipping and removing

To equip armor, interact with **your own tame adult wolf** while holding it. The wolf must not already be wearing body armor. Equipping does not require the wolf to be sitting, but putting it somewhere safe first makes care easier. Puppies cannot receive armor through this interaction. [Equip conditions][interactions]

To remove it, the owner uses **[Shears](Shears.md) without sneaking**. The armor drops as an item with its existing damage and other item data. The Shears must not be broken and take one durability damage on success. If the wolf has a leash connection, shearing that connection is checked first; another use may be needed for the armor. An equipment-change-prevention enchantment can block normal shearing outside Creative. [Owner restriction][owner] · [Shears interaction order][shears] · [Equipment removal][removal]

## Repairing worn armor

1. Order your wolf to sit with an empty-hand interaction
2. Hold an **Armadillo Scute** and interact with the sitting, armored wolf
3. Each successful use consumes one scute and repairs **8 durability**, up to the armor's maximum

The wolf must be yours, its armor must be damaged, and it must actually be in its sitting pose. The bundled repair tag contains only Armadillo Scute. This repairs the armor, not the wolf's health; feed an injured wolf separately, for example with [Rotten Flesh](RottenFlesh.md), which heals **8 health points**. [Repair conditions and amount][interactions] · [Repair ingredient][repair-tag] · [Wolf healing][healing]

## How protection works

For a damage source outside the wolf-armor bypass tag, the Wolf's special damage handler sends **the incoming damage amount, rounded up**, to the armor's durability handler and does not run its normal health-damage branch for that hit. Ordinary item-durability modifiers may affect the resulting wear. This is a damage buffer, not a promise of 64 attacks survived: different hits spend different amounts. [Absorption][absorption] · [Durability processing][durability]

The special protection is bypassed by the bundled list of damage types, including:

- Drowning, suffocation, cramming, freezing, drying out, starvation, and outside-world-border damage
- Magic, indirect magic, Wither, and Thorns
- Void and generic-kill damage through the nested bypass-invulnerability tag

Those sources proceed through normal damage handling instead. Keep the wolf away from environmental hazards even when it looks fully armored. [Bypass tag][bypass] · [Nested exceptions][invulnerability] · [Absorption check][absorption]

### Fully damaged armor in this snapshot

**Do not assume the armor disappears or that protection stops cleanly at zero durability.** MattMC keeps fully damaged items as broken stacks. Its durability handler ignores further wear on an already-broken item, while the Wolf's absorption check tests the item type and damage-source tag without checking whether the armor is broken. The equipment-break callback stops item effects but does not remove the stack. [Broken-stack behavior][durability] · [Absorption check][absorption] · [Break callback][break-callback]

Consequently, the inspected source still routes eligible hits through the absorption branch while fully damaged Wolf Armor remains equipped. This is a **source-identified inconsistency**, not a tested guarantee of indefinite protection. Repair or remove damaged armor normally, and do not use this behavior as a substitute for keeping the wolf safe.

## Appearance and recovery

Armor develops progressively stronger crack overlays as its remaining-durability fraction falls below 95%, 69%, and 32%. Cracks are a cue to check and repair it. [Crack thresholds][cracks] · [Armor rendering][render]

You can combine Wolf Armor with dye in a crafting grid; the bundled dyeable tag and armor-dye recipe include it. Remove worn armor before dyeing it. Using dye directly on your wolf changes its collar instead. A Water Cauldron can wash the armor's dye off, consuming one water level. [Dyeable tag][dyeable] · [Dye recipe][dye-recipe] · [Dye implementation][dye] · [Collar interaction][interactions] · [Washing][wash]

Equipping armor marks that equipment slot for a guaranteed equipment drop under normal death-loot handling. Recovery still depends on loot rules, item-drop-prevention effects, and what happens to the dropped item afterward. Remove it with Shears when you want deliberate recovery; the Wolf's ordinary death-loot table supplies no new armor. [Equipping and drop flag][drop-flag] · [Equipment drops][equipment-drops] · [Mob-loot condition][loot-rule] · [Wolf loot][wolf-loot]

## Related pages

- [Wolf](../mobs/Wolf.md)
- [Armadillo Scute](ArmadilloScute.md)
- [Shears](Shears.md)
- [Rotten Flesh](RottenFlesh.md)
- [Items](Items.md)

## Verification scope

Source-reviewed at MattMC commit `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. Crafting, item components, scute routes, ownership restrictions, shearing, repair ingredients, damage exceptions, broken-item behavior, dyeing, and equipment drops were inspected. No in-game crafting, equipping, repair, removal, dyeing, or armor-damage test was run.

[registration]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1285
[components]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Item.java#L469-L485
[material]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/equipment/ArmorMaterials.java#L33-L38
[slot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/equipment/ArmorType.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/wolf_armor.json
[creative]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1651
[brushing]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/armadillo/Armadillo.java#L299-L322
[brush-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/brush/armadillo.json
[shedding]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/armadillo/Armadillo.java#L139-L153
[shed-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/gameplay/armadillo_shed.json
[interactions]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L455-L504
[owner]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L449-L452
[shears]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Entity.java#L2134-L2144
[removal]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Entity.java#L2211-L2233
[repair-tag]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/repairs_wolf_armor.json
[healing]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L458-L464
[absorption]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L404-L432
[durability]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[bypass]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/damage_type/bypasses_wolf_armor.json
[invulnerability]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/damage_type/bypasses_invulnerability.json
[break-callback]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3540-L3553
[cracks]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Crackiness.java
[render]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/client/renderer/entity/layers/WolfArmorLayer.java
[dyeable]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/dyeable.json
[dye-recipe]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/armor_dye.json
[dye]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/crafting/ArmorDyeRecipe.java
[wash]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/core/cauldron/CauldronInteraction.java#L313-L329
[drop-flag]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Mob.java#L782-L784
[equipment-drops]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Mob.java#L814-L839
[loot-rule]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L565-L567
[wolf-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities/wolf.json

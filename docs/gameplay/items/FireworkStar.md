# Firework Star

A **Firework Star** (`minecraft:firework_star`) is a crafting ingredient that stores one explosion design for [Firework Rockets](FireworkRocket.md). It is not a launchable rocket or a thrown explosive. Matching stars stack to 64. [Registration][items] · [Ordinary item use][item-use] · [Default stack limit][default-stack]

## Obtaining

### Crafting a design

Arrange **one [Gunpowder](Gunpowder.md)** and **one or more [Dyes](Dyes.md)** in separate slots to craft **one star**. Add an optional shape ingredient, Diamond for Trail, and Glowstone Dust for Flicker during this craft. The recipe allows only one shape ingredient, one Diamond, and one Glowstone Dust. [Active recipe][star-data] · [Matching and output][star-recipe]

See [Make a star and choose its effects](../mechanics/Fireworks.md#make-a-star-and-choose-its-effects) for the exact shape ingredients, all accepted colors, and grid limits. A shape such as Large Ball changes the saved effect design; it does not itself increase the rocket's damage or damage range. [Damage calculation][rocket-damage]

## Usage

### Fade colors and rockets

Combine **one existing star plus one or more dyes**, without Gunpowder, to craft **one star** with replacement fade colors. This keeps its original colors, shape, Trail, Flicker, and other stack components, including a custom name. Repeating the fade craft replaces the previous fade list. [Fade recipe][fade-data] · [Copy and update][fade-recipe] · [Preserved explosion fields][explosion]

Use the finished star in the rocket recipe. Each occupied star slot with explosion data adds one explosion entry to each of the three output rockets; the star is consumed. The explosion design transfers, but the star's name and unrelated components do not. A bare star lacking an explosion component contributes no entry, even though the recipe accepts it. [Rocket assembly][rocket-recipe] · [Input consumption][consume-inputs]

## Behavior

**Use rockets without stars for Elytra travel.** Stars supply explosion entries that can damage the flyer and other nearby living entities. The [Fireworks guide](../mechanics/Fireworks.md) owns the launch, damage, persistence, and display rules. Its recipes describe stored effects; full visual results have not been verified in-game on the current Rust renderer. [Explosion damage][rocket-damage]

## Notes

### Sources and verification

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`; active recipe loading, ingredient consumption, component transfer, and effect dispatch were checked in [Fireworks](../mechanics/Fireworks.md#sources-and-verification). No in-game crafting, fading, launch, damage, persistence, or visual test was run. Custom components and data packs can change the result.

Related: [Firework Rocket](FireworkRocket.md) · [Fireworks](../mechanics/Fireworks.md) · [Dyes](Dyes.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L2106-L2109
[item-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Item.java#L164-L191
[default-stack]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L391
[star-data]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/firework_star.json
[star-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/FireworkStarRecipe.java#L15-L127
[rocket-damage]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/projectile/FireworkRocketEntity.java#L186-L251
[fade-data]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/firework_star_fade.json
[fade-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/FireworkStarFadeRecipe.java#L20-L75
[explosion]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/component/FireworkExplosion.java#L24-L100
[rocket-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/FireworkRocketRecipe.java#L22-L79
[consume-inputs]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L82-L106

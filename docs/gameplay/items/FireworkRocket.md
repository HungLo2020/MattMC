# Firework Rocket

A **Firework Rocket** (`minecraft:firework_rocket`) can launch from a block, propel a gliding [Elytra](Elytra.md) user, or serve as held [Crossbow](Crossbow.md) ammunition. Its Flight Duration controls its lifetime; optional [Firework Stars](FireworkStar.md) add explosion data and damage. Default rockets have Flight Duration 1 and no explosions, and stack to 64 when their components match. [Registration][items] · [Default stack limit][default-stack] · [Rocket use][rocket-use]

## Obtaining

### Crafting and choosing a rocket

Arrange **one [Paper](Paper.md)** and **one to three [Gunpowder](Gunpowder.md)**, with each Gunpowder in its own slot, to craft **three rockets**. Add optional Firework Stars in the remaining slots for explosion payloads. For Elytra travel, leave the stars out: an explosive boosting rocket can damage its flyer. [Active recipe][rocket-data] · [Crafting result][rocket-recipe] · [Explosion damage][rocket-damage]

The [Fireworks guide](../mechanics/Fireworks.md) gives the exact ingredient limits, colors and effects, Flight Duration timings, damage conditions, saved-data behavior, and current visual-verification limits.

## Usage

### Using it

- **Launch:** use it on a block while not gliding; the rocket starts near the clicked face and accelerates upward
- **Boost:** use it while already gliding; it accelerates you in your look direction and drops your leash connections. A ground launch does not start a glide
- **Crossbow:** hold rockets in the offhand with the Crossbow in the main hand. Rockets only sitting elsewhere in the inventory are not chosen by its fallback search
- **Dispenser:** place rockets inside and activate it; it launches them along its facing rather than attaching them to a flyer

[Hand/Dispenser behavior][rocket-use] · [Movement][rocket-flight] · [Crossbow selection][crossbow] · [Inventory search][player-ammo] · [Dispenser dispatch][dispenser]

## Behavior

Survival use consumes one rocket. Spectator, cooldown, Adventure block-use permissions, and server interaction restrictions can prevent a launch. An empty explosion list means no firework explosion damage; it does not remove Elytra collision hazards. Read [launching and propulsion](../mechanics/Fireworks.md#launching-and-propulsion) and [explosions and damage](../mechanics/Fireworks.md#explosions-and-damage) before choosing travel or combat rockets. [Use gates][game-mode] · [Adventure check][adventure] · [Damage handling][rocket-damage]

## Notes

### Sources and verification

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`; active recipe and interaction dispatch were traced in [Fireworks](../mechanics/Fireworks.md#sources-and-verification). No in-game crafting, use, boost, combat, or visual test was run. Data packs, components, and server settings can differ.

Related: [Firework Star](FireworkStar.md) · [Fireworks](../mechanics/Fireworks.md) · [Elytra](Elytra.md) · [Crossbow](Crossbow.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L2106-L2109
[default-stack]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L391
[rocket-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/FireworkRocketItem.java#L28-L96
[rocket-data]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/firework_rocket.json
[rocket-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/FireworkRocketRecipe.java#L22-L79
[rocket-damage]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/projectile/FireworkRocketEntity.java#L186-L251
[rocket-flight]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/projectile/FireworkRocketEntity.java#L56-L184
[crossbow]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CrossbowItem.java#L54-L164
[player-ammo]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/player/Player.java#L1834-L1855
[dispenser]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/dispenser/ProjectileDispenseBehavior.java#L25-L46
[game-mode]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L300-L397
[adventure]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L372

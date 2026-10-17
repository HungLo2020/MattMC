# Fireworks

**Firework Rockets** provide [Elytra](../items/Elytra.md) propulsion and carry optional explosion effects made from **Firework Stars**. For travel, craft rockets **without stars**: adding a star gives the rocket a damaging payload, including against its attached flyer. This guide owns the recipes, launch differences, stored effects, and damage rules; the [Rocket](../items/FireworkRocket.md) and [Star](../items/FireworkStar.md) pages provide item entry points. [Rocket recipe][rocket-recipe] · [Explosion damage][rocket-damage]

## Craft rockets

Arrange **one [Paper](../items/Paper.md)** and **one, two, or three [Gunpowder](../items/Gunpowder.md)** in any positions. Put each Gunpowder in a **separate slot**. Optionally add Firework Stars in the remaining slots. Every successful craft returns **three identical rockets**. A second Paper, a fourth Gunpowder slot, or any other ingredient prevents this recipe from matching. [Bundled recipe][rocket-data] · [Matching and result][rocket-recipe]

| Gunpowder slots | Flight Duration on output | Maximum star slots in a 3×3 grid | Timed expiry without an earlier hit |
| ---: | ---: | ---: | --- |
| 1 | 1 | 7 | Tick 21–32; about 1.05–1.60 seconds |
| 2 | 2 | 6 | Tick 31–42; about 1.55–2.10 seconds |
| 3 | 3 | 5 | Tick 41–52; about 2.05–2.60 seconds |

**Flight Duration is not a count of seconds.** The rocket chooses a lifetime of `10 × (1 + Flight Duration) + random(0–5) + random(0–6)` ticks, then expires after its age exceeds that lifetime. The table counts its first tick as tick 1 and assumes 20 TPS. Hits can end the flight sooner; these values do not guarantee a height, travel distance, or uninterrupted boost. [Lifetime and tick condition][rocket-flight]

Each occupied input slot contributes once, regardless of the stack size in that slot, and taking one crafting result consumes one item from each occupied slot. For example, a stack of three Gunpowder in one slot makes Flight Duration **1**, while one Gunpowder in each of three slots makes **3**. Stars also count by occupied slot. [Recipe counting][rocket-recipe] · [Input consumption][consume-inputs]

## Make a star and choose its effects

Craft **one Gunpowder plus at least one [Dye](../items/Dyes.md)** to obtain **one Firework Star**. Put different ingredients in separate slots, in any arrangement. A basic star has the **Small Ball** shape. You may include one shape ingredient, one Diamond, and one Glowstone Dust in the same craft if the grid has room. [Bundled recipe][star-data] · [Star matching and output][star-recipe]

| Optional ingredient | Stored effect |
| --- | --- |
| None of the shape ingredients below | Small Ball |
| Fire Charge | Large Ball |
| Gold Nugget | Star |
| Feather | Burst |
| Skeleton Skull, Wither Skeleton Skull, Creeper Head, Player Head, Dragon Head, Zombie Head, or Piglin Head | Creeper-shaped |
| Diamond | Trail |
| Glowstone Dust | Flicker, called twinkle in the saved data |

Only **one shape ingredient** is allowed: two heads, or a head plus a Feather, reject the craft. A second Gunpowder, Diamond, or Glowstone Dust slot also rejects it. Shape, Trail, and Flicker must be chosen when creating the star; the fade recipe below cannot add them afterward. These names describe the saved effects and their intended particle patterns; see [display limits](#effects-and-current-rendering). [Accepted ingredients and limits][star-recipe] · [Effect names][explosion]

All **16 dyes** are accepted: White, Orange, Magenta, Light Blue, Yellow, Lime, Pink, Gray, Light Gray, Cyan, Purple, Blue, Brown, Green, Red, and Black. Every dye slot adds its firework color to a list; the recipe does **not blend the dyes into one color**. Repeating a dye in another slot adds another entry. A 3×3 grid fits up to eight dye slots with only Gunpowder, or five with a shape ingredient, Diamond, and Glowstone Dust. [Color values][dye-colors] · [Color collection][star-recipe]

## Add or replace fade colors

Combine **one existing Firework Star plus one or more dyes**, in any positions, to obtain **one star** with a new fade-color list. There is **no Gunpowder** in this recipe. Two star slots or any non-dye extra ingredient reject it. A 3×3 grid fits up to eight dye slots. [Bundled fade recipe][fade-data] · [Matching and result][fade-recipe]

Fade crafting **replaces** the old fade colors instead of appending them. It preserves the star's initial colors, shape, Trail, Flicker, custom name, and other components by copying one star and updating only its explosion's fade list. It consumes the input star and dyes. A component-free star uses the default Small Ball explosion when fade data is added. [Stack copying][copy] · [Fade update][fade-recipe] · [Preserved explosion fields][explosion]

## Launching and propulsion

| Use | What happens |
| --- | --- |
| Use a rocket on a block while not gliding | Spawns it just outside the clicked face; its ordinary flight accelerates upward, even when the clicked face is on the side |
| Use a rocket while already gliding | Attaches it to you and accelerates in your look direction while you continue gliding |
| Fire it from a [Crossbow](../items/Crossbow.md) | Launches an independent projectile along the shot direction |
| Activate a [Dispenser](../blocks/DispenserAndDropper.md) containing rockets | Consumes one rocket and launches it along the Dispenser's facing, with launch spread |

The Crossbow and Dispenser mark rockets as shot at an angle, skipping the upward acceleration used by a normal block launch. They do not attach rockets to a nearby Elytra user. The rocket keeps its configured flight duration and explosion payload in all these launch routes. [Hand and Dispenser setup][rocket-use] · [Movement branches][rocket-flight] · [Crossbow projectile][crossbow] · [Registered Dispenser behavior][dispenser-registration] · [Dispenser launch][dispenser]

**A ground launch does not start a glide.** Begin eligible Elytra flight first, then use a rocket. Ordinary Survival launch or boost consumes one rocket; Creative's infinite-material ability preserves the held supply through the shared use rules. Boosting also drops the flyer's leash connections, so do not expect attached leashes to survive that action. [Rocket interactions][rocket-use] · [Creative block-use handling][game-mode] · [Boost consumption][consume]

With a Crossbow in the main hand, hold rockets in the **offhand** to select them. The fallback search through ordinary inventory accepts arrows, not rockets. The [Crossbow guide](../items/Crossbow.md) owns loading time, Multishot, Piercing, stored ammunition, and durability costs. [Held ammunition][held-ammo] · [Crossbow predicates][crossbow] · [Inventory fallback][player-ammo]

## Explosions and damage

On the server, a rocket expires when its lifetime runs out. An ordinary, non-deflected hit on an eligible entity ends it sooner, even without explosion data. A block hit triggers early detonation **only if the explosion list is nonempty**. All three routes use the same damage calculation. [Collision and explosion handling][rocket-damage] · [Hit/deflection dispatch][projectile-hit]

- **No explosion entries:** no firework explosion damage, including when fired from a Crossbow
- **One or more entries:** base damage is **5 + 2 × entry count** health points: 7 for one entry and 19 for seven, before defenses and other damage rules
- **Attached flyer:** receives the base-damage request directly, without the distance or cover checks used for nearby targets
- **Other living entities:** checked within five blocks of the rocket's position; at least one ray to the target's feet or halfway up its height must be clear of block collision shapes. Fluids are ignored by this cover check. Damage falls with distance as `base × sqrt((5 − distance) / 5)`, reaching zero at five blocks

These are damage requests, not guaranteed health loss. [Health and damage](Health.md), protection, invulnerability, and server rules still matter. More colors, extra Gunpowder, Large Ball, Trail, Flicker, and fade colors do **not** increase this base damage or its five-block check; the **number of saved explosion entries** does. The firework's blast code damages living entities without calling a block-destroying world explosion. Keep explosive rockets away from yourself, other players, and animals. [Damage calculation][rocket-damage]

## Stored data and use restrictions

A rocket copies **each star's explosion component**, including its initial and fade colors, shape, Trail, and Flicker. It does not transfer the star's custom name or other unrelated components to the newly crafted rockets. A star with no explosion component can be accepted as an ingredient but contributes **no explosion entry**. Default item registration gives a plain rocket Flight Duration 1 and no explosions; the default Star item has no explosion component. [Rocket assembly][rocket-recipe] · [Item defaults][items]

Both explosion and rocket components have save and network codecs. The launched entity copies the rocket stack and saves that item, its age, chosen lifetime, and angled-shot flag. Its attached flyer is **not** written by that save routine, so do not rely on save/reload preserving an in-progress attached boost. Custom components can exceed normal crafting values; the limits above describe ordinary recipes. [Component registration][components] · [Rocket component][fireworks] · [Launch copy][rocket-flight] · [Entity save fields][rocket-save]

Spectators cannot launch rockets through ordinary item use. Item cooldowns can block use. Block launches also go through server reach/protected-area checks and, in Adventure, the item's matching **Can Place On** permission; carrying ordinary rockets does not bypass those restrictions. Interacting with a usable block may open or activate that block first, so use a plain surface or the secondary-use control when appropriate. [Server item-use caller][server-use] · [Game-mode and interaction ordering][game-mode] · [Adventure check][adventure]

## Effects and current rendering

The client receives a rocket event and passes its explosion list to the firework particle system. That system defines the shape patterns, per-spark color selection, fade targets, Trail, and Flicker; an empty explosion list takes a small poof-particle route instead. The stored recipes and intended effects are established by source inspection. [Client explosion dispatch][rocket-save] · [Particle start][particle-start] · [Effect construction][particle-effects]

The current Rust renderer has a generic route for the registered firework spark/flash particles, plus an item-entity submission route for the flying rocket. That wiring is **not visual acceptance**: this review did not run the game or verify the appearance, colors, fades, trails, flicker, sound, or complete effect sequences on Rust. Do not use a missing or incomplete display as evidence that an explosive rocket is safe; server damage is calculated separately. [Particle providers][particle-providers] · [Active Rust caller][rust-frame] · [Particle extraction][rust-particles] · [Admission checks][rust-admission] · [Rocket submission][rocket-renderer]

## Sources and verification

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Checked active item/component registration, all three bundled special recipes, recipe loading and crafting/result dispatch, hand/Crossbow/Dispenser launches, flight/collision/damage, persistence, player-use gates, and client particle/Rust submission paths. No gameplay, crafting, launch, flight, combat, persistence, sound, or visual test was run. Data packs, components, server rules, ticking, and later changes can alter these defaults. [Recipe serializers][recipe-types] · [Resource loading][recipe-loader] · [Reload registration][recipe-reload] · [Crafting caller][crafting-call]

Related: [Firework Rocket](../items/FireworkRocket.md) · [Firework Star](../items/FireworkStar.md) · [Dyes](../items/Dyes.md) · [Elytra](../items/Elytra.md) · [Crossbow](../items/Crossbow.md) · [Mechanics](Mechanics.md)

[rocket-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/FireworkRocketRecipe.java#L22-L79
[rocket-damage]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/projectile/FireworkRocketEntity.java#L186-L251
[rocket-data]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/firework_rocket.json
[rocket-flight]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/projectile/FireworkRocketEntity.java#L56-L184
[consume-inputs]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L82-L106
[star-data]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/firework_star.json
[star-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/FireworkStarRecipe.java#L15-L127
[explosion]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/component/FireworkExplosion.java#L24-L100
[dye-colors]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/DyeColor.java#L24-L40
[fade-data]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/firework_star_fade.json
[fade-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/FireworkStarFadeRecipe.java#L20-L75
[copy]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ItemStack.java#L607-L624
[rocket-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/FireworkRocketItem.java#L28-L96
[crossbow]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CrossbowItem.java#L54-L164
[dispenser-registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L69-L80
[dispenser]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/dispenser/ProjectileDispenseBehavior.java#L25-L46
[game-mode]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L300-L397
[consume]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1079
[held-ammo]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ProjectileWeaponItem.java#L20-L39
[player-ammo]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/player/Player.java#L1834-L1855
[projectile-hit]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L222-L299
[items]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L2106-L2109
[components]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/component/DataComponents.java#L269-L274
[fireworks]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/component/Fireworks.java#L18-L80
[rocket-save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/projectile/FireworkRocketEntity.java#L261-L297
[server-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1255-L1325
[adventure]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L372
[particle-start]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/multiplayer/ClientLevel.java#L789-L796
[particle-effects]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/particle/FireworkParticles.java#L152-L279
[particle-providers]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/particle/ParticleResources.java#L100-L109
[rust-frame]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L887-L909
[rust-particles]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/particle/QuadParticleGroup.java#L66-L85
[rust-admission]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/state/QuadParticleRenderState.java#L76-L146
[rocket-renderer]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/FireworkEntityRenderer.java#L24-L58
[recipe-types]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/RecipeSerializer.java#L16-L20
[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L88
[recipe-reload]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/ReloadableServerResources.java#L66-L68
[crafting-call]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/inventory/CraftingMenu.java#L47-L75

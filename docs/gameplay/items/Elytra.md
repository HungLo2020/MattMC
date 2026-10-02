# Elytra

**Elytra** (`minecraft:elytra`) lets you glide when equipped in the **chest slot**. Obtain it from an End Ship's item frame, then check its remaining durability before leaving solid ground. Wearing it replaces the chestplate in that slot; its default registration provides no armor attribute bonus. [Item properties][item] · [End City rewards](../structures/EndCity.md#chest-loot-and-the-elytra-frame)

## Obtaining and equipping

The bundled [End Ship](../structures/EndCity.md) template has one Elytra marker. During placement, that marker creates an item frame holding one Elytra. It is a displayed reward, not an End City chest-loot result. In Survival with entity drops enabled, strike the occupied frame and collect the released item. A city without a ship does not provide this reward. [Ship template][ship] · [Reward placement][reward] · [Frame collection][frame]

Put the Elytra into the chest equipment slot, or use it while holding it to swap it with the item already worn there. Equipment-removal restrictions can prevent the swap. Elytra does not stack. [Equip interaction][use] · [Swap conditions][equipment] · [Durability properties][properties]

## Starting and controlling a glide

1. Equip a usable Elytra and choose open air with a clear landing route
2. Become airborne, then press **Jump again** to start gliding
3. Look in the direction you want to travel. Pitch changes how momentum trades between forward motion and height
4. Approach a broad landing area and reduce speed before reaching walls or narrow openings

Starting a glide requires you to be off the ground, out of water, not riding another entity, not using Creative flight, and free of **Levitation**. The normal client input also excludes climbing. Landing, climbing, losing eligible equipment, or gaining Levitation can stop a glide. Fast horizontal collisions can cause wall-impact damage. [Jump input][input] · [Player checks][player] · [Movement and collision][movement] · [Continuing-flight checks][flight]

A [Shulker](../mobs/Shulker.md) hit is especially dangerous: its Levitation effect blocks gliding until the effect ends. Do not jump over the void expecting newly collected wings to override it.

## Durability and the last point

Default Elytra has **432 maximum durability**. Ordinary continuous gliding requests **one durability point every 20 ticks**, about once per second at 20 TPS. Unbreaking and Creative's infinite-material ability can change actual wear. Its equipment settings disable the ordinary wear-on-being-hurt behavior. [Item settings][item] · [Flight wear][flight] · [Shared wear processing][stack]

**Gliding becomes unavailable at damage 431, with one durability point still remaining.** The flight check asks whether the *next* point of damage would break the item. It stops flight at that threshold, before the shared fully-broken condition at damage 432. This is not a destroyed item: keep and repair the Elytra. [Glider eligibility][eligibility] · [Damage thresholds][stack]

MattMC also retains ordinary fully damaged stacks rather than deleting them through normal wear. That general rule does not make an exhausted Elytra flyable. See [Durability, broken items, and repair](../mechanics/Durability.md) for the shared behavior. Avoid planning a trip around an exact maximum flight time; the available durability and wear modifiers matter more than the number printed on a fresh item.

## Repair and enchantments

At an [Anvil](../mechanics/AnvilMechanics.md), repair Elytra with **Phantom Membranes**. Each accepted membrane restores up to **108 durability** from a default Elytra, one quarter of its maximum, and the Anvil calculates a level cost. Combining it with another Elytra is a separate repair route that consumes the donor. [Repair material][item] · [Anvil calculation][anvil]

The bundled durability-enchantment tag includes Elytra, so applicable **Unbreaking** and **Mending** books can be applied through an Anvil. Mending repairs a selected damaged enchanted item from collected experience while it is held or equipped; simply leaving the Elytra elsewhere in your inventory is insufficient. Its XP repair writes the damage value directly, so this route also supports a retained fully damaged Elytra. [Supported items][enchantable] · [Unbreaking][unbreaking] · [Mending][mending] · [Selection][enchantment-helper] · [XP repair][xp]

The [Grindstone and crafting repair comparison](../mechanics/Durability.md#choose-a-repair-method) explains the costs of combining two matching items through other methods, including lost enchantments or other item data. Check the output before sacrificing customized wings.

## Firework boosts

Use a **Firework Rocket while already gliding** to attach a rocket and accelerate in your look direction. Ordinary Survival use consumes one rocket. Launching a rocket from the ground does not start Elytra flight. [Rocket use][rocket-item] · [Attached boost][rocket]

For travel, craft **one Paper plus one, two, or three Gunpowder**, with each Gunpowder in its own crafting slot, and **no Firework Stars**. The shapeless special recipe returns **three rockets**, with Flight Duration 1, 2, or 3 respectively. More Gunpowder lengthens the rocket's lifetime. [Active recipe][rocket-data] · [Recipe inputs and output][rocket-recipe] · [Lifetime][rocket]

Stars add explosion effects. When a boosting rocket with such effects explodes, it can damage the attached flyer and nearby entities. Rockets crafted without stars have no explosion-damage payload. This removes that particular damage source, not the risk of flying into a wall or exhausting your Elytra. [Explosion damage][rocket]

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Reviewed item registration, template reward creation, equipment and flight callers, wear/repair paths, enchantment data, and active rocket recipes. No in-game frame collection, glide, crash, wear-out, repair, enchantment, or boost test was run. Custom components, enchantments, and server data can change these defaults.

Related: [End City](../structures/EndCity.md) · [Shulker](../mobs/Shulker.md) · [Durability](../mechanics/Durability.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1196-L1208
[ship]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/structure/end_city/ship.nbt
[reward]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/structure/structures/EndCityPieces.java#L369-L387
[frame]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/decoration/ItemFrame.java#L157-L243
[use]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Item.java#L173-L182
[equipment]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/equipment/Equippable.java#L127-L157
[properties]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Item.java#L386-L390
[input]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/client/player/LocalPlayer.java#L713-L739
[player]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/player/Player.java#L1320-L1386
[movement]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2365-L2420
[flight]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2883-L2920
[eligibility]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3703-L3709
[stack]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[anvil]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L120-L205
[enchantable]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/enchantable/durability.json
[unbreaking]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/enchantment/unbreaking.json
[mending]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/enchantment/mending.json
[enchantment-helper]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L479-L497
[xp]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ExperienceOrb.java#L293-L311
[rocket-item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/FireworkRocketItem.java
[rocket]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/projectile/FireworkRocketEntity.java#L56-L251
[rocket-data]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/firework_rocket.json
[rocket-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/crafting/FireworkRocketRecipe.java

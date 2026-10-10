# Trident

**A Trident is a reusable melee and throwing weapon.** Throw it and recover the same item, add Loyalty for a return flight, or choose Riptide for a water-and-rain launch. Its registered ID is `minecraft:trident`. [Registration][registration] · [Use and release][trident]

## Obtaining

Two checked loot routes provide Tridents:

- **[Drowned](../mobs/Drowned.md):** a Drowned holding a naturally equipped Trident can drop it on a player-credited kill with mob loot enabled. The ordinary equipment drop chance is **8.5%**, rising by one percentage point per Looting level to **11.5% at Looting III**. These percentages apply to the equipped weapon, not every Drowned. Its ordinary equipment roll gives a Trident approximately 6.25% of the time. Dropped weapons can be heavily damaged, so inspect the durability. [Equipment roll][drowned] · [Drop conditions and damage][mob-drops] · [Base chance][drop-chance] · [Looting][looting]
- **Normal [Vaults](../blocks/Vault.md#normal-vaults):** an accepted Trial Key can yield a Trident through the unique reward pool. The bundled calculation is **25% × 1/12 = 1/48**, about **2.08% per successful opening**. The Trident entry has no enchantment or wear function. The Vault guide owns chamber access, keys, player eligibility, and collecting ejected rewards. [Configured normal template][vault-template] · [Reward resolution][vault-use] · [Parent table][vault-reward] · [Unique weights][vault-unique] · [Default entry weight][loot-weight]

A Drowned's thrown projectiles are **not collectible Survival Tridents**; killing an armed Drowned for its equipment and picking up its shots are different routes. [Drowned projectile creation][drowned] · [Non-player pickup rules][arrow]

No bundled crafting recipe for a Trident was found in the active recipe resources. It is an ordinary category-listed item in the [inventory item browser](../mechanics/InventoryBrowser.md), which also allows insertion in Creative. That is separate from the loot routes above. [Active recipe loader][recipe-loader] · [Category entry][creative]

## Usage

### Melee and throwing

Use **Attack** for melee. With the ordinary player attributes and an unbroken, unmodified Trident, a fully charged basic melee attack starts at **9 damage points** before enchantments, critical hits, and defenses. Attack speed is approximately **1.1**, giving a nominal full-recharge interval of about **18.18 ticks**, or 0.91 seconds at 20 TPS. Follow [Combat's attack timing](../mechanics/Combat.md#time-your-melee-attacks) for the shared charge rules. [Trident modifiers][trident] · [Player calculation][player] · [Default speed][attributes]

To throw an ordinary Trident:

1. Hold **Use** for at least **10 game ticks**, about half a second at 20 TPS
2. Release Use to launch it; a shorter hold does not throw
3. Recover it where it lands, or wait for Loyalty to bring it back

Holding longer does not build a Bow-style damage multiplier. A throw moves the Trident out of the Survival inventory and into the projectile, including its damage and enchantments. Its ordinary projectile hit starts at **8 damage points**, before eligible enchantments and defenses; it does not use the arrow's speed-based impact formula. [Throw flow][trident] · [Projectile damage and carried item][thrown] · [Stack transfer][stack]

Only its owner can pick up a player-owned Trident through the normal touch path. A grounded Trident must finish its brief impact shake, and collection needs inventory room. Keep a slot available for a returning Trident too. [Owner check and returning pickup][thrown] · [Ground pickup][arrow]

### Recover before despawning

Recover a lodged Trident promptly if it has no Loyalty. While it remains embedded and is being ticked by the server, its ordinary despawn counter reaches **1,200 ticks**, about **one minute at 20 TPS**, then removes the projectile and its carried Trident. The counter advances during grounded processing, rather than measuring the time since the throw; starting to fall again resets it. [Grounded server processing][grounded-timer] · [Counter reset and despawn threshold][despawn-counter]

**Loyalty suppresses this counter only when the Trident's normal pickup is allowed.** Creative-only and disallowed pickup modes do not get that exemption. The exemption does not prevent other causes of loss; the [Loyalty return conditions](#enchantment-choices-and-conditions) still apply. [Loyalty and pickup gate][loyalty-despawn]

### Riptide movement

With **Riptide I–III**, the same 10-tick hold-and-release launches **you** in the aimed direction instead of creating a thrown Trident. You must be in **Water or rain**, both when beginning use and when releasing it. Ordinary melee still works when dry. Higher levels increase the launch strength; launching from the ground also lifts the player first. [Riptide use conditions][trident] · [Riptide levels][riptide]

The spin attack lasts up to **20 ticks** and can end early on a living-entity collision or the checked horizontal-collision condition. Its contact attack goes through the player's combat calculation, so the listed spin damage is not a guaranteed fixed health loss. Plan clear travel and a safe landing; Riptide does not grant the special Wind Charge fall allowance. [Launch and spin duration][trident] · [Collision and spin end][living] · [Contact attack and fall rules][player]

## Behavior

### Enchantment choices and conditions

- **Loyalty I–III:** after an entity impact or several ticks embedded in a block, the Trident turns back toward its owner. Higher levels increase return acceleration. It needs a living owner who is not a spectator; an eligible recoverable Trident drops as an item if that return owner becomes invalid. Loyalty is not an instant inventory transfer or protection against every way a projectile can be lost. [Loyalty][loyalty] · [Return, pickup, and despawn handling][thrown]
- **Impaling I–V:** adds **2.5 damage per level** against types in the bundled aquatic tag. That includes fish, Guardians, Squids, Dolphins, Turtles, Axolotls, Tadpoles, and both Nautilus types. **Drowned is absent**; simply being wet or underwater does not satisfy this enchantment's target test. [Impaling condition][impaling] · [Target-tag link][impaling-tag] · [Aquatic members][aquatic]
- **Channeling I:** a successful thrown-Trident hit can summon lightning during a **thunderstorm** when the struck entity can see the sky. Its block-hit branch needs a sky-exposed **Lightning Rod** during a thunderstorm. Ordinary melee hits and rain without thunder do not satisfy those conditions. [Channeling][channeling] · [Entity/block effect dispatch][thrown]

Riptide excludes **Loyalty and Channeling** in normal enchantment combinations; Loyalty and Channeling can be paired with each other. Impaling can accompany either approach. [Riptide exclusions][riptide-exclusive] · [Riptide definition][riptide] · [Impaling definition][impaling]

### Durability and repair

A Trident has **250 durability**. Ordinary successful melee hits cost **1 durability**. A completed throw or Riptide release costs **1** before modifiers; a later successful Riptide contact attack also goes through ordinary weapon-hit wear. Mining a nonzero-destroy-time block with it costs **2**. [Registration][registration] · [Release wear][trident] · [Weapon wear][stack] · [Contact dispatch][player] · [Mining wear][item]

The registered Trident has **no repair ingredient**. Use another Trident on an [Anvil](../mechanics/AnvilMechanics.md), or the matching-item methods in [Durability and repair](../mechanics/Durability.md#choose-a-repair-method). Those methods handle enchantments differently. **Mending** can repair a damaged Trident held in either hand using collected XP, including an ordinary retained broken stack; use [Experience and Mending](../mechanics/Experience.md) for selection among damaged equipment. [Registration][registration] · [Repair eligibility][stack] · [Anvil matching-item repair][anvil] · [Mending definition][mending] · [Eligible durability items][durability-tag] · [XP repair][xp] · [Equipment selection][enchantment-helper]

### The last throw and broken Tridents

MattMC keeps fully worn equipment as broken stacks. An already-broken Trident cannot begin a throw or Riptide, and ordinary equipment updates remove its weapon attributes. [Broken-stack handling][stack] · [Use guard][trident] · [Attribute guard][living]

**One durability point remaining still permits a final release.** The release checks the broken state first, applies wear, and then creates the projectile or starts Riptide. A final throw can therefore carry a **broken Trident**, which remains recoverable through the normal pickup path. Its projectile still starts from the 8-point base hit, but the broken-stack guards suppress Impaling's damage bonus and Channeling's hit effects. Loyalty's return lookup has no equivalent broken-stack guard. These distinctions are source-reviewed and have not been tested in-game. [Release order][trident] · [Projectile and return code][thrown] · [Enchantment guards][enchantment-helper]

## Notes

- [Time, weather, and sleep](../mechanics/TimeWeatherAndSleep.md#rain-thunder-and-the-place-you-stand) explains rain, thunder, and local precipitation checks
- [Combat](../mechanics/Combat.md), [Drowned](../mobs/Drowned.md), and [Vault](../blocks/Vault.md) cover shared fighting and acquisition details
- [Mace](Mace.md), [Wind Charge](WindCharge.md), [Enchanting](../enchanting/Enchanting.md), and [Items](Items.md) cover related choices

Source-reviewed on **2026-10-02** at `c1adfb58c73bd6918cde87943be31afef6a2ccf4`. No in-game loot, damage, pickup, return, weather, Riptide, or repair test was run. Counts and conditions describe the bundled definitions. These are selected verified loot routes, not a claim that every scripted equipment source has been cataloged. Data packs, custom components, attributes, and target behavior can change the result.

Lodged-Trident despawning and its Loyalty exception were additionally source-reviewed on **2026-10-10** at `1b9b103398fd70d5b5152b93a1d0abc581fffc19`. No in-game despawn or return test was run.

[registration]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/Items.java#L2360-L2370
[trident]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/TridentItem.java
[drowned]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/Drowned.java
[mob-drops]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/Mob.java#L814-L839
[drop-chance]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/DropChances.java
[looting]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/enchantment/looting.json
[vault-template]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/structure/trial_chambers/reward/vault.nbt
[vault-use]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java#L246-L334
[vault-reward]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward.json
[vault-unique]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_unique.json
[loot-weight]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java
[arrow]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java
[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java
[creative]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[player]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/player/Player.java
[attributes]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java
[thrown]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/projectile/ThrownTrident.java
[stack]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/ItemStack.java
[riptide]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/enchantment/riptide.json
[living]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/LivingEntity.java
[loyalty]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/enchantment/loyalty.json
[impaling]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/enchantment/impaling.json
[impaling-tag]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/entity_type/sensitive_to_impaling.json
[aquatic]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/entity_type/aquatic.json
[channeling]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/enchantment/channeling.json
[riptide-exclusive]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/enchantment/exclusive_set/riptide.json
[item]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/Item.java
[anvil]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/inventory/AnvilMenu.java
[mending]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/enchantment/mending.json
[durability-tag]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/item/enchantable/durability.json
[xp]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/ExperienceOrb.java
[enchantment-helper]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java

[grounded-timer]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L190-L199
[despawn-counter]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L301-L338
[loyalty-despawn]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/projectile/ThrownTrident.java#L202-L208

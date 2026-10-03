# Instant effects

**Instant Health and Instant Damage change health when delivered.** Healing potions normally restore health; Harming potions normally cause magic damage. A specific entity-type tag reverses those results for some mobs, and drinking, splashing, clouds, and arrows deliver them differently. Check the recipient and the bottle form before using either around animals or allies. [Registered effects][registered] · [Healing and damage rules][instant-math]

The values below cover the ordinary **I and II potion types**, whose stored amplifiers are 0 and 1. Health points are half-hearts. Damage values are amounts requested before applicable damage handling, not guaranteed health lost. [Potion definitions][potions] · [Calculation][instant-math]

| Effect and recipient | Level I, full strength | Level II, full strength |
| --- | ---: | ---: |
| Instant Health, ordinary recipient | Heal 4 points (2 hearts) | Heal 8 points (4 hearts) |
| Instant Damage, ordinary recipient | Request 6 damage (3 hearts) | Request 12 damage (6 hearts) |
| Instant Health, inverted recipient | Request 6 damage (3 hearts) | Request 12 damage (6 hearts) |
| Instant Damage, inverted recipient | Heal 4 points (2 hearts) | Heal 8 points (4 hearts) |

## Instant Health

Effect ID: `minecraft:instant_health`. The ordinary potion names are **Healing** and **Healing II**.

Use Healing to restore missing ordinary health. The healing path has no food-bar requirement, caps health at the recipient's current maximum, and only heals while current health is above zero. It cannot revive a dead target, increase maximum health, or refill [Absorption](CombatEffects.md#absorption). A normal drinkable potion has a consumption component without a food component, so a full hunger bar does not prevent drinking it. [Healing implementation][heal-cap] · [Potion registration][potion-item] · [Consumption eligibility][potion-food]

Drinking applies the instant calculation once at full strength when consumption finishes. The item dispatches its potion-content listener, which calls the server's instant-effect method directly; it does not leave an ordinary Healing timer to keep restoring health. [Consumption callback][drink-listener] · [Potion listener][drink-call] · [Direct application][drink]

For [Skeleton Horses](../mobs/SkeletonHorse.md), [Zombie Horses](../mobs/ZombieHorse.md), and other recipients in the inversion list below, Healing causes damage instead. Use the recipient's actual tag membership, not the beneficial label on the effect. [Inversion lookup][inversion] · [Skeleton types][skeletons] · [Zombie types][zombies]

## Instant Damage

Effect ID: `minecraft:instant_damage`. The ordinary potion names are **Harming** and **Harming II**.

Against an ordinary recipient, the effect requests magic or indirect-magic damage. Both damage types bypass ordinary armor reduction and the normal shield’s blocking in the bundled damage tags. They remain eligible for [Resistance](CombatEffects.md#resistance), applicable protection enchantments, and [Absorption](CombatEffects.md#absorption); the player path processes those protections before subtracting remaining damage from health. See [Armor](../mechanics/Armor.md) for equipment protection. [Damage type selection][instant-math] · [Armor bypass][armor-tag] · [Shield bypass][shield-tag] · [Normal shield configuration][shield-item] · [Blocking consumer][shield-consumer] · [Reduction order][magic-reduction] · [Player health damage][player-damage]

Harming is not guaranteed damage. Invulnerability, death state, recent-hit handling, and species-specific damage rules can reject or reduce a hit. The bundled magic types are not in the Fire damage tag, so [Fire Resistance](WaterAndFireEffects.md#fire-resistance) does not protect against this effect. They also are not listed in the bundled effect, Resistance, or enchantment bypass tags. [Initial gates][damage-gates] · [Recent-hit gate][cooldown] · [Fire types][fire-tag] · [Effect bypasses][effects-tag] · [Resistance bypasses][resistance-tag] · [Enchantment bypasses][enchantment-tag]

Against players, magic attributed to a living non-player source, such as a Witch or Dragon, also follows difficulty scaling before the shared damage handler. Player-owned potion damage and the unattributed magic from the arrow’s status tick do not meet that same non-player-source condition. This is another reason the table is a base effect amount rather than a promised health loss. [Magic types][difficulty-magic] · [Indirect magic type][difficulty-types] · [Source condition][difficulty-source] · [Player difficulty handling][difficulty-player] · [Arrow effect damage][instant-math]

On an inverted recipient, Harming instead takes the same capped healing path described above. This is useful when caring for the listed undead horses, but does not guarantee that every delivery form reaches every mob. [Reversed calculation][instant-math] · [Health cap][heal-cap]

## Which mobs reverse healing and harm?

The checked `inverted_healing_and_harm` tag includes `undead`, which resolves through the following bundled entries:

- Skeleton, Stray, Wither Skeleton, Skeleton Horse, and Bogged
- Zombie, Zombie Villager, Zombie Horse, Zombified Piglin, Zoglin, Drowned, Husk, and Zombie Nautilus
- Wither and Phantom

These are **15 distinct registered IDs**; Zombie Nautilus appears both directly and through the zombie group, which does not make its effect happen twice. The active check asks whether the entity's type belongs to the tag. It does not inspect appearance, a mob's name, or an imported mod's description. Do not assume an imported creature reverses these effects because it looks undead. Data packs that change these tags can change the result. [Active check][inversion] · [Inversion tag][inversion-tag] · [Undead tag][undead] · [Skeleton group][skeletons] · [Zombie group][zombies]

## Drinking, splashing, clouds, and arrows

- **Drinkable:** one full-strength application to the drinker when consumption finishes. It can harm the drinker if the potion or recipient makes that the damage branch. [Drink delivery][drink]
- **Splash:** one application on impact to each eligible nearby recipient. Strength falls with the gap between the impact potion box and the recipient's expanded bounding box: the multiplier is `1 − gap / 4`, only while that gap is below 4 blocks and the recipient is in the initial search box. Overlapping boxes produce full strength. The method rounds the scaled healing/damage amount to a whole point, so a weak edge splash can produce zero. These are source geometry rules, not a tested player-to-player distance guarantee. [Impact dispatch][thrown-dispatch] · [Splash geometry][splash] · [Rounding][instant-math]
- **Lingering:** each eligible cloud application uses half strength. For ordinary recipients, Healing I/II therefore requests 2/4 healing points, and Harming I/II requests 3/6 damage points per application. The thrown potion creates a cloud with a 10-tick initial wait; the cloud checks recipients every 5 ticks and normally allows the same recipient again after 20 ticks. Its radius also shrinks with time and by half a block after each accepted recipient, so leaving and re-entering does not promise a fixed number of doses. The accepted contact uses its cooldown and radius even if a full-health target gains nothing or a damage gate prevents harm. Repeated healing or harm comes from fresh cloud applications. [Cloud creation][lingering-create] · [Reapplication default][cloud-defaults] · [Active cloud checks][cloud-apply]
- **Tipped arrow:** the ordinary arrow hit happens first. After a successful hit reaches the effect callback, it adds the potion effect as an active instance. The ordinary one-tick instant effect survives duration scaling, executes on its next status-effect tick at full effect strength, and is removed when that tick expires. It does not inherit the cloud's half-strength multiplier. [Physical-hit ordering][arrow-hit] · [Arrow effect callback][arrow-effect] · [Minimum scaled duration][duration-scale] · [Instant tick condition][instant-tick] · [Effect tick][effect-tick] · [Removal][effect-remove]

**Do not use a Healing arrow as a harmless substitute for a Healing splash.** It can wound or kill before healing, and healing cannot revive a target killed by the impact. Similarly, a Harming arrow is not reliably “arrow damage plus 6/12”: during the recent-hit window, the damage handler rejects an incoming amount no larger than the previous hit, or applies only the larger hit's excess before later reductions. The physical hit always comes first; the recent-hit damage gate applies whenever the potion effect takes the damage branch. [Arrow order][arrow-hit] · [Health gate][heal-cap] · [Damage comparison][cooldown]

Delivery gates also differ. Splashes check potion susceptibility; clouds additionally require a recipient accepted by at least one of their effects. Arrows use the ordinary effect-admission method. For example, Armor Stands reject potion susceptibility, while the Wither rejects added effect instances but its separate `canBeAffected` override explicitly rejects Wither status and otherwise uses inherited checks. A splash/cloud instant call therefore does not share the arrow's blanket effect-instance rejection; the Wither's own damage gates still apply if that call hurts it. Avoid treating “rejects status effects” as proof that all instant-potion delivery is identical. [Splash gate][splash] · [Cloud gates][cloud-apply] · [Effect admission][effect-gate] · [Armor Stand][armor-stand] · [Wither instance rejection][wither-effect] · [Wither effect check][wither-allowed] · [Wither damage rules][wither-damage]

## Brewing and obtaining supplies

Use the [Brewing guide](../brewing/Brewing.md) for bottles, fuel, and the Brewing Stand. The server initializes the following potion mixes, and the stand uses that active recipe collection. [Server initialization][brew-server] · [Stand processing][brew-stand]

| Starting potion | Ingredient | Result |
| --- | --- | --- |
| Water | Nether Wart | Awkward |
| Awkward | Glistering Melon Slice | Healing |
| Healing | Glowstone Dust | Healing II |
| Healing | Fermented Spider Eye | Harming |
| Healing II | Fermented Spider Eye | Harming II |
| Harming | Glowstone Dust | Harming II |
| Poison or extended Poison | Fermented Spider Eye | Harming |
| Poison II | Fermented Spider Eye | Harming II |

The mixes do not provide extended Healing/Harming types or Redstone upgrades for them. Adding Glistering Melon directly to Water makes Mundane, so finish the Nether Wart step first. Gunpowder converts a drinkable potion to splash; Dragon's Breath converts splash to lingering while retaining the potion type. [Instant-effect mixes][brew-mixes] · [Awkward versus Water][brew-start] · [Container conversions][brew-forms] · [Complete checked mix list][brew-all]

Craft a [Glistering Melon Slice](../items/GlisteringMelonSlice.md) with one Melon Slice surrounded by eight Gold Nuggets. The registered ingredient itself has no food/consumption component; eating it is not a Healing route. [Crafting recipe][melon-recipe] · [Item registration][melon-item]

For tipped arrows, place the appropriate **Lingering Potion in the center of a crafting grid and eight ordinary Arrows around it**. The active special recipe produces eight Tipped Arrows and copies the potion contents. A master Fletcher can also offer tipped arrows with a randomly selected brewable effect potion, so Healing and Harming types are eligible, but a particular Fletcher is not guaranteed the requested type or even that trade. The table's base exchange is five Arrows plus two Emeralds for five Tipped Arrows; actual prices follow the shared [Trading](../trading/Trading.md) rules. [Loaded recipe][arrow-recipe] · [Crafting check and output][arrow-craft] · [Fletcher pool][fletcher-table] · [Random potion selection][fletcher-choice] · [Offer selection][trade-select] · [Random offer subset][trade-random]

An adult Fletcher's [Hero of the Village](../mechanics/Raid.md) gift path also has eligible Healing and Harming Tipped Arrow entries. Each such selected entry requests 0–1 arrow, among other possible gifts; it is not a guaranteed free potion or arrow. [Installed gift behavior][gift-active] · [Profession mapping][gift-profession] · [Gift caller and hero check][gift-route] · [Eligible arrow entries][gift-list]

The inventory browser lists enabled Healing/Harming potion types in drinkable, splash, and lingering forms. Ordinary listed-item insertion is available in Creative as described in [Inventory Browser](../mechanics/InventoryBrowser.md). That access is separate from brewing, crafting, trades, and gifts. [Potion listings][browser] · [Enabled-type generation][browser-types]

## Mob sources and food distinctions

[Witches](../mobs/Witch.md) can drink Healing when injured. Their ranged attack starts with Harming as its fallback choice, then checks the other potion conditions; when healing another raider, it chooses Healing at 4 health points or below, otherwise Regeneration. The installed AI goals call this potion attack. A Witch also reduces damage tagged as Witch-resistant to 15% after its inherited magic-reduction step, and cancels damage attributed to itself. The tag includes magic and indirect magic, so do not expect an ordinary Harming hit to remove its headline 6 points. [Active goals][witch-goals] · [Self-healing choice and completion][witch-drink] · [Thrown potion choice][witch-throw] · [Defensive override][witch-defense] · [Resisted types][witch-tag]

The [Ender Dragon](../mobs/EnderDragon.md) produces Instant Damage clouds: its perched flame uses level I, while the dragon-fireball impact uses level II. Both pass through the cloud's half-strength instant application, subject to the usual eligibility and damage handling. Move out of the cloud; it can apply again after its cooldown, and Fire Resistance does not stop magic damage. [Perched cloud][dragon-flame] · [Active fireball attack][dragon-caller] · [Fireball cloud][dragon-fireball] · [Cloud application][cloud-apply]

[Golden Apples](../items/GoldenApple.md) and [Enchanted Golden Apples](../items/EnchantedGoldenApple.md) supply Regeneration and other effects through their registered consumable definitions, not Instant Health. Food-based recovery described in [Hunger](../mechanics/Hunger.md) and periodic [Regeneration](Regeneration.md) are separate healing routes. A food or animal-feeding interaction that heals directly does not thereby apply this status effect. [Apple registrations][apple-items] · [Apple effect lists][apple-effects] · [Instant-effect method][instant-math]

## Clearing and custom-effect limits

[Milk](../items/MilkBucket.md) removes active effects, but it does not reverse health already restored or damage already taken. Ordinary drinking and splashing execute their instant call directly; a continuing cloud is a separate source that can apply again. Leaving a harmful cloud matters more than looking for a lingering Harming timer to clear. [Milk definition][milk] · [Clear action][clear] · [Active-effect removal][remove-all] · [Direct drink][drink] · [Splash][splash] · [Cloud reapplication][cloud-apply]

Operator `/effect` access requires permission level 2. The command treats an instant effect's supplied duration as **ticks**, with a one-tick default when duration is omitted. A longer custom duration can run the instant tick repeatedly; it is not an ordinary stronger or longer-lasting brewed potion. This reference's I/II amounts do not promise behavior for arbitrary custom amplifiers, mixed-effect items, altered tags, or modified servers. [Command permission][command-gate] · [Instant command duration][command-duration] · [Tick condition][instant-tick] · [Duration execution][effect-tick]

## Sources and verification

Source-reviewed on 2026-10-02 at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. The active source tree matches the checked documentation baseline. The guide follows effect registration, item consumption, brewing and crafting, projectile/cloud/arrow callers, entity tags, health/damage consumers, and selected mob/trade/gift sources. It is not an exhaustive loot catalog. No in-game brewing, healing, damage, cloud, arrow, command, trade, or timing test was run. Time conversions assume 20 game ticks per second.

Related: [Status effects](Effects.md) · [Brewing](../brewing/Brewing.md) · [Combat effects](CombatEffects.md) · [Gameplay](../Gameplay.md)

[difficulty-types]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/damage_type/indirect_magic.json#L1-L5
[difficulty-magic]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/damage_type/magic.json#L1-L5
[difficulty-source]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/damagesource/DamageSource.java#L95-L100
[difficulty-player]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L721-L750
[shield-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L2256-L2275
[shield-consumer]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1270-L1306
[registered]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffects.java#L50-L51
[instant-math]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/HealOrHarmMobEffect.java#L16-L40
[potions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L53-L56
[heal-cap]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1127-L1140
[drink]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionContents.java#L154-L164
[drink-call]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionContents.java#L232-L235
[drink-listener]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumable.java#L77-L91
[potion-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1770-L1778
[potion-food]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumable.java#L95-L98
[damage-gates]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1147-L1154
[cooldown]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1181-L1195
[magic-reduction]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1789-L1825
[player-damage]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L789-L808
[armor-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json#L1-L23
[shield-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/damage_type/bypasses_shield.json#L1-L15
[resistance-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/damage_type/bypasses_resistance.json#L1-L6
[effects-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/damage_type/bypasses_effects.json#L1-L5
[enchantment-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/damage_type/bypasses_enchantments.json#L1-L5
[fire-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/damage_type/is_fire.json#L1-L11
[inversion]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1027-L1029
[inversion-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/entity_type/inverted_healing_and_harm.json#L1-L5
[undead]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/entity_type/undead.json#L1-L9
[skeletons]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/entity_type/skeletons.json#L1-L9
[zombies]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/entity_type/zombies.json#L1-L12
[thrown-dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/AbstractThrownPotion.java#L69-L84
[splash]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/ThrownSplashPotion.java#L38-L70
[lingering-create]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/ThrownLingeringPotion.java#L31-L44
[cloud-defaults]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/AreaEffectCloud.java#L39-L58
[cloud-apply]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/AreaEffectCloud.java#L191-L247
[arrow-effect]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/Arrow.java#L104-L111
[arrow-hit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L404-L449
[duration-scale]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L180-L187
[effect-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L214-L250
[effect-remove]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L824-L835
[instant-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/InstantenousMobEffect.java#L8-L15
[effect-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L982-L1012
[armor-stand]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/decoration/ArmorStand.java#L581-L584
[wither-effect]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/boss/wither/WitherBoss.java#L488-L491
[wither-damage]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/boss/wither/WitherBoss.java#L437-L465
[wither-allowed]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/boss/wither/WitherBoss.java#L540-L543
[witch-defense]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Witch.java#L194-L205
[witch-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/damage_type/witch_resistant_to.json#L1-L8
[brew-mixes]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L170-L180
[brew-start]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L230-L235
[brew-forms]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L133-L141
[brew-all]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L133-L190
[brew-server]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/MinecraftServer.java#L336-L340
[brew-stand]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L170-L183
[melon-recipe]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/glistering_melon_slice.json#L1-L17
[melon-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1793
[apple-items]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1411-L1417
[apple-effects]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L29-L45
[arrow-recipe]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/tipped_arrow.json#L1-L4
[arrow-craft]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/TippedArrowRecipe.java#L14-L46
[fletcher-table]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L270-L295
[fletcher-choice]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1527-L1538
[trade-select]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L836
[trade-random]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L232
[gift-list]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/gameplay/hero_of_the_village/fletcher_gift.json#L69-L105
[gift-profession]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/behavior/GiveGiftToHero.java#L24-L38
[gift-route]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/behavior/GiveGiftToHero.java#L103-L127
[gift-active]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java#L150-L161
[witch-goals]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Witch.java#L60-L73
[witch-drink]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Witch.java#L119-L148
[witch-throw]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Witch.java#L208-L235
[dragon-fireball]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/DragonFireball.java#L30-L59
[dragon-flame]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/boss/enderdragon/phases/DragonSittingFlamingPhase.java#L54-L89
[dragon-caller]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/boss/enderdragon/phases/DragonStrafePlayerPhase.java#L77-L87
[browser]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1756-L1770
[browser-types]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2207-L2213
[milk]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L64
[clear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/consume_effects/ClearAllStatusEffectsConsumeEffect.java#L20-L22
[remove-all]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L943-L953
[command-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L33-L48
[command-duration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L153-L176

# Swords

**MattMC has seven ordinary material swords, including Copper.** All seven have the same base attack speed and the same sword-specific block-mining rules. Their damage, durability, and repair materials differ. Use the linked item pages for exact creation recipes. [Registrations][sword-registry] · [Sword properties][sword-properties] · [Sword tag][sword-tag]

## Choose a material

These attack values are the default player's **main-hand attributes with an unbroken sword**, before enchantments or other modifiers. All seven give **1.6 attack speed**. Attack damage is in damage points; it is not a promise of that much health loss against an armored or otherwise protected target. [Player damage base][player-base] · [Attack-speed base][speed-base] · [Material bonuses][materials] · [Sword modifiers][sword-properties]

| Sword | Attack damage | Maximum durability | Anvil repair ingredient |
| --- | ---: | ---: | --- |
| [Wooden Sword](../items/WoodenSword.md) | 4 | 59 | Accepted planks |
| [Stone Sword](../items/StoneSword.md) | 5 | 131 | Cobblestone, Blackstone, or Cobbled Deepslate |
| [Copper Sword](../items/CopperSword.md) | 5 | 190 | Copper Ingot |
| [Iron Sword](../items/IronSword.md) | 6 | 250 | Iron Ingot |
| [Golden Sword](../items/GoldenSword.md) | 4 | 32 | Gold Ingot |
| [Diamond Sword](../items/DiamondSword.md) | 7 | 1,561 | Diamond |
| [Netherite Sword](../items/NetheriteSword.md) | 8 | 2,031 | Netherite Ingot |

The variant pages define the accepted ingredient tags. [Durability and repair](Durability.md) owns the repair-method formulas and data-preservation warnings, and [Armor](Armor.md) explains damage reduction.

All seven ordinary swords are category-listed and can be requested through the [inventory browser](InventoryBrowser.md) in Creative. This route is separate from crafting, upgrading and loot. [Category entries](https://github.com/HungLo2020/MattMC/blob/b153e7232bbb43920a8694afbdb0053c2e219d77/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1584-L1591)

## Attack charge and movement

Use [Combat](Combat.md#time-your-melee-attacks) for shared attack recharge, the damage curve, and [critical/sprint/sweep choices](Combat.md#choose-a-critical-sprint-hit-or-sweep). At the ordinary sword attack speed of 1.6, the nominal full-charge interval is 12.5 game ticks, with the actual attack sampling a half-tick offset. Different movement and charge states select different branches; a sword does not guarantee a critical or sweep on every hit. [Charge formula][attack-delay] · [Attack conditions][critical-sprint] · [Sweep conditions][sweep]

### What a sweep can hit

A sweep searches the main target's bounding box expanded by **1 block horizontally and 0.25 vertically**. A candidate must also be **less than 3 blocks from the player**, must not be the player or original target, must not be allied to the player, and must not be a marker Armor Stand. Nearby non-allied animals can therefore be caught by a sweep. [Selection and secondary hits][sweep]

With the default sweeping-damage ratio of zero, an otherwise unmodified full-charge sweep starts at **1 damage point**, regardless of sword material. Sweeping Edge raises that ratio: levels I, II, and III add **1/2, 2/3, and 3/4**. The secondary-hit calculation starts from `1 + ratio × ordinary damage`, then applies that target's enchantment damage calculation and attack charge. It is not the main hit's full damage copied to every target. [Default ratio][sweep-ratio] · [Sweeping Edge data][sweep-enchantment] · [Attribute propagation][modifier-stack] · [Enchantment modifiers][modifier-enchantment] · [Equipped attributes][equipment] · [Sweep calculation][sweep] · [Server enchantment callback][enchanted-server]

Swords have a zero blocking-disable value in their ordinary weapon component. Keep [Shield behavior](../items/Shield.md) and the [axe blocking-disable path](AxesAndHoes.md#material-and-combat-choices) separate from sword sweep damage. [Sword weapon component][sword-properties] · [Weapon defaults][weapon-component]

## Cobwebs and other mining uses

Sword mining does **not** use the material's ordinary pickaxe/axe mining-speed value. The seven swords share these fixed rules before player modifiers:

| Target | Sword mining rule | Correct-tool status from this rule |
| --- | --- | --- |
| Cobweb | Speed 15.0 | Yes |
| Bamboo stalk and Bamboo Shoot | Instant-mining speed override | No additional drop permission |
| Sword-efficient tag | Speed 1.5 | No additional drop permission |
| Other blocks | Baseline speed 1.0 | No matching correct-tool rule |

The efficient tag includes checked Leaves, Vine, Glow Lichen, Pumpkin/Carved Pumpkin/Jack o'Lantern, Melon, Cocoa, Big Dripleaf and its stem, and Chorus Plant/Flower. A speed override alone does not guarantee a block's drop. See [Mining](Mining.md) for the separate block and loot checks, and [Bamboo](../blocks/Bamboo.md) for that plant's collection rules. [Sword tool rules][sword-properties] · [Instant targets][instant-tag] · [Efficient targets][efficient-tag] · [Rule matching][tool] · [Player mining modifiers][player-mining]

An unbroken ordinary sword without Silk Touch gives **1 [String](../items/String.md#obtaining)** from a Cobweb. The block requires a correct tool; a broken sword fails that normal harvest gate. The Cobweb loot table also has a Silk Touch branch, but **Silk Touch's checked supported-item tag excludes swords**, so that branch is not a normal Survival sword-enchanting route. Use [Shears](../items/Shears.md) to preserve the Cobweb through its explicit loot branch. [Cobweb requirement][cobweb] · [Cobweb loot][cobweb-loot] · [Sword qualification][sword-properties] · [Broken drop guard][mine-guard] · [Silk Touch support][silk] · [Mining-loot items][mining-loot-tag] · [Table eligibility][supported-enchantment] · [Table filtering][table-filter] · [Anvil eligibility][anvil-enchantment] · [Supported-item check][can-enchant]

### Creative block breaking

An unbroken ordinary sword blocks normal Creative block destruction through its tool component. There is a checked exception: the generic stack method returns true immediately for an already-broken stack, bypassing that item-level Creative restriction. Both client and server call the stack method. This describes the inspected source path; it was not tested in-game. [Sword flag][sword-properties] · [Item restriction][creative-item] · [Broken-stack exception][creative-stack] · [Client call][creative-client] · [Server call][creative-server]

## Enchanting and upkeep

Compare [Sharpness, Smite and Bane of Arthropods](../enchanting/MeleeDamageEnchantments.md) for exact damage bonuses, tagged targets and the slowing effect.

Use [Looting, Fire Aspect and Knockback](../enchanting/MeleeUtilityEnchantments.md) to compare reward bonuses, ignition, cooked-loot conditions and the primary hit's push.

Use [Enchanting](../enchanting/Enchanting.md) for table setup and [Anvil combining](AnvilMechanics.md#combining-enchantments) for books and donors. Sweeping Edge supports the ordinary sword tag. Sharpness, Smite, and Bane of Arthropods belong to the same checked exclusion group, so the normal compatibility check does not combine them on one sword. This is selected sword guidance, not a complete enchantment catalogue. [Sword enchantment tag][enchant-sword] · [Sweeping Edge][sweep-enchantment] · [Sharpness][sharpness] · [Smite][smite] · [Bane][bane] · [Exclusion group][damage-exclusive] · [Compatibility callback][compatibility]

The normal successful-hit callback requests **1 durability** for a sword. Ordinary mining of a nonzero-hardness block requests **2 durability**; zero-hardness mining skips that wear. The ordinary weapon wear is applied once through the primary hit callback, not once for every secondary sweep target. Enchantment and infinite-material checks can change ordinary wear. [Sword wear values][sword-properties] · [Mining callback][mine-wear] · [Sweep and hit ordering][sweep] · [Primary hit callback][hit-route] · [Hit wear][mine-guard] · [Damage processing][broken]

At maximum damage the normal damage path **retains the sword as a broken stack**. Its tool-speed accessor becomes 1.0, its correct-tool check fails, and the sweep condition rejects it. Normal equipment handling removes its combat modifiers; the server's checked enchantment damage, knockback, and attacker post-hit paths also guard against broken weapons. Repair it before relying on its sword behavior. [Retention][broken] · [Speed guard][use-guard] · [Drop and hit guards][mine-guard] · [Sweep guard][sweep] · [Equipment update][equipment] · [Break callback][break-effects] · [Enchantment damage guard][enchanted-damage] · [Knockback guard][enchanted-knockback] · [Post-hit guard][enchanted-post]

### Repair and Netherite upgrade

Use each variant's accepted material for Anvil repair, or the matching-item methods explained in [Durability](Durability.md#choose-a-repair-method). The sword material assigns its repair tag through the same checked repair component as the other tools. [Repair assignment][materials] · [Repair lookup][repair-check] · [Anvil material path][anvil]

A sword with **Mending** can be selected for XP repair while held in either hand. Other damaged equipment with a matching XP-repair effect can be chosen instead. The checked orb callback subtracts damage directly, so it can repair an ordinary retained broken sword too. Mending's default effect doubles the XP amount into repair durability, capped by the item's current damage; this is separate from collecting all of that XP as player levels. [Mending definition][mending] · [Sword eligibility][durability-tag] · [Equipment selection][mending-selection] · [Damage predicate][damaged-check] · [XP repair and remaining XP][xp-repair] · [Repair amount][mending-amount] · [Enchantment iteration][mending-iterate]

The [Netherite Sword page](../items/NetheriteSword.md#obtaining) owns its exact Diamond-to-Netherite recipe. [Smithing](../smithing/Smithing.md) owns template consumption and component preservation: an upgrade carries saved damage across rather than resetting it to zero.

### Recycling and fuel

Copper, Iron, and Golden Swords can each be consumed for **1 matching metal nugget**, including retained broken inputs. The checked Furnace recipes take 200 ticks and Blast Furnace recipes 100 ticks, with 0.1 recipe experience. The ingredient checks use item identity. See [Furnace experience handling](../blocks/Furnace.md#experience-and-troubleshooting). [Smelt Copper][copper-smelting] · [Blast Copper][copper-blasting] · [Smelt Iron][iron-smelting] · [Blast Iron][iron-blasting] · [Smelt Gold][gold-smelting] · [Blast Gold][gold-blasting] · [Ingredient match][ingredient] · [Cooking match][cook-match] · [Input consumption][cook-use]

A Wooden Sword supplies **200 default furnace fuel ticks**, regardless of its remaining durability. Burning consumes the sword. Use [Furnace fuel planning](../blocks/Furnace.md#fuel-planning) for the shared rules. [Fuel lookup and default unit][fuel-default] · [Wooden tool entries][fuel-tools]

## Separately named Skelewag Sword

The [Skelewag Sword](../items/SkelewagSword.md) is separately registered as a plain `ItemSkelewagSword` with 430 durability. Its inspected class adds no sword tool, weapon, or combat-attribute implementation, and it is absent from the ordinary swords tag. The seven-material chart, sweep eligibility, and fixed mining rules above therefore should not be assigned to it by name. Its acquisition is outside this guide. [Registration][skelewag-reg] · [Class][skelewag-class] · [Sword tag][sword-tag]

Related: [Armor](Armor.md) · [Durability](Durability.md) · [Shield](../items/Shield.md) · [Enchanting](../enchanting/Enchanting.md) · [Smithing](../smithing/Smithing.md) · [Mechanics](Mechanics.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7`. No in-game combat, mining, enchanting, repair, or wear-out test was run. These claims follow active player callbacks and the checked bundled recipes, tags, and components; game rules, enchantments, data packs, and later builds can change the result.

[sword-registry]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java#L1306-L1336
[sword-properties]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ToolMaterial.java#L62-L90
[sword-tag]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/item/swords.json
[player-base]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L220-L224
[speed-base]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L13-L17
[materials]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L59
[attack-entry]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1743-L1752
[weapon-hand]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2076-L2079
[charge-tick]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L282-L289
[attack-delay]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L1723-L1731
[attack-charge]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L971-L983
[enchanted-server]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/server/level/ServerPlayer.java#L2140-L2142
[critical-sprint]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L989-L1043
[blindness]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L1919-L1920
[sweep]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L1015-L1064
[sweep-ratio]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L90-L92
[sweep-enchantment]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/enchantment/sweeping_edge.json
[modifier-stack]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L1016-L1020
[modifier-enchantment]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L393-L398
[equipment]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2668-L2683
[weapon-component]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/component/Weapon.java#L10-L25
[instant-tag]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/sword_instantly_mines.json
[efficient-tag]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/sword_efficient.json
[tool]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/component/Tool.java#L39-L84
[player-mining]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L623-L656
[cobweb]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/Blocks.java#L689-L700
[cobweb-loot]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/cobweb.json
[mine-guard]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L546-L588
[silk]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/enchantment/silk_touch.json
[mining-loot-tag]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/item/enchantable/mining_loot.json
[supported-enchantment]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L124-L129
[table-filter]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L587-L599
[anvil-enchantment]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L179-L202
[can-enchant]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L179-L180
[creative-item]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Item.java#L154-L156
[creative-stack]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L1111-L1116
[creative-client]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L115-L127
[creative-server]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L266
[enchant-sword]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/item/enchantable/sword.json
[sharpness]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/enchantment/sharpness.json
[smite]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/enchantment/smite.json
[bane]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/enchantment/bane_of_arthropods.json
[damage-exclusive]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/enchantment/exclusive_set/damage.json
[compatibility]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L160-L161
[mine-wear]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Item.java#L236-L251
[hit-route]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L1090-L1106
[broken]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[use-guard]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L379
[break-effects]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3540-L3553
[enchanted-damage]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L180-L187
[enchanted-knockback]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L202-L209
[enchanted-post]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L224-L240
[repair-check]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L1106-L1108
[anvil]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L133-L151
[mending]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/enchantment/mending.json
[durability-tag]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/item/enchantable/durability.json
[mending-selection]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L479-L497
[damaged-check]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L450
[xp-repair]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/ExperienceOrb.java#L278-L310
[mending-amount]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L336-L339
[mending-iterate]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L127-L132
[copper-smelting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/smelting/copper_nugget_from_smelting.json
[copper-blasting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/blasting/copper_nugget_from_blasting.json
[iron-smelting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/smelting/iron_nugget_from_smelting.json
[iron-blasting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/blasting/iron_nugget_from_blasting.json
[gold-smelting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/smelting/gold_nugget_from_smelting.json
[gold-blasting]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/recipe/blasting/gold_nugget_from_blasting.json
[ingredient]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/crafting/Ingredient.java#L60-L65
[cook-match]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/crafting/SingleItemRecipe.java#L33-L35
[cook-use]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L237-L265
[fuel-default]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L26-L39
[fuel-tools]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L77-L81
[skelewag-reg]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java#L2748
[skelewag-class]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/alexsmobs/item/ItemSkelewagSword.java#L7-L13

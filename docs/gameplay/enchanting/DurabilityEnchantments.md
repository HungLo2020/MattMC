# Unbreaking and Mending

**Unbreaking reduces ordinary durability wear; Mending repairs damage when you collect experience orbs.** They can coexist on supported equipment. Neither increases the item's maximum durability. Use this guide for their chances, eligibility and practical setup; [Durability and repair](../mechanics/Durability.md#choose-a-repair-method) already compares material repairs, donor items, Grindstones and crafting-grid repairs. [Unbreaking definition][unbreaking] · [Mending definition][mending] · [Wear application][wear-gates] · [XP repair][pickup]

## Unbreaking: a chance for each wear point

`minecraft:unbreaking` has ordinary levels **I–III**. When an action requests positive wear through the shared durability handler, Unbreaking rolls separately for each requested durability point and removes the points whose rolls succeed. An action requesting several points can therefore keep some wear and avoid the rest. It is not one all-or-nothing roll per attack, mined block or use. [Wear processing][wear-helper] · [Effect dispatch][wear-dispatch] · [Per-point rolls][binomial]

The bundled definition uses two rates, selected by the **item's membership in the armor enchantment tag**:

| Unbreaking level | Wear point avoided: item outside armor tag | Wear point avoided: item in armor tag |
| --- | ---: | ---: |
| I | 50% | 20% |
| II | 2/3, about 66.7% | 4/15, about 26.7% |
| III | 75% | 30% |

At level `L`, the avoided-wear chance is `L / (L + 1)` outside the armor tag, or `2L / (5L + 5)` inside it. Thus Unbreaking III still allows **25%** of ordinary requested non-armor wear points and **70%** of armor wear points through on average. These are probabilities, not a promise of a fixed number of uses or a fixed pattern of saved points. [Both conditional formulas][unbreaking] · [Fraction evaluation][fraction] · [Linear level values][linear] · [Item context][tool-context] · [Armor membership][armor-tag]

The armor branch covers the ordinary four-piece armor families and Turtle Shell helmet listed below. **Elytra and Shield are outside that tag**, so they use the non-armor rate even when equipped defensively. The branch is selected by tag membership, not by the equipment slot or whether an item looks like armor. [Armor tag][armor-tag] · [Conditional Unbreaking effects][unbreaking]

Unbreaking is evaluated after the shared handler checks damageability, the broken state and the applicable player's infinite-material ability. Already-broken stacks receive no further ordinary wear, and skipped wear is not a repair. Specialized handlers that directly change item data need their own source review; the probabilities here describe the checked shared wear path. [Damageability and wear gates][wear-gates]

## Making Mending repair the intended item

`minecraft:mending` has one ordinary level. The [Experience guide](../mechanics/Experience.md#why-mending-can-leave-the-bar-unchanged) explains repair-before-XP and the basic two-durability-per-point conversion. For controlling which item gets repaired:

1. **Hold the damaged Mending item in either hand, or wear it.** Ordinary inventory and unselected hotbar slots are not scanned; the selected hotbar stack is the main-hand candidate
2. **Remove other damaged Mending equipment if you want to target one item.** With the bundled Mending effect, each eligible damaged equipped item is a candidate for random selection; the selector does not prioritize the most damaged item. Undamaged equipment is excluded
3. **Collect experience orbs.** Existing levels are not spent automatically. Repair happens during orb pickup, and any remaining points can select another damaged item before the final remainder reaches your XP bar

[Equipment selection][selection] · [Random list selection][random-choice] · [Selected main hand][equipped-mainhand] · [Pickup and recursion][pickup]

The integer remainder matters near full repair. For example, an item missing **5 durability** can be fully repaired by a **3-point orb**, leaving **1 point** to repair another eligible item or enter the XP bar. With the ordinary effect, repair capacity is twice the available XP; the remainder calculation uses whole-number division, so an odd final durability point does not necessarily consume another whole XP point. This example describes one pickup's arithmetic, not an XP farming rate. [Repair/remainder calculation][pickup] · [Repair amount helper][repair-value] · [Mending multiplier][mending] · [Multiplication effect][multiply]

### Retained broken equipment can be repaired

A retained broken item with Mending can still be selected while held or equipped. The selector checks **damaged**, which includes a damageable item at its maximum damage, and has no separate broken-stack exclusion. Repair subtracts damage directly; the first positive repair takes the item below the broken threshold again. Merely carrying the broken item elsewhere in inventory does not make it a candidate. [Damaged versus broken][damaged-broken] · [Selection][selection] · [Repair helper][repair-value] · [Applied repair][pickup]

This does not mean every function of every custom broken item is identical. Keep the [broken-item distinctions](../mechanics/Durability.md#what-stops-working) with the existing equipment owners. If you use a [Grindstone](../blocks/Grindstone.md#removing-enchantments), its ordinary removal operation strips Unbreaking and Mending along with the other non-curse enchantments; use [Anvil mechanics](../mechanics/AnvilMechanics.md) when preserving useful enchantments is the goal. [Non-curse removal][grindstone-removal] · [Curse membership][curse-tag]

## Supported items and conflicts

Both enchantments use the same bundled `#minecraft:enchantable/durability` tag, containing **83 item types**:

| Family | Supported items |
| --- | --- |
| Ordinary tools and melee weapons | Axes, Hoes, Pickaxes, Shovels, Swords and Spears in Wood, Stone, Copper, Iron, Gold, Diamond and Netherite: 42 types |
| Ordinary armor | Helmets, Chestplates, Leggings and Boots in Leather, Copper, Chainmail, Iron, Gold, Diamond and Netherite, plus Turtle Shell helmet (`minecraft:turtle_helmet`): 29 types |
| Other equipment | Bow, Crossbow, Trident, Mace, Fishing Rod, Shears, Flint and Steel, Shield, Elytra, Brush, Carrot on a Stick and Warped Fungus on a Stick: 12 types |

This is an exact family summary of the supported tag, not every durable item in MattMC. For example, [Centipede Leggings](../items/CentipedeLeggings.md) are absent even though their material properties enable enchanting. A custom item's durability component alone does not add it to this tag. [Durability membership][durability-tag] · [Supported-item checks][support]

**Unbreaking and Mending are compatible with each other. Mending conflicts with Infinity**, including when combining books. Neither Mending nor Unbreaking declares its own exclusion list, but Infinity names an exclusion tag containing Mending, and the compatibility check tests both directions. Unbreaking has no cross-enchantment exclusion in the bundled definitions. Use [Bow enchantments](BowEnchantments.md#infinity-which-ammunition-is-saved) for Infinity's ammunition behavior and [the acquisition comparison](BowEnchantments.md#obtaining-and-combining-them) for verified Bow-enchantment routes. [Definitions][unbreaking] · [Mending][mending] · [Infinity exclusion][infinity] · [Bow exclusion members][bow-exclusion] · [Default empty exclusions][defaults] · [Symmetric compatibility][compatible]

## Obtaining and applying them

**At an Enchanting Table, Unbreaking is in the ordinary pool and Mending is absent.** Rerolling does not produce Mending. Unbreaking offers still require the item to be enchantable and unenchanted, eligible for the effect, and within a selected cost range. A plain Book can produce an Unbreaking book. Follow [Enchanting](Enchanting.md) for the existing setup and payment rules. [Table pool][table-tag] · [Pool members][non-treasure] · [Stack gate][table-gate] · [Pool caller][table-caller] · [Selection rules][table-selection]

Supported does not always mean directly table-enchantable. **Shears, Flint and Steel, Shield, Elytra, Brush and the two steering sticks lack the ordinary enchanting-enabled component in their registrations.** They can receive these supported enchantments from a compatible book at an Anvil, even though the table does not offer them directly in this setup. The other supported families use enchanting-enabled item properties. [Shears][shears-item] · [Flint and Steel][flint-item] · [Shield][shield-item] · [Elytra and steering sticks][steering-elytra] · [Brush][brush-item] · [Table gate][table-gate] · [Anvil eligibility][anvil] · [Tool properties][tool-properties] · [Armor properties][armor-properties] · [Spear properties][spear-properties] · [Bow][bow-item] · [Fishing Rod][rod-item] · [Mace][mace-item] · [Trident and Crossbow][trident-crossbow]

Two additional verified book routes are useful:

- **Ordinary Librarian stock:** selected enchanted-book offers can choose either enchantment from the tradeable pool, at Unbreaking I–III or Mending I. The normal Librarian has book listings in levels 1–4, but listings and the particular book are selected, so a profession or level does not guarantee your desired book. The offer takes Emeralds plus a Book. The [optional trade-rebalance rules](../trading/Trading.md#optional-trade-rebalance) use a different profession table; do not apply the ordinary pool to those worlds. [Librarian pools][librarian-pools] · [Active table/caller][trade-caller] · [Offer selection][offer-selection] · [Tradeable pool][tradeable] · [Book construction and price inputs][book-trade]
- **Inventory item browser:** ordinary category entries include **Unbreaking III and Mending I Enchanted Books**, usable through the [browser's Creative insertion route](../mechanics/InventoryBrowser.md). The category generator emits maximum-level books; separate search-tab-only entries for other levels are not the entries this inventory panel collects. Follow the browser owner for cursor and available-space rules. [Book category output][category-books] · [Level generation][book-generation] · [Visibility][tab-visibility] · [Browser assembly][browser-list] · [Click][browser-click] · [Capacity][browser-capacity] · [Client][browser-client] · [Server checks][browser-server] · [Insertion][browser-insert]

Apply the book to a supported item through the Anvil. Equal Unbreaking levels can combine up to III; Mending remains level I. The result still obeys compatibility and the Anvil's existing costs and data-preservation rules. These verified routes are not an exhaustive loot or trading inventory. [Anvil combination][anvil]

## Related pages

- [Experience points and Mending](../mechanics/Experience.md)
- [Durability and repair](../mechanics/Durability.md)
- [Anvil mechanics](../mechanics/AnvilMechanics.md)
- [Grindstone](../blocks/Grindstone.md)
- [Enchanting](Enchanting.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. The two definitions, recursive support tags, compatibility declarations, wear processing, active XP repair, item registrations and the listed table/trade/browser routes were checked. No in-game wear, XP pickup, repair, enchanting, inventory or trade test was run. Item components, data packs and later builds can change these rules.

[unbreaking]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/unbreaking.json
[mending]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/mending.json
[infinity]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/infinity.json
[durability-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/enchantable/durability.json
[armor-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/enchantable/armor.json
[table-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/enchantment/in_enchanting_table.json
[non-treasure]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/enchantment/non_treasure.json
[tradeable]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/enchantment/tradeable.json
[bow-exclusion]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/enchantment/exclusive_set/bow.json
[curse-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/enchantment/curse.json
[grindstone-removal]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/GrindstoneMenu.java#L180-L191
[wear-gates]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemStack.java#L457-L485
[wear-helper]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L90-L94
[wear-dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L209-L211
[filtered-effect]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L338-L350
[tool-context]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L394-L400
[binomial]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/effects/RemoveBinomial.java#L13-L24
[fraction]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/LevelBasedValue.java#L91-L108
[linear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/LevelBasedValue.java#L131-L147
[pickup]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ExperienceOrb.java#L274-L310
[selection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L479-L496
[random-choice]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/Util.java#L705-L710
[equipped-mainhand]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/PlayerEquipment.java#L14-L22
[repair-value]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L336-L340
[repair-dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L229-L230
[multiply]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/effects/MultiplyValue.java#L8-L20
[damaged-broken]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L450
[support]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L116-L130
[compatible]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L160-L180
[anvil]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L175-L223
[defaults]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L60-L70
[table-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemStack.java#L965-L971
[table-caller]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/EnchantmentMenu.java#L185-L197
[table-selection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L542-L600
[basic-tools]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1306-L1350
[tool-properties]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ToolMaterial.java#L31-L38
[armor-items]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1357-L1406
[armor-properties]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Item.java#L459-L467
[spear-properties]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L2800-L2812
[bow-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1289-L1289
[rod-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1663-L1663
[mace-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L2034-L2045
[trident-crossbow]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L2360-L2377
[steering-elytra]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1190-L1208
[flint-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1286-L1286
[shears-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1742-L1744
[shield-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L2256-L2277
[brush-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L2527-L2527
[librarian-pools]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L299-L340
[trade-caller]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L841
[offer-selection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L233
[book-trade]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1279-L1324
[category-books]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1953-L1956
[book-generation]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2216-L2234
[tab-visibility]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTab.java#L227-L239
[browser-list]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-click]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L376-L390
[browser-capacity]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L595-L630
[browser-client]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1903
[browser-insert]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2006-L2018

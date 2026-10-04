# Librarian trades

A **Librarian** uses a [Lectern](../blocks/Lectern.md#librarian-job-site) and can provide Enchanted Books, Bookshelves, Lanterns, Glass, Clocks, Compasses and Name Tags. This guide lists the possible offers and their base costs. Start with [Trading](Trading.md) for the trading screen, unlocking levels and restocking, and [Villager employment](../mobs/Villager.md#employment-and-changing-jobs) for claiming or changing a job.

## Ordinary offer pools

Use this table when **Trade Rebalance is not enabled**. Each newly unlocked trading level selects **up to two valid entries at random** from that level's pool; it does not give every row. Previously selected offers remain as the villager levels up. Level 5 has only the Name Tag entry in the ordinary pool. [Active table choice][offer-switch] · [Selection][offer-selection] · [Retained offers][offer-retention] · [Promotion][promotion]

The payment column is what **you give**; the result is what **you receive**. Quantities are **base amounts before price adjustments**. Stock means completed uses of that individual offer before replenishment, not items received: twelve uses of the Glass offer can provide 48 Glass. XP means **villager experience per completed use**, separate from the player's XP. [Ordinary listings][ordinary] · [Sales][item-sales] · [Purchases and 12-use default][item-purchases] · [Book listing][books] · [Experience award][villager-xp]

| Trading level | You give, at base price | You receive | Stock, in uses | Villager XP |
| --- | --- | --- | ---: | ---: |
| 1 | 24 Paper | 1 Emerald | 16 | 2 |
| 1 | Emeralds + 1 Book | 1 Enchanted Book | 12 | 1 |
| 1 | 9 Emeralds | 1 Bookshelf | 12 | 1 |
| 2 | 4 Books | 1 Emerald | 12 | 10 |
| 2 | Emeralds + 1 Book | 1 Enchanted Book | 12 | 5 |
| 2 | 1 Emerald | 1 Lantern | 12 | 5 |
| 3 | 5 Ink Sacs | 1 Emerald | 12 | 20 |
| 3 | Emeralds + 1 Book | 1 Enchanted Book | 12 | 10 |
| 3 | 1 Emerald | 4 Glass | 12 | 10 |
| 4 | 2 Books and Quills (raw listing; see below) | 1 Emerald | 12 | 30 |
| 4 | Emeralds + 1 Book | 1 Enchanted Book | 12 | 15 |
| 4 | 5 Emeralds | 1 Clock | 12 | 15 |
| 4 | 4 Emeralds | 1 Compass | 12 | 15 |
| 5 | 20 Emeralds | 1 Name Tag | 12 | 30 |

**Book and Quill exception:** the level-4 listing declares a base count of two, but this item normally stacks to **one**. The actual trade clamps the first input to that stack limit, so the payable amount is **one Book and Quill**, even before any reputation discount. Use a spare: this offer does not require blank pages. See [Book and Quill notes](../items/BookAndQuill.md#notes). [Stack limit][writable-stack] · [Price clamp][prices] · [Actual payment][payment] [payment-caller][] · [Item matching][book-cost]

“Book” means an ordinary [Book](../items/Book.md); “Book and Quill” is the separate [writable item](../items/BookAndQuill.md). The enchanted-book payment has **both** Emeralds and one ordinary Book. Its Emerald amount depends on the selected enchantment and level, as described below. [Ordinary inputs][ordinary] · [Book offer][books] · [One-Book cost][book-cost]

For a particular book, inspect the offer before trading. There is a book entry in each ordinary level-1–4 pool, but neither choosing the profession nor unlocking a level guarantees that entry or a particular enchantment. Removing the Lectern after the villager has earned experience does not provide an ordinary offer reset; follow [employment and changing jobs](../mobs/Villager.md#employment-and-changing-jobs). [Pool][ordinary] · [Random selection][offer-selection]

## Enchanted-book selection and price

An ordinary book entry chooses one enchantment from the bundled **`minecraft:tradeable`** tag, then chooses a level from **I through that enchantment's normal maximum, inclusive**. The tag includes all 36 `non_treasure` entries, plus **Curse of Binding, Curse of Vanishing, Frost Walker and Mending**. Mending is possible in ordinary Librarian book offers without a Swamp-type restriction; it is not guaranteed stock. Soul Speed, Swift Sneak and Wind Burst are absent from this ordinary pool. [Tradeable tag][tradeable] · [Nested non-treasure members][non-treasure] · [Selection and level limits][books] · [Definition levels][level-range] · [Inclusive random level][random-level]

The book's **enchantment level**, not the Librarian's trading level, controls the initial Emerald price. For enchantment level **L**:

1. Choose a random integer **R from 0 through 10L + 4**
2. Start with **2 + 3L + R Emeralds**
3. Double that amount if the enchantment is in **`double_trade_price`**
4. Cap the generated base price at **64 Emeralds**

The bundled double-price tag expands to `treasure`. Within the ordinary tradeable pool, the doubled entries are **Curse of Binding, Curse of Vanishing, Frost Walker and Mending**. Other treasure enchantments being in the double-price tag does not add them to Librarian stock. [Price calculation][books] · [Double-price tag][double-price] · [Nested treasure members][treasure] · [Actual ordinary pool][tradeable]

These are the possible generated base prices, before demand and player-specific adjustments:

| Enchantment level | Base Emeralds, not doubled | Base Emeralds, doubled when applicable |
| --- | ---: | ---: |
| I | 5–19 | 10–38 |
| II | 8–32 | 16–64 |
| III | 11–45 | — |
| IV | 14–58 | — |
| V | 17–64 | — |

Dashes mean no doubled enchantment in the bundled ordinary pool reaches that level; this does not imply Mending II–V or additional treasure books. Capping also means outcomes at 64 need not be as likely as other amounts. **Mending I starts at 10–38 Emeralds plus one Book** under these rules. Demand, reputation and Hero of the Village can then change the displayed Emerald amount. [Book formula][books] · [Mending's single level][mending-level] · [Price adjustments][prices] [special-prices][]

Every generated enchanted-book offer here has **12 uses**. Reopening the trading screen or restocking does not roll a fresh enchantment: the offer is retained, and restocking resets its use count and updates demand. Check [Enchanted Books](../items/EnchantedBook.md#applying-and-combining) and the relevant enchantment guide before buying for a particular item; the Anvil still checks support, compatibility and costs. [Offer retention][offer-retention] · [Restocking][restock] · [Book stock][books]

## Keeping a Librarian supplied

Keep the Librarian's **own claimed Lectern accessible** and allow it to work. The ordinary work behavior checks that job site before calling the timed restocking logic. Each completed trade consumes one use of its offer; twelve book trades exhaust that book offer until it replenishes, even if the villager still has other goods. [Work activity][work-brain] [work-package][] · [Workstation check][work] · [Use counting][use-count] [uses][]

Use [stock and restocking](Trading.md#stock-and-restocking) for the shared limits and [prices can change](Trading.md#prices-can-change) for demand and player-specific adjustments. The trade's first input is adjusted and clamped to at least one item and at most its stack limit. The ordinary Book required as a second input remains required. The base table is a planning reference; pay the amounts actually displayed. [Price consumer][prices] · [Player adjustments][special-prices]

## Optional Trade Rebalance

**This section applies only when `minecraft:trade_rebalance` is enabled with its bundled data.** That flag is absent from the default feature set. The enabled feature selects a separate Librarian table; these are implemented optional rules, not requirements for ordinary worlds. [Default flags][flags] · [Bundled feature declaration][feature-pack] · [Active table switch][offer-switch]

The non-book exchanges, stock and XP match the ordinary table, but book placement changes:

- **Levels 1–3:** the possible book entry uses the villager type's **common** pool, for **1, 5 or 10 villager XP** respectively
- **Level 4:** the pool contains Book-and-Quill sales, Clock purchases and Compass purchases; it has **no new book entry**
- **Level 5:** the pool contains the type's **special book** and the Name Tag; the special book awards **30 villager XP**

The same up-to-two-valid-entry selection applies. Both common and special books still use **Emeralds plus one Book, 12 uses, and the price formula above**. The mapping reads the villager's **stored type**, rather than looking up the biome where the trading screen is opened. The table below shows the bundled common possibilities and the configured special-book level for each type. [Replacement table][experimental] · [Common mapping][common-mapping] · [Special mapping][special-mapping] · [Stored type][stored-type] · [Book constructor][books]

| Villager type | Common book possibilities at levels 1–3 | Special book at level 5 | Pool evidence |
| --- | --- | --- | --- |
| Desert | [Fire Protection I–IV][fire_protection-level], [Thorns I–III][thorns-level], [Infinity I][infinity-level] | [Efficiency][efficiency-level] III | [Common][desert-common] · [Special][desert-special] |
| Jungle | [Feather Falling I–IV][feather_falling-level], [Projectile Protection I–IV][projectile_protection-level], [Power I–V][power-level] | [Unbreaking][unbreaking-level] II | [Common][jungle-common] · [Special][jungle-special] |
| Plains | [Punch I–II][punch-level], [Smite I–V][smite-level], [Bane of Arthropods I–V][bane_of_arthropods-level] | [Protection][protection-level] III | [Common][plains-common] · [Special][plains-special] |
| Savanna | [Knockback I–II][knockback-level], [Curse of Binding I][binding_curse-level], [Sweeping Edge I–III][sweeping_edge-level] | [Sharpness][sharpness-level] III | [Common][savanna-common] · [Special][savanna-special] |
| Snow | [Aqua Affinity I][aqua_affinity-level], [Looting I–III][looting-level], [Frost Walker I–II][frost_walker-level] | [Silk Touch][silk_touch-level] I | [Common][snow-common] · [Special][snow-special] |
| Swamp | [Depth Strider I–III][depth_strider-level], [Respiration I–III][respiration-level], [Curse of Vanishing I][vanishing_curse-level] | [Mending][mending-level] I | [Common][swamp-common] · [Special][swamp-special] |
| Taiga | [Blast Protection I–IV][blast_protection-level], [Fire Aspect I–II][fire_aspect-level], [Flame I][flame-level] | [Fortune][fortune-level] II | [Common][taiga-common] · [Special][taiga-special] |

Common-book levels use each enchantment's normal definition. The special-book mapping fixes Efficiency, Protection and Sharpness at **III**, and Unbreaking and Fortune at **II**. Silk Touch and Mending each have only level **I**. The tag membership and mapping together determine the special result; do not infer the maximum book level from the villager reaching level 5. [Common mapping][common-mapping] · [Special mapping][special-mapping] · [Level bounds][level-range] · [Book selection][books]

Mending's Swamp-type special entry in this optional table does **not** make all Librarians sell Mending or impose a Swamp requirement on ordinary offers. The common pools are also narrower than the ordinary tradeable tag. These tables describe newly generated offers in the reviewed paths; they do not claim that changing a world feature rewrites offers already stored on existing villagers. [Ordinary tag][tradeable] · [Experimental pools][experimental] · [Stored offers][offer-retention]

## Related pages

- [Trading](Trading.md): levels, stock and price changes
- [Villager](../mobs/Villager.md#professions-and-job-sites): profession and workstation ownership
- [Lectern](../blocks/Lectern.md#librarian-job-site): Librarian setup
- [Enchanted Book](../items/EnchantedBook.md): storing, applying and combining enchantments
- [Unbreaking and Mending](../enchanting/DurabilityEnchantments.md#obtaining-and-applying-them): acquisition and supported equipment
- [Movement enchantments](../enchanting/MobilityEnchantments.md#getting-the-enchantments): other routes for books absent from ordinary Librarian offers

## Sources and verification

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`. Checked the active ordinary and optional Librarian tables, constructor defaults, offer selection, retained stock, experience and price consumers, restocking caller, recursive enchantment tags, and all enchantment levels in the optional table. The price ranges are calculated from the reviewed code. No in-game trading, employment, restocking or feature-switch test was run. Server data packs and later builds can change the pools and definitions; this is not a promise about a particular world's live stock.

[ordinary]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L299-L338
[item-sales]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1217-L1243
[item-purchases]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1422-L1479
[books]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1279-L1330
[offer-switch]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L836
[offer-selection]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L233
[offer-retention]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L99-L109
[promotion]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/npc/Villager.java#L700-L708
[villager-xp]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/npc/Villager.java#L576-L588
[use-count]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L120-L124
[uses]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/trading/MerchantOffer.java#L123-L176
[writable-stack]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/Items.java#L2027-L2029
[payment]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/trading/MerchantOffer.java#L183-L203
[payment-caller]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/MerchantResultSlot.java#L49-L62
[prices]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/trading/MerchantOffer.java#L85-L116
[special-prices]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/npc/Villager.java#L467-L484
[restock]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/npc/Villager.java#L390-L443
[work]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/ai/behavior/WorkAtPoi.java#L21-L41
[work-package]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java#L74-L98
[work-brain]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/npc/Villager.java#L223-L233
[tradeable]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/tags/enchantment/tradeable.json#L1-L9
[non-treasure]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/tags/enchantment/non_treasure.json#L1-L40
[double-price]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/tags/enchantment/double_trade_price.json#L1-L5
[treasure]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/tags/enchantment/treasure.json#L1-L11
[level-range]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L140-L145
[random-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/util/Mth.java#L138-L140
[book-cost]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/trading/ItemCost.java#L32-L54
[experimental]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L831-L865
[common-mapping]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1134-L1146
[special-mapping]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1148-L1160
[stored-type]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1592-L1603
[flags]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/flag/FeatureFlags.java#L32-L42
[feature-pack]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/datapacks/trade_rebalance/pack.mcmeta#L1-L6
[desert-common]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/tags/enchantment/trades/desert_common.json#L1-L7
[fire_protection-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/fire_protection.json#L57-L57
[thorns-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/thorns.json#L44-L44
[infinity-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/infinity.json#L27-L27
[desert-special]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/tags/enchantment/trades/desert_special.json#L1-L5
[efficiency-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/efficiency.json#L23-L23
[jungle-common]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/tags/enchantment/trades/jungle_common.json#L1-L7
[feather_falling-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/feather_falling.json#L39-L39
[projectile_protection-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/projectile_protection.json#L40-L40
[power-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/power.json#L31-L31
[jungle-special]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/tags/enchantment/trades/jungle_special.json#L1-L5
[unbreaking-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/unbreaking.json#L65-L65
[plains-common]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/tags/enchantment/trades/plains_common.json#L1-L7
[punch-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/punch.json#L31-L31
[smite-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/smite.json#L32-L32
[bane_of_arthropods-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/bane_of_arthropods.json#L68-L68
[plains-special]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/tags/enchantment/trades/plains_special.json#L1-L5
[protection-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/protection.json#L36-L36
[savanna-common]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/tags/enchantment/trades/savanna_common.json#L1-L7
[knockback-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/knockback.json#L24-L24
[binding_curse-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/binding_curse.json#L13-L13
[sweeping_edge-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/sweeping_edge.json#L32-L32
[savanna-special]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/tags/enchantment/trades/savanna_special.json#L1-L5
[sharpness-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/sharpness.json#L25-L25
[snow-common]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/tags/enchantment/trades/snow_common.json#L1-L7
[aqua_affinity-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/aqua_affinity.json#L24-L24
[looting-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/looting.json#L32-L32
[frost_walker-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/frost_walker.json#L115-L115
[snow-special]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/tags/enchantment/trades/snow_special.json#L1-L5
[silk_touch-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/silk_touch.json#L21-L21
[swamp-common]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/tags/enchantment/trades/swamp_common.json#L1-L7
[depth_strider-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/depth_strider.json#L25-L25
[respiration-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/respiration.json#L24-L24
[vanishing_curse-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/vanishing_curse.json#L13-L13
[swamp-special]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/tags/enchantment/trades/swamp_special.json#L1-L5
[mending-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/mending.json#L20-L20
[taiga-common]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/tags/enchantment/trades/taiga_common.json#L1-L7
[blast_protection-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/blast_protection.json#L52-L52
[fire_aspect-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/fire_aspect.json#L32-L32
[flame-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/flame.json#L20-L20
[taiga-special]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/tags/enchantment/trades/taiga_special.json#L1-L5
[fortune-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/enchantment/fortune.json#L11-L11

# Curse of Binding and Curse of Vanishing

Read a cursed item's tooltip before equipping it. **Curse of Binding locks ordinary equipment removal; Curse of Vanishing removes affected carried equipment on death when inventory is dropped.** Neither is removed by a Grindstone. Both have only ordinary **level I**, and both can coexist on the same supported item. [Binding definition][binding] · [Vanishing definition][vanishing] · [Level bounds][eligibility] · [Compatibility][compatibility] · [Grindstone handling][grindstone]

## Which items support them?

| Curse and exact ID | Supported items in the bundled tags |
| --- | --- |
| Curse of Binding, `minecraft:binding_curse` | The 29 ordinary armor items below, Elytra, Carved Pumpkin and the seven heads/skulls below |
| Curse of Vanishing, `minecraft:vanishing_curse` | All Binding-supported items, plus the equipment, tools and Compass listed below |

The shared ordinary armor set is **Helmet, Chestplate, Leggings and Boots in Leather, Copper, Chainmail, Gold, Iron, Diamond and Netherite**, plus the **Turtle Shell helmet**. The seven heads/skulls are **Player, Creeper, Zombie, Skeleton, Wither Skeleton, Dragon and Piglin**. Binding therefore supports 38 bundled item types. [Binding's equippable tag][equippable-tag] · [Helmets][heads] · [Chestplates][chests] · [Leggings][legs] · [Boots][feet] · [Heads and skulls][skulls]

Vanishing's additional supported items are:

- Swords, Axes, Pickaxes, Shovels, Hoes and Spears in **Wood, Stone, Copper, Iron, Gold, Diamond and Netherite**
- Shield, Bow, Crossbow, Trident, Mace, Flint and Steel, Shears, Brush, Fishing Rod, Carrot on a Stick, Warped Fungus on a Stick and Compass

This resolves to **92 item types**. In particular, the tag names do not mean “every damageable item” or “everything that can be equipped”: custom equipment, animal armor, Recovery Compass and arbitrary integrated tools are not added by those descriptions alone. [Vanishing tag][vanishing-tag] · [Nested durability tag][durability-tag] · [Swords][swords] · [Axes][axes] · [Pickaxes][pickaxes] · [Shovels][shovels] · [Hoes][hoes] · [Spears][spears]

## Binding: check before you put it on

Binding's definition declares armor slots, and its ordinary player restriction is enforced when an item with the applied `prevent_armor_change` effect is already in a guarded armor slot. The player inventory creates those guarded slots for Head, Chest, Legs and Feet. Merely carrying a Binding item in ordinary inventory or holding it in a hand does not lock that storage slot. [Definition][binding] · [Player armor slots][armor-menu] · [Pickup check][armor-pickup]

Once equipped in Survival or Adventure:

- Ordinary pickup, shift-click removal and hotbar swapping cannot take the cursed piece out
- Using another wearable item cannot swap it out
- Dropping it directly from the armor slot still uses the guarded removal path
- A Dispenser cannot replace it, because automatic equipment insertion requires the destination slot to be empty

These checks are based on the applied curse, not who made the item or who equipped it. **Creative players are explicitly exempt** from the menu pickup and held-item swap restrictions. [Pickup][armor-pickup] · [Shift-click][quick-move] · [Hotbar swap][menu-swap] · [Drop path][menu-drop] · [Guarded take][safe-take] · [Held-item use][item-use] · [Equipment swap][equipment-swap] · [Dispenser insertion][dispenser] · [Empty-slot gate][dispenser-gate]

### Breaking the item does not free the slot

**Do not rely on wearing out Binding armor or Elytra to remove it in MattMC.** Reaching maximum damage retains the broken stack. Binding's pickup and swap checks still see its applied curse and have no broken-item exemption. A fully damaged piece can therefore leave you locked into nonfunctional equipment. This is a consequence of the checked retained-item and curse paths; other enchantments have their own broken-item behavior. [Retained stack][broken] · [Pickup restriction][armor-pickup] · [Swap restriction][equipment-swap] · [Effect lookup][has-effect]

### Death and other removal conditions

For an ordinary non-Spectator death with **keepInventory disabled**, inventory and equipment are emptied into death drops. **Binding alone does not prevent that drop**, so the worn slot is cleared; the dropped piece still carries Binding and will lock again if re-equipped. If that same piece also has Vanishing, it is removed before the drops are created. Death also has the usual item-recovery and experience risks described in [Death and respawn](../mechanics/DeathAndRespawn.md). [Death dispatch][death-entry] · [Curse filtering and drop gate][death-filter] · [Inventory drop][inventory-drop] · [Equipment clearing][equipment-drop]

With **keepInventory enabled**, death preserves the equipped item and its curse, and the respawn path copies that inventory. Death therefore does not release the Binding slot in that case. Creative removal is the explicit mode exception described above; this guide does not assume access to administrative item-editing commands. [Death gate][death-filter] · [Respawn copying][respawn] · [Creative exception][armor-pickup]

## Vanishing: the death-inventory check

Vanishing checks **carried inventory as well as worn gear**. With keepInventory disabled, the ordinary player death path scans the direct inventory/equipment slots and removes nonempty stacks with the applied `prevent_equipment_drop` effect before dropping everything else. Moving a cursed sword from your hand into an ordinary inventory slot does not save it. A retained broken item is still affected: the effect lookup does not check durability. [Definition][vanishing] · [Death filter][death-filter] · [Inventory slot mapping][inventory-slots] · [Slot reader][inventory-reader] · [Removal][inventory-removal] · [Applied-effect lookup][has-effect]

Vanishing does not lock equipment removal or make the item disappear just because you unequip it. Store an item you want to preserve in a chest before a dangerous trip; that chest's contents are outside this player-inventory scan. [Ender Chest storage](../blocks/EnderChest.md#player-ownership-and-persistence) has its own persistence rules. With **keepInventory enabled**, this death filter and inventory-drop step are skipped and the cursed equipment is kept. The checked death entry also skips ordinary death-loot dispatch for a Spectator. [Death filter and gate][death-filter] · [Spectator gate][death-entry] · [Respawn inventory and Ender storage][respawn]

An ordinary **Enchanted Book storing Vanishing does not itself vanish from this check**. The death test reads applied enchantments; the book keeps its curse in the separate stored-enchantments component. Applying that book to supported equipment changes which component supplies the active effect. This distinction is about ordinary book data, not command-modified books carrying an applied curse. [Applied iterator][iterator] · [Effect lookup][has-effect] · [Book registration][book-storage] · [Anvil component selection][craft-components]

For mobs, the checked ordinary equipment-drop branch also rejects equipment with the applied Vanishing effect, even before its player-credit/preserved-drop and random-chance conditions. This does not mean that unrelated items in the mob's loot table are erased. [Mob equipment-drop filter][mob-drop]

## Obtaining and applying curses

Both curses are treasure enchantments and are **absent from the ordinary Enchanting Table pool**, including when enchanting plain Books. Repeated table rerolls cannot produce them in the bundled setup. [Treasure tag][treasure] · [Table tag][table-tag] · [Non-treasure pool][non-treasure] · [Active table selection][table-pool]

A selected Survival route is a **standard Librarian's Enchanted Book trade**. With the experimental trade-rebalance feature disabled, Novice through Expert offer lists can choose either curse from the tradeable pool. Offers are random, and a specific villager need not sell either curse. The trade takes emeralds and one Book; inspect the displayed price. The experimental feature uses a different offer branch and is not covered by this standard-pool claim. [Tradeable pool][librarian-pool] · [Librarian offers][librarian-offers] · [Book creation and payment][librarian-book] · [Feature-dependent dispatcher][active-trades] · [Offer generation][offer-call]

Use an [Anvil](../mechanics/AnvilMechanics.md) to apply a curse book to a supported item, or transfer it while combining matching damageable equipment. A book holding several enchantments can bring a supported curse along with the useful effects, so inspect the result. Both curses stay at level I when combined. Their bundled definitions have no exclusive set, so Binding and Vanishing can coexist. [Default exclusion set][codec-defaults] · [Anvil checks and combination][anvil] · [Binding][binding] · [Vanishing][vanishing] · [Compatibility][compatibility]

## Repairing is not curse removal

- A **Grindstone** keeps both bundled curses while stripping non-curse enchantments; a book still holding a curse remains an Enchanted Book
- **Crafting-grid repair** of two matching damageable items explicitly transfers curses to the repaired result
- **Anvil repair and combining** start with the left item's enchantments and can add supported donor enchantments; repairing or renaming does not provide a curse-removal operation

These methods therefore do not provide an ordinary way to cleanse cursed equipment. Removing a Binding item from your body, when permitted, also does not remove the curse from the item. [Curse membership][curse-tag] · [Grindstone preservation][grindstone] · [Crafting repair][craft-repair] · [Anvil starting state and transfer][anvil] · [Anvil result][anvil-result]

## Related pages

- [Death and respawn](../mechanics/DeathAndRespawn.md)
- [Durability and repair](../mechanics/Durability.md)
- [Grindstone](../blocks/Grindstone.md)
- [Enchanted Book](../items/EnchantedBook.md)
- [Aqua Affinity, Respiration and Thorns](UnderwaterAndThornsEnchantments.md)
- [Enchanting](Enchanting.md)

## Sources and verification

Source-reviewed on **2026-10-04** at commit `78e8e0423084f010bb47e36132550619b37644c2`. The live registry resources and recursive eligibility tags, inventory-slot removal/swap/drop and Dispenser gates, applied-versus-stored enchantment readers, death/respawn, repair menus and standard Librarian offer generation were traced. [Effect component registration][curse-components] · [World-loading caller][world-load] · [Registry wiring][registry] · [Resource and tag loading][resource-loading]

No in-game equipping, curse-removal, Dispenser, death, respawn, trading or repair tests were run. The Librarian example is one selected acquisition route, not an exhaustive loot catalog. Data packs, modified components and later builds can alter these rules. The broken-item statements describe the checked implementation separately for each effect; they do not establish a policy for all passive enchantments.

[binding]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/enchantment/binding_curse.json
[vanishing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/enchantment/vanishing_curse.json
[eligibility]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L116-L146
[compatibility]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L160-L180
[grindstone]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/GrindstoneMenu.java#L167-L193
[equippable-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/enchantable/equippable.json
[heads]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/head_armor.json
[chests]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/chest_armor.json
[legs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/leg_armor.json
[feet]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/foot_armor.json
[skulls]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/skulls.json
[vanishing-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/enchantable/vanishing.json
[durability-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/enchantable/durability.json
[swords]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/swords.json
[axes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/axes.json
[pickaxes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/pickaxes.json
[shovels]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/shovels.json
[hoes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/hoes.json
[spears]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/spears.json
[armor-menu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/InventoryMenu.java#L48-L68
[armor-pickup]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/ArmorSlot.java#L47-L52
[quick-move]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L418-L428
[menu-swap]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L476-L516
[menu-drop]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L519-L537
[safe-take]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/Slot.java#L98-L126
[item-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L173-L181
[equipment-swap]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/equipment/Equippable.java#L127-L156
[dispenser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/dispenser/EquipmentDispenseItemBehavior.java#L20-L36
[dispenser-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3555-L3568
[broken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[has-effect]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L444-L451
[death-entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayer.java#L869-L910
[death-filter]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L587-L603
[inventory-drop]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Inventory.java#L443-L453
[equipment-drop]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityEquipment.java#L62-L68
[respawn]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1554-L1587
[inventory-slots]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Inventory.java#L35-L60
[inventory-reader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Inventory.java#L406-L435
[inventory-removal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Inventory.java#L363-L372
[iterator]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L127-L157
[book-storage]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2110-L2117
[craft-components]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L69-L87
[mob-drop]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Mob.java#L813-L839
[treasure]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/enchantment/treasure.json
[table-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/enchantment/in_enchanting_table.json
[non-treasure]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/enchantment/non_treasure.json
[table-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/EnchantmentMenu.java#L185-L197
[librarian-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/enchantment/tradeable.json
[librarian-offers]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L299-L338
[librarian-book]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1279-L1325
[active-trades]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/Villager.java#L818-L843
[offer-call]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L232
[codec-defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L60-L70
[anvil]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L119-L223
[curse-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/enchantment/curse.json
[craft-repair]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RepairItemRecipe.java#L55-L80
[anvil-result]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L238-L265
[curse-components]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/EnchantmentEffectComponents.java#L105-L120
[world-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/WorldLoader.java#L35-L44
[registry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L119-L130
[resource-loading]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L310-L334

# Flint and Steel

**Flint and Steel** (`minecraft:flint_and_steel`) is a reusable ignition tool for fires, Campfires, Candles, TNT and Creepers. It has **64 durability** and does not stack. Choose it for repeated lighting; [Fire Charges](FireCharge.md) are consumable and can also serve as Dispenser ammunition. [Registration][flint-registration] · [Durability and stack limit][durability-property]

## Obtaining

### Crafting

Combine **one [Flint](Flint.md) and one [Iron Ingot](IronIngot.md)** in any two separate crafting slots to make **one Flint and Steel**. This shapeless recipe fits the inventory's 2 × 2 grid. Taking the result consumes both ingredients. [Bundled recipe][flint-recipe] · [Active recipe loading][recipe-loader] · [Input consumption][crafting-consumption]

### Loot and Creative access

- **Ruined Portal chests** and **[Nether Fortress chests](../structures/NetherFortress.md)** can select one Flint and Steel as a loot entry. Neither guarantees the tool in each chest; those amounts describe a selected entry, not a whole chest's maximum. The portal template and fortress chest-placement code bind the relevant loot tables. [Portal loot][portal-loot] · [Portal chest template][portal-template] · [Portal selection][portal-generation] · [Portal structure set][portal-set] · [Fortress loot][fortress-loot] · [Fortress chest placement][fortress-chest] · [Fortress structure set][fortress-set]
- In a **Skyblock-generated dimension**, first completing the Obsidian-acquisition advancement can award the tool. The bonus table makes one equal-weight selection between **one Flint and Steel**, **one Diamond Pickaxe**, and **8–12 Obsidian**. It does not award all three. The active reward hook checks the current dimension's generator and adds the result to inventory, dropping an uninserted result beside the player. [Advancement condition][obsidian-advancement] · [Bonus table][skyblock-loot] · [Completion hook][award] · [Generator gate and delivery][skyblock-award]
- The Creative catalog includes this tool. Use the [inventory browser's Creative request route](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item); seeing its entry in Survival does not supply it there. [Catalog entry][creative]

## Usage

### Light a block or start a fire

Use the tool on a **dry, unlit [Campfire](../blocks/Campfires.md#lighting-and-extinguishing)**, a **dry, unlit [Candle group](../blocks/Candles.md#lighting-and-extinguishing)**, or an **unlit candle on Cake** to light that existing block. Both Campfire variants and all bundled candle colors are eligible. The whole candle group lights in one action. Waterlogged Campfires and standalone Candles cannot be lit this way. [Tool action][flint-use] · [Campfire check][campfire-light] · [Candle check][candle-light] · [Candle-cake routing][cake-light] · [Candle-cake eligibility][cake-eligible] · [Campfire tag][campfire-tag] · [Candle tag][candle-tag] · [Candle-cake tag][cake-tag]

Otherwise, use a block face with **air just outside it**. The new position must support fire or form an eligible portal. The floor below the new flame chooses its type: Soul Sand or Soul Soil makes **Soul Fire**; other accepted positions make ordinary **Fire**. Clicking an already lit or waterlogged lighting block is not a guaranteed harmless failure: the item can fall through to this adjacent-fire attempt. [Item branches][flint-use] · [Air and support check][fire-placement] · [Fire selection][fire-selection] · [Soul Fire bases][soul-tag]

The [Fire guide](../blocks/Fire.md#support-persistence-and-spread) owns support, spread, burnout and extinguishing. For an Obsidian frame, follow [Nether portal construction and activation](../blocks/NetherPortals.md#building-and-activating); this fire-activation route works only in the Overworld and Nether. Disabling `doFireTick` does not stop direct ignition. [Portal dimension check][fire-lifecycle] · [Direct use][flint-use] · [Fire-tick gate][fire-tick]

### Prime TNT or a Creeper

Use Flint and Steel **directly on placed [TNT](../blocks/TNT.md#priming-routes)** without secondary use to prime it, when `tntExplodes` allows it. Holding the secondary-use control skips TNT's block interaction and can instead place fire beside the selected face. A disabled TNT rule does not disable the separate adjacent-fire action. [TNT handler][tnt] · [Block-before-item ordering][server-block-use]

Use it on a **[Creeper](../mobs/Creeper.md#charged-and-manually-ignited-creepers)** to ignite its fuse. Retreat immediately: manual ignition keeps that fuse advancing even after the Creeper loses its target. The current entity dispatcher reaches the Creeper handler, which accepts this tool through the bundled igniter tag and rejects a fully broken stack. [Player route][player-interact] · [Mob dispatch][mob-dispatch] · [Ignition check][creeper-use] · [Igniter tag][creeper-tag] · [Fuse behavior][creeper-fuse]

### Dispenser use

Load the tool into a **[Dispenser](../blocks/DispenserAndDropper.md#facing-loading-and-activation)** facing the intended target. Its selected action tries to place fire directly in front, light an eligible Campfire or Candle there, or prime placed TNT there. A successful action requests one durability point; an unsuitable target or blocked TNT priming does not. It keeps the tool in the inventory instead of launching it. [Behavior registration and action][flint-dispense] · [Active bootstrap][bootstrap] · [Dispenser dispatch][dispense-dispatch]

## Behavior

### Wear, repair and broken tools

Ordinary successful Survival ignition requests **one durability point**. Failed ordinary placement does not request wear, and Creative players do not pay normal durability costs. Unbreaking can reduce the actual wear. The default tool is included in the supported-item tag for **Unbreaking and Mending**; see [Durability enchantments](../enchanting/DurabilityEnchantments.md) for their application and XP-repair rules. [Ignition wear][flint-use] · [Shared wear processing][wear] · [Supported tools][durability-tag] · [Unbreaking][unbreaking] · [Mending][mending]

**MattMC retains the tool when its damage reaches 64.** It becomes fully broken instead of disappearing. Ordinary held-item fire placement and block lighting stop, and Creeper ignition rejects it. Repair it before relying on those actions. Matching tools can be combined through the [repair routes](../mechanics/Durability.md#choose-a-repair-method); the default registration assigns no Iron Ingot or Flint material-repair component. [Retained broken stack and wear gate][wear] · [Item-use guard][stack-use] · [Creeper guard][creeper-use] · [Crafting repair][repair] · [Tool registration][flint-registration] · [Material-repair requirement][repairable]

Two narrower paths differ at this snapshot: **a retained broken tool can still directly prime selected TNT and perform its Dispenser ignition action**. TNT's block callback runs before the item-use broken guard; Dispenser dispatch also accepts the nonempty broken stack. Both then request wear, which already-broken stacks skip. This source-derived exception has not been tested in game and does not enable ordinary hand-held fire placement or Creeper ignition. [Packet and permission gate][packet-use] · [Selected-block dispatch][server-block-use] · [Block forwarding][block-forward] · [TNT action][tnt] · [Dispenser slot selection][dispense-slots] · [Dispenser dispatch][dispense-dispatch] · [Dispenser action][flint-dispense] · [Already-broken wear handling][wear]

## Notes

Block use still goes through server reach, protected-position and game-mode checks. Spectator does not run the ignition item route; Adventure's ordinary item-use fallback requires a matching **Can Place On** permission. A usable block can act before the held item; use a plain surface or the secondary-use control when choosing where to place fire. These permissions do not turn catalog visibility into Survival acquisition. [Server admission][packet-use] · [Game-mode and action ordering][server-block-use] · [Adventure/item guard][stack-use]

### Sources and verification

Source-reviewed on **2026-10-04** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Checked item registration, recipe loading and consumption, loot bindings, the Skyblock reward hook, player/block/entity dispatch, loaded lighting and igniter tags, Creative count/wear protection, and the separate Dispenser route. No in-game crafting, loot, ignition, durability, repair, automation, portal or visual test was run. Data packs, modified item components, permissions and game rules can change relevant behavior.

Related: [Fire Charge](FireCharge.md) · [Fire and Soul Fire](../blocks/Fire.md) · [Durability](../mechanics/Durability.md) · [Items](Items.md)

[flint-registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1286
[durability-property]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Item.java#L382-L391
[flint-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/flint_and_steel.json
[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L90
[crafting-consumption]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L82-L106
[portal-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/chests/ruined_portal.json#L54-L64
[portal-template]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/structure/ruined_portal/portal_1.nbt
[portal-generation]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/levelgen/structure/structures/RuinedPortalStructure.java#L103-L110
[portal-set]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/worldgen/structure_set/ruined_portals.json
[fortress-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/chests/nether_bridge.json#L64-L69
[fortress-chest]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/levelgen/structure/structures/NetherFortressPieces.java#L697-L712
[fortress-set]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/worldgen/structure_set/nether_complexes.json
[obsidian-advancement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/advancement/story/form_obsidian.json
[skyblock-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/skyblock_advancement/story/form_obsidian.json
[award]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/PlayerAdvancements.java#L220-L236
[skyblock-award]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/PlayerAdvancements.java#L393-L450
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1453-L1459
[flint-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/FlintAndSteelItem.java#L25-L56
[campfire-light]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CampfireBlock.java#L332-L336
[candle-light]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CandleBlock.java#L157-L161
[cake-light]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CandleCakeBlock.java#L72-L83
[cake-eligible]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CandleCakeBlock.java#L150-L152
[campfire-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/block/campfires.json
[candle-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/block/candles.json
[cake-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/block/candle_cakes.json
[fire-placement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/BaseFireBlock.java#L190-L218
[fire-selection]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/BaseFireBlock.java#L44-L50
[soul-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/block/soul_fire_base_blocks.json
[fire-lifecycle]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/BaseFireBlock.java#L156-L178
[fire-tick]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/FireBlock.java#L137-L145
[tnt]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/TntBlock.java#L86-L120
[server-block-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L339-L395
[player-interact]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/player/Player.java#L854-L870
[mob-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1073
[creeper-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/monster/Creeper.java#L209-L227
[creeper-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/creeper_igniters.json
[creeper-fuse]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/monster/Creeper.java#L125-L146
[flint-dispense]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L210-L240
[bootstrap]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/Bootstrap.java#L42-L57
[dispense-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/DispenserBlock.java#L82-L130
[wear]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[durability-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/enchantable/durability.json
[unbreaking]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/enchantment/unbreaking.json
[mending]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/enchantment/mending.json
[stack-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L369
[repair]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/RepairItemRecipe.java#L17-L88
[repairable]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ItemStack.java#L1106-L1109
[packet-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1255-L1278
[block-forward]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L787-L788
[dispense-slots]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/entity/DispenserBlockEntity.java#L34-L45

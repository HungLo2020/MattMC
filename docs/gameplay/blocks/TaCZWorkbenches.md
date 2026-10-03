# TaCZ Workbenches

Use the **TaCZ Gun Smith Table**, **TaCZ Ammo Assembly Table**, and **TaCZ Attachment Table** to turn carried materials into firearms, ammunition, and attachments. They share the controls below, but offer different recipe groups. [Block registration][registration], [menu registration][menu-types], and [screen registration][menu-screens] connect all three tables to that behavior.

## Gun Smith Table

**TaCZ Gun Smith Table** (`minecraft:gun_smith_table`) offers Pistol, Sniper, Rifle, Shotgun, SMG, Heavy Weapon, and Machine Gun recipes in the bundled set. The menu also supports a Misc group, but hides any group with no recipes. These are the table's current English category names. [Groups][groups] · [Names][tabs-english] · [Visible tabs][screen]

For a first example, **16 Iron Ingots make one [Glock 17](../items/Glock17.md)**, found under Pistol. The recipe uses the ingots in your inventory; there is no gun-shaped crafting-grid pattern inside this table. [Recipe][glock-recipe] · [Pistol grouping][glock-index]

See the [Gun Smith Table item](../items/TaCZGunSmithTable.md) for the inventory entry.

## Ammo Assembly Table

**TaCZ Ammo Assembly Table** (`minecraft:ammo_workbench`) offers Ammo, Personal Defense Cartridges, Intermediate & Full-Power Rifle Cartridges, Large-Caliber Specialized, Explosives & Projectiles, and Shotgun Shells in the bundled set. An Alternative Projectiles tab is configured too, but empty groups do not appear. [Groups][groups] · [Names][tabs-english] · [Visible tabs][screen]

For example, **10 Copper Ingots and 2 Gunpowder make 50 [9mm Bullets](../items/9mmBullet.md)** under Personal Defense Cartridges. The displayed output count is the amount per craft, not necessarily a full stack of that ammunition. [Recipe][9mm-recipe] · [Output display][display]

See the [Ammo Assembly Table item](../items/TaCZAmmoAssemblyTable.md) for the inventory entry.

## Attachment Table

**TaCZ Attachment Table** (`minecraft:attachment_workbench`) offers Scope, Muzzle, Stock, Grip, Extended Mag, and Laser groups. It creates attachment items in your inventory; this menu has no firearm input or attachment-installation slot. [Groups and inventory slots][groups] · [Menu][menu]

For example, **8 Iron Ingots make one [Light Ammo Extended Mag I](../items/LightAmmoExtendedMagI.md)**. For Full Metal Jacket Ammo, also look under **Extended Mag**. Its bundled index places this ammo modifier in that group, and this table has no separate Ammo Modifier tab. [Magazine recipe][mag-recipe] · [Full Metal Jacket grouping][fmj-index]

See the [Attachment Table item](../items/TaCZAttachmentTable.md) for the inventory entry.

## Crafting the tables

Each recipe makes **one table** at a [Crafting Table](CraftingTable.md). Arrange the ingredients as follows, reading each row from left to right:

| Table | Top row | Middle row | Bottom row |
| --- | --- | --- | --- |
| Gun Smith Table | Iron Ingot · Iron Ingot · Iron Ingot | Copper Ingot · Redstone Dust · Copper Ingot | Plank · empty · Plank |
| Ammo Assembly Table | Copper Ingot · Copper Ingot · Copper Ingot | Redstone Dust · Gunpowder · Redstone Dust | Plank · empty · Plank |
| Attachment Table | Iron Ingot · Glass · Iron Ingot | Copper Ingot · Redstone Dust · Copper Ingot | Plank · empty · Plank |

The attachment recipe uses the exact **Glass** block item, not a glass pane or an arbitrary stained-glass block. The planks use the `minecraft:planks` item tag. The bundled tag includes the standard wood planks and Bamboo Planks; it does not include every integrated wood-looking material. [Gun recipe][gun-recipe] · [Ammo recipe][ammo-recipe] · [Attachment recipe][attachment-recipe] · [Accepted planks][planks]

All three table items also appear in the Creative inventory. [Creative entries][creative]

## Placing and collecting

Place a table as a normal block. It occupies **one block position**, faces toward the player who placed it, and has a shape **14/16 of a block high**. Its only custom state is horizontal `facing`; there is no second half, waterlogged state, lit state, or processing state. [Placement and shape][block]

All three have hardness **2.5**, blast resistance **6**, and no light emission. They do **not** require a particular tool to drop their item, so ordinary Survival breaking by hand can collect them. The bundled axe mining tag does not include these tables; their wooden appearance is not evidence of an axe speed bonus. [Properties][registration] · [Default light and tool requirement][properties] · [Drop eligibility][tool] · [Axe tag][axe-tag]

Each block's loot table returns its own table item, subject to the explosion-survival condition. There is no Silk Touch requirement or alternate Fortune output in these tables. [Gun table loot][gun-loot] · [Ammo table loot][ammo-loot] · [Attachment table loot][attachment-loot]

## Using a workbench

1. Carry the recipe materials and use the placed table. An empty hand is a reliable way to invoke its menu. [Open action][block]
2. Choose a category icon across the top, then an item in the result list. Category arrows move between groups of seven; result arrows move between pages of six. Scrolling over the result list also changes its page. [Screen controls][screen]
3. Check the ingredient display. In Survival, it reads **required/available** and turns a count red when too little is available. Tag ingredients accept matching members of that tag; the single icon is a representative item, not necessarily the only acceptable material. [Counts][display] · [Matching and tag icons][matching]
4. Activate the **bottom-right craft control**. One accepted click makes one recipe batch, immediately consuming its listed materials and adding the output to your inventory. In Survival, any output that cannot fit is dropped beside you. Leave room in Creative: its inventory insertion discards an uninserted remainder instead of dropping it. [Button][screen] · [Craft transaction][transaction] · [Inventory insertion][inventory-add]

There are **no separate input, output, or fuel slots**, and no processing timer or stored workbench inventory. Keep ingredients on your player, not in a neighboring chest. The recipe search includes player equipment slots such as the offhand when they contain matching materials. Shift-click only moves items between the main inventory and hotbar; it does not bulk-craft. [Menu slots and shift-click][menu] · [Ingredient search][transaction] · [Player inventory mapping][inventory]

Recipes are listed without a recipe-book unlock or discovery check. Choosing an entry does not consume anything; crafting does. The server checks the open menu, table validity, recipe index, and available materials before completing the request. Leaving the table's interaction range or replacing the table invalidates its menu. [Recipe list and checks][menu] · [Server handler][server] · [Validity][validity]

## Current limitations

- **Creative still needs the listed materials present.** Its ingredient display shows infinity, but the craft checks still require enough matching items. A successful Creative craft leaves those ingredients unconsumed. [Display][display] · [Checks and consumption][transaction] · [Craft button][screen]
- **Some screen labels may show translation keys.** The screen requests `gui.tacz.gun_smith_table.*` labels that are absent from the bundled language resources at the reviewed snapshot. The bottom-right button is the craft control. The block and category names do have English entries. [Requested labels][display] · [Block names][english] · [Category names][tabs-english]
- **Bundled recipes are not unrestricted gun-pack support.** The table list comes from registered TaCZ item definitions and packaged recipe files, and is loaded once. A file's presence in an imported pack or an upstream wiki recipe is not enough to put it in this menu. This path does not use the ordinary data-pack recipe manager or recipe-book discovery. [Loading and accepted outputs][loading] · [Cached recipe lists][cache] · [Group filtering][groups]

## Related pages

- [TaCZ firearms](../mechanics/TaCZFirearms.md): gun controls, ammunition, reloading and attachment installation
- [All blocks](Blocks.md)
- [Workstation catalog](catalog/workstations.md)
- [Crafting Table](CraftingTable.md)
- [Gun Smith Table item](../items/TaCZGunSmithTable.md), [Ammo Assembly Table item](../items/TaCZAmmoAssemblyTable.md), and [Attachment Table item](../items/TaCZAttachmentTable.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `beb4335362d5983b867ef84d66a74ce668b6ef7d`. Registration, current callback signatures, menu and screen wiring, packaged recipe lookup, item registration, and the server transaction were inspected. This is **source review**, not an in-game crafting, placement, mining, automation, Creative, or translation test. The cited rules describe the bundled snapshot; changed item tags or a later implementation can alter the result.

[registration]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/Blocks.java#L6810-L6824
[items]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/Items.java#L2678-L2689
[english]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/assets/minecraft/lang/en_us.json#L9041-L9048
[gun-recipe]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipe/gun_smith_table.json#L1-L18
[ammo-recipe]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipe/ammo_workbench.json#L1-L18
[attachment-recipe]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipe/attachment_workbench.json#L1-L19
[planks]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/item/planks.json#L1-L16
[block]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/TaczWorkbenchBlock.java#L27-L95
[block-menus]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/TaczWorkbenchBlock.java#L98-L144
[menu]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L51-L122
[groups]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L125-L185
[tabs-english]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/assets/minecraft/lang/en_us.json#L8541-L8562
[screen]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/client/gui/screens/inventory/TaczWorkbenchScreen.java#L59-L197
[display]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/client/gui/screens/inventory/TaczWorkbenchScreen.java#L199-L273
[transaction]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L106
[loading]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L174
[cache]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L30-L39
[matching]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L193-L247
[inventory]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/entity/player/Inventory.java#L406-L435
[server]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1865-L1879
[validity]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L75-L98
[gun-loot]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/loot_table/blocks/gun_smith_table.json#L1-L21
[ammo-loot]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/loot_table/blocks/ammo_workbench.json#L1-L21
[attachment-loot]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/loot_table/blocks/attachment_workbench.json#L1-L21
[tool]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/tags/block/mineable/axe.json#L1-L59
[glock-recipe]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipes/gun/glock_17.json#L1-L15
[glock-index]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/index/guns/glock_17.json#L1-L8
[9mm-recipe]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipes/ammo/9mm.json#L1-L23
[mag-recipe]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/recipes/attachments/light_extended_mag_1.json#L1-L15
[fmj-index]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/resources/data/minecraft/index/attachments/ammo_mod_fmj.json#L1-L8
[creative]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1072-L1074
[menu-types]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/inventory/MenuType.java#L37-L39
[menu-screens]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/client/gui/screens/MenuScreens.java#L106-L108
[properties]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L992-L1008

[inventory-add]: https://github.com/HungLo2020/MattMC/blob/beb4335362d5983b867ef84d66a74ce668b6ef7d/src/main/java/net/minecraft/world/entity/player/Inventory.java#L246-L290

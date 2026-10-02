# Guster Pottery Sherd

**Guster Pottery Sherd** (`minecraft:guster_pottery_sherd`) is a crafting ingredient that gives one side of a [Decorated Pot](../blocks/DecoratedPot.md#accepted-sherds-and-current-patterns) the Guster pattern. [Registration][item] · [English name][name] · [Pattern mapping][pattern]

## Obtaining

Recover it by **shattering a Trial Chamber pot decorated with the Guster pattern**. The checked Trial Chamber decoration pool places a pot that stores one Guster Sherd and three Bricks as its face ingredients. [Trial pool][pool] · [Decorated template][template] · [Loaded decorations][load]

Use a tagged breaking tool, such as a pickaxe **without Silk Touch**, to recover those ingredients. Breaking with an empty hand or a Silk Touch tool keeps the decorated pot instead. See the [exact shattering and recovery rules](../blocks/DecoratedPot.md#breaking-keep-the-pot-or-recover-ingredients). [Break check][shatter] · [Loot branches][loot]

The sherd is also listed in the [inventory item browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item), which supplies ordinary listed items in Survival and Creative when inventory space is available. [Category entry][creative]

## Usage

Put it in one of the four ingredient positions of the [cross-shaped Decorated Pot recipe](../blocks/DecoratedPot.md#crafting-and-choosing-faces). Combine it with three Bricks or other accepted sherds; each position chooses one face. [Recipe][recipe] · [Accepted sherds][sherds]

## Behavior

Using the sherd on an already placed pot inserts it into the pot's storage when space permits; it does not change that pot's decoration. Choose patterns while crafting. [Placed-pot interaction][insert]

## Notes

- Item ID: `minecraft:guster_pottery_sherd`
- Source-reviewed at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6` on 2026-10-02. The Trial Chamber pool, stored decorations and recovery path were checked in source/data; no world-generation, brushing, shattering or crafting gameplay test was run

Related: [Decorated Pot](../blocks/DecoratedPot.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L2595
[name]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/assets/minecraft/lang/en_us.json#L4923
[pattern]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/DecoratedPotPatterns.java#L49
[pool]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/decor.json#L42-L73
[template]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/structure/trial_chambers/decor/guster_pot.nbt
[load]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/DecoratedPotBlockEntity.java#L55-L64
[shatter]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/DecoratedPotBlock.java#L173-L197
[loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/blocks/decorated_pot.json
[creative]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1916
[recipe]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/DecoratedPotRecipe.java#L15-L44
[sherds]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/decorated_pot_sherds.json#L23-L25
[insert]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/DecoratedPotBlock.java#L95-L132

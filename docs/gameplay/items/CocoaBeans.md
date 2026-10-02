# Cocoa Beans

Cocoa Beans plant the log-supported [Cocoa crop](../blocks/Cocoa.md) and provide ingredients for Brown Dye and Cookies. [Planting registration][cocoa-item] · [Dye recipe][brown-dye] · [Cookie recipe][cookies]

## Obtaining

Break Cocoa pods on Jungle timber: **ages 0–1 return one bean; mature age 2 returns three** before explosion decay. The checked table adds no Fortune or Silk Touch bonus. The [Cocoa guide](../blocks/Cocoa.md#finding-your-first-beans) traces the natural Jungle-tree source. [Drops][cocoa-loot]

## Usage

Use one bean to plant Cocoa against a horizontal side of Jungle Log, Jungle Wood, Stripped Jungle Log, or Stripped Jungle Wood. One bean crafts into **one Brown Dye**; a row of Wheat, Cocoa Beans, Wheat makes **eight Cookies**. A bean has a **65%** chance to raise a partly filled Composter by one level; the first accepted item in an empty Composter always succeeds. [Support][cocoa-support] · [Timber tag][jungle-logs] · [Dye][brown-dye] · [Cookies][cookies] · [Compost value][cocoa-compost] · [Compost roll][compost-roll]

## Behavior

The bean itself has no food component. Its crop grows without irrigation or a light threshold in the checked code, and is harvested by breaking and replanting. See [Cocoa](../blocks/Cocoa.md) for growth, Bone Meal, and harvesting controls. [Item registration][cocoa-item] · [Growth][cocoa-growth]

## Notes

- Item ID: `minecraft:cocoa_beans`; the placed block is `minecraft:cocoa`. [Registrations][cocoa-item]
- [Items](Items.md)

## Sources and verification

Source-reviewed at `85428ea17c51c31ddb67b6c1060b78f4c3a13515` on 2026-10-02. Inline source links identify the exact implementation and bundled data used here. No in-game growth timing, harvest, mob-interaction, or placement test was run. Data packs, world-generation settings, and game rules can change these results; quoted ordinary drops assume block drops are enabled.

[cocoa-item]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/item/Items.java#L1692-L1695
[brown-dye]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/recipe/crafting/brown_dye.json#L1-L12
[cookies]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/recipe/crafting/cookie.json#L1-L15
[cocoa-loot]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/loot_table/blocks/cocoa.json#L1-L35
[cocoa-support]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/CocoaBlock.java#L61-L105
[jungle-logs]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/tags/block/jungle_logs.json#L1-L8
[cocoa-compost]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L131-L136
[compost-roll]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L317
[cocoa-growth]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/CocoaBlock.java#L28-L65

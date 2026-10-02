# Cocoa

Plant [Cocoa Beans](../items/CocoaBeans.md) on the side of Jungle timber, wait for the large age-2 pod, then break it for **three beans**. Replant one to keep producing dye and Cookie ingredients. Cocoa uses a log-supported lifecycle with no irrigation or light threshold in its growth check. [Registration][cocoa-registration] · [Planting item][cocoa-item] · [Growth][cocoa-growth] · [Drops][cocoa-loot]

## Finding your first beans

Look for Cocoa attached to Jungle trees. The bundled Jungle biome selects a tree-placement feature whose tree selector can choose the Cocoa-decorated Jungle tree. That tree has a 0.2 decorator chance, followed by individual placement checks near the bottom of its trunk; **not every Jungle tree has pods**. Successful decoration can place any of Cocoa's three ages. Break a pod to obtain planting beans. [Biome][jungle-biome] · [Tree placement][jungle-placed] · [Tree selection][jungle-selector] · [Selected tree][jungle-tree-placed] · [Decorator configuration][jungle-cocoa] · [Pod placement][cocoa-decoration] · [Drops][cocoa-loot]

## Planting and support

Use a bean against a horizontal side of **Jungle Log, Jungle Wood, Stripped Jungle Log, or Stripped Jungle Wood**. Those four blocks form the checked Jungle Logs tag. Cocoa's facing points toward the supporting block, and the check does not restrict that block's log axis. Player-placed timber works; it need not remain part of a tree. Jungle Planks and other species' logs are not accepted by this tag. [Support and placement][cocoa-support] · [Accepted timber][jungle-logs]

Leave an adjacent cell for the pod and keep its supporting timber in place. Removing the support breaks the attached pod through the normal neighbor-update removal path. Cocoa needs neither Farmland nor water, and its support and growth methods add no brightness or biome test. [Support loss][cocoa-support] · [Neighbor removal][neighbor-removal] · [Growth][cocoa-growth]

## Growth and Bone Meal

Cocoa starts at **age 0**, grows through **age 1**, and is mature at **age 2**. Each eligible random tick has a **1-in-5** growth roll, advancing one age on success. Fully grown pods stop random ticking. This is a probability per random tick, not a fixed harvest interval. [Growth and age][cocoa-growth]

Each accepted [Bone Meal](../items/BoneMeal.md) use advances an immature pod **one stage** and succeeds without a further random roll. Two uses take a newly planted pod to maturity. The mature pod is no longer a valid Bone Meal target; applying more does not duplicate beans. [Cocoa Bone Meal][cocoa-bonemeal] · [Bone Meal execution][bonemeal-use]

## Harvesting

| Pod age | Ordinary drop when broken |
| --- | --- |
| 0 or 1 | **1 Cocoa Bean** |
| 2 | **3 Cocoa Beans** |

The checked table has no Fortune or Silk Touch branch. Explosion decay can reduce recovery from blasts. The registration has no correct-tool requirement, so a special harvesting tool is unnecessary. [Loot][cocoa-loot] · [Registration][cocoa-registration] · [Mining drop path][block-mining]

**Break and replant the pod.** Cocoa does not implement MattMC's mature-crop use-to-harvest or hoe-area-harvest callbacks: its class extends the ordinary horizontal block family, whose inherited interaction does not harvest. [Cocoa class][cocoa-class] · [Parent class][horizontal-block] · [Default interaction][default-use]

## Uses

- **Brown Dye:** one bean crafts into **one Brown Dye**. [Recipe][brown-dye]
- **Cookies:** a row of Wheat, Cocoa Beans, Wheat crafts into **eight Cookies**. Beans themselves have no food component in their registration. [Recipe][cookies] · [Bean registration][cocoa-item]
- **Composting:** a bean has a **65%** chance to add one level to a partly filled Composter. The first accepted compostable item in an empty Composter succeeds automatically. [Compost value][cocoa-compost] · [Compost roll][compost-roll]

## Registered forms and related pages

The placed pod is `minecraft:cocoa`; its planting and harvested item is `minecraft:cocoa_beans`. [Block registration][cocoa-registration] · [Item registration][cocoa-item]

- [Cocoa Beans](../items/CocoaBeans.md)
- [Tree logs and roots](TreeLogsAndRoots.md#jungle-timber)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `85428ea17c51c31ddb67b6c1060b78f4c3a13515` on 2026-10-02. Inline source links identify the exact implementation and bundled data used here. No in-game growth timing, harvest, mob-interaction, or placement test was run. Data packs, world-generation settings, and game rules can change these results; quoted ordinary drops assume block drops are enabled.

[cocoa-registration]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/Blocks.java#L2590-L2600
[cocoa-item]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/item/Items.java#L1692-L1695
[cocoa-growth]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/CocoaBlock.java#L28-L65
[cocoa-loot]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/loot_table/blocks/cocoa.json#L1-L35
[jungle-biome]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/worldgen/biome/jungle.json#L78-L96
[jungle-placed]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/worldgen/placed_feature/trees_jungle.json#L1-L35
[jungle-selector]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/worldgen/configured_feature/trees_jungle.json#L1-L24
[jungle-tree-placed]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/worldgen/placed_feature/jungle_tree.json#L1-L17
[jungle-cocoa]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/worldgen/configured_feature/jungle_tree.json#L1-L16
[cocoa-decoration]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/levelgen/feature/treedecorators/CocoaDecorator.java#L27-L47
[cocoa-support]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/CocoaBlock.java#L61-L105
[jungle-logs]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/tags/block/jungle_logs.json#L1-L8
[neighbor-removal]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/Block.java#L213-L227
[cocoa-bonemeal]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/CocoaBlock.java#L107-L125
[bonemeal-use]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L78
[block-mining]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/Block.java#L439-L443
[cocoa-class]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/CocoaBlock.java#L28-L131
[horizontal-block]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/HorizontalDirectionalBlock.java#L10-L29
[default-use]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L202-L210
[brown-dye]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/recipe/crafting/brown_dye.json#L1-L12
[cookies]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/recipe/crafting/cookie.json#L1-L15
[cocoa-compost]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L131-L136
[compost-roll]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L317

# Pale Moss Carpet

**Pale Moss Carpet** (`minecraft:pale_moss_carpet`) creates a low floor covering that can also dress nearby walls. Recover the **bottom floor piece** when collecting it: the upper wall decoration does not yield a second carpet item. [Item registration][items] · [Carpet behavior][carpet] · [Bottom-only loot][loot]

## Obtaining

Craft **two Pale Moss Blocks side by side → three Pale Moss Carpets**. The recipe fits the inventory crafting grid and specifically requires pale blocks; ordinary green Moss Blocks do not substitute. [Pale Moss Block](PaleMossBlock.md) covers a starter supply and renewal. [Carpet recipe][recipe]

The bottom piece of existing Pale Moss Carpet returns **one carpet** when broken in ordinary Survival, including by hand. Silk Touch and Shears are unnecessary, and Fortune does not increase that count. A hoe speeds mining. **Breaking only the upper wall piece drops nothing**, regardless of those tools. Pale moss patch vegetation is another possible source of carpet. See [registered forms and harvesting](../blocks/MossAndPaleMoss.md#registered-forms-and-harvesting) for the complete comparison. [Bottom-only loot][loot] · [Registration][block] · [Hoe tag][hoe] · [Harvest gate][gate] · [Pale vegetation][vegetation]

## Usage

Place the carpet on a **non-air block**. The floor adds side covering where neighboring block faces can support it; the full [Pale Moss Carpet guide](../blocks/MossAndPaleMoss.md#pale-moss-carpet) explains attachment and changing walls. Its floor collision is only **one-sixteenth of a block high**, and the side covering does not form a blocking wall. [Support and collision][carpet] · [Side attachment][attach]

Placement can add wall covering in the **one block immediately above** the floor piece. To fill eligible upper sides, apply **Bone Meal to the bottom piece**. The upper space must be replaceable or an existing upper Pale Moss Carpet, with supported wall faces that connect to the matching sides below. Bone Meal is accepted only when those upper sides can change; an accepted Survival use consumes one meal. This extends the wall decoration, not a field of new carpet items. [Placement and growth][carpet] · [Placement dispatch][placement] · [Bone Meal consumption][meal]

## Behavior

The upper decoration has **no collision** and relies on the bottom piece. Removing the base or its support removes the covering when the block updates. That upper piece still supplies no item, so trimming and regrowing it does not duplicate carpets. For water exposure, use the family's [water and recovery rules](../blocks/MossAndPaleMoss.md#water-and-recovery); neither piece is waterloggable. [Support updates][carpet] · [Bottom-only loot][loot]

## Notes

This item places `minecraft:pale_moss_carpet`. [Moss Carpet](MossCarpet.md) is the green floor-only covering, while [Pale Hanging Moss](PaleHangingMoss.md) is a separate ceiling plant with different tools and growth rules. The [canonical family guide](../blocks/MossAndPaleMoss.md) keeps their placed mechanics and composting details together.

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`: registrations, complete bottom-only loot and carpet recipe, mining tag, active placement dispatch, side support/collision, and bottom-piece Bone Meal callbacks. No gameplay test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L374-L378
[carpet]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/MossyCarpetBlock.java
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/pale_moss_carpet.json
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/pale_moss_carpet.json
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6825-L6841
[hoe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/hoe.json
[gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[vegetation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/pale_moss_vegetation.json
[attach]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/MultifaceBlock.java#L251-L260
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L48-L87
[meal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L81

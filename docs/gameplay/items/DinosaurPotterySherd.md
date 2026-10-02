# Dinosaur Pottery Sherd

**Dinosaur Pottery Sherd** is registered as `minecraft:dinosaur_pottery_sherd` and is an accepted Decorated Pot crafting ingredient. Its current visual behavior has a source limitation described below. [Item registration][custom-items] · [Sherd tag][sherds]

## Obtaining

It is listed in the Creative menu. This source audit did not establish a Survival archaeology loot route for this item; an archaeology source should not be assumed from its name. [Creative listing][custom-creative]

## Usage

Use it in one of the four cross-shaped ingredient positions for a [Decorated Pot](../blocks/DecoratedPot.md#crafting-and-choosing-faces). The selected face retains this sherd's item identity. You may mix it with Bricks or other accepted sherds. [Recipe][recipe] · [Stored decorations][decorations]

## Behavior

The current item-to-pattern map has no Dinosaur entry, so the inspected pot renderer uses its **plain-face fallback**. This does not turn the stored ingredient into a Brick: shattering the pot returns this sherd for that face. See the [pattern comparison](../blocks/DecoratedPot.md#accepted-sherds-and-current-patterns). [Pattern mapping][patterns] · [Material lookup][material-lookup] · [Fallback][pattern-fallback] · [Ingredient drops][shatter]

## Notes

- Item ID: `minecraft:dinosaur_pottery_sherd`
- Source-reviewed on 2026-10-02 at `8d065c943710a8d4e3d609b0406dda95ed62cc63`; no archaeology acquisition, crafting or rendered-pattern gameplay test was run

Related: [Decorated Pot](DecoratedPot.md) · [Items](Items.md)

[custom-items]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/Items.java#L2608-L2609
[sherds]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/item/decorated_pot_sherds.json
[custom-creative]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1929-L1930
[recipe]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/crafting/DecoratedPotRecipe.java#L15-L46
[decorations]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/PotDecorations.java#L25-L66
[patterns]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/DecoratedPotPatterns.java
[material-lookup]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/client/renderer/Sheets.java#L200-L203
[pattern-fallback]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/client/renderer/blockentity/DecoratedPotRenderer.java#L112-L121
[shatter]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/DecoratedPotBlock.java#L173-L197

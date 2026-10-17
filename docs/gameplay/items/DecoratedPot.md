# Decorated Pot

The **Decorated Pot item** (`minecraft:decorated_pot`) places a four-face pottery container with one storage slot. See the [Decorated Pot block guide](../blocks/DecoratedPot.md#decorated-pot) for crafting orientation, supported patterns, storage, water and breaking behavior. [Item components][item] · [Placed implementation][block]

## Obtaining

Craft one pot from **four loose Bricks and/or accepted Pottery Sherds** in a cross around an empty center in a 3 × 3 grid. The corners are also empty. The bottom ingredient chooses the front face, top chooses back, and middle-left/right choose their corresponding faces. Four Bricks make a plain pot. [Crafting logic][recipe] · [Ingredient tag][ingredients] · [Plain recipe][plain-recipe]

The current tag accepts 25 sherds. Dinosaur and Footprint are accepted and retained, but currently use a plain rendered face because their pattern mappings are absent. Review the [pattern comparison](../blocks/DecoratedPot.md#accepted-sherds-and-current-patterns) before spending those materials. [Sherd tag][sherds] · [Pattern map][patterns] · [Renderer fallback][pattern-fallback]

## Usage

Place the pot, then use a compatible held item on it to insert **one item at a time**. It holds one matching stack, with that item's stack limit. A sherd used on an existing pot goes into storage rather than repainting its face. There is no inventory menu or empty-hand withdrawal action; use a Hopper or break the pot to recover storage. [Player use][insert] · [Single slot][single-slot] · [Hopper access][hopper-find]

## Behavior

Breaking by hand drops the pot with its decorations. Breaking with a tagged sword/tool, Trident or Mace shatters it into four face ingredients unless a protecting enchantment such as Silk Touch applies. **Stored items spill separately either way**; ordinary pot loot copies decorations only. [Break selection][shatter] · [Tool tag][break-tools] · [Silk Touch tag][silk] · [Loot][loot] · [Container spill][remove-contents]

The placed pot can be waterlogged and gives a Comparator fullness reading. Those states and interactions are explained in the [block guide](../blocks/DecoratedPot.md).

## Notes

- This item places `minecraft:decorated_pot`; it is separate from the small [Flower Pot](FlowerPot.md)
- Decoration and container components exist on the item type, but ordinary breaking does not preserve its stored contents inside the dropped pot
- Source-reviewed on 2026-10-02 at `8d065c943710a8d4e3d609b0406dda95ed62cc63`; no crafting, storage, mining, water or rendering gameplay test was run

Related: [Decorated Pot block](../blocks/DecoratedPot.md) · [Brick](Brick.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/Items.java#L458-L461
[block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/DecoratedPotBlock.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/crafting/DecoratedPotRecipe.java#L15-L46
[ingredients]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/item/decorated_pot_ingredients.json
[plain-recipe]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/decorated_pot_simple.json
[sherds]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/item/decorated_pot_sherds.json
[patterns]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/DecoratedPotPatterns.java
[pattern-fallback]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/client/renderer/blockentity/DecoratedPotRenderer.java#L112-L121
[insert]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/DecoratedPotBlock.java#L95-L145
[single-slot]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/ticks/ContainerSingleItem.java#L8-L62
[hopper-find]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L375-L388
[shatter]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/DecoratedPotBlock.java#L173-L197
[break-tools]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/item/breaks_decorated_pots.json
[silk]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/enchantment/prevents_decorated_pot_shattering.json
[loot]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/blocks/decorated_pot.json
[remove-contents]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L250-L254

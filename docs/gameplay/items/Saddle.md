# Saddle

A Saddle (`minecraft:saddle`) is an equipment item that stacks to **one**. MattMC includes a bundled crafting recipe; it is not restricted to chest loot or trading.

## Crafting

Use **three Leather and one Iron Ingot**. Put one Leather in the top-center slot, with Leather–Iron Ingot–Leather in the row below. The shaped recipe produces one Saddle.

## Equipping and riding

Interact with a suitable living animal to equip its empty saddle slot. The target must be permitted by the Saddle's entity tag and pass that animal's slot rules. For example, [Pigs](../mobs/Pig.md) accept the saddle slot only while alive and adult.

The allowed-entity tag includes horses and their listed variants, Donkeys, Mules, Pigs, Striders, Camels, Nautilus, and Zombie Nautilus. This shared tag does not bypass each species' taming, age, mounting, or control rules. Consult the relevant mob guide before treating a saddle as a universal ride permission.

For a Pig, a Carrot on a Stick is still needed for control after mounting. The saddle interaction consumes one item into the animal's equipment slot.

## Removing a Saddle

Saddles are marked as shearable equipment. On an eligible, unoccupied mob, interact with usable Shears **without sneaking** to remove the equipment and drop it into the world. This costs one Shears durability before normal modifiers. A protection against equipment changes can block removal, except for Creative players.

Leash-removal interactions take priority, so shearing a leashed animal may remove its connections first. The equipment path is separate from attacking the animal; killing it is not the only source-defined recovery method.

## Related pages

- [Pig](../mobs/Pig.md)
- [Carrot on a Stick](CarrotOnAStick.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game breeding, riding, equipment, loot, or food test was run. Data packs, components, and game rules can change the described behavior.

- [Crafting recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/saddle.json)
- [Item properties](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java)
- [Saddle component and equip action](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/equipment/Equippable.java)
- [Allowed entities](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/entity_type/can_equip_saddle.json)
- [Shearing equipment and leash priority](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Entity.java)
- [Mob shearing gate and interaction order](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Mob.java)
- [Pig age and control rules](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Pig.java)

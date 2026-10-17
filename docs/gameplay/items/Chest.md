# Chest

Chest is the placeable item for the [Chest block](../blocks/Chest.md), which stores 27 slots of items, or 54 when connected as a compatible double chest. Its ID is `minecraft:chest`.

## Obtaining and use

Craft eight planks-tag items around an empty center. Place the result and interact with the block to use it. The block article owns the complete recipe, collection rules, and operating guidance.

Tags determine accepted ingredients; similarly named integrated materials are not automatically interchangeable. See the block page before assuming a custom block can substitute in this recipe.

## Other uses

Hold a Chest and interact without Sneak/Crouch with a tame, unoccupied [Donkey](../mobs/Donkey.md#adding-and-using-cargo-storage), [Mule](../mobs/Mule.md#taming-riding-and-carrying-supplies), or [Llama](../mobs/Llama.md#chest-capacity-and-carpets) that has no Chest to add cargo storage. If the animal is leashed to you, the first interaction detaches the Lead; interact again to attach the Chest. Follow the species guides for capacity, inventory access, and recovery. [Chest interaction][pack-animal-chest] · [Interaction order][pack-animal-dispatch] · [Lead removal][pack-animal-unleash]

Selected crafting uses include [Hopper](Hopper.md#obtaining), [Trapped Chest](TrappedChest.md#obtaining), [Shulker Box](ShulkerBox.md#obtaining-and-use), [Minecart with Chest](MinecartWithChest.md#obtaining), and [Oak Boat with Chest](OakBoatWithChest.md#crafting-and-controls). Their pages cover the ingredients and layouts. [Recipes: Hopper][hopper-recipe] · [Trapped Chest][trapped-chest-recipe] · [Shulker Box][shulker-box-recipe] · [Chest Minecart][chest-minecart-recipe] · [Oak Chest Boat][oak-chest-boat-recipe]

## Related pages

- [Chest: recipe and block behavior](../blocks/Chest.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. This is not a gameplay test; data packs and later builds can change recipes and tags.

- [Crafting recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/chest.json)
- [Item registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java)

The additional animal-equipping and crafting routes were source-reviewed at `9bafc14d2e2943dcfe8a37e9e81dc001386b88bb` on 2026-10-04; no in-game equipping or crafting test was run.

[pack-animal-chest]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/entity/animal/horse/AbstractChestedHorse.java#L142-L173
[hopper-recipe]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/recipe/crafting/hopper.json
[trapped-chest-recipe]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/recipe/crafting/trapped_chest.json
[shulker-box-recipe]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/recipe/crafting/shulker_box.json
[chest-minecart-recipe]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/recipe/crafting/chest_minecart.json
[oak-chest-boat-recipe]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/recipe/crafting/oak_chest_boat.json
[pack-animal-dispatch]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/entity/Mob.java#L1054-L1077
[pack-animal-unleash]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/entity/Entity.java#L2146-L2160

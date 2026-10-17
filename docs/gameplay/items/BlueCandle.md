# Blue Candle

**Blue Candle** (`minecraft:blue_candle`) places the [Blue Candle variant](../blocks/Candles.md#blue-candle). It is an ordinary BlockItem with the shared candle grouping and lighting rules. [Item binding][items] · [Registration helper][factory]

## Obtaining

Craft **1 uncolored [Candle](Candle.md) + 1 [Blue Dye](BlueDye.md) → 1 Blue Candle**, in any arrangement. This recipe requires the uncolored Candle item; it does not recolor an already dyed candle. [Exact shapeless recipe][recipe] · [All color recipes](../blocks/Candles.md#crafting-and-colors)

Ordinary mining of a standalone group, including by hand, returns **its full count of 1–4 Blue Candle items**. No special tool or Silk Touch is needed, and Fortune adds no bonus. [Exact group loot][loot] · [Collection rules](../blocks/Candles.md#mining-and-collection)

## Usage

Place it on a top face with valid center support. Use more **Blue Candle items** on that group to build up to four; other candle colors cannot join it. [Placement and grouping](../blocks/Candles.md#placement-and-grouping) · [Matching-item rule][group]

Light a dry group with [Flint and Steel](FlintAndSteel.md) or a [Fire Charge](FireCharge.md). Lit groups emit **3 light per candle, up to 12**. Use an empty hand to extinguish a standalone group. [Lighting controls](../blocks/Candles.md#lighting-and-extinguishing) · [Light formula][light] · [Empty-hand callback][empty-hand]

## Behavior

A Water Bucket can waterlog a standalone group and extinguish it. It cannot be relit while waterlogged; see the [source-Water and removal rules](../blocks/Candles.md#water-and-light), including the support check when removing Water. Collected items do not retain the group's lit or waterlogged state. [Water and ignition gate][water] · [Item-only loot][loot]

Add one to an **untouched Cake** for the matching [Blue Candle cake block](../blocks/Cake.md#blue-candle-cake). A cake holds only one candle. The first successful eating interaction returns that candle; breaking the cake also returns the candle but destroys the food. Follow the [Cake interaction and recovery rules](../blocks/Cake.md#adding-and-recovering-a-candle), especially when extinguishing without eating. [Cake insertion][cake-add] · [Eating callback][cake-eat] · [Matching candle-cake loot][cake_loot]

## Notes

The [exact color entry](../blocks/Candles.md#blue-candle) and [standalone support-loss detail](../blocks/Candles.md#placement-and-grouping) belong to the shared block guide. [Items](Items.md)

Source-reviewed at `60fc787a7e081a0d77cfea29c847ef4b3807634b` on 2026-10-02. The exact recipe, item binding, standalone/cake loot and shared active callbacks were checked. No gameplay crafting, lighting, Water, support-removal or eating test was run.

[cake-add]: https://github.com/HungLo2020/MattMC/blob/60fc787a7e081a0d77cfea29c847ef4b3807634b/src/main/java/net/minecraft/world/level/block/CakeBlock.java#L54-L69
[cake-eat]: https://github.com/HungLo2020/MattMC/blob/60fc787a7e081a0d77cfea29c847ef4b3807634b/src/main/java/net/minecraft/world/level/block/CandleCakeBlock.java#L72-L103
[cake_loot]: https://github.com/HungLo2020/MattMC/blob/60fc787a7e081a0d77cfea29c847ef4b3807634b/src/main/resources/data/minecraft/loot_table/blocks/blue_candle_cake.json
[empty-hand]: https://github.com/HungLo2020/MattMC/blob/60fc787a7e081a0d77cfea29c847ef4b3807634b/src/main/java/net/minecraft/world/level/block/CandleBlock.java#L76-L85
[factory]: https://github.com/HungLo2020/MattMC/blob/60fc787a7e081a0d77cfea29c847ef4b3807634b/src/main/java/net/minecraft/world/item/Items.java#L2743-L2781
[group]: https://github.com/HungLo2020/MattMC/blob/60fc787a7e081a0d77cfea29c847ef4b3807634b/src/main/java/net/minecraft/world/level/block/CandleBlock.java#L88-L104
[items]: https://github.com/HungLo2020/MattMC/blob/60fc787a7e081a0d77cfea29c847ef4b3807634b/src/main/java/net/minecraft/world/item/Items.java#L2498-L2514
[light]: https://github.com/HungLo2020/MattMC/blob/60fc787a7e081a0d77cfea29c847ef4b3807634b/src/main/java/net/minecraft/world/level/block/CandleBlock.java#L36-L43
[loot]: https://github.com/HungLo2020/MattMC/blob/60fc787a7e081a0d77cfea29c847ef4b3807634b/src/main/resources/data/minecraft/loot_table/blocks/blue_candle.json
[recipe]: https://github.com/HungLo2020/MattMC/blob/60fc787a7e081a0d77cfea29c847ef4b3807634b/src/main/resources/data/minecraft/recipe/crafting/blue_candle.json
[water]: https://github.com/HungLo2020/MattMC/blob/60fc787a7e081a0d77cfea29c847ef4b3807634b/src/main/java/net/minecraft/world/level/block/CandleBlock.java#L140-L160

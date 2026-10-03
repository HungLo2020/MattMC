# Clock

A **Clock** (`minecraft:clock`) shows the Overworld's day–night cycle on a sun-and-moon dial. It is useful underground or indoors because its reading comes from world time, without checking whether the item can see the sky. [Clock model][model] · [Time source][time]

## Obtaining

- **Crafting:** put **one Redstone Dust in the center** of a 3 × 3 crafting grid and **four Gold Ingots above, below, left and right**. Leave the corners empty to make **one Clock**. [Recipe][recipe]
- **Loot:** Clocks are possible finds in **shipwreck map chests** and **ruined portal chests**. They are random loot, not a guaranteed item in either chest. [Shipwreck loot][shipwreck] · [Ruined portal loot][portal]
- **Trading:** an **expert-level Librarian** may sell **one Clock for a base price of five Emeralds**. The offer appears in both the normal and experimental trade-rebalance tables; a particular villager need not select it. [Trade tables][trades] · [Offer selection][villager]
- **Creative:** the Clock is listed in the tools and utilities catalog. Use the [inventory item browser](../mechanics/InventoryBrowser.md) in Creative to request one; merely seeing it in the Survival catalog does not provide a Survival acquisition route. [Catalog][catalog] · [Protocol admission][protocol]

## Usage

Read the dial while holding the Clock, or look at its icon in your hotbar or inventory. It works passively: there is no Clock-specific use action, fuel, setting or durability cost. Normal Clocks stack to **64**. [Item registration][items] · [Default item behavior][item] · [Default stack size][components]

For a wall display, use a Clock on an empty **Item Frame** or **Glow Item Frame**; the frame holds one item. Use the filled frame normally to rotate the whole display in 45-degree steps. In MattMC, **sneak-use on a filled frame toggles the frame's visibility**. Rotation changes the display's orientation, not the time it reads. The frame's comparator signal depends on its rotation, so this is not an automatic time-of-day redstone sensor. [Frame controls and output][frame] · [Glow frame inheritance][glow-frame]

## Behavior

The bundled model reads world daytime only in **`minecraft:overworld`**. It selects among 64 dial images and follows the world's day–night cycle, not real-world wall-clock time or the current moon phase. Changes to world daytime, including a disabled daylight cycle, affect the reading. [Clock model][model] · [Time source][time] · [World-time updates][world-time]

In **the Nether, the End and other dimension IDs**, that same model uses a random time value. A sky or daylight cycle in another dimension does not make this Clock show its local time: the model specifically names the Overworld. Treat the wandering dial outside that dimension as an unusable time reading. [Dimension selection][model]

The time calculation also needs an item owner and a client world. Ordinary player inventory and held-item rendering provide the player; a framed Clock uses the frame attached to its item stack. A preview without either an owner or an attached entity can use a fixed zero reading, so an isolated catalog or preview image is not proof of the current time. [Owner and world fallback][owner] · [Inventory rendering][gui] · [Held rendering][held] · [Frame ownership][frame]

## Notes

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. The active GUI, held-item and frame paths resolve the selected Clock model before sending its geometry to the Rust renderer; the GUI cache includes the selected model's identity. This is source evidence, **not an in-game test of dial motion or visual accuracy**. Crafting, chest generation, trades and frame interactions were not run in a client. Resource packs, data packs and server settings can change the defaults described here. [GUI extraction and cache][gui-native] · [Selected model identity][model-identity] · [Held Rust submission][held-native] · [Frame rendering][frame-renderer]

Related: [Items](Items.md) · [Item Frame](ItemFrame.md) · [Glow Item Frame](GlowItemFrame.md) · [Daylight Detector](../blocks/DaylightDetector.md)

[recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/clock.json
[shipwreck]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/chests/shipwreck_map.json
[portal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/chests/ruined_portal.json
[trades]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java
[villager]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L839
[catalog]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1481-L1485
[protocol]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
[items]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1664
[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Item.java#L163-L194
[components]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[frame]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/decoration/ItemFrame.java#L269-L407
[glow-frame]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/decoration/GlowItemFrame.java
[model]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/assets/minecraft/items/clock.json
[time]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/item/properties/numeric/Time.java
[world-time]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/level/ServerLevel.java#L443-L458
[owner]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/item/properties/numeric/NeedleDirectionHelper.java#L17-L34
[gui]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/gui/GuiGraphics.java#L822-L846
[held]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/ItemInHandRenderer.java#L149-L172
[gui-native]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/vulkanic/gui/GuiFlatItemMeshCollector.java#L49-L140
[model-identity]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/item/BlockModelWrapper.java#L69-L102
[held-native]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L8511-L8570
[frame-renderer]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/ItemFrameRenderer.java#L117-L163

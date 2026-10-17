# Bell

The **Bell** item places `minecraft:bell`, a ringable village utility block. Its floor, ceiling, and wall attachment forms share this one item. See [Bell](../blocks/Bell.md) for support, ringing faces, redstone pulses, Villager response, and Raider outlines. [Item registration][bell-item] · [Placed behavior][bell]

## Obtaining

There is no bundled crafting recipe. Collect a generated village Bell, buy one through the verified smith trades, or use its Creative entry. The base trade price is **36 Emeralds for one Bell**; profession level and optional trade rebalance are detailed in [obtaining and collecting](../blocks/Bell.md#obtaining-and-collecting). [Village template][town-template] · [Trades][trades] [rebalance] · [Creative entry][creative]

A pickaxe is efficient, but this registration has no correct-tool drop gate. Ordinary hand or tool harvesting can return **one Bell**, without Silk Touch; Fortune does not increase it. [Registration][bell-reg] · [Pickaxe tag][pickaxe] · [Loot][bell-loot] · [Harvest gate][gate]

## Usage

Place it on a suitable floor, ceiling, or wall support. Use an allowed horizontal side of the Bell body, or give it a fresh redstone signal, to ring it. The mounting orientation determines which sides accept manual/projectile hits. [Mounting](../blocks/Bell.md#mounting-and-support) · [Ringing controls](../blocks/Bell.md#ringing-controls) · [Source][bell]

## Behavior

Ringing can make Villagers seek hiding places and can start the bounded Raider-resonance sequence. It does not assign a trading profession. The 60-tick entity-search refresh is not a ringing cooldown. [Villager response][reaction] · [Meeting-point registration][poi] · [Search and resonance][bell-entity]

## Notes

Source-reviewed at `8d065c943710a8d4e3d609b0406dda95ed62cc63` on 2026-10-02. No gameplay test was run. The canonical [Bell guide](../blocks/Bell.md) owns the exact ranges, timing, and limitations.

[bell-item]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/Items.java#L2445
[bell]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/BellBlock.java
[town-template]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/structure/village/taiga/town_centers/taiga_meeting_point_1.nbt
[trades]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L490-L570
[rebalance]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L920-L930
[creative]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1087-L1092
[bell-reg]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/Blocks.java#L5380-L5384
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[bell-loot]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/blocks/bell.json
[gate]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[reaction]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/ai/behavior/ReactToBell.java
[poi]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L135
[bell-entity]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/BellBlockEntity.java

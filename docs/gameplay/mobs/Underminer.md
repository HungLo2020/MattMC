# Underminer

The **Underminer** is a ghostly miner that hides when you approach and retaliates if attacked. Its ore-finding behavior needs server-provided data: **the bundled game supplies neither natural spawn entries nor accepted ore offerings**. For a dependable encounter, use its spawn egg. [Goals and hiding][underminer] · [Bundled data][data]

## Obtaining

Get an [Underminer Spawn Egg](../items/UnderminerSpawnEgg.md) through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Creative**, then use it on a block with room for the mob. It belongs to the ordinary Spawn Eggs category. [Egg registration][egg] · [Category entry][egg-category] · [Egg placement][egg-use]

There is **no verified natural biome, depth or mineshaft encounter** in this snapshot. The bundled biome and structure spawn lists, optional data packs and structure templates contain no Underminer entry. Mineshafts have no spawn override for it. Its mineshaft-wandering goal only moves an existing Underminer; it does not create one. [Bundled data][data] · [Normal mineshaft][mineshaft] · [Badlands mineshaft][mesa-mineshaft] · [Spawn-list selection][spawn-lists] · [Wandering goal][wandering]

The source includes a dark, below-sea-level spawn check, but it is **not registered** in the active spawn-placement table. The configuration's spawn weight of 50 is also unused; the separate one-roll spawn check does not add this mob to a spawn list. Darkening a tunnel or waiting for Halloween therefore does not establish a spawning route. Servers can supply their own spawn data. [Spawn checks][underminer] · [Placement registrations][placements] · [Configuration][config] · [Roll helper][roll]

## Behavior

### Hiding and light

In Survival or Adventure, approaching within the default **8-block** distance makes an idle Underminer begin hiding, unless it remembers an accepted offering. Creative and Spectator players do not trigger this check. Hiding reaches its fully hidden state after **10 ticks**, about half a second at normal tick speed; that state rejects ordinary attack interaction. Back away to let it emerge. A living combat target prevents this proximity hiding. [Hiding and attack interaction][underminer] · [Distance setting][config] · [Player filtering][nearest-player]

This is a **distance response, not a torch response**. There is no flee-light goal or sunlight-burning routine in this mob. Its body rendering uses ordinary world lighting, so do not rely on it as a light source or assume it remains fully bright underground. The body is intended to fade, but the dwarf appearance has the rendering limitation described below. [Goals][underminer] · [Lighting caller][lighting] · [Body transparency][transparency]

### Ore guidance and offerings

**No default ore, ingot, gem or food is verified as an offering.** Acceptance uses the item tag `minecraft:underminer_ores`, but that tag has no bundled definition, including in the optional packs. Ordinary ore tags do not fill it automatically. There is no taming, breeding or food-healing interaction. [Tag identity][tags] · [Bundled data][data] · [Tag membership][tag-membership] · [Interaction implementation][underminer] · [Inherited interaction][interaction]

If a server adds accepted items to that tag, the implemented sequence is:

1. **Drop an accepted item near it**, rather than right-clicking it. Pickup requires `mobGriefing` to be enabled and the dropped item's pickup delay to have expired. The offering handler consumes the **whole dropped stack**, so offer one item at a time. [Pickup caller][pickup] · [Offering handler][underminer]
2. It remembers that item for **2,000–3,199 ticks**, roughly **100–160 seconds**. During that time it stops hiding from nearby players and looks for blocks whose item is exactly the offered item. An ingot or gem will not point to ore merely because it comes from that ore. [Offering memory and block matching][underminer]
3. While holding its required pickaxe, it searches up to **16 blocks horizontally and 8 vertically** for matching blocks hidden behind another block. It selects a nearby obstruction within two blocks of the ore, approaches it and makes a limited-duration mining attempt. **You must break the blocks yourself**: its mining action makes hit sounds and updates animation state, but does not destroy blocks or create ore drops. Searches include a random start check, so guidance is not immediate or guaranteed. [Search and mining goal][underminer]

This is guidance, not bartering: it does not pay out a reward. Breaking the indicated obstruction calls an advancement hook, but that hook is a **no-op in this build**, so no advancement is awarded through it. The default missing tag also prevents its unguided search from recognizing ores. [Mining completion][underminer] · [Advancement hook][advancement]

### Combat and movement

It has **20 health points (10 hearts)** and a base attack-damage attribute of **3**, before equipment and other combat modifiers. It has no automatic nearest-player attack goal; hurting it can provoke melee retaliation and alert nearby Underminers. Keep clear of a group when attacking one. [Attributes and goals][underminer] · [Attribute registration][attributes] · [Group retaliation][retaliation] · [Melee damage caller][melee]

It floats without gravity, can move through blocks and ignores fluid pushing, so a normal wall or water stream is not a dependable enclosure. Its ghostly appearance does not grant general damage immunity, and its registration is not fire-immune. It is allowed to remain in Peaceful in the checked registration, despite being in the monster category. [Movement and damage handling][underminer] · [Entity registration][registry] · [Peaceful default][peaceful] · [Despawn caller][despawn]

### Keeping one and collecting drops

Use a [Name Tag](../items/NameTag.md) if you want to keep one from ordinary distance despawning. An accepted offering also prevents that despawning while the offering is remembered, but it does not tame the mob. Offering memory and its remaining timer are saved with the entity. Normal equipment pickup can also make it persistent, and may replace its weapon if the offered equipment is an upgrade. [Persistence and saved data][underminer] · [Naming][name-tag] · [Equipment pickup][equipment-pickup]

A normally finalized Underminer equips a **[Diamond Pickaxe](../items/DiamondPickaxe.md)**: the imported “Ghostly Pickaxe” reference resolves to that ordinary item. There is no bundled Underminer death-loot table or separate ghostly reward. With mob loot enabled and a recent player kill credit, the original held pickaxe uses the current **8.5%** default equipment-drop chance, rising to **11.5% with Looting III** under the bundled enchantment. A dropped original pickaxe is damaged. The old 50% method in the mob class is not used by the current equipment-drop caller. [Starting equipment][underminer] · [Pickaxe mapping][pickaxe] · [Bundled loot data][data] · [Missing-table fallback][loot-fallback] · [Death-loot gate][death-loot] · [Equipment drops][equipment-drops] · [Default chance][drop-chances] · [Looting][looting]

## Notes

- Registered entity: `minecraft:underminer`; spawn egg: `minecraft:underminer_spawn_egg`
- Registered size: **0.6 × 1.2 blocks**; category: `MobCategory.MONSTER`
- This is bundled Alex's Mobs content, implemented by `EntityUnderminer`

[Entity registration][registry] · [Egg registration][egg]

Normal spawn finalization selects the dwarf form **70%** of the time and each of two tall variants **15%** of the time. A name containing **“herobrine”**, ignoring letter case, forces the second tall variant. The same override uses the program's April 1 or October 29–31 date flags, captured when the date helper initializes. [Variant selection][underminer] · [Date flags][dates]

**Appearance limitation:** the selected native rendering path submits the dwarf's imported model through an empty copied model root, which can raise an empty-mesh error when that body is rendered. The tall form uses a separate humanoid model, so that specific limitation does not apply to it. This is a source-traced condition, not an in-game reproduction or a claim that every encounter crashes. Also, the current render-state extraction does not populate the held-item state, so the equipped pickaxe is not a dependable visual cue. [Selected body layer][transparency] · [Model selection][model] · [Imported root][model-root] · [Native dispatch][native-dispatch] · [Native extraction][native-extraction] · [Held-item layer][held-layer] · [State extraction][renderer] · [Inherited extraction][living-renderer] · [Held-item state][armed-state]

Related: [Underminer Spawn Egg](../items/UnderminerSpawnEgg.md) · [Spawn eggs](../items/SpawnEggs.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Checked active callers, registrations, bundled resources and the selected native body-rendering path. No in-game spawning, pickup, mining, combat, drop or rendering test was run. Server data packs and custom entity data can change these defaults.

[underminer]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityUnderminer.java
[data]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data
[egg]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1974
[egg-category]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2109
[egg-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L48-L99
[mineshaft]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/worldgen/structure/mineshaft.json
[mesa-mineshaft]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/worldgen/structure/mineshaft_mesa.json
[spawn-lists]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[wandering]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/MonsterAIWalkThroughHallsOfStructure.java
[placements]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[config]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/config/AMConfig.java#L78-L81
[roll]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/AMEntityRegistry.java#L62-L71
[nearest-player]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/EntityGetter.java#L75-L100
[lighting]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/EntityRenderer.java#L59-L69
[transparency]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/render/layer/LayerUnderminerTransparency.java
[tags]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/misc/AMTagRegistry.java
[tag-membership]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/Holder.java#L167-L177
[interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1111
[pickup]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L439-L475
[advancement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/misc/AMAdvancementTriggerRegistry.java
[attributes]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L249
[retaliation]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/target/HurtByTargetGoal.java#L33-L115
[melee]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1292-L1320
[registry]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1377-L1383
[peaceful]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2074
[despawn]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L598-L630
[name-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/NameTagItem.java
[equipment-pickup]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L478-L590
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/item/AMItemRegistry.java#L57
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[death-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1474
[equipment-drops]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L813-L839
[drop-chances]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/DropChances.java#L9-L13
[looting]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/enchantment/looting.json
[dates]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/AlexsMobs.java#L12-L27
[model]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/model/ModelUnderminerWrapper.java
[model-root]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/citadel/client/model/basic/BasicEntityModel.java
[native-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java#L248-L281
[native-extraction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java
[held-layer]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/render/layer/LayerUnderminerItem.java
[renderer]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/render/RenderUnderminer.java#L46-L73
[living-renderer]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java#L1222-L1279
[armed-state]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/state/ArmedEntityRenderState.java

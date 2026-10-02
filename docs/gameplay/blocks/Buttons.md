# Buttons

Buttons provide a temporary **signal of 15** when pressed. Choose Stone or Polished Blackstone for a 20-game-tick click pulse, or a wooden button for 30 ticks and arrow activation. This guide covers all **15 registered buttons**, including the imported Pewen exception. [Signal and press logic][button-output] · [Output directions][button-signal] · [Block items][items] · [Pewen items][pewen-items]

## Stone and Oak comparison

| Property | Stone Button | Oak Button |
| --- | --- | --- |
| Recipe | One ordinary Stone → one button, shapeless | One Oak Planks → one button, shapeless |
| Ordinary click pulse | 20 game ticks | 30 game ticks |
| At 20 game ticks per second | 1 second | 1.5 seconds |
| Arrow activation | Disabled | Enabled |
| Item ID | `minecraft:stone_button` | `minecraft:oak_button` |

The [complete material table](#other-materials) gives the exact ingredients and evidence. Game ticks are not redstone ticks: 20 game ticks equal 10 redstone ticks, and 30 equal 15. These are scheduled game-time delays; lag or changed tick rate changes elapsed real time. [Game-time scheduling][schedule] · [Active tick dispatch][tick-dispatch]

## Placement and operation

Attach a button to a sturdy floor, wall, or ceiling face. Placement checks the support face; removing valid support breaks the button and normally drops its item. Buttons have no movement-blocking collision. [Placement and support loss][support] · [Support-loss removal][support-drop] · [Ordinary button properties][button-properties] · [Collision behavior][collision]

Interact with an unpressed button to power it. Using it again while already pressed does **not** restart the pulse. Merely walking over it or dropping an item on it does not press it; the entity-contact route looks for the arrow class described below. All materials use the same hand-operated logic. The ordinary server interaction path permits this in Survival and Creative, while Spectator interaction cannot press it. [Player interaction][press] · [Server interaction dispatch][use-dispatch] · [Contact and release checks][arrow-contact]

A powered button supplies signal 15 to neighboring receivers and direct power toward its attached support. State changes notify both the button's neighbors and the support's neighbors. It does not store a selected signal strength. See [Redstone basics](../redstone/Redstone.md) for connecting outputs. [Signal directions][output] · [Neighbor updates][neighbors]

## Arrow detail

Every wooden button in the material table, including Crimson, Warped, Bamboo, and Pewen, enables the same arrow check. Stone and Polished Blackstone disable it. The check searches the **current button shape's bounding box** for an `AbstractArrow`; the pressed shape is smaller, so overlap with the button matters. [Button shapes][shape] · [Arrow query and rescheduling][arrows] · [Stone block-set flags][stone-types] · [Wooden block-set flags][wood-types]

Ordinary arrows, spectral arrows, and thrown tridents inherit that class, so they qualify **when their entity bounds overlap the detection box**. This does not make snowballs or every other projectile eligible. [Arrow class][arrow-class] · [Spectral Arrow class][spectral-class] · [Thrown Trident class][trident-class]

An eligible arrow can start a wooden button's pulse. At each 30-tick release check, an arrow still in the box keeps it powered and schedules another check. Removing the arrow does not create a fresh timer: release occurs at the next scheduled check that finds no arrow. A manually pressed wooden button can also remain on this way. [Contact, release, and repeat checks][arrow-hold] · [Active entity-contact dispatch][contact-dispatch]

### Wind-charge activation

A Wind Charge's trigger explosion can press an unpowered button of **any** material, including the two stone types. This is a separate explosion callback, so Stone's disabled arrow flag does not prevent it. Breeze Wind Charge triggering additionally depends on `mobGriefing`. A button already powered is not re-pressed by that callback. [Button explosion callback][explosion-button] · [Player Wind Charge trigger][wind-charge] · [Breeze Wind Charge trigger][breeze-charge] · [Server trigger-mode mapping][trigger-mode] · [Affected-block callback][explosion-dispatch] · [Trigger permission][trigger-gate]

## Other materials

All ordinary button recipes are **shapeless: one listed ingredient → one matching button**, so they fit the inventory crafting grid. Each names one exact ingredient. Cobblestone, ordinary Blackstone, raw Bamboo, Bamboo Mosaic, and mixed or unrelated planks are not substitutes for the listed inputs. Pewen's definition is the exception discussed below. Material recipes and self-drop loot are linked individually here.

| Material and exact block/item ID | One recipe ingredient | Press / arrow recheck | Evidence |
| --- | --- | --- | --- |
| <span id="stone-button"></span>[Stone](../items/StoneButton.md)<br>`minecraft:stone_button` | Ordinary Stone | 20 ticks; no arrow activation | [Registration][reg-stone_button] · [Recipe][recipe-stone_button] · [Loot][loot-stone_button] |
| <span id="polished-blackstone-button"></span>[Polished Blackstone](../items/PolishedBlackstoneButton.md)<br>`minecraft:polished_blackstone_button` | Polished Blackstone | 20 ticks; no arrow activation | [Registration][reg-polished_blackstone_button] · [Recipe][recipe-polished_blackstone_button] · [Loot][loot-polished_blackstone_button] |
| <span id="oak-button"></span>[Oak](../items/OakButton.md)<br>`minecraft:oak_button` | Oak Planks | 30 ticks; arrow-enabled | [Registration][reg-oak_button] · [Recipe][recipe-oak_button] · [Loot][loot-oak_button] |
| <span id="spruce-button"></span>[Spruce](../items/SpruceButton.md)<br>`minecraft:spruce_button` | Spruce Planks | 30 ticks; arrow-enabled | [Registration][reg-spruce_button] · [Recipe][recipe-spruce_button] · [Loot][loot-spruce_button] |
| <span id="birch-button"></span>[Birch](../items/BirchButton.md)<br>`minecraft:birch_button` | Birch Planks | 30 ticks; arrow-enabled | [Registration][reg-birch_button] · [Recipe][recipe-birch_button] · [Loot][loot-birch_button] |
| <span id="jungle-button"></span>[Jungle](../items/JungleButton.md)<br>`minecraft:jungle_button` | Jungle Planks | 30 ticks; arrow-enabled | [Registration][reg-jungle_button] · [Recipe][recipe-jungle_button] · [Loot][loot-jungle_button] |
| <span id="acacia-button"></span>[Acacia](../items/AcaciaButton.md)<br>`minecraft:acacia_button` | Acacia Planks | 30 ticks; arrow-enabled | [Registration][reg-acacia_button] · [Recipe][recipe-acacia_button] · [Loot][loot-acacia_button] |
| <span id="cherry-button"></span>[Cherry](../items/CherryButton.md)<br>`minecraft:cherry_button` | Cherry Planks | 30 ticks; arrow-enabled | [Registration][reg-cherry_button] · [Recipe][recipe-cherry_button] · [Loot][loot-cherry_button] |
| <span id="dark-oak-button"></span>[Dark Oak](../items/DarkOakButton.md)<br>`minecraft:dark_oak_button` | Dark Oak Planks | 30 ticks; arrow-enabled | [Registration][reg-dark_oak_button] · [Recipe][recipe-dark_oak_button] · [Loot][loot-dark_oak_button] |
| <span id="pale-oak-button"></span>[Pale Oak](../items/PaleOakButton.md)<br>`minecraft:pale_oak_button` | Pale Oak Planks | 30 ticks; arrow-enabled | [Registration][reg-pale_oak_button] · [Recipe][recipe-pale_oak_button] · [Loot][loot-pale_oak_button] |
| <span id="mangrove-button"></span>[Mangrove](../items/MangroveButton.md)<br>`minecraft:mangrove_button` | Mangrove Planks | 30 ticks; arrow-enabled | [Registration][reg-mangrove_button] · [Recipe][recipe-mangrove_button] · [Loot][loot-mangrove_button] |
| <span id="bamboo-button"></span>[Bamboo](../items/BambooButton.md)<br>`minecraft:bamboo_button` | Bamboo Planks | 30 ticks; arrow-enabled | [Registration][reg-bamboo_button] · [Recipe][recipe-bamboo_button] · [Loot][loot-bamboo_button] |
| <span id="crimson-button"></span>[Crimson](../items/CrimsonButton.md)<br>`minecraft:crimson_button` | Crimson Planks | 30 ticks; arrow-enabled | [Registration][reg-crimson_button] · [Recipe][recipe-crimson_button] · [Loot][loot-crimson_button] |
| <span id="warped-button"></span>[Warped](../items/WarpedButton.md)<br>`minecraft:warped_button` | Warped Planks | 30 ticks; arrow-enabled | [Registration][reg-warped_button] · [Recipe][recipe-warped_button] · [Loot][loot-warped_button] |
| <span id="pewen-button"></span>[Pewen](../items/PewenButton.md)<br>`minecraft:pewen_button` | **Bundled recipe incompatible**; see [Pewen limitation](#pewen-exceptions) | 30 ticks; arrow-enabled | [Registration][reg-pewen_button] · [Bundled definition][recipe-pewen_button] · [Loot][loot-pewen_button] |

Polished Blackstone Button actually uses the **Stone** block-set behavior. Pewen uses **Cherry** behavior. Those assignments determine their timing, sounds, and arrow rules.

## Mining, water, and pistons

Every listed button has hardness and blast resistance **0.5**, and normally drops **one matching button** when mined, including by hand. No registration requires a correct tool; none of the loot tables has a Silk Touch or Fortune branch. Stone and Polished Blackstone are pickaxe-efficient, the 12 vanilla wooden buttons are axe-efficient, and Pewen is absent from both mining tags. The per-variant loot tables above include the explosion-survival condition, so ordinary mining drops are not an explosion guarantee. [Strength property][strength] · [Axe tag][axe-tag] · [Pickaxe tag][pick-tag] · [Wooden-button tag][wood-button-tag] · [Stone-button tag][stone-button-tag] · [Mining-speed rules][tool-rule] · [Drop requirement check][toolgate] · [Survival drop dispatch][mine-dispatch]

**Buttons do not waterlog.** Incoming water can replace any of these buttons and run its normal item-drop path. Keep a button outside a water stream when it must remain placed. This follows the empty fluid state and non-solid, no-collision registration, together with the active fluid-spreading code. [Default fluid state][empty-fluid] · [Solid-state calculation][solid] · [Fluid motion test][motion] · [Fluid admission][fluid-admission] · [Replacing a block with fluid][spread] · [Water drop callback][water-drops]

The 14 vanilla buttons use the **destroy** piston reaction: a piston pushing into them breaks them and runs their loot. Pewen uses the default **normal** reaction and can enter the piston movement path instead; it still needs valid support after movement. Do not assume its moving circuit behavior matches Oak. [Ordinary piston property][piston-properties] · [Pewen registration][pewen-properties] · [Default piston reaction][default-piston] · [Piston eligibility][piston-check] · [Destroyed-block drops][piston-drop]

### Pewen exceptions

The placed Pewen Button is registered and uses the 30-tick Cherry arrow-enabled behavior, but its bundled one-plank recipe uses an old ingredient-object format that the active ingredient codec does not accept. Treat it as a **registered control with an incompatible crafting definition**, not a verified Survival crafting route. The existing [Pewen guide](Pewen.md#construction-and-recipes) owns the family integration warning. [Pewen Button definition][pewen-recipe] · [Active shapeless recipe codec][shapeless-codec] · [Ingredient codec][ingredient-codec] · [Accepted holder-set forms][holder-codec] · [Item holder names][item-codec] · [Active recipe loading][recipe-loader]

Pewen is also absent from the wooden-button mining and fuel tags. The default furnace fuel list gives **100 burn ticks per button** to Oak, Spruce, Birch, Jungle, Acacia, Cherry, Dark Oak, Pale Oak, Mangrove, and Bamboo. Crimson and Warped are explicitly removed by the non-flammable-wood item tag; Stone, Polished Blackstone, and Pewen have no checked fuel entry. See [Furnace fuel planning](Furnace.md#fuel-planning) for how burn time is used. [Fuel tag membership][fuel-buttons] · [Fuel exclusions][nonflammable] · [Default fuel entries and removal][fuel] · [Server fuel initialization][fuel-init]

## Related pages

- [Pressure plates](PressurePlates.md) and [Lever](Lever.md)
- [Stone Button item](../items/StoneButton.md) and [Oak Button item](../items/OakButton.md)
- [Wood construction](WoodConstruction.md), [Pewen](Pewen.md), and [Mining](../mechanics/Mining.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `be4ac3081f175ffb862938ab118b20e6457110d8` on 2026-10-02. Every listed registration, recipe, loot table, and relevant tag was checked against the active interaction, entity-contact, fluid, and scheduled-tick paths. No gameplay or circuit timing test was run.

[button-output]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/ButtonBlock.java#L87-L114
[button-signal]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/ButtonBlock.java#L131-L144
[items]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/item/Items.java#L1038-L1051
[pewen-items]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/item/Items.java#L312-L313
[schedule]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/LevelAccessor.java#L35-L43
[tick-dispatch]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/server/level/ServerLevel.java#L766-L770
[support]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/FaceAttachedHorizontalDirectionalBlock.java#L27-L82
[support-drop]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Block.java#L213-L225
[button-properties]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L7237-L7239
[collision]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L325-L330
[press]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/ButtonBlock.java#L87-L114
[use-dispatch]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L375
[arrow-contact]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/ButtonBlock.java#L146-L181
[output]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/ButtonBlock.java#L131-L144
[neighbors]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/ButtonBlock.java#L183-L195
[shape]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/ButtonBlock.java#L61-L85
[arrows]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/ButtonBlock.java#L153-L181
[stone-types]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/state/properties/BlockSetType.java#L83-L118
[wood-types]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/state/properties/BlockSetType.java#L119-L217
[arrow-class]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/entity/projectile/Arrow.java#L19-L25
[spectral-class]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/entity/projectile/SpectralArrow.java#L16-L22
[trident-class]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/entity/projectile/ThrownTrident.java#L27-L35
[arrow-hold]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/ButtonBlock.java#L146-L181
[contact-dispatch]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/entity/Entity.java#L1175-L1205
[explosion-button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/ButtonBlock.java#L97-L105
[wind-charge]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/entity/projectile/windcharge/WindCharge.java#L55-L72
[breeze-charge]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/entity/projectile/windcharge/BreezeWindCharge.java#L22-L39
[trigger-mode]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/server/level/ServerLevel.java#L1156-L1167
[explosion-dispatch]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/ServerExplosion.java#L205-L214
[trigger-gate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/ServerExplosion.java#L291-L299
[reg-stone_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L1915-L1915
[recipe-stone_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/stone_button.json
[loot-stone_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/stone_button.json
[reg-polished_blackstone_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L5859-L5861
[recipe-polished_blackstone_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/polished_blackstone_button.json
[loot-polished_blackstone_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/polished_blackstone_button.json
[reg-oak_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L2724-L2724
[recipe-oak_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/oak_button.json
[loot-oak_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/oak_button.json
[reg-spruce_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L2725-L2725
[recipe-spruce_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/spruce_button.json
[loot-spruce_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/spruce_button.json
[reg-birch_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L2726-L2726
[recipe-birch_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/birch_button.json
[loot-birch_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/birch_button.json
[reg-jungle_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L2727-L2727
[recipe-jungle_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/jungle_button.json
[loot-jungle_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/jungle_button.json
[reg-acacia_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L2728-L2728
[recipe-acacia_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/acacia_button.json
[loot-acacia_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/acacia_button.json
[reg-cherry_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L2729-L2729
[recipe-cherry_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/cherry_button.json
[loot-cherry_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/cherry_button.json
[reg-dark_oak_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L2730-L2732
[recipe-dark_oak_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/dark_oak_button.json
[loot-dark_oak_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/dark_oak_button.json
[reg-pale_oak_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L2733-L2735
[recipe-pale_oak_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/pale_oak_button.json
[loot-pale_oak_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/pale_oak_button.json
[reg-mangrove_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L2736-L2738
[recipe-mangrove_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/mangrove_button.json
[loot-mangrove_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/mangrove_button.json
[reg-bamboo_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L2739-L2739
[recipe-bamboo_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/bamboo_button.json
[loot-bamboo_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/bamboo_button.json
[reg-crimson_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L5680-L5680
[recipe-crimson_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/crimson_button.json
[loot-crimson_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/crimson_button.json
[reg-warped_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L5681-L5681
[recipe-warped_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/warped_button.json
[loot-warped_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/warped_button.json
[reg-pewen_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L7048-L7056
[recipe-pewen_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/pewen_button.json
[loot-pewen_button]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/pewen_button.json
[strength]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1143-L1153
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[pick-tag]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood-button-tag]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/tags/block/wooden_buttons.json
[stone-button-tag]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/tags/block/stone_buttons.json
[tool-rule]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/item/ToolMaterial.java#L35-L45
[toolgate]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[mine-dispatch]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[empty-fluid]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L228-L230
[solid]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L482-L497
[motion]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L534-L543
[fluid-admission]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L401-L427
[spread]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L268-L277
[water-drops]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L87-L91
[piston-properties]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L7237-L7239
[pewen-properties]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L7048-L7061
[default-piston]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1013-L1019
[piston-check]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L226-L258
[piston-drop]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L287-L299
[pewen-recipe]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/pewen_button.json
[shapeless-codec]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L83-L91
[ingredient-codec]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/item/crafting/Ingredient.java#L32-L33
[holder-codec]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/resources/HolderSetCodec.java#L25-L38
[item-codec]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/item/Item.java#L90-L92
[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L72-L82
[fuel-buttons]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/tags/item/wooden_buttons.json
[nonflammable]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/tags/item/non_flammable_wood.json
[fuel]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[fuel-init]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/server/MinecraftServer.java#L340-L340

# Iron Bars, Chain, Door and Trapdoor

Use Iron Bars for thin connected barriers, Iron Chain for straight decorative runs, and Iron Doors or Trapdoors for powered openings. **Iron doors and trapdoors do not open by hand.** For other materials, see [wood construction](WoodConstruction.md#doors) and [copper construction](CopperConstruction.md#doors-and-trapdoors); their recipes and opening controls differ. [Iron controls][iron-controls] · [Registered names][names]

## Forms and crafting

These are four distinct blocks and matching items. The current chain name is **Iron Chain**, with ID `minecraft:iron_chain`. Use [Iron Ingots](../items/IronIngot.md) and [Iron Nuggets](../items/IronNugget.md), not raw metal. [Bars and chain registration][reg-bars-chain] · [Door registration][reg-door] · [Trapdoor registration][reg-trapdoor] · [Bar/chain items][items-bars-chain] · [Door item][items-door] · [Trapdoor item][items-trapdoor]

| Block and exact ID | Ingredients and arrangement | Result |
| --- | --- | ---: |
| Iron Bars — `minecraft:iron_bars` | 6 Iron Ingots in 2 full rows of 3 | **16 bars** [recipe][recipe-iron_bars] |
| Iron Chain — `minecraft:iron_chain` | One vertical column: Iron Nugget, Iron Ingot, Iron Nugget | **1 chain** [recipe][recipe-iron_chain] |
| Iron Door — `minecraft:iron_door` | 6 Iron Ingots in 2 columns of 3 | **3 doors** [recipe][recipe-iron_door] |
| Iron Trapdoor — `minecraft:iron_trapdoor` | 4 Iron Ingots in a 2 × 2 square | **1 trapdoor** [recipe][recipe-iron_trapdoor] |

The trapdoor recipe fits the inventory grid. The other three need a [Crafting Table](CraftingTable.md), because their patterns are three slots wide or tall. [Bars recipe][recipe-iron_bars] · [Chain recipe][recipe-iron_chain] · [Door recipe][recipe-iron_door] · [Trapdoor recipe][recipe-iron_trapdoor]

## Mining and collection

A pickaxe is the efficient mining tool for all four. **Iron Bars, Iron Chain and Iron Trapdoor require an unbroken pickaxe for their normal mining drop; Iron Door can be collected by hand.** Every bundled pickaxe material qualifies for the first three, including Wooden and Golden: none of these four IDs belongs to the Stone-, Iron-, or Diamond-required sets. [Properties][reg-bars-chain] · [Door properties][reg-door] · [Trapdoor properties][reg-trapdoor] · [Pickaxe targets][mineable-pickaxe] · [Bars tag][tag-bars] · [Chains tag][chains] · [Stone-required set][needs_stone_tool] · [Iron-required set][needs_iron_tool] · [Diamond-required set][needs_diamond_tool] · [Material/tool rules][tool] · [Pickaxe assignment][tool-family]

| Block | Hardness | Blast resistance | Ordinary collection result |
| --- | ---: | ---: | --- |
| Iron Bars | 5 | 6 | 1 Iron Bars item [loot][loot-iron_bars] |
| Iron Chain | 5 | 6 | 1 Iron Chain [loot][loot-iron_chain] |
| Iron Door | 5 | 5 | 1 Iron Door for the two-block assembly; only its lower half has loot [loot][loot-iron_door] |
| Iron Trapdoor | 5 | 5 | 1 Iron Trapdoor [loot][loot-iron_trapdoor] |

The single-number strength used by Door and Trapdoor sets both hardness and blast resistance. Silk Touch and Fortune do not change any of these four loot outputs. Each table has an explosion-survival condition, so the mining results are not guaranteed explosion yields. [Strength semantics][strength] · [Bars loot][loot-iron_bars] · [Chain loot][loot-iron_chain] · [Door loot][loot-iron_door] · [Trapdoor loot][loot-iron_trapdoor]

A retained **broken pickaxe** loses its normal speed and cannot satisfy the three correct-tool gates. The Door exception follows its block property; it is not a special pickaxe tier. See [Mining](../mechanics/Mining.md) for the shared harvest and speed rules. [Broken-tool speed][broken-speed] · [Broken-tool drop guard][broken-drop] · [Player harvest check][harvest] · [Active harvest dispatch][harvest-dispatch]

## Iron Bars

Bars form a **one-block-high collision shape**, with a central post and connected strips **2/16 block wide**. Their visible gaps do not make the connected strips passable. A freestanding bar keeps the narrow center post; it does not gain the 1.5-block collision height of a [Fence](WoodConstruction.md#fences). [Bar dimensions][bars] · [Collision assembly][cross]

Connections update horizontally toward:

- Other Iron Bars, Glass Panes and stained panes, and the Copper Bars family, which share the bar implementation
- Wall-tagged blocks
- A neighboring sturdy side face, unless that block is a connection exception

Leaves, pumpkins, melons, barriers and shulker boxes are excluded from that sturdy-face route. Removing a connection changes the shape, but bars need no continuing floor or side support. They have no open/closed control and are not climbable like a [Ladder](Ladder.md). [Connection rules and updates][bars] · [Copper and plain-pane registrations][reg-bars-chain] · [Glass pane connections](GlassAndPanes.md#placement-and-pane-connections) · [Connection exceptions][connection-exceptions] · [Default survival][default-support] · [Climbable blocks][climbable]

### Bars beside a curing Zombie Villager

Iron Bars also count toward the small nearby-block bonus **after a Zombie Villager's cure has started**. On a 1% random check per conversion tick, the code searches an 8 × 8 × 8 volume and considers at most 14 Iron Bars or Bed blocks combined; each found block has a 30% chance to add one extra tick of progress on that check. These are occasional bonuses, not a guaranteed cure time. Copper Bars and Iron Chains do not qualify for the explicit Iron Bars check. Bars alone do not start the cure: the checked interaction uses a Golden Apple while the Zombie Villager has Weakness. [Conversion and starting interaction][curing-start] · [Exact bonus scan][curing-bars]

## Iron Chain

A chain occupies a straight **3/16 × 3/16-block cross-section** along one full block axis. The clicked face selects that axis: top/bottom for vertical, east/west for X, north/south for Z. Adjacent chain blocks do not bend it or add connection arms; line their axes up for a continuous run. [Chain shape and state][chain] · [Clicked-face axis][axis]

Chains have collision, so their visible holes are not empty passages through the narrow central shape. They need no continuing support and remain after the original attachment is removed. They are decorative building pieces, not climbable ropes; they are absent from the bundled climbable tag. [Chain shape][chain] · [Default collision and survival][default-support] · [Climbable tag][climbable]

## Iron Door

One item places a **two-block-high assembly**. Leave both spaces clear and put the lower half above a sturdy upper face. The top destination must be replaceable and within the build limit. Losing the supporting floor or either half removes the remaining unsupported half. [Door placement][door-place] · [Floor support][door-use] · [Half synchronization][door-shape] · [Unsupported-block removal][removal] · [Removal drops][removal-drops]

The door faces in your horizontal placement direction. Neighboring solid collision blocks, an adjacent door, and your click position choose its hinge; see [door placement](WoodConstruction.md#doors) for the shared layout rules. The panel is **3/16 block thick** and rotates to the side when opened. Opening clears the doorway's middle but leaves the panel's collision at its new side. There is **no waterlogged state**. [Facing and hinge][door-place] · [Panel shape][door-shape] · [Collision behavior][default-support] · [Door state list][door-states]

## Iron Trapdoor

An Iron Trapdoor is a **3/16-block-thick panel**. Closed, it lies along the bottom or top of its block space; open, it stands vertically. It still has collision in that vertical position, so allow clearance beside the open panel. Unlike the Door, it has no continuing floor or attachment-support requirement. [Shape][trapdoor-shape] · [Default collision and survival][default-support]

On a side-face placement, the clicked face sets the facing and the clicked height chooses the upper or lower half. On a top/bottom placement, it faces opposite your horizontal direction and uses the clicked face to choose the half. It can be waterlogged. An open trapdoor directly above a Ladder with the **same facing** also continues that ladder's climbing route, including this iron variant; it must first be opened with power. [Placement][trapdoor-use] · [Ladder and trapdoor test][climb] · [Ladder exit layout](Ladder.md#exiting-through-a-trapdoor)

## Power and water

**Both iron opening blocks require redstone control.** Power at placement sets them open immediately. Later, a change in detected power opens them when power arrives and closes them when it leaves. An Iron Door checks for power at **either half**; an Iron Trapdoor checks its own position. They reject ordinary hand toggles and direct Wind Charge burst toggles. A Wind Charge can still operate a suitable [Button](Buttons.md#wind-charge-activation), whose resulting power can operate the door. [Iron block-set flags][iron-controls] · [Door placement][door-place] · [Door hand/power behavior][door-use] · [Door burst condition][door-wind] · [Trapdoor hand/burst behavior][trapdoor-shape] · [Trapdoor power][trapdoor-use]

For an entrance, plan a reachable control on each side or an appropriate [Pressure Plate](PressurePlates.md#what-each-plate-detects) before closing yourself in. Hand-operable alternatives are the [wooden](WoodConstruction.md#waterlogging-and-power) and [copper](CopperConstruction.md#doors-and-trapdoors) forms.

**Iron Bars, Iron Chain and Iron Trapdoor can hold water; Iron Door cannot.** Placing a waterloggable form into source Water stores that water. These placement checks distinguish source Water from Flowing Water. A Water Bucket can fill an eligible placed block, and an empty Bucket can collect its stored water. The bucket's normal restrictions still apply, including water evaporation in ultrawarm dimensions and secondary-use placement routing. [Bars water state][bars] · [Chain water state][chain] · [Trapdoor water state][trapdoor-use] · [Source/flowing fluid types][fluids] · [Bucket filling and pickup][water] · [Bucket restrictions][bucket]

Redstone opening or closing an Iron Trapdoor retains its stored water and schedules a fluid update. Bars and chains also retain their waterlogged state when neighbors change; neither becomes an opening mechanism when powered. [Trapdoor update][trapdoor-use] · [Bars update][bars] · [Chain update][chain]

## Finding generated examples

Crafting is a direct route for all four. The checked generation code also places **Iron Bars and Iron Doors in Stronghold prison halls**, and **Iron Chains in some Mineshaft supports** when the support search finds a suitable ceiling. Collect these using the same mining rules above. These are verified examples, not a complete structure or chest-loot inventory, and a particular structure is not guaranteed to contain each form. [Stronghold prison placement][prison] · [Stronghold piece selection][stronghold-pool] · [Stronghold generator][stronghold] · [Mineshaft support call][mineshaft-support] · [Conditional chain placement][mineshaft-chain]

## Sources and verification

Source-reviewed at `3cc0d7d93500be7a135c5577511b5565406d42e6` on 2026-10-02. English labels, block/item registration, all four recipes and loot tables, recursively expanded tool tags, shape/support/water rules, power and Wind Charge gates, ladder interaction, curing bonus, and the cited generation paths were inspected. No in-game crafting, mining, placement, movement, redstone, curing or structure-generation test was run. Bundled recipes and tags can be changed by data packs.

[iron-controls]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/state/properties/BlockSetType.java#L11-L46
[names]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/assets/minecraft/lang/en_us.json#L1739-L1744
[reg-bars-chain]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/Blocks.java#L2307-L2331
[reg-door]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/Blocks.java#L1764-L1768
[reg-trapdoor]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/Blocks.java#L3144-L3148
[items-bars-chain]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/item/Items.java#L553-L555
[items-door]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/item/Items.java#L1068
[items-trapdoor]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/item/Items.java#L1089
[recipe-iron_bars]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/recipe/crafting/iron_bars.json#L1-L15
[recipe-iron_chain]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/recipe/crafting/iron_chain.json#L1-L17
[recipe-iron_door]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/recipe/crafting/iron_door.json#L1-L16
[recipe-iron_trapdoor]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/recipe/crafting/iron_trapdoor.json#L1-L15
[mineable-pickaxe]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json#L1-L395
[tag-bars]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/tags/block/bars.json#L1-L13
[chains]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/tags/block/chains.json#L1-L13
[needs_stone_tool]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json#L1-L82
[needs_iron_tool]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json#L1-L16
[needs_diamond_tool]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json#L1-L9
[tool]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L48
[tool-family]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/item/Item.java#L431-L440
[loot-iron_bars]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/loot_table/blocks/iron_bars.json#L1-L21
[loot-iron_chain]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/loot_table/blocks/iron_chain.json#L1-L21
[loot-iron_door]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/loot_table/blocks/iron_door.json#L1-L30
[loot-iron_trapdoor]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/loot_table/blocks/iron_trapdoor.json#L1-L21
[strength]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1143-L1153
[broken-speed]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/item/ItemStack.java#L374-L379
[broken-drop]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L588
[harvest]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L656
[harvest-dispatch]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L276-L299
[bars]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/IronBarsBlock.java#L30-L110
[cross]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/CrossCollisionBlock.java#L37-L85
[connection-exceptions]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/Block.java#L243-L250
[default-support]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[climbable]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/tags/block/climbable.json#L1-L13
[curing-start]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/entity/monster/ZombieVillager.java#L123-L149
[curing-bars]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/entity/monster/ZombieVillager.java#L244-L266
[chain]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/ChainBlock.java#L25-L84
[axis]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java#L48-L55
[door-place]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L143-L199
[door-use]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L201-L244
[door-shape]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L47-L108
[removal]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/Block.java#L213-L224
[removal-drops]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/Level.java#L263-L280
[door-states]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L266-L268
[trapdoor-shape]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/TrapDoorBlock.java#L40-L110
[trapdoor-use]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/TrapDoorBlock.java#L130-L193
[climb]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1652-L1676
[door-wind]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L110-L138
[fluids]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/material/Fluids.java#L6-L11
[water]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L18-L49
[bucket]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/item/BucketItem.java#L100-L130
[prison]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/levelgen/structure/structures/StrongholdPieces.java#L893-L948
[stronghold-pool]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/levelgen/structure/structures/StrongholdPieces.java#L45-L66
[stronghold]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/levelgen/structure/structures/StrongholdStructure.java#L27-L51
[mineshaft-support]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java#L443-L451
[mineshaft-chain]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java#L474-L505

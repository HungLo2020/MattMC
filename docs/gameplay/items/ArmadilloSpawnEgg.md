# Armadillo Spawn Egg

## Obtaining

In **Creative**, open your inventory and find **Armadillo Spawn Egg** by its displayed name in the item browser. Keep the mouse cursor empty and leave inventory space, then click to request one egg or Shift-click for a stack. See [finding and requesting an item](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) for the controls. The egg's Spawn Eggs category entry feeds this browser catalog. [Category entry][category] · [Browser list][browser-list]

The catalog is also visible in Survival, but ordinary Survival insertion requests are skipped: admission requires the server player's infinite-materials ability, normally supplied by Creative. See the browser's [mode and permission limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits) and the shared [spawn-egg acquisition guide](SpawnEggs.md#obtaining). [Admission check][admission] · [Server ability][server-ability] · [Mode abilities][mode-abilities]

## Usage

For ordinary block use, the egg places an [Armadillo](../mobs/Armadillo.md) at the clicked block's position if its collision shape is empty; otherwise it uses the neighboring position in the clicked-face direction. [Placement][placement]

Follow the shared guides for [hand placement, source-liquid use and Survival/Creative consumption](SpawnEggs.md#placing-a-mob-by-hand), [changing a Monster or Trial Spawner](SpawnEggs.md#changing-a-spawner), and [dispensing eggs](SpawnEggs.md#dispensing-eggs).

## Behavior

Using a matching egg on a **living Armadillo** can create a **baby Armadillo** at that animal's position. It needs no second adult, food or love mode, and the clicked Armadillo may itself be a baby. This supported [egg-on-mob interaction](SpawnEggs.md#using-an-egg-on-an-existing-mob) is separate from [feeding and breeding](../mobs/Armadillo.md#feeding-and-breeding). [Active interaction][mob-interaction] · [Baby creation][offspring] · [Armadillo offspring][armadillo-offspring] · [Baby state][baby-state]

The egg interaction is handled before the Armadillo's frightened-state refusal for ordinary feeding. A successful baby creation consumes one egg in Survival; Creative's infinite-materials handling preserves it. [Interaction order][mob-interaction] · [Feeding refusal][feeding-refusal] · [Egg consumption][offspring] · [Creative preservation][consume]

## Notes

* This item is registered as `minecraft:armadillo_spawn_egg`. [Registration][registration]
* It appears in the Spawn Eggs creative tab. [Category entry][category]
* Babies must grow up before they can provide scutes through brushing or shedding. See [collecting scutes](../mobs/Armadillo.md#collecting-scutes) for adult care and collection rules. [Adult brushing][feeding-refusal] · [Adult shedding][shedding]

Source-reviewed at MattMC commit `cc140840a21e5c6c932c23abf34124418d6506b0` on 2026-10-04. This entry checks acquisition, shared egg use and the Armadillo baby path; no inventory request, egg use or gameplay test was performed.

[registration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/Items.java#L1795
[category]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1960-L1976
[browser-list]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[admission]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
[server-ability]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2320-L2323
[mode-abilities]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/level/GameType.java#L62-L79
[placement]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L50-L103
[mob-interaction]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/Mob.java#L1054-L1100
[offspring]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L184
[armadillo-offspring]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/animal/armadillo/Armadillo.java#L53-L79
[baby-state]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/AgeableMob.java#L156-L164
[feeding-refusal]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/animal/armadillo/Armadillo.java#L299-L321
[consume]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1080
[shedding]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/animal/armadillo/Armadillo.java#L139-L153

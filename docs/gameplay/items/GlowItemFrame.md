# Glow Item Frame

A **Glow Item Frame** is the crafted glowing variant of an [Item Frame](ItemFrame.md). It displays one item or filled Map on a wall, floor, or ceiling and shares the ordinary frame's rotation, removal, and MattMC visibility controls. Its display uses a different lighting setup, with the current appearance limits explained below. [Item registration][registration] · [Shared behavior][glow]

## Obtaining

The [Glow Ink Sac recipe](GlowInkSac.md#usage) combines **one Item Frame and one Glow Ink Sac**, in any arrangement, to produce **one Glow Item Frame**. Both ingredients are consumed. The recipe is shapeless, so the player's 2 × 2 crafting grid is large enough. Make the ordinary frame using its [eight-Stick, one-Leather recipe](ItemFrame.md#obtaining); the Glow Ink Sac guide covers the ink's acquisition. [Conversion recipe][recipe]

Craft the conversion before placing the frame. Glow Ink used on a placed ordinary frame goes through that frame's insertion/rotation interaction; it does not perform this crafting recipe. [Frame interaction][interaction]

Glow Item Frames are also category-listed in the Creative catalog. Follow the [inventory browser's mode and permission limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits); seeing the entry in Survival does not mean its insertion request succeeds. A broken Glow Item Frame can return a Glow Item Frame under the normal [frame drop rules](ItemFrame.md#support-removal-and-drops). [Catalog entry][catalog] · [Returned frame item][glow]

## Usage

Place it on the desired face of a valid support, then use an item on the empty frame to display one copy. For a Chest label, Sneak/Crouch while placing to bypass opening the Chest. The [shared placement guide](ItemFrame.md#placing-a-frame) explains space and permission checks. [Placement][placement]

Once occupied:

- Use normally to rotate: ordinary item models turn 45 degrees per step, while filled Maps turn 90 degrees
- **Sneak/Crouch + use** to hide or restore the frame surround while retaining the display; this MattMC toggle requires an occupied frame
- Attack once in ordinary Survival to release the item, then attack the empty frame to recover the Glow Item Frame, subject to entity-drop rules

Hiding the surround does not turn it into an ordinary frame or remove its contents. An empty hidden frame must be refilled before the occupied-frame visibility toggle can be used again. See [Item Frame controls](ItemFrame.md#inserting-rotating-and-hiding) for the full interaction table. [Inherited interaction][interaction] · [Display submission][render]

## Behavior

### Display appearance

On the inspected **built-in Rust mesh rendering path**, ordinary framed item models receive fixed bright lightmap input rather than the frame's surrounding-light sample. The visible frame surround also receives a minimum rendering-brightness adjustment. This source review establishes the rendering inputs and their active mesh path; it does not certify identical visible brightness for every item, shader pack, or graphics setting. [Frame producer][render] · [Active mesh submission][submission] · [Rust mesh lighting][rust-mesh] · [Built-in lighting shader][rust-light]

Filled Maps use a separate material-quad path. The frame producer supplies a different lighting value for a glowing Map, but **the visible brightness difference between ordinary and glow-framed Maps has not been verified** on that path. Choose the frame for its appearance after checking your current setup; do not treat the glow name or a lighting constant as a measured result. This review also establishes no world-light emission level or spawn-proofing effect. [Map submission][map-render] · [Material lighting selection][material-light] · [Material shader][material-shader]

### Shared frame rules

Glow Item Frames need the same support and clearance as ordinary frames. Losing support, item removal, entity-drop settings, and `Fixed` entity-data exceptions all follow the [shared frame behavior](ItemFrame.md#behavior). Breaking one returns the glowing frame item when the drop rules allow; it does not refund the Glow Ink Sac separately. [Inherited rules and returned item][glow] · [Drop handling][drops]

They also supply the same **0 when empty and 1–8 when occupied** Comparator input. Use the [frame-specific circuit layout](ItemFrame.md#reading-rotation-with-a-comparator); glow and invisibility do not add signal strength. Compare/subtract processing belongs to the [Comparator guide](../blocks/RedstoneComparator.md#compare-and-subtract-modes). [Signal calculation][interaction] · [Comparator frame lookup][comparator]

## Notes

- This item is registered as `minecraft:glow_item_frame`
- Unmodified inventory Glow Item Frames stack to **64**; a placed frame holds only one item
- Glow frames use their own placement, insertion, rotation, removal, and break sounds

[Registration][registration] · [Default stack size][stack] · [Variant sounds][glow]

Related: [Item Frame](ItemFrame.md) · [Glow Ink Sac](GlowInkSac.md) · [Map](Map.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the shapeless recipe, registration, inherited server interactions and drops, Comparator lookup, and the active Java-to-Rust display submission and built-in shader paths. No in-game crafting, placement, visibility, brightness, lighting, or circuit comparison was run. The Map appearance limit above is separate from the confirmed item and interaction rules.

[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/glow_item_frame.json
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2053-L2054
[catalog]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1116-L1117
[glow]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/GlowItemFrame.java#L12-L50
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/HangingEntityItem.java#L34-L75
[interaction]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/ItemFrame.java#L363-L415
[drops]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/ItemFrame.java#L154-L243
[render]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/entity/ItemFrameRenderer.java#L47-L140
[submission]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java#L1084-L1178
[rust-mesh]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/render/worldrender/source/mesh.rs#L1859-L1889
[rust-light]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/render/shaderpack/programs/builtin/glsl/minimal_terrain_material_vertex.glsl#L91-L121
[map-render]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/MapRenderer.java#L37-L83
[material-light]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/render/worldrender/geometry/batching.rs#L549-L552
[material-shader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/render/worldrender/vanilla/glsl/material_vertex.glsl#L72-L86
[comparator]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComparatorBlock.java#L97-L129
[stack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L381-L390

# Detector Rail

The Detector Rail item places `minecraft:detector_rail`, which senses minecarts and emits redstone power without supplying the Powered Rail's boost.

## Obtaining and use

Craft **six Detector Rails** from six Iron Ingots, one Stone Pressure Plate, and one Redstone Dust using the [shared rail recipe](../blocks/Rails.md#crafting-and-collecting). Ordinary Survival mining returns the rail without a correct-tool requirement.

Place it in a supported straight or ascending track section. It outputs 15 while a cart is detected and rechecks occupied track every 20 game ticks. A Comparator can read a container cart's fullness through it. The [Rails guide](../blocks/Rails.md#detector-rail-output) distinguishes that analog reading from ordinary cart-presence output and includes a lamp example.

## Related pages

- [Rails](../blocks/Rails.md)
- [Minecart](Minecart.md) and [Activator Rail](ActivatorRail.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. No gameplay test was run. The canonical Rails guide and existing Transport guide provide the recipe, drop, placement, and vehicle-behavior evidence.

- [Rail item registrations](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java)
- [Creative listings](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)

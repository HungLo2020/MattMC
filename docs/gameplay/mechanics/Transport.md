# Transport

Use a **boat** for a route you can row across water, and a **minecart** when you want a vehicle to follow a built track. Plan the landing or stop before loading passengers or valuable cargo. In particular, an unpowered Powered Rail is a brake, and a powered Activator Rail ejects riders. [Boat controls][rowing] · [Rail braking][braking] · [Activator behavior][activator]

## Choosing a vehicle

| Need | Starting choice | Important limit |
| --- | --- | --- |
| Water travel with another passenger | [Oak Boat](../items/OakBoat.md) | Two seats; no built-in cargo inventory |
| Water travel with stored supplies | [Oak Boat with Chest](../items/OakBoatWithChest.md) | One seat and 27 slots |
| A repeatable tracked passenger route | [Minecart](../items/Minecart.md) | One seat; track and propulsion are separate construction costs |

The standard boat's first passenger controls it; player boarding moves a player ahead of a non-player passenger when necessary. Its chest variant replaces the second seat with storage. The plain Minecart accepts only an empty passenger slot. These are vehicle capacities, not a promise that every mob can board: automatic pickup has additional restrictions. [Boat capacity][boat-capacity] · [Passenger ordering][ordering] · [Chest capacity][chest-capacity] · [Cart capacity][cart-capacity] · [Boat pickup][boat-pickup] · [Cart pickup][cart-pickup]

For boats, allow more than a one-block-wide opening, keep the route on the surface, and leave room to slow down at the landing. Boats are 1.375 blocks wide; their water movement has friction rather than an instant stop. Sustained submersion ejects passengers. Follow the [Oak Boat guide](../items/OakBoat.md) for placement, rowing, animal loading, chest access, and recovery. [Boat size][boat-size] · [Water motion][water-motion] · [Submersion][submersion]

## Building a basic rail route

Start with a supported line of ordinary [Rail](../items/Rail.md), then add [Powered Rail](../items/PoweredRail.md) where the cart needs propulsion or a controlled stop. A basic cart takes five Iron Ingots. For track:

| Output | Materials | Crafting layout |
| --- | --- | --- |
| 16 Rails | 6 Iron Ingots and 1 Stick | Iron in both outer columns; Stick in the center |
| 6 Powered Rails | 6 Gold Ingots, 1 Stick, and 1 Redstone Dust | Gold in both outer columns; Stick in the center; Redstone Dust below it |

These are bundled shaped recipes handled by the active crafting serializer. Use a [Crafting Table](../blocks/CraftingTable.md) for their three-wide layouts. [Cart recipe][cart-recipe] · [Rail recipe][rail-recipe] · [Powered Rail recipe][powered-recipe] · [Recipe serializer][recipe-serializer]

Rails need support below them. Ascending rails additionally depend on support at the high end. Finish the support and track connections before placing a cart, and avoid removing those supports later. Ordinary rails can curve; Powered, Detector, and Activator Rails use straight or ascending shapes. Build corners with ordinary rails. [Support rules][support] · [Ordinary shapes][rail-shape] · [Special shapes][special-shape] · [Detector shapes][detector-shape]

The cart follows the connected track. Rider input is a small low-speed assist, not a way to turn off the rails or continually accelerate at cruising speed. Face along the track and press Forward if you need a starting nudge. [Rider assistance][rider] · [Input direction][input-direction]

## Starting and stopping a cart

For a simple terminal, use a **flat Powered Rail at the end of a straight track**, with a redstone-conducting solid backing block immediately beyond the rail and a controllable redstone signal. A nearly stationary cart on the powered rail receives a launch impulse away from that backing block. The block must pass the game's redstone-conductor check; an arbitrary decorative block is not guaranteed to work. [Flat-rail launch][launch] · [Backing-block test][backing]

An isolated flat Powered Rail can remain still even when powered, because power alone does not choose a travel direction. Give the cart a push or rider nudge, use a suitable slope, or provide the backing-block arrangement. Moving carts receive further acceleration from powered sections. [Launch and acceleration][launch] · [Slope influence][slopes] · [Rider assistance][rider]

To stop an arriving cart:

1. Leave a section of Powered Rail **unpowered** at the stopping area
2. Release movement controls as you approach
3. Keep sufficient braking space before the end of the route, and check the layout with an empty cart before using passengers or cargo
4. Power the terminal rail again when you want to depart

In the default movement model, an unpowered Powered Rail halves horizontal motion at each applicable step, then sets motion to zero below **0.03 blocks per tick**. It is not a guaranteed instant stop on first contact. Low-speed player input can bypass that braking step, which is why holding Forward at the platform can frustrate a stop. [Braking and input exception][braking]

A Powered Rail can receive power from a neighboring signal or propagate it through compatible connected Powered Rails. The connected search is limited to eight rail steps and requires matching rail type and axis. Do not assume one distant switch powers an entire railway; inspect the powered sections along the route. [Signal update][power] · [Propagation limit][power-limit] · [Connected-rail checks][power-links]

## Recognizing special rails

- **Powered Rail:** boosts when powered and brakes when unpowered
- **Detector Rail:** senses a Minecart and produces a redstone signal. It checks again while occupied; it does not supply the Powered Rail's boost
- **Activator Rail:** calls the cart's activation behavior when powered. On a plain rideable Minecart, that ejects the passenger

All four rail items and their blocks are actively registered. Detector and Activator Rails have different jobs even though their names both suggest automation. Use Detector Rails for sensing and Activator Rails only where the particular cart's activation effect is wanted. [Powered-rail identification][powered-check] · [Detector sensing][detector] · [Activator effect][activator] · [Activator callsite][activation-call] · [Rail items][rail-items] · [Powered and Detector blocks][rail-blocks] · [Ordinary Rail block][ordinary-block] · [Activator block][activator-block]

## Why another railway design may behave differently

Ordinary default worlds select the legacy movement implementation. Enabling the optional `minecart_improvements` feature selects a separate implementation, including an experimental `minecartMaxSpeed` rule. A design that assumes that experiment may not match a default world. [Default flags][defaults] · [Default world configuration][world-default] · [Movement selection][selection] · [Feature check][feature] · [Experimental rule][rule]

Under default movement, occupied carts retain more motion than empty carts, water lowers the speed limit, and slopes change motion. These differences make a single universal Powered Rail spacing unreliable. Check the intended direction, load, slope, and world settings before extending a route. This guide does not prescribe a source-unverified maximum gap between boosts. [Speed and slowdown][speed] · [Water slowdown][water-slowdown] · [Slopes][slopes]

If a ride fails, first check the simple causes: rail support, a missed track connection, an unpowered boost section, no launch direction, an occupied vehicle, or an unintended powered Activator Rail. For leaving either vehicle, use **Sneak/Crouch**, which defaults to Left Ctrl in MattMC; Shift is Sprint. [Boarding restriction][board] · [Default controls][keys] · [Control-key mapping][control-key] · [Dismount][dismount]

## Related pages

- [Strider](../mobs/Strider.md) and [Happy Ghast](../mobs/HappyGhast.md): Lava riding and harnessed flight
- [Llamas](../mobs/Llama.md), [Trader Llamas](../mobs/TraderLlama.md), and [Camels](../mobs/Camel.md): caravans, retention, and two-player travel
- [Horses](../mobs/Horse.md), [Donkeys](../mobs/Donkey.md), and [Mules](../mobs/Mule.md): land mounts and animal cargo
- [Skeleton Horses](../mobs/SkeletonHorse.md) and [Zombie Horses](../mobs/ZombieHorse.md): undead mount acquisition and care
- [Rails](../blocks/Rails.md): placed-track support, special rails, and vehicle-specific activation
- [Oak Boat](../items/OakBoat.md)
- [Minecart](../items/Minecart.md)
- [Rail](../items/Rail.md)
- [Powered Rail](../items/PoweredRail.md)
- [Detector Rail](../items/DetectorRail.md)
- [Activator Rail](../items/ActivatorRail.md)
- [Mechanics](Mechanics.md)

## Verification scope

Source-reviewed at MattMC commit `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. Boat and cart registrations, capacities and controls, recipes and serializers, rail block codecs and update callbacks, active movement callsites, braking, signal propagation, detector/activator behavior, and the default/experimental branch were inspected. No in-game route, station, spacing, passenger, or speed test was run.

[rowing]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractBoat.java#L571-L600
[braking]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/OldMinecartBehavior.java#L161-L185
[activator]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/Minecart.java#L47-L61
[boat-capacity]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractBoat.java#L730-L749
[ordering]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Entity.java#L2353-L2369
[chest-capacity]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractChestBoat.java#L30-L49
[cart-capacity]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Entity.java#L2386-L2388
[boat-pickup]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractBoat.java#L270-L288
[cart-pickup]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/OldMinecartBehavior.java#L369-L395
[boat-size]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L954-L965
[water-motion]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractBoat.java#L528-L569
[submersion]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractBoat.java#L205-L217
[cart-recipe]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/minecart.json
[rail-recipe]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/rail.json
[powered-recipe]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/powered_rail.json
[recipe-serializer]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/crafting/RecipeSerializer.java#L9-L11
[support]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/BaseRailBlock.java#L58-L108
[rail-shape]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/RailBlock.java#L14-L25
[special-shape]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/PoweredRailBlock.java#L15-L27
[detector-shape]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/DetectorRailBlock.java#L30-L42
[rider]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/OldMinecartBehavior.java#L161-L176
[input-direction]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/server/level/ServerPlayer.java#L2159-L2163
[launch]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/OldMinecartBehavior.java#L242-L271
[backing]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractMinecart.java#L444-L446
[slopes]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/OldMinecartBehavior.java#L120-L143
[power]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/PoweredRailBlock.java#L129-L142
[power-limit]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/PoweredRailBlock.java#L30-L33
[power-links]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/PoweredRailBlock.java#L105-L125
[powered-check]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/OldMinecartBehavior.java#L113-L118
[detector]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/DetectorRailBlock.java#L50-L114
[activation-call]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/OldMinecartBehavior.java#L55-L69
[rail-items]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1122-L1125
[rail-blocks]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java#L682-L687
[ordinary-block]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java#L1416
[activator-block]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2899-L2901
[defaults]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/flag/FeatureFlags.java#L33-L41
[world-default]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/WorldDataConfiguration.java#L14-L19
[selection]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractMinecart.java#L97-L104
[feature]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractMinecart.java#L577-L579
[rule]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/GameRules.java#L209-L213
[speed]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/OldMinecartBehavior.java#L410-L418
[water-slowdown]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/AbstractMinecart.java#L448-L455
[board]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/vehicle/Minecart.java#L23-L35
[keys]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/client/Options.java#L555-L565
[control-key]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/blaze3d/platform/InputConstants.java#L455-L460
[dismount]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/player/Player.java#L451-L459

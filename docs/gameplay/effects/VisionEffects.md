# Vision effects

**Night Vision brightens the rendered scene; Blindness restricts vision and ordinary sprinting/critical hits; Darkness combines restricted vision with pulsing lighting.** Their visual rules are separate, so Night Vision does not cancel the harmful effects. Use this reference to choose supplies, recognize the restrictions, and distinguish an effect from its on-screen presentation. [Registrations][register-vision] [register-darkness] · [Lightmap inputs][lightmap] · [Fog selection][fog-priority] · [Blindness gameplay check][mobility]

Times below use **20 game ticks per second**. Rendering descriptions follow MattMC's current Java-to-Rust whole-frame path and built-in consumers. They are source rules, not measured visibility distances or promises that every shader pack produces the same image. [Current entry][active-entry] · [Semantic lightmap update][active-frame] · [Frame producer][active-primitives] · [Submitted environment][render-submit]

## Night Vision

Effect ID: `minecraft:night_vision`.

Night Vision supplies a brightness factor to the current lightmap; the Rust terrain renderer samples the resulting lightmap. It also supplies the current shader-pack Night Vision input. This improves how the scene is displayed without creating a light source in the world. Monster spawning still uses actual sky/block light and its own rules, so a bright-looking cave is not evidence that it has been lit against spawning. [Lightmap selection][lightmap] · [Rust color calculation][lightmap-rust] · [Terrain sampling][terrain-light] · [Pack input][night-pack] · [World-light spawn checks][world-light]

The brightness factor stays at full strength while **more than 200 ticks** remain, or while duration is infinite. During the final **200 ticks / 10 seconds**, it oscillates rather than steadily fading out. Its formula ranges from **0.4 to 1.0**; these are rendering factors, not a percentage of objects guaranteed visible. A five-second stew serving begins inside this near-expiry window. [Duration-based factor][night-scale] · [Infinite-duration handling][effect-infinite]

Night Vision has **no amplifier-based strength upgrade in the checked visual calculation**. It does not clear Blindness or Darkness, restore the sky they suppress, or extend their selected fog range. When Darkness is present, the outside-water fog-color path also skips Night Vision's brightening branch, while the lightmap can still receive both effects. [Factor calculation][night-scale] · [Separate fog color][fog-color] · [Fog priority][fog-priority] · [Lightmap inputs][lightmap] · [Sky eligibility][sky-effects]

### Night Vision sources

- **Potion:** brew **Awkward Potion + Golden Carrot** for Night Vision I, **3,600 ticks / 3 minutes**. Add Redstone for **9,600 ticks / 8 minutes**. The registered mix list has no Glowstone upgrade for Night Vision II. [Active mixes][night-brew] · [Potion definitions][night-potions] · [Complete mix list][brew-all]
- **Poppy or Torchflower Suspicious Stew:** each checked recipe grants **Night Vision I for 100 ticks / 5 seconds**. Use [Suspicious Stew](../items/SuspiciousStew.md) and the [flower guide](../blocks/Flowers.md#small-flower-variants) for serving preparation and the brown-Mooshroom route. [Poppy recipe][poppy] · [Torchflower recipe][torchflower] · [Effect consumption][stew-eat] · [Level I entries][stew-level]
- **Selected Farmer trade:** a level-4 Farmer can offer a stew with **Night Vision I for 100 ticks / 5 seconds**. It is one possible selected offer, not a guaranteed item from every Farmer. [Offer pool][stew-trades] · [Stored stew component][stew-offer] · [Active trade selection][trade-pool] · [Random choices][trade-random]

A [Golden Carrot](../items/GoldenCarrot.md) is the potion ingredient. Craft one by surrounding a Carrot with **eight Gold Nuggets**. Eating that ingredient uses the ordinary food component and does **not** grant Night Vision. [Crafting recipe][carrot-recipe] · [Item registration][carrot-item] · [Default food binding][default-food] · [Default consumption][default-consumption]

## Blindness

Effect ID: `minecraft:blindness`.

Blindness supplies short-range dark fog and suppresses the current sky/celestial input. In the ordinary selected Blindness fog environment, the full-duration endpoint parameter is **5**, with start **1.25**; the final sub-20-tick interval relaxes the range toward the configured render distance. These are source fog parameters, not a guaranteed five-block visibility radius. [Fog calculation][blind-fog] · [Current collection][fog-collect] · [Rust fog inputs][fog-header] · [Terrain fog blend][fog-render] · [Sky extraction][sky-check] · [Sky publication][sky-publish] · [Rust sky gate][sky-rust]

Blindness also affects ordinary player actions:

- **Sprinting:** the client checks Blindness both before starting and while continuing running or swimming sprint. Active sprint stops through these ordinary checks. [Blindness-only restriction][mobility] · [Sprint eligibility][sprint-gate] · [Stop conditions][sprint-stop-conditions] · [Active start/stop dispatch][sprint-tick]
- **Falling melee criticals:** the ordinary player critical branch requires that the player is not under the Blindness restriction. A falling hit while blinded does not pass that branch. Other damage bonuses and attack methods have their own rules; use [Combat](../mechanics/Combat.md#choose-a-critical-sprint-hit-or-sweep). [Critical gate][critical] · [Restriction definition][mobility]

Darkness and Night Vision are not part of that Blindness-only action check. Their presence alone does not impose those sprint/critical restrictions. Increasing Blindness's level also does not strengthen these presence-based gates or the checked fog calculation. [Action check][mobility] · [Fog consumer][blind-fog]

### Blindness sources

**Azure Bluet or Open Eyeblossom Suspicious Stew** grants **Blindness I for 220 ticks / 11 seconds** in the checked crafting recipes. The selected level-4 Farmer Blindness stew instead stores **120 ticks / 6 seconds**. Keep track of the source of a serving rather than assuming all Blindness stew has the same duration. The effect is applied by eating the prepared stew; [Eyeblossom contact with Bees](../blocks/Eyeblossoms.md#bees-and-harmful-effects) is a separate interaction. [Azure recipe][azure] · [Open Eyeblossom recipe][eyeblossom] · [Farmer list][stew-trades] · [Trade component][stew-offer] · [Consumption][stew-eat]

An [Illusioner](../mobs/Illusioner.md) present in an encounter can cast **Blindness I for 400 ticks / 20 seconds**. Its installed spell goal requires a valid spell opportunity, a target different from its stored last target, and **effective local difficulty greater than 2**. This is a local-difficulty check, not simply a “Hard mode only” rule. The linked mob page distinguishes its acquisition route; this effect source does not establish an ordinary raid or biome spawn. [Installed goals][illusioner-goals] · [Blindness spell conditions/result][illusioner-blind] · [Active spell execution][spell-call] · [Local difficulty comparison and calculation][local-difficulty]

There is **no ordinary Blindness potion type or brewing recipe** in the checked registry. The effect's existence and its command ID do not create a listed brewable bottle. [Potion registry][potion-list] · [Brewing list][brew-all]

## Darkness

Effect ID: `minecraft:darkness`.

Darkness uses a blended fog restriction, suppresses the active sky input, and supplies a separate pulsing lightmap contribution. Its registered blend-in/out setting is **22 ticks**; the end fade starts when 22 ticks remain. The fog blend and the lighting pulse are different inputs. At full fog blend, the default environmental start/end parameters are **11.25 / 15**; they are not a measured visibility distance. [Effect blend registration][register-darkness] · [Blend settings][blend-setting] · [Client blend updates][dark-blend] · [Fog calculation][dark-fog] · [Sky condition][sky-effects] · [Lighting inputs][lightmap]

The current built-in lightmap can receive Night Vision and Darkness simultaneously. Its Darkness color subtraction follows the Night Vision calculation in the branch where dimension ambient light is zero; gamma-derived brightness is adjusted separately. Do not treat either effect as a universal cancellation of the other. [Simultaneous inputs][lightmap] · [Actual Rust calculation order and ambient-light condition][lightmap-rust]

### Darkness sources and refresh

While its server AI runs, a [Warden](../mobs/Warden.md#darkness-emerging-and-burrowing) checks its aura every **120 game ticks**, granting fresh **Darkness I for 260 ticks / 13 seconds** to eligible non-allied Survival/Adventure players **strictly less than 20 blocks** from its position. It does not need to be angry at that player for this aura check. An eligible [Sculk Shrieker response](../blocks/SculkShrieker.md#warning-levels-and-summoning) calls the same Darkness helper with a **40-block** distance limit, also strict, from the block center. Not every shriek passes the response gates. [Warden AI caller][warden-ai] · [Darkness helper][warden-darkness] · [Player filtering][effect-players] · [Game-mode delegation][mode-delegate] · [Survival/Adventure definition][survival-adventure] · [Shrieker eligibility and response][shrieker-gates]

The helper refreshes only if the effect is absent, the current level is weaker, or its remaining duration is **199 ticks or less**. Infinite duration is not treated as nearing expiry. Leaving the source can let a remaining effect finish, while Milk can remove it sooner; staying nearby can let a later source check apply it again. The complete Shrieker warning/summoning rules remain with its guide. [Refresh predicate][effect-players] · [Helper threshold][warden-darkness] · [Duration comparison][effect-infinite]

A fresh grant from this Warden/Shrieker helper requests **no ordinary HUD effect icon**. That does not mean the effect is absent: the inventory effect display still lists active effects without that HUD flag. Commands or other applications can use different flags. [Grant flags][warden-darkness] · [Constructor flags][effect-flags] · [Current GUI call][gui-call] · [HUD dispatch][gui-effects] · [HUD icon filter][gui-icon] · [Inventory effect list][inventory-effects] · [Inventory icons][inventory-icons]

There is **no ordinary Darkness potion or brewing recipe** in the checked registry. Darkness also has no attack or movement attribute modifier in its registration; the action restrictions described above belong to Blindness. [Effect registration][register-darkness] · [Potion registry][potion-list] · [Brewing list][brew-all] · [Player restriction][mobility]

### Darkness Pulsing setting

The Accessibility setting is named **Darkness Pulsing**. Setting it to **Off** removes the inspected built-in lightmap's Darkness pulse and its Darkness subtraction from the gamma-derived brightness input. It does **not** remove the status, its raw fog blend, or the sky-suppression check. Shader packs also receive a raw Darkness factor separate from the scaled pulse, so their final presentation can differ. [Option and default][dark-option] · [Accessibility menu][option-menu] · [Exact label][option-label] · [Lightmap scaling][lightmap] · [Unscaled fog][dark-fog] · [Sky gate][sky-effects] · [Raw pack factor][dark-pack] · [Scaled pack pulse][pulse-pack] · [Rust pack inputs][pack-inputs]

## When vision effects overlap

- **Blindness with Darkness:** Blindness is selected first for their shared fog-distance environment. Darkness can still contribute its separate lighting pulse. [Fog ordering][fog-priority] · [Active selection][fog-select] · [Independent lightmap inputs][lightmap]
- **Night Vision with either harmful effect:** brightness handling does not remove their status, fog, sky gate, or Blindness's player-action restrictions. Darkness also blocks Night Vision's outside-water fog-color brightening, while the lightmap still accepts Night Vision. [Fog color][fog-color] · [Lightmap selection][lightmap] · [Sky check][sky-effects] · [Player gate][mobility]
- **Night Vision with Conduit Power:** Night Vision supplies the chosen lightmap/night-vision factor before the Conduit fallback. Conduit's breathing and mining benefits are separate; see [water and fire effects](WaterAndFireEffects.md#conduit-power). [Factor selection][lightmap] · [Current pack helper][night-pack]

Lava and Powder Snow precede these harmful effects in fog-environment selection. Hooks, render distance, camera changes and shader packs also matter. Treat the fog numbers above as defaults for their selected source branches, not a universal final image. [Priority][fog-priority] · [Render-distance stage and hooks][fog-select] · [Current environment record][environment]

## Brewing, delivery, and clearing

Use [Brewing](../brewing/Brewing.md) for bottles, fuel and Awkward Potion. The active server registry and stand execute the Night Vision chain described above. Fermented Spider Eye converts ordinary Night Vision to [Invisibility](VisibilityEffects.md#invisibility), or extended Night Vision to extended Invisibility; it does not extend Night Vision or make Blindness. [Server initialization][brew-server] · [Stand execution][brew-stand] · [Registered transformations][night-brew]

Splash duration depends on impact distance. Ordinary lingering and tipped-arrow variants use shorter item duration scales; use [shared potion delivery](MovementEffects.md#delivery-changes-duration) for details. The ordinary registered Night Vision variants are also category-listed in MattMC's [inventory item browser](../mechanics/InventoryBrowser.md), available in **Survival and Creative** separately from ingredient gathering and brewing. No ordinary Blindness or Darkness bottle is added merely because the effect is registered. [Splash consumer][splash] · [Item scales][potion-scales] · [Category generation][browser-potions] · [Enabled-type enumeration][browser-types] · [Potion types][potion-list]

**Milk clears Night Vision as well as Blindness and Darkness.** Honey only removes Poison. A qualifying Totem activation clears current effects before granting its replacement effects. Changing Darkness Pulsing is a presentation adjustment, not an effect-removal action. [Milk][milk] · [Clear callback][milk-clear] · [All-effect removal][clear] · [Honey][honey] · [Totem activation][totem-call] · [Totem sequence][totem]

Different effects can coexist; repeated copies of the same effect do not add their levels or simply add their durations. Stronger applications take precedence, equal-level longer applications replace remaining time, and a weaker hidden effect may return with its remaining duration. Finite hidden durations count down while hidden; infinite duration stays infinite. [Effect map][effect-apply] · [Replacement][effect-update] · [Hidden ticking][effect-tick] · [Infinite duration][effect-infinite]

With command permission level 2, for example:

```mcfunction
/effect give @s minecraft:night_vision 60 0
/effect clear @s minecraft:blindness
/effect clear @s minecraft:darkness
```

`60` is seconds and amplifier `0` means level I. Higher amplifiers do not increase the inspected vision formulas, which use presence, duration or the Darkness blend instead. Commands remain separate from ordinary browser access. [Permission][command-gate] · [Arguments][command-args] · [Time conversion][command-seconds] · [Specific clear][command-clear] · [Night Vision factor][night-scale] · [Blindness fog][blind-fog] · [Darkness fog][dark-fog]

## Related pages

- [Status effects](Effects.md), [Movement effects](MovementEffects.md), [Combat effects](CombatEffects.md), and [Water and fire effects](WaterAndFireEffects.md)
- [Warden](../mobs/Warden.md), [Sculk Shrieker](../blocks/SculkShrieker.md), and [Combat](../mechanics/Combat.md)
- [Suspicious Stew](../items/SuspiciousStew.md), [Flowers](../blocks/Flowers.md), [Eyeblossoms](../blocks/Eyeblossoms.md), and [Brewing](../brewing/Brewing.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`; its `src/main` tree matches the inspected current publisher baseline. Checked active whole-frame Java-to-Rust lightmap/fog/sky/HUD consumers, player action gates, described brewing/food/trade/spell/Warden/Shrieker callers, and effect replacement/removal. **No in-game visibility, shader comparison, combat, sprint, spell, food, trade, command, or timing test was run.** Data packs, custom state, shader packs, rendering settings, camera choice and server timing can change outcomes.

[register-vision]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffects.java#L72-L73
[register-darkness]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffects.java#L112
[lightmap]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/LightTexture.java#L217-L245
[lightmap-rust]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/rust/render/shaderpack/vanilla/lightmap.rs#L113-L154
[terrain-light]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/rust/render/shaderpack/programs/builtin/glsl/minimal_terrain_material_vertex.glsl#L107-L121
[night-scale]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L600-L606
[night-pack]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L2995-L3006
[fog-color]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/fog/FogRenderer.java#L148-L168
[fog-priority]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/fog/FogRenderer.java#L46-L54
[fog-select]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/fog/FogRenderer.java#L248-L287
[blind-fog]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/fog/environment/BlindnessFogEnvironment.java#L25-L49
[dark-fog]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/fog/environment/DarknessFogEnvironment.java#L25-L42
[fog-collect]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L2816-L2832
[fog-header]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/rust/render/worldrender/geometry/batching.rs#L2188-L2223
[fog-render]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/rust/render/shaderpack/programs/builtin/glsl/minimal_terrain_material_fragment_direct.glsl#L83-L89
[sky-check]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/LevelRenderer.java#L1965-L1977
[sky-effects]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/LevelRenderer.java#L2383-L2386
[sky-publish]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L3241-L3284
[sky-rust]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/rust/render/worldrender/vanilla/resources/sky.rs#L277-L297
[dark-blend]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L371-L397
[blend-setting]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffect.java#L144-L151
[dark-option]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/Options.java#L673-L681
[option-menu]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/options/AccessibilityOptionsScreen.java#L30-L42
[option-label]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/assets/minecraft/lang/en_us.json#L6015-L6016
[dark-pack]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L2840-L2866
[pulse-pack]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L2979-L2987
[pack-inputs]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/rust/render/worldrender/frame/requests.rs#L708-L726
[active-entry]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/Minecraft.java#L1355-L1370
[active-frame]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L648-L654
[active-primitives]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L830-L837
[render-submit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java#L933-L984
[environment]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L2699-L2766
[world-light]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Monster.java#L86-L112
[mobility]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L1919-L1921
[sprint-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/player/LocalPlayer.java#L1009-L1022
[sprint-stop-conditions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/player/LocalPlayer.java#L804-L810
[sprint-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/player/LocalPlayer.java#L689-L710
[critical]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L1002-L1013
[night-potions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L15-L18
[night-brew]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L146-L150
[brew-all]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L133-L190
[brew-server]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/MinecraftServer.java#L336-L340
[brew-stand]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L170-L183
[carrot-recipe]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/golden_carrot.json#L1-L17
[carrot-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L2062
[default-food]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Item.java#L366-L372
[default-consumption]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L13-L15
[poppy]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_poppy.json#L1-L23
[torchflower]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_torchflower.json#L1-L23
[azure]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_azure_bluet.json#L1-L23
[eyeblossom]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_open_eyeblossom.json#L1-L23
[stew-eat]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/SuspiciousStewEffects.java#L38-L42
[stew-level]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/SuspiciousStewEffects.java#L58-L72
[stew-trades]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L103-L112
[stew-offer]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1482-L1503
[trade-pool]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L836
[trade-random]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L232
[illusioner-goals]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Illusioner.java#L64-L75
[illusioner-blind]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Illusioner.java#L197-L235
[spell-call]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/SpellcasterIllager.java#L165-L198
[local-difficulty]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/DifficultyInstance.java#L31-L59
[warden-ai]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/warden/Warden.java#L277-L285
[warden-darkness]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/warden/Warden.java#L393-L396
[effect-players]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectUtil.java#L47-L62
[mode-delegate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L101-L103
[survival-adventure]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/GameType.java#L90-L92
[shrieker-gates]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/SculkShriekerBlockEntity.java#L127-L147
[effect-flags]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L61-L75
[gui-call]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L977-L980
[gui-effects]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/Gui.java#L240-L245
[gui-icon]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/Gui.java#L546-L578
[inventory-effects]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/EffectsInInventory.java#L42-L56
[inventory-icons]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/EffectsInInventory.java#L96-L103
[potion-list]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L10-L80
[splash]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/ThrownSplashPotion.java#L38-L68
[potion-scales]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L2242-L2255
[browser-potions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1756-L1770
[browser-types]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2207-L2213
[effect-update]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L123-L170
[effect-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L214-L260
[effect-infinite]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L168-L188
[effect-apply]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L982-L1000
[milk]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L64
[milk-clear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/consume_effects/ClearAllStatusEffectsConsumeEffect.java#L20-L22
[clear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L943-L953
[honey]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L16-L20
[totem]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/DeathProtection.java#L25-L40
[totem-call]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1349-L1375
[command-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L33-L48
[command-args]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L70-L90
[command-seconds]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L153-L176
[command-clear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L226-L232

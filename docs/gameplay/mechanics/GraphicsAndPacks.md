# Graphics settings and packs

Open **Options...** from the title or pause menu. Choose **Video Settings...** for graphics controls, **Resource Packs...** for local resource packs, or **Video Settings... → Shader Packs...** for shader selection. These screens save changes differently. Labels below are the bundled English text; another language or resource pack can replace them. [Title menu][title-menu] · [Pause menu][pause-menu] · [Options routes][options] · [Shader entry][video-pages]

## Video settings: apply or undo first

Video settings are grouped into **General**, **Quality**, **Performance**, and **Advanced**. Ordinary edits remain pending until you choose **Apply**, which applies and saves them, or **Undo**, which discards those pending edits. **Done** is disabled and **Escape** cannot close the screen while edits remain pending. After applying or undoing, use Done or Escape to return. [Pages][video-pages] · [Save and close][video-save] · [Pending values][video-values]

In General, **Graphics Backend** selects **OpenGL** or **Vulkan** for the next startup. Apply the change, then restart the game. Both choices currently use Rust rendering. This control does not restore the old Java renderer. See [Render architecture](../../development/rendering/RENDER-ARCHITECTURE.md) for current ownership. [Restart flag][backend-option] · [Backend route][backend-route] · [Frame caller][frame-caller] · [Legacy entry][legacy-render]

Three General controls you can adjust are:

- **Render Distance:** 2–32 chunks; effective distance is capped by the server-supplied distance. [Control][general-controls] · [Effective distance][effective-distance] · [Terrain consumer][terrain-distance]
- **Simulation Distance:** 5–32 chunks; the integrated single-player server receives this setting. [Control][general-controls] · [Server update][simulation]
- **Brightness:** 0–100; feeds the brightness factor used by the Rust vanilla lightmap. [Control][general-controls] · [Brightness input][gamma-input] · [Copied input][gamma-copy] · [Native calculation][gamma-native]

## Resource packs: select, order, then close

**Resource Packs...** opens **Select Resource Packs**. Use **Open Pack Folder** to find the configured directory. Local discovery accepts folders containing `pack.mcmeta` and regular `.zip` files for metadata checking; a ZIP extension alone does not establish a usable pack. [Folder and screen][pack-screen] · [Discovery][pack-discovery] · [Metadata][pack-metadata]

Use **Search...** to match a pack's ID, title, or description. Hover over a pack icon and click its transfer control to move it between **Available** and **Selected**. Selected packs have up/down controls where movement is allowed. Required packs cannot be removed, and fixed-position neighbors can block reordering. Incompatible metadata prompts a warning before selection. [Search][pack-search] · [Selection controls][pack-controls] · [Movement rules][pack-movement]

Higher entries in Selected take priority for ordinary same-path resource lookup. Resource filtering and merged resources have separate rules. **Done commits the selection and order**; **Escape also commits**. This screen has no Cancel/discard action. A changed saved pack list requests a resource reload. [Ordering][pack-order] · [Lookup][pack-lookup] · [Close][pack-close] · [Escape dispatch][screen-escape] · [Save/reload][pack-save]

## Shader packs: selection and settings

On **Shader Packs**, use **Open Shader Pack Folder...** to open the game's `shaderpacks` directory. The list includes directories and `.zip` files. Selecting a pack also stages shaders as enabled. The toggle shows **Shaders: Enabled** or **Shaders: Disabled**; an empty list shows **Shaders: No Packs Present** and disables the toggle. [Folder][shader-folder] · [Discovery][shader-discovery] · [List and toggle][shader-list] · [Selection][shader-select]

- **Apply** saves/applies the current configuration and keeps the screen open
- **Done** applies and returns to the parent screen
- **Cancel** closes without applying the staged selection/toggle and clears queued option edits; it cannot undo an earlier Apply
- **Shader Pack Settings...** applies the selection before opening its options. Availability depends on shaders being enabled and the available option menu. **Shader Pack List...** switches back and also applies pending edits

**Escape is not a universal cancel.** Within shader options it first goes back through nested menus or returns to the list; the final screen-close path applies. Use Cancel to close without applying the staged selection or queued option edits. [Buttons and switching][shader-buttons] · [Settings availability][shader-settings] · [Escape navigation][shader-escape] · [Apply/discard][shader-save]

## When the selection does not appear to work

A listed, highlighted, saved, or successfully parsed shader pack is not proof that it is rendering compatibly. Applying a change asks the renderer to load the saved pack data for the next frame. If loading fails, the previous complete pack data can remain in use. The renderer can also fall back to its Rust vanilla route if the selected pack cannot cover the frame or required Distant Horizons depth data is unavailable. See the [Goal 5 rendering checkpoint](../../development/rendering/GOAL-5-STATUS.md#remaining-work) for current limits. [Refresh request][shader-refresh] · [Frame refresh][semantic-frame] · [Collection and retention][shader-collect] · [Admission][shader-admission]

Check for a `shaders` folder at the root of the pack folder or ZIP. The active collector uses that location. The Java menu parser can accept some nested ZIP layouts, which does not establish support for arbitrary wrapped archives in the active collector. [Collector layout][shader-layout] · [Menu parser][shader-parser]

If Shader Packs... is missing, see [Render architecture troubleshooting](../../development/rendering/RENDER-ARCHITECTURE.md#troubleshooting): the tab depends on initialized shader configuration. [Entry condition][video-pages]

Related: [Mechanics](Mechanics.md) · [Content guide](../ContentGuide.md)

World gameplay-data controls are covered in [World data packs](WorldDataPacks.md).

## Sources and verification

Source-reviewed on **2026-10-04** at `9bafc14d2e2943dcfe8a37e9e81dc001386b88bb`; the reviewed documentation checkout `d723254d406f300bdcf2579d39dfd5a433e43869` has the identical `src/main` tree. Checked active menu callers, persistence, resource lookup, source collection, native admission, and 31 effective bundled English labels after language merging/deprecation. No live UI, pack installation/reload, image, performance, or compatibility test was run. [Language loading][language-load] · [Deprecation][language-deprecation] · [Bundled labels][english]

[options]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/options/OptionsScreen.java#L73-L93
[video-pages]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/sodium/client/gui/SodiumOptionsGUI.java#L51-L82
[video-save]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/sodium/client/gui/SodiumOptionsGUI.java#L190-L323
[backend-option]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/sodium/client/gui/SodiumGameOptionPages.java#L138-L149
[backend-route]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/vulkanic/VulkanicAPI.java#L670-L750
[frame-caller]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Minecraft.java#L1360-L1369
[legacy-render]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L609-L622
[general-controls]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/sodium/client/gui/SodiumGameOptionPages.java#L48-L90
[effective-distance]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Options.java#L1750-L1752
[terrain-distance]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/vulkanic/world/RustGalWholeFrameTerrainSource.java#L758-L772
[simulation]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/server/IntegratedServer.java#L111-L123
[gamma-copy]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L2706-L2751
[gamma-native]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/rust/render/shaderpack/vanilla/lightmap.rs#L113-L154
[pack-screen]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/packs/PackSelectionScreen.java#L62-L132
[pack-discovery]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/packs/repository/PackDetector.java#L35-L56
[pack-metadata]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/packs/repository/FolderRepositorySource.java#L51-L59
[pack-search]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/packs/PackSelectionScreen.java#L135-L150
[pack-controls]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/packs/TransferableSelectionList.java#L274-L318
[pack-movement]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/packs/PackSelectionModel.java#L99-L219
[pack-order]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/packs/PackSelectionModel.java#L37-L58
[pack-lookup]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/packs/resources/FallbackResourceManager.java#L63-L140
[pack-close]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/packs/PackSelectionScreen.java#L96-L127
[pack-save]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Options.java#L847-L865
[shader-folder]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/irisshaders/iris/Iris.java#L576-L589
[shader-discovery]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/irisshaders/iris/Iris.java#L432-L434
[shader-list]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/irisshaders/iris/gui/element/ShaderPackSelectionList.java#L157-L328
[shader-select]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/irisshaders/iris/gui/element/ShaderPackSelectionList.java#L500-L518
[shader-buttons]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/irisshaders/iris/gui/screen/ShaderPackScreen.java#L261-L292
[shader-settings]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/irisshaders/iris/gui/screen/ShaderPackScreen.java#L338-L347
[shader-escape]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/irisshaders/iris/gui/screen/ShaderPackScreen.java#L365-L405
[shader-save]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/irisshaders/iris/gui/screen/ShaderPackScreen.java#L543-L605
[shader-refresh]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/irisshaders/iris/Iris.java#L512-L529
[shader-collect]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java#L2294-L2360
[shader-admission]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/rust/render/worldrender/source/admission.rs#L1538-L1618
[shader-layout]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/vulkanic/shaderpack/RustShaderPackSourceCollector.java#L103-L141
[shader-parser]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/irisshaders/iris/Iris.java#L314-L338
[language-load]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/resources/language/ClientLanguage.java#L35-L69
[language-deprecation]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/locale/DeprecatedTranslationsInfo.java#L74-L91
[english]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/lang/en_us.json#L4472-L6670
[title-menu]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/TitleScreen.java#L175-L179
[pause-menu]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/PauseScreen.java#L88-L88
[video-values]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/sodium/client/gui/options/OptionImpl.java#L85-L110
[gamma-input]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/LightTexture.java#L239-L245
[screen-escape]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/Screen.java#L111-L115
[semantic-frame]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L670-L680

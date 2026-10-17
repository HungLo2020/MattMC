# Chat and closed captions

Use **Chat Settings...** to make conversations easier to read, and **Accessibility Settings...** to turn sound cues into text. This guide covers the ordinary chat panel and closed captions; menu names below use MattMC's bundled English labels. [Menu routes][menus] · [Chat settings][chat-menu] · [Accessibility settings][accessibility-menu]

## Open the settings

- **Options → Chat Settings...** has chat visibility, text size, opacity, spacing, width, height, and delay
- **Options → Accessibility Settings...** has **Closed Captions**, **Notification Time**, and the shared **Text Background** controls
- **Options → Music & Sounds...** also has **Closed Captions**; both menus change the same option. It defaults to **OFF**. [Sound menu][sound-menu] · [Caption default][caption-default]

During play, **Open Chat** defaults to **T** and **Open Command** to **/**. Both bindings are configurable; see [Movement and controls](Movement.md#choosing-your-controls) for the key-binding menu. Both normal openers use the chat-availability checks below. [Defaults][keys] · [T mapping][key-t] · [Slash mapping][key-slash] · [Openers][openers]

## Make chat easier to read

In Chat Settings, adjust these together:

- **Chat Text Size** changes the chat panel's scale. If chat is too small or this is **OFF**, raise it
- **Chat Text Opacity** controls the letters; **Text Background Opacity** controls the background behind chat lines
- **Line Spacing** separates lines, while **Width** controls their available horizontal space
- **Focused Height** applies while the chat screen is open. **Unfocused Height** applies during ordinary play after closing it

Opening chat also shows retained lines without their ordinary age fade. A taller focused panel gives you more reading room without making the closed panel as tall. [Size and dimensions][chat-options] · [Opacity and spacing][opacity-options] · [Chat layout][chat-layout] · [Focused rendering][focused-chat] · [Line aging][chat-aging]

For the size of the whole interface, **GUI Scale** is in **Options → Video Settings...**. See [Graphics settings and packs](GraphicsAndPacks.md#video-settings-apply-or-undo-first) for that menu's apply/undo flow. [GUI Scale control][gui-scale] · [Interface resize][gui-resize] · [Active GUI projection][gui-projection]

## Show, limit, or hide chat

The **Chat** control in Chat Settings has three choices:

| Choice | What it changes |
| --- | --- |
| **Shown** | The default; permits ordinary player chat as well as system messages |
| **Commands Only** | The inspected MattMC server sends system messages but withholds ordinary player chat; does not itself block the chat/command openers |
| **Hidden** | Hides the ordinary chat panel and blocks both normal chat/command openers |

Changing this setting cannot remove launcher or profile restrictions on multiplayer chat. Those restrictions have a local-server exception; **Hidden** does not. Server/mod message types can differ, and Hidden is not a promise to suppress every on-screen notice. [Default and choices][visibility-default] · [Client restrictions][chat-status] · [Screen gate][chat-access] · [Local-server exception][chat-status-rules] · [Server delivery][server-delivery] · [Server filters][server-acceptance]

## Slow a busy conversation

**Chat Delay: None** is the default. Raise Chat Delay in Chat Settings or Accessibility Settings to space out eligible incoming chat, up to **6 seconds**. The setting queues player chat and messages using the disguised-chat path; system messages use a separate immediate path. It changes when messages arrive in the panel, not how long an already displayed line stays visible. Use Notification Time for caption lifetime. [Delay option][delay-option] · [Queue handling][chat-queue] · [Disguised chat][disguised-chat] · [System messages][system-chat]

## Enable and extend sound captions

Turn **Closed Captions** on in Accessibility Settings or Music & Sounds. Captions appear near the bottom right, with **<** or **>** cues where applicable to suggest a sound's direction relative to you. They do not provide an exact location. A sound needs caption text in its sound definition and must pass the caption listener's range check; not every sound produces a caption. [Enablement and range][caption-listener] · [Text and arrows][caption-layout] · [Sound metadata][caption-metadata] · [Sound delivery][sound-delivery]

Increase **Notification Time** in Accessibility Settings to keep cues available longer. Caption lifetime is **3 seconds × this multiplier**: the default **1×** gives 3 seconds, and **2×** gives 6 seconds. The setting ranges from **0.5× to 10×** and is shared with other notifications. These are source timings, not a measured fade schedule. [Multiplier][notification-option] · [Caption lifetime][caption-lifetime] · [Shared use][notification-shared]

**Text Background: Chat** is the default. Chat uses **Text Background Opacity** in either mode; captions use an **80%** background in Chat mode. Choose **Text Background: Everywhere** in Accessibility Settings to make captions use your selected opacity too. [Background setting][background-option] · [Background selection][background-choice] · [Chat background][chat-background] · [Caption background][caption-layout]

## If text is missing

Check **Chat: Shown**, a nonzero Chat Text Size, and chat opacity first. Open chat to distinguish its focused height from the smaller closed panel. If the HUD is hidden, **F1** restores it during normal play: with no screen open, the hidden HUD suppresses ordinary chat and captions. An in-game UI can still request captions while the HUD is hidden. [HUD key][hud-key] · [F1 mapping][key-f1] · [HUD gates][hud-gates]

For missing sound cues, check Closed Captions and the sound's caption/range limits. For cues disappearing too quickly, adjust Notification Time rather than Chat Delay.

Related: [Inventory controls](InventoryControls.md) · [Distortion Effects](../effects/VisibilityEffects.md#distortion-effects-setting) · [Darkness Pulsing](../effects/VisionEffects.md#darkness-pulsing-setting) · [Mechanics](Mechanics.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `9bafc14d2e2943dcfe8a37e9e81dc001386b88bb`; documentation baseline `d723254d406f300bdcf2579d39dfd5a433e43869` has the same `src/main` tree. Effective English labels were checked across all four bundled namespaces and their deprecation rules. [Language loading][language-load] · [Deprecation][language-deprecation] · [Chat labels][chat-labels] · [Caption label][caption-label] · [Timing label][notification-label]

The active whole-frame route reaches the chat/caption producers and provides Rust admission paths for their ordinary text and solid rectangles. This is source evidence, not a live visual or sound test, or a guarantee for every font, resource pack, or frame. No game, UI, audio, settings, or pack test was run. [Active caller][active-caller] · [GUI extraction][gui-extraction] · [Text admission][text-admission] · [Rectangle admission][rectangle-admission] · [Whole-frame submission][frame-submit] · [Native decode][native-decode] · [Native validation][native-validation]

[menus]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/options/OptionsScreen.java#L35-L95
[chat-menu]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/options/ChatOptionsScreen.java#L14-L35
[accessibility-menu]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/options/AccessibilityOptionsScreen.java#L22-L49
[sound-menu]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/options/SoundOptionsScreen.java#L20-L27
[caption-default]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Options.java#L481-L484
[keys]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Options.java#L570-L572
[key-t]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/blaze3d/platform/InputConstants.java#L390-L390
[key-slash]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/blaze3d/platform/InputConstants.java#L454-L454
[openers]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Minecraft.java#L2088-L2093
[chat-options]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Options.java#L338-L369
[opacity-options]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Options.java#L247-L275
[chat-layout]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/components/ChatComponent.java#L451-L489
[focused-chat]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/ChatScreen.java#L213-L219
[chat-aging]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/components/ChatComponent.java#L67-L79
[gui-scale]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/sodium/client/gui/SodiumGameOptionPages.java#L103-L115
[gui-resize]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Minecraft.java#L1529-L1539
[gui-projection]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java#L858-L867
[visibility-default]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Options.java#L239-L245
[chat-status]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Minecraft.java#L2399-L2406
[chat-access]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Minecraft.java#L1121-L1140
[chat-status-rules]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Minecraft.java#L2925-L2951
[server-delivery]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1742-L1759
[server-acceptance]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1801-L1811
[delay-option]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Options.java#L370-L380
[chat-queue]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/multiplayer/chat/ChatListener.java#L37-L111
[disguised-chat]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/multiplayer/chat/ChatListener.java#L132-L143
[system-chat]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/multiplayer/chat/ChatListener.java#L189-L199
[caption-listener]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/components/SubtitleOverlay.java#L35-L55
[caption-layout]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/components/SubtitleOverlay.java#L80-L111
[caption-metadata]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/components/SubtitleOverlay.java#L117-L130
[sound-delivery]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/sounds/SoundEngine.java#L351-L396
[notification-option]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Options.java#L381-L390
[caption-lifetime]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/components/SubtitleOverlay.java#L62-L68
[notification-shared]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/Gui.java#L1476-L1488
[background-option]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Options.java#L497-L506
[background-choice]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Options.java#L1221-L1230
[chat-background]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/components/ChatComponent.java#L88-L108
[hud-key]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/KeyboardHandler.java#L535-L543
[key-f1]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/blaze3d/platform/InputConstants.java#L397-L397
[hud-gates]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/Gui.java#L234-L260
[language-load]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/resources/language/ClientLanguage.java#L35-L56
[language-deprecation]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/locale/DeprecatedTranslationsInfo.java#L77-L92
[chat-labels]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/lang/en_us.json#L5987-L6005
[caption-label]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/lang/en_us.json#L6205-L6210
[notification-label]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/lang/en_us.json#L6157-L6158
[active-caller]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Minecraft.java#L1356-L1370
[gui-extraction]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L968-L1058
[text-admission]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java#L266-L372
[rectangle-admission]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java#L795-L848
[frame-submit]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java#L958-L997
[native-decode]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/rust/render/bridge/world/whole_frame.rs#L1246-L1265
[native-validation]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/rust/render/guirender/frontend/requests.rs#L286-L361

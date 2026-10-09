# Map colors and image processing

> Implemented. Integrated native, Java and Vulkan regression checks
> pass for palette/image processing and map materials. Real vanilla and strict
> shader-final framed-map comparisons pass after ordering, texture-origin and
> source-writer corrections. Full suites/lifecycle checks pass; vanilla still
> misses the performance floor. No isolated migration speedup is established.

## Ownership

[`content/map_color/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/content/map_color)
owns the 62 palette identities/RGB values, four brightness definitions and the
256 packed-color lookup tables. Block state rules reuse its typed `MapColor`;
the former intrinsic-rule path re-exports that type for existing consumers.
Unassigned slots 62/63 remain transparent `NONE`. Shading keeps Frozen's exact
integer arithmetic. RGBA expansion is independent of host endianness.

Java `MapColor` and `Brightness` are compatibility views of small native tables.
Loading those tables must not construct either class: callers can initialize
brightness before color aliases. Queries read immutable local projections and
make no per-pixel native calls. Public aliases, packed-byte masking, null behavior
and range checks retain their existing contracts.

## Map images

Java stages the saved map's **16 KiB indexed colors** as an immutable CPU input,
instead of expanding them into a 64 KiB Java RGBA array. ABI 73 format 3 carries
those indices. The GUI frontend admits the expanded resident byte count first,
then converts once and stores ordinary RGBA8. VulkanicGAL has no map-specific
texture format or resource policy. Map reset/close removes the staged asset;
normal native generation replacement retires its GPU resources. Exact staged
CPU identities resolve before inferred resource paths, so `map/<id>` is not
mistaken for `textures/map/<id>.png`; atlas and reload lifecycles remain explicit.
A retained clean map republishes its indexed CPU data when reload/invalidation
removed the image cache, even when the saved map object did not change.

World-map consumers still use the existing PNG asset transport. Their whole-image
encoder now lives in [`assets/map_image.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/map_image.rs):
it writes indexed PNG directly, with a bounded output buffer and no intermediate
RGBA image. The temporary Java call copies indexed input and encoded output;
Rust's world asset owner decodes it. Removing this PNG transport is future work.
No Java texture or borrowed GPU handle participates in this route.
Primary map submission owns image/decoration geometry. Text and count-only
coverage replay keep decoration labels without enqueuing duplicate map quads;
the framed-map gate still requires exactly one executed image quad.
Explicit indexed maps resolve before resource-manager and texture-manager
lookups; unchanged revisions reuse the registered world payload without another
encode or upload.

Map images and decoration quads use a dedicated semantic material. Rust selects
the lightmap, 0.1 alpha cutoff, source-alpha blending, tested depth writes and
fog, then records these draws after opaque entity/model meshes. The former
generic blended path drew maps before their frame model without writing depth,
so the model erased their color. Text-only replay must also preserve the caller's
pose stack while retaining label transforms. See the real-map checks in
[render verification](../rendering/RENDER-VERIFICATION.md).

Shared world-texture upload converts Minecraft's top-left image rows to the
native sampling convention. The map material normalizes its copied Minecraft
UVs once when that texture-origin metadata requires it; canonical textures keep
their original UVs. An asymmetric Vulkan readback regression covers both origins.
Changing image bytes or flipping every map UV would break another consumer.

Lightmap staging follows the material's resource contract, including maps, in
normal, Fabulous and external composition. Selected-source preparation removes
mesh instances from its provisional graph; a map must still stage its own
lightmap there. The meshless Vulkan regression reproduces that admission failure.

Selected-source admission accepts the map material's blended-cutout mode in
both scalar preparation and pipeline creation. Its render-stage value comes
from the active pack's `MC_RENDER_STAGE_ENTITIES` definition and updates on
reload; map rendering belongs to the entity phase, not translucent terrain.

Framed-map producers now carry ordinary/glow-frame CPU identities. Rust selects
the world glyph writer (`EntitiesTrans`, with the source fallback chain),
resolves `entity.properties` IDs and supplies quad-derived normal, mid-UV and
tangent semantics. Glyphs use an explicit local-texture role, separate from
terrain-atlas ownership. Adjacent material groups retain source order and their
own named outputs within the existing combined submission. The source owner is
[`contracts/glyph.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/contracts/glyph.rs).

Five strict shader-final captures of the generic writer differed from Frozen
(crop RGB17.379/27.635/15.773 in the first frame). The native glyph correction
passes all five crops. The initial corrected invocation still failed a general
harness gate requiring unrelated ModelPart receipts, despite complete typed
item-frame/block-model proof; that failed invocation remains failed.
The final `feature-parity/native-map-policy-final-glyph-20261008/`
passes every gate on release SHA037cbb38, with five independently completed
final-output frames: whole RGB2.337/1.819/2.160 and first crop3.339/2.836/3.443
(tolerance6). All three vanilla framed/rotated/decorated cases also pass.
Runtime sources, native library and Frozen remained unchanged; reviewed images
match and no owned clients were orphaned. The final proof supersedes the earlier
successful proof retired by the normal retention driver.
Other packs/lighting conditions and held-map source-family/runtime coverage
remain unfinished.

Saved map data, world scanning, map decorations, dirty tracking and the transitional
Java CPU asset cache remain separate unfinished owners. This slice does not
complete map gameplay, saving or the entire image asset pipeline.

## Editing and verification

Edit palette declarations and brightness in `content/map_color/mod.rs`; preserve
IDs and append deliberately. Keep palette definitions independent of consumers,
and bounds/encoding in their asset and renderer owners. Rebuild Java and Rust
together after ABI changes; see the [bridge guide](../rendering/JAVA-BRIDGE.md).

The retained binary fixture comes from actual CPU `NativeImage.setPixel` bytes
in untouched Frozen `7a4d181717dc0c7da9086f1a17687dfd522ce417`, one pixel per packed
code 0..255. It detects the previous semantic route's red/blue swap in 208 codes.
Native tests and both staged Java initialization probes match these bytes;
the indexed PNG decodes to the same pixels, including transparency. Integrated
checks cover CPU-image invalidation/republication and an actual Vulkan red/blue
upload/draw. Four map Vulkan regressions reproduce the old opaque-model
overwrite, UV reversal and meshless lightmap failure, and check ordering, nearer
occlusion, transparency, lightmap, alpha cutoff, blending and fog. These and 12 native decoder checks
pass; 60 focused Java checks pass across runs. Five fresh v10 Frozen observer
pairs on the corrected release match all ten semantic digests and source/library
integrity. Bootstrap medians are2.303/2.277s (+1.1%); combined migration allocation
is960.26/1067.78 MB (~10.1% lower), without an isolated map speedup claim.
Receipts: `build/map-policy-rejection-contract-verification-20261008/results.json` and
`build/map-palette-migration-draft/`. The final full workflow passes1,725 Java and2,389 Rust tests, all seven lifecycle
cases and reviewed vanilla/Iris+DH images. Its overall result remains failed:
vanilla misses the FPS/p99 floor. The fresh shader+DH recheck passes with clean
health; original failed receipts remain failed. See
[the current performance record](https://github.com/HungLo2020/MattMC/blob/master/SUMMARY.md).
These scoped results do not establish broad full-client parity.

Before publishing, run native content/asset tests, `NativeMapColorsTest`,
`NativeMapImagesTest`, the raw GUI/ABI regressions, `MapRendererReplayTest` and the v10 Frozen observer.
Then run real framed/decorated/rotated map comparisons and the normal lifecycle
and performance workflow. The existing framed-map fixture uses green and gray,
which cannot detect this channel swap; keep the full CPU fixture and the red/blue
GPU regression alongside it. Record held-map coverage separately.

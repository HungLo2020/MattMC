# RenderDoc input observations

For a missing legacy particle input, inspect Frozen's actual OpenGL draw before
adding a source default. On Linux, the official RenderDoc distribution's
`qrenderdoc` embeds the matching Python SDK. Place `renderdoccmd` and
`qrenderdoc` under `DevUtils/.cache/tools/renderdoc/bin/`, then prepare a fresh
diagnostic Gradle home **outside** the capture artifact root:

```sh
python3 DevUtils/tests/rendering/PrepareFrozenRenderDocLaunch.py \
  --gradle-home DevUtils/.cache/tools/renderdoc/frozen-observation-home
python3 DevUtils/tests/rendering/ObserveRenderDocCapture.py \
  --artifact-root artifacts/graphics-captures/my-particle-observation
```

In another terminal, run the ordinary Frozen OpenGL `Capture.py` invocation
with that `GRADLE_USER_HOME`, the same fresh artifact root,
`--renderdoc-capture` and `--cutout-terrain-particle visible`. Supply the shared
world and immutable selected shader-pack sources as usual. The isolated init
script wraps only the game JVM after normal build/fixture staging; it does not
change Frozen files. It forwards `allJvmArgs` once; Gradle includes `jvmArgs`
there already. Regenerate a fresh diagnostic home after changing the launcher;
previously generated init scripts retain their old argument assembly.
Wrapping the entire harness can interfere with Gradle's
toolchain probes. The observer verifies the exact client process and records at
most three frames. Frozen's running frame counter is a setup receipt, not a live
counter; its confirmed particle fixture is the observer's producer signal.

Replay only after the harness has terminated. Verify the particle vertex stride,
colors/light values and reflected shader input, then inspect whether its array is
enabled and the captured generic value. Do not infer a default from an absent
vertex field or a hardcoded GL attribute location. Three MakeUp oak-leaves draws
in `goal5/makeup-particle-renderdoc-v9/particle-input-replay/` have the exact
fixture bytes and active, disabled `mc_Entity = (0, 0, 0, 1)`. The compact Rust
material contract models that value explicitly; other terrain-only lanes remain
unsupported. Run `cargo test ... material` for lowering, native module compilation
and stream checks. These observations do not establish Current MakeUp image
parity, first-frame correctness or performance. The matrix's CLI replay failure
remains a failure; separate SDK evidence does not rewrite its verdict.

For legacy fullscreen transforms, retain the captured vertex shader as well
as raw inputs and post-VS positions. In the same MakeUp capture, event 1958
uses a unit quad (position/UV stride 20) whose clip corners span `[-1,1]`.
`fullscreen-input-replay-v2/` records those bytes and the native shader's exact
projection, including its all-zero Z column. This establishes composite input
semantics, independently of Current's procedural triangle. Run
`cargo test ... fullscreen_legacy_transform -- --test-threads=1` for Current's
native pixel check; gameplay admission and image parity still need live runs.

`sky-input-replay/` records the same Frozen frame's sky fan (event 917, stride
12, Y=16) and celestial unit XZ quad (event 958, stride 20). Their native
vertex shaders use the draw model-view/projection uniforms, rather than the
composite unit-quad projection. Inspect these separately before lowering a
sky `gl_Vertex` read; source-local coordinates and transformed clip positions
are different contracts. `sky-uniforms.json` retains the exact camera,
celestial model-view, projection, color and TAA offset used by those draws.
RenderDoc's returned `ShaderVariable` matrix values are row-major; transpose
them when forming column-major semantic fixtures. Constant-buffer descriptor
fields come from `GetConstantBlock(...).descriptor` in SDK 1.46.
The same capture's event 888 is Iris's horizon: 2,076 indices, POSITION stride
12, radius 160, Y=±16 and `ColorModulator=(0.7019608,0.8156863,1,1)`.
Its selected sky source precedes the vanilla disc. Native shaders, vertex
bytes and uniform observations are retained in `goal5/horizon-input-replay/`.

Run `cargo test ... sky_legacy_transform -- --test-threads=1` for native
readbacks against those captured inputs. The tests retain the local unit
celestial coordinates, separate draw matrix reads in both stages and the
vanilla sky clock (zero in this capture, while Iris's `sunAngle` is 0.25).
They also check disc geometry/depth and copied color. MakeUp's unmodified
source-stage probe now compiles the declared sky and celestial writers;
neither result establishes live sky image parity, nighttime behavior or a
coherent first world frame. Rebuild the native release library before live runs.


Process identity regressions:

```sh
python3 -m unittest discover -s DevUtils/tests/rendering -p test_renderdoc_observer.py -v
```

The observer rejects foreign working directories, ambiguous clients and a client
class name appearing only as another program's argument. Its SDK subprocess is
bounded; missing producers, disconnected clients and incomplete captures fail.

For an ordinary Frozen world observation without a particle fixture, select
`--producer world`. `--capture-count 1` limits storage to one requested frame;
`--dwell-seconds 10` allows the world to render before that request. The observer
still verifies the exact isolated OpenGL client. World entry alone does not
prove that DH or a particular material was drawn: inspect the captured draw
inputs and attachments during replay before interpreting the observation.
Keep RenderDoc separate from the harness's validation instrumentation and
preserve its disk preflight reserve. Retain ordinary clean-validation results
separately; a RenderDoc observation is not a performance or parity acceptance.

The shader-disabled coast observation in `goal5/dh-water-source-observation/`
retains Frozen frame 2221. SDK inspection found 218 DH draws containing water,
including low-skylight vertices. Event 1064's first vertex has reduced RGBA
57/66/61/255 and sky/block light 0/0; its post-vertex lightmap multiplier is
25/255 in every RGB channel. Inspect the original and post-vertex bytes before
assuming that dark DH rows should be raised to daylight. The automatic harness
RenderDoc replay failed and remains failed; the separate SDK observation does
not change that verdict or establish Current pixel parity.

Start the observer before a short deterministic launch. When more time is needed,
the existing static-pose dwell can keep the isolated world alive without changing
Frozen's readiness policy. During SDK replay, select representative shadow,
terrain, deferred and composite events; replaying every draw can exceed the bound.
In SDK 1.46, `GetReadOnlyResources` returns `UsedDescriptor` entries:
`access.index` selects the reflected resource and `descriptor.resource` identifies
the actual texture. Do not interpret every reflected `fixedBindNumber=0` as unit 0.

Verify depth storage before decoding raw bytes. The buried Complementary capture
under `goal5/underground-light-shafts/` uses D32 normalized unsigned 32-bit depth,
not float32. `GetTextureData` retains native GL rows; `PickPixel` coordinates are
top-left even for GL. Validate the conversion at non-clear pixels before comparing
the raw texture-address domain with Current. Current's full attachment diagnostic
uses the pack shadow extent for `shadow_depth`; inspect its per-attachment width
and height rather than assuming the manifest's screen extent applies to every map.

When comparing fog uniforms, check the starting save pose and timing as well as
the captured camera. Frozen's Iris timer uses elapsed wall time; Current's
deterministic temporal fixture can use a fixed 1/60-second step. Equal frame dwell
alone does not equalize smoothing histories. Save the requested pose in a new
owned fixture before launch with
[`PrepareTerrainTurnFixture.java`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/rendering/PrepareTerrainTurnFixture.java)
`OWNED_COPIED_RUN --pose X,Y,Z,YAW,PITCH`, and apply the same frame cap/dwell to
both clients. Retain exact-frame typed uniform values before attributing the
remaining image difference to rendering policy. A common cap is a control,
not proof that the clocks or custom accumulators matched.

For Current's selected-source attachment capture, enable
`MATTMC_GRAPHICS_AUDIT=1` and
`MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_UNIFORM_RECEIPT=1`. The completed
`gameplay-attachments-frame-N.json` manifest lists `source_uniform_receipts`:
immutable copies of the prepared fullscreen scalar blocks for that exact
frame, correlation and GAL submission. Publication follows completed attachment
readback; rejected captures release their retained CPU data. Decode each field's
32-bit words using its declared scalar type. The ordinary latest-stage receipt
files are overwritten by later frames and may contain unload values, so they
cannot substitute for these manifest-linked records. This diagnostic preserves
rendering policy and allocates no additional GPU resources.

Optional compatibility attachments require a producer. A full dump lists an
unused translucent color/depth snapshot in `attachment_evidence` with
`kind="unavailable"`, `captured=false` and a reason; it emits no PNG/raw readback
for that snapshot. A completed earlier producer or an already-recorded producer
in the same submission admits the readback. Do not initialize unused images just
for a dump or infer zero-valued contents from absent files. The selected source
route can use its own named translucent outputs without this compatibility pair.

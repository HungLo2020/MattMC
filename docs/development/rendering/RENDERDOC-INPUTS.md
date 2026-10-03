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

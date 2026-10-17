# Entity shadow ordering checks

Typed orb placements refer to boundaries in the collected mesh stream. Shadow
capture filters foil and outline meshes, so its compaction must update those
boundaries before the later source-admission mapping. Keep equal-boundary order,
camera placements, culling inputs and shadow roles intact. The
[Java bridge](JAVA-BRIDGE.md) describes the transport and ABI.

Run the focused tests after building the current native library:

```sh
./gradlew -PmattmcRustProfile=release test -x testRustNative -x buildRustNative \
  --tests net.vulkanic.world.ShadowOnlyEntityCaptureTest \
  --tests net.vulkanic.world.ExperienceOrbSemanticCollectorTest \
  --tests net.vulkanic.bridge.ExperienceOrbInstanceEncodingTest \
  --tests net.vulkanic.bridge.WorldEntityCullingEncodingTest
```

Run the full native gate separately when Rust changes. These tests reproduce
foil removal through the real capture-ending code, preserve placement metadata
and residency, reject partial remaps, and check the appended ABI 68 fields.
They supplement gameplay evidence; they cannot establish entity-shadow pixels.

## Copied-world fixture

Copy a complete run containing `saves/Origin` into a fresh artifact directory.
Keep the original and Frozen checkout untouched. Mark only that copied run with
an empty `.shadow-orb-fixture-owned` file. The opt-in
[fixture driver](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/rendering/PrepareShadowOrbFixture.java)
adds a persistent ordinary enchanted sword item, then an XP orb, to the chosen
entity chunk. It retains existing entities, rejects duplicate fixture UUIDs,
and reopens the region file to verify persistence. It uses CPU storage APIs;
it creates no renderer or presenter.

```sh
./gradlew -PmattmcRustProfile=release \
  -I DevUtils/tests/rendering/shadow_orb_fixture.gradle prepareShadowOrbFixture \
  --args='/absolute/copied/run 162.5 100.0 533.5'
```

Those coordinates lie behind the Origin capture camera at `(150.5,100,530.5)`,
yaw 105. Set `MATTMC_CAPTURE_RUN_SOURCE` to the copied run and use the same shader
pack, camera, settings and world in the ordinary Current/Frozen OpenGL capture
pair. Preserve validation, readiness and image tolerances. Follow with normal
moving gameplay and transitions for broader ordering and resource coverage.

Historical scoped evidence lives under `goal5/shadow-orb-ordering-fix/` and
`goal5/shadow-orb-entity-runtime-pair/`: the CPU regression failed before the fix;
the real shader session exercised compaction and both clients completed cleanly.
Its static image result does not establish general shadow parity or flicker repair.

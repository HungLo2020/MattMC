# Vulkan Parity Audit

Repo-owned tools for incrementally moving OpenGL/Vulkan parity forward with
evidence instead of screenshots alone.

## Source Audit

```bash
python3 DevUtils/VulkanParityAudit/vulkan_parity_audit.py source-audit --write
```

Reports architecture pressure points:

- `VulkanicAPI.GL_*` usage
- direct OpenGL references outside OpenGL backend code
- GL-named subsystem paths
- `VulkanicAPI.getCommandContext()` hotspots

## Capture Diff

```bash
python3 DevUtils/VulkanParityAudit/vulkan_parity_audit.py auto-diff --write
```

Finds the newest matching OpenGL/Vulkan `RunDevCapture.sh` pair and compares
`ShaderInputParity*` log events.

For quick triage on huge logs:

```bash
python3 DevUtils/VulkanParityAudit/vulkan_parity_audit.py auto-diff \
  --max-resource-events 30000 \
  --max-uniform-buffer-events 50000 \
  --max-vertex-input-events 30000 \
  --write
```

Important classifications:

- `strict-ubo-payload-mismatch`: same semantic UBO, different payload hash
- `ubo-range-metadata-difference`: payload matches, bound range metadata differs
- `strict-sampler-mismatch`: same semantic sampler, different texture metadata
- `backend-only-resource`: observed on only one backend; treat as lower-confidence
  until reproduced in synchronized captures

The intended workflow is:

1. Capture OpenGL and Vulkan with the same world, camera, time, shader state, and DH state.
2. Run this audit.
3. Fix the smallest strict mismatch first.
4. Re-run the audit and then RunDevCapture visual validation.

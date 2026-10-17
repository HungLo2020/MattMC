#!/usr/bin/env python3
"""Copy a game directory into a small-radius Distant Horizons test source.

Current writes DH data formats Frozen cannot read, so Current/Frozen DH
comparisons start without a DH cache. With the default 128-chunk radius each
client would spend the run regenerating LODs; this copy sets a small LOD and
generation radius (default 32 chunks) so both clients finish generating during
settle and the comparison measures steady-state DH. The source is unchanged.

    python3 DevUtils/tests/rendering/PrepareSmallDhSource.py \\
      --source artifacts/performance/quick-comparison-20261008/compatible-source/run \\
      --output artifacts/testing-inputs/dh-small/run
    MATTMC_CAPTURE_RUN_SOURCE=$PWD/artifacts/testing-inputs/dh-small/run \\
      python3 DevUtils/PerfAudit/Gameplay.py ... --world-distant-horizons-real-world
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import shutil


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--source", type=Path, required=True, help="game directory with config/DistantHorizons.toml")
    parser.add_argument("--output", type=Path, required=True, help="new game directory to create")
    parser.add_argument("--dh-radius", type=int, default=32, help="LOD and generation radius in chunks (default 32)")
    args = parser.parse_args()
    if args.output.exists():
        parser.error(f"{args.output} already exists")
    shutil.copytree(args.source, args.output, symlinks=True)
    # No DH cache: each client generates its own, in the format it writes.
    for cache in args.output.glob("saves/*/data/DistantHorizons.sqlite*"):
        cache.unlink()
    config = args.output / "config" / "DistantHorizons.toml"
    text = config.read_text()
    for key in ("lodChunkRenderDistanceRadius", "maxGenerationRequestDistance"):
        text, count = re.subn(rf"(?m)^(\s*{key}\s*=\s*)\d+", rf"\g<1>{args.dh_radius}", text)
        if count != 1:
            parser.error(f"expected one {key} in {config}, found {count}")
    config.write_text(text)
    print(f"{args.output}: DH radius {args.dh_radius} chunks, no DH cache")


if __name__ == "__main__":
    main()

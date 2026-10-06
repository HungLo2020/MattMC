#!/usr/bin/env python3
"""Verify the real Gradle native staging task without Cargo or a client.

An isolated build directory and fake Cargo payload keep all normal builds,
libraries and running clients untouched. Existing read-only mappings must keep
their original bytes when a later build publishes a replacement library.
"""

import argparse
import json
import mmap
import os
from pathlib import Path
import subprocess
import sys
import tempfile


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact-root", type=Path,
                        default=Path("artifacts/native-library-staging"))
    args = parser.parse_args()
    if os.name == "nt":
        parser.error("This mapping regression requires Linux or macOS; Windows file sharing differs.")
    repo = Path(__file__).resolve().parents[3]
    artifact_root = args.artifact_root.resolve()
    artifact_root.mkdir(parents=True, exist_ok=True)
    cargo_name = ("mattmc_rust.dll" if os.name == "nt" else
                  "libmattmc_rust.dylib" if sys.platform == "darwin" else
                  "libmattmc_rust.so")
    gradle = "gradlew.bat" if os.name == "nt" else "./gradlew"
    with tempfile.TemporaryDirectory(prefix="isolated-", dir=artifact_root) as temp:
        isolated = Path(temp)
        payload = isolated / "payload.bin"
        fake_cargo = isolated / "fake-cargo.py"
        fake_cargo.write_text(
            "from pathlib import Path\nimport sys\n"
            "target=Path(sys.argv[1])/'release'/sys.argv[2]\n"
            "target.parent.mkdir(parents=True,exist_ok=True)\n"
            "target.write_bytes(Path(sys.argv[3]).read_bytes())\n",
            encoding="utf-8",
        )
        init = isolated / "isolate.gradle"
        init.write_text(
            "gradle.beforeProject { project ->\n"
            "  project.layout.buildDirectory.set(new File(System.getenv('MATTMC_STAGING_TEST_BUILD')))\n"
            "  project.afterEvaluate {\n"
            "    project.tasks.named('buildRustNative') { task ->\n"
            "      task.commandLine(System.getenv('MATTMC_STAGING_TEST_PYTHON'),\n"
            "        System.getenv('MATTMC_STAGING_TEST_CARGO'),\n"
            "        task.environment.get('CARGO_TARGET_DIR'),\n"
            "        System.getenv('MATTMC_STAGING_TEST_CARGO_NAME'),\n"
            "        System.getenv('MATTMC_STAGING_TEST_PAYLOAD'))\n"
            "    }\n"
            "  }\n"
            "}\n",
            encoding="utf-8",
        )
        env = os.environ.copy()
        env.update(MATTMC_STAGING_TEST_BUILD=str(isolated / "build"),
                   MATTMC_STAGING_TEST_PYTHON=sys.executable,
                   MATTMC_STAGING_TEST_CARGO=str(fake_cargo),
                   MATTMC_STAGING_TEST_CARGO_NAME=cargo_name,
                   MATTMC_STAGING_TEST_PAYLOAD=str(payload))

        def stage(contents: bytes, label: str) -> None:
            payload.write_bytes(contents)
            with (artifact_root / f"{label}.log").open("wb") as log:
                subprocess.run(
                    [gradle, "--no-daemon", "--offline", "--console=plain",
                     "-I", str(init), "-PmattmcRustProfile=release",
                     "buildRustNative", "--rerun-tasks"],
                    cwd=repo, env=env, stdout=log, stderr=subprocess.STDOUT,
                    check=True,
                )

        original = b"original native mapping\n" * 65536
        stage(original, "initial")
        files = list((isolated / "build/rust/native").glob("mattmc_rust*"))
        assert len(files) == 1, files
        native = files[0]
        with native.open("rb") as opened:
            with mmap.mmap(opened.fileno(), 0, access=mmap.ACCESS_READ) as mapping:
                replacement = b"replacement native payload\n" * 131072
                stage(replacement, "replacement")
                assert native.read_bytes() == replacement
                assert mapping[:] == original, "Staging overwrote a running library's mapped bytes"
                assert not os.path.samestat(os.fstat(opened.fileno()), native.stat()), \
                    "Staging must replace the file instead of overwriting its inode"
                with native.open("rb") as second_opened:
                    with mmap.mmap(second_opened.fileno(), 0, access=mmap.ACCESS_READ) as second_mapping:
                        smaller = b"smaller native payload\n" * 1024
                        stage(smaller, "smaller-replacement")
                        assert native.read_bytes() == smaller
                        assert mapping[:] == original
                        assert second_mapping[:] == replacement, \
                            "A smaller replacement invalidated an existing mapping"
        assert len(list(native.parent.iterdir())) == 1, "Temporary staging files leaked"
        (artifact_root / "verification.json").write_text(
            json.dumps({"status": "passed", "existing_mapping_preserved": True,
                        "replacement_complete": True, "old_inode_preserved": True,
                        "smaller_replacement_preserves_both_mappings": True,
                        "temporary_files_removed": True,
                        "normal_build_directory_untouched": True}, indent=2) + "\n",
            encoding="utf-8",
        )
    print("Native staging preserves existing mappings and publishes a complete replacement.")


if __name__ == "__main__":
    main()

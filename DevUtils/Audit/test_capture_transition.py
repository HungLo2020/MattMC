"""Exercise the actual shell runner's presented-title early-exit branch."""
import subprocess
import unittest
from pathlib import Path


class PresentedTitleCompletionTest(unittest.TestCase):
    def test_acknowledgment_does_not_truncate_transition_timeline(self):
        source = (Path(__file__).resolve().parents[1] / "Common/capture_runner.sh").read_text()
        begin = source.index('    if capture_frozen_title_presented_frame "$CLIENT_PID"')
        end = source.index('\n    if [[ "$screenshot_enabled"', begin)
        branch = source[begin:end]
        for transition, expected in (("true", "false"), ("false", "true")):
            with self.subTest(transition=transition):
                # Stub only external actions. Execute the runner's real branch;
                # neither a JVM nor a renderer is started by this regression.
                script = '''
capture_frozen_title_presented_frame() { return 0; }
kill() { :; }
CLIENT_PID=1
GRADLE_PID=1
META_LOG=/dev/null
MATTMC_FROZEN_TITLE_FRAME_CAPTURE_DIR=/unused
elapsed=5
title_screen_capture_completed=false
TITLE_SCREEN_TRANSITION_CAPTURE=$1
for iteration in 1; do
''' + branch + '''
done
printf '%s' "$title_screen_capture_completed"
'''
                result = subprocess.run(["bash", "-c", script, "capture-regression", transition],
                                        text=True, capture_output=True, check=True)
                self.assertEqual(expected, result.stdout)


if __name__ == "__main__":
    unittest.main()

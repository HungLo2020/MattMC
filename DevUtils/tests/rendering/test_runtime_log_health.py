"""The lifecycle false-positive and the errors that must still reject a run."""
import gzip
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parent))
from runtime_log_health import separate_shutdown_disconnects

STOP = "[22:11:38] [Render thread/INFO]: Stopping!\n"
CLOSE = ("[22:11:39] [Server thread/INFO]: HungLo lost connection: "
         "Internal Exception: java.nio.channels.ClosedChannelException\n")


class RuntimeLogHealthTest(unittest.TestCase):
    def test_exact_ordered_shutdown_is_separated_and_reported(self):
        text,count=separate_shutdown_disconnects(STOP+CLOSE)
        self.assertEqual(1,count)
        self.assertNotIn("Exception",text)

    def test_earlier_closes_errors_and_stack_traces_remain(self):
        cases=[CLOSE+STOP,
               STOP.replace("thread/INFO","thread/ERROR")+CLOSE,
               STOP+CLOSE.replace("thread/INFO","thread/ERROR"),
               STOP+CLOSE.replace("ClosedChannelException","IOException"),
               STOP+CLOSE+"java.nio.channels.ClosedChannelException\n",
               "java.lang.IllegalStateException\n"+STOP+CLOSE]
        for source in cases:
            with self.subTest(source=source):
                text,_=separate_shutdown_disconnects(source)
                self.assertIn("Exception",text)

    def test_panic_and_gal_dependency_violations_are_preserved(self):
        source=STOP+CLOSE+"panicked at frame.rs\nDependencyViolation\n"
        text,count=separate_shutdown_disconnects(source)
        self.assertEqual(1,count)
        self.assertIn("panicked at",text)
        self.assertIn("DependencyViolation",text)

    def test_lifecycle_scanner_reads_both_plain_and_gzip_logs(self):
        import RunLifecycleGate as gate
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            capture=root/"current-rust-vulkan-shaders-on/capture/run-01/capture"
            capture.mkdir(parents=True)
            for suffix in [".log",".log.gz"]:
                with self.subTest(suffix=suffix):
                    p=capture/("runClient_1"+suffix)
                    data=(STOP+CLOSE).encode()
                    p.write_bytes(gzip.compress(data) if suffix.endswith("gz") else data)
                    counts=gate.scan_logs(root)
                    self.assertEqual(0,counts["exception"])
                    self.assertEqual(1,counts["shutdown-disconnects"])
                    p.unlink()
            (capture/"runClient_2.log").write_text(CLOSE+STOP)
            self.assertEqual(2,gate.scan_logs(root)["exception"])


if __name__=="__main__":
    unittest.main()

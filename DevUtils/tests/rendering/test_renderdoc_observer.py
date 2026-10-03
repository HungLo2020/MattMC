import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from ObserveRenderDocCapture import CONFIG_ENV, SCRIPT_PATH, isolated_pid


CLIENT = 'net.fabricmc.loader.impl.launch.knot.KnotClient'


class RenderDocObserverIdentityTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.game = self.root / 'game_dir_test'
        self.game.mkdir()
        self.proc = self.root / 'proc'
        self.proc.mkdir()
        self.java = self.root / 'java'
        self.java.touch()

    def process(self, pid=100, *, cwd=None, args=None):
        process = self.proc / str(pid)
        process.mkdir()
        (process / 'exe').symlink_to(self.java)
        (process / 'cwd').symlink_to(cwd or self.game)
        values = args or ['java', '-cp', 'libraries', CLIENT]
        (process / 'cmdline').write_bytes(b'\0'.join(v.encode() for v in values) + b'\0')
        (process / 'stat').write_text(
            f'{pid} (java (worker)) ' + ' '.join(['S'] + ['0'] * 18 + ['55']))
        return process

    def test_gradle_replaced_arguments_still_identify_the_exact_isolated_client(self):
        self.process(args=['java', '-Xmx8G', '-cp', 'libraries', CLIENT,
                           '--quickPlaySingleplayer=Origin'])
        self.assertEqual((100, '55'), isolated_pid(self.game, self.proc))

    def test_foreign_directory_and_misleading_client_token_are_rejected(self):
        other = self.root / 'other'
        other.mkdir()
        process = self.process(cwd=other)
        self.assertIsNone(isolated_pid(self.game, self.proc))
        (process / 'cwd').unlink()
        (process / 'cwd').symlink_to(self.game)
        for args in (['java', '-cp', CLIENT, 'other.Main'],
                     ['java', 'other.Main', CLIENT],
                     ['java', '-jar', 'other.jar', CLIENT],
                     ['java', CLIENT, '--gameDir', str(other)]):
            (process / 'cmdline').write_bytes(b'\0'.join(v.encode() for v in args) + b'\0')
            self.assertIsNone(isolated_pid(self.game, self.proc))

    def test_ambiguous_clients_fail_instead_of_selecting_the_first(self):
        self.process()
        self.process(101)
        with self.assertRaisesRegex(RuntimeError, 'more than one client'):
            isolated_pid(self.game, self.proc)

    def test_embedded_sdk_namespace_does_not_need_dunder_file(self):
        namespace = {'__name__': 'renderdoc_console_import_test'}
        with patch.dict(os.environ, {CONFIG_ENV: json.dumps({'script_path': str(SCRIPT_PATH)})}):
            exec(compile(SCRIPT_PATH.read_text(), str(SCRIPT_PATH), 'exec'), namespace)
        self.assertNotIn('__file__', namespace)
        self.assertEqual(SCRIPT_PATH, namespace['SCRIPT_PATH'])


if __name__ == '__main__':
    unittest.main()

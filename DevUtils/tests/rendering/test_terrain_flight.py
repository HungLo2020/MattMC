"""Flight observations must reject failed setup and release input on interruption."""
import ctypes as C
from pathlib import Path
import signal
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import RunTerrainFlight as flight
from x11_game_input import GameInput


class TerrainFlightTest(unittest.TestCase):
    DH_CONFIG = '''[client.advanced.debugging]
rendererMode = "DEFAULT"
[client.advanced.graphics.quality]
lodChunkRenderDistanceRadius = 32
vanillaFadeMode = "DOUBLE_PASS"
[client.advanced.graphics.fog]
enableDhFog = true
[common.worldGenerator]
enableDistantGeneration = false
'''

    def test_standalone_entrypoint_loads_shared_tools_without_inherited_python_path(self):
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run([sys.executable, str(Path(flight.__file__).resolve()), '--help'],
                                    cwd=directory, text=True, capture_output=True, timeout=15)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('forest', result.stdout)

    def test_inland_pose_is_accepted_by_targeted_input_but_other_teleports_are_rejected(self):
        controller = GameInput.__new__(GameInput)
        controller.tap = mock.Mock()
        with mock.patch('x11_game_input.time.sleep'):
            controller.command('tp @s 150.5 95 530.5 285 25')
        self.assertEqual(controller.tap.call_args_list[0], mock.call('slash'))
        self.assertEqual(controller.tap.call_args_list[-1], mock.call('Return'))
        controller.tap.reset_mock()
        with self.assertRaises(ValueError):
            controller.command('tp @s 150.5 95 530.5 285 25; kill @e')
        controller.tap.assert_not_called()

    def test_stationary_turn_observer_starts_after_typing_and_before_command_submission(self):
        controller = GameInput.__new__(GameInput)
        events = []
        controller.tap = lambda key: events.append(key)
        with mock.patch('x11_game_input.time.sleep'):
            controller.command('tp @s 150.5 95 530.5 105 25', before_submit=lambda: events.append('observer'))
        self.assertEqual(events[-3:], ['5', 'observer', 'Return'])
        self.assertEqual(events[0], 'slash')

    def test_first_turn_requires_a_world_view_not_just_a_joined_server_or_loading_screen(self):
        self.assertFalse(flight.debug_world_text('Loading terrain... HungLo joined the game'))
        self.assertFalse(flight.debug_world_text('Facing: west'))
        self.assertTrue(flight.debug_world_text('Facing: east (Towards positive X)\nSection-relative: 06 15 02'))

    def test_preserved_observation_still_requires_shared_disk_reserve_and_run_estimate(self):
        required = 9 * 1024 ** 3
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with mock.patch.object(flight.artifact_retention, 'free_bytes', return_value=required - 1):
                with self.assertRaisesRegex(RuntimeError, 'insufficient free disk'):
                    flight.flight_disk_preflight(root)
            with mock.patch.object(flight.artifact_retention, 'free_bytes', return_value=required):
                receipt = flight.flight_disk_preflight(root)
            self.assertEqual(receipt['reserve_bytes'], 8 * 1024 ** 3)
            self.assertEqual(receipt['estimated_run_bytes'], 1024 ** 3)

    def test_attachment_observation_reserves_additional_readback_and_audit_storage(self):
        required = 10 * 1024 ** 3
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with mock.patch.object(flight.artifact_retention, 'free_bytes', return_value=required - 1):
                with self.assertRaisesRegex(RuntimeError, 'insufficient free disk'):
                    flight.flight_disk_preflight(root, diagnostic_attachments=True)
            with mock.patch.object(flight.artifact_retention, 'free_bytes', return_value=required):
                receipt = flight.flight_disk_preflight(root, diagnostic_attachments=True)
        self.assertEqual(receipt['reserve_bytes'], 8 * 1024 ** 3)
        self.assertEqual(receipt['estimated_run_bytes'], 2 * 1024 ** 3)

    def test_dh_configuration_rejects_disabled_unbounded_or_generating_runs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'config').mkdir()
            path = root / 'config/DistantHorizons.toml'
            path.write_text(self.DH_CONFIG)
            self.assertEqual(flight.verify_dh_enabled(root)['dh_radius_chunks'], 32)
            for before, after in [('"DEFAULT"', '"DISABLED"'), ('= 32', '= 0'),
                                  ('= 32', '= 65'), ('= 32', '= true'),
                                  ('enableDistantGeneration = false', 'enableDistantGeneration = true')]:
                path.write_text(self.DH_CONFIG.replace(before, after))
                with self.assertRaises(RuntimeError):
                    flight.verify_dh_enabled(root)

    def test_dh_source_requires_nonempty_frozen_compatible_database_without_writing_it(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'config').mkdir()
            (root / 'config/DistantHorizons.toml').write_text(self.DH_CONFIG)
            (root / 'saves/Origin/data').mkdir(parents=True)
            database = root / 'saves/Origin/data/DistantHorizons.sqlite'
            with sqlite3.connect(database) as connection:
                connection.execute('CREATE TABLE FullData (DataFormatVersion INTEGER)')
            with self.assertRaises(RuntimeError):
                flight.verify_dh_source(root, 'Origin')
            with sqlite3.connect(database) as connection:
                connection.execute('INSERT INTO FullData VALUES (2)')
            before = database.read_bytes()
            self.assertEqual(flight.verify_dh_source(root, 'Origin')['database_format_counts'], [(2, 1)])
            self.assertEqual(database.read_bytes(), before)
            with sqlite3.connect(database) as connection:
                connection.execute('INSERT INTO FullData VALUES (4)')
            with self.assertRaises(RuntimeError):
                flight.verify_dh_source(root, 'Origin')

    def test_dh_trace_requires_valid_submission_and_matching_video_interval(self):
        line = '[MattMC DH] submitted world_frame=8 submission=12 instances=5 opaque=2 transparent=1 water=2 wall_ms=1000'
        samples = flight.dh_execution_samples(line)
        self.assertEqual(len(samples), 1)
        wrapped = '[08:06:16] [Render thread/INFO]: [STDOUT]: ' + line
        self.assertEqual(flight.dh_execution_samples(wrapped), samples)
        self.assertEqual(flight.dh_execution_samples('[08:06:16] [Render thread/INFO]: [System] [CHAT] ' + line), [])
        self.assertEqual(flight.dh_execution_samples('unrelated message ' + line), [])
        for invalid in (line.replace('instances=5', 'instances=6'),
                        line.replace('submission=12', 'submission=0'),
                        line.replace('water=2', 'water=-1')):
            with self.assertRaises(RuntimeError):
                flight.dh_execution_samples(invalid)
        video = {'status': 'complete', 'ffmpeg_wall_start_ns': 999_000_000,
                 'ffmpeg_wall_end_ns': 1_001_000_000}
        self.assertEqual(flight.dh_submissions_for_video(samples, video), samples)
        for invalid in (dict(video, status='failed'), dict(video, ffmpeg_wall_start_ns=1_002_000_000)):
            with self.assertRaises(RuntimeError):
                flight.dh_submissions_for_video(samples, invalid)

    def test_setup_input_does_not_wait_for_dh_before_positioning_the_camera(self):
        proof = {'status': 'verified', 'windowProperties': 'Minecraft - Singleplayer'}
        route = '[MattMC shaders] shader route active'
        self.assertTrue(flight.input_ready(proof, 'rust-vulkan', 'on', route))
        self.assertTrue(flight.input_ready(proof, 'rust-vulkan', 'off', ''))
        self.assertTrue(flight.input_ready(proof, 'opengl', 'off', ''))

    def controller(self):
        controller = object.__new__(GameInput)
        controller.pid, controller.identity = 42, 'start'
        controller.target, controller.window = '0x123', 0x123
        controller.display, controller.root = 5, 6
        controller.original_focus, controller.previous_handler = 7, 8
        controller.errors, controller.held = [], set()
        controller.x = mock.Mock()
        controller.x.XKeysymToKeycode.return_value = 20
        controller.x.XSendEvent.return_value = 1
        controller.focus = mock.Mock(return_value=controller.window)
        controller.alive = mock.Mock(return_value=True)
        return controller

    def test_focus_loss_rejects_new_input_but_releases_held_game_key(self):
        controller = self.controller()
        controller.held.add('w')
        controller.focus.return_value = 777
        with self.assertRaises(RuntimeError):
            controller.key('s', True)
        controller.x.XSendEvent.assert_not_called()
        controller.key('w', False)
        event = controller.x.XSendEvent.call_args.args[-1]._obj.key
        self.assertEqual((event.type, event.window), (3, controller.window))
        self.assertFalse(controller.held)

    def test_look_motion_requires_live_focus_and_checks_it_again_after_delivery(self):
        controller = self.controller()
        controller.xtest = mock.Mock()
        controller.xtest.XTestFakeRelativeMotionEvent.return_value = 1
        controller.look_relative(100, 0)
        controller.xtest.XTestFakeRelativeMotionEvent.assert_called_once_with(5, 100, 0, 0)
        self.assertEqual(controller.focus.call_count, 2)
        controller.xtest.reset_mock()
        controller.focus.return_value = 777
        with self.assertRaises(RuntimeError):
            controller.look_relative(100, 0)
        controller.xtest.XTestFakeRelativeMotionEvent.assert_not_called()

    def test_look_motion_rejects_unbounded_and_noninteger_input_before_delivery(self):
        controller = self.controller()
        controller.xtest = mock.Mock()
        for dx, dy in [(201, 0), (0, -201), (float('nan'), 0), (True, 0)]:
            with self.assertRaises(ValueError):
                controller.look_relative(dx, dy)
        controller.xtest.XTestFakeRelativeMotionEvent.assert_not_called()

    def test_gone_or_reused_pid_never_receives_new_input(self):
        controller = self.controller()
        controller.alive.return_value = False
        controller.held.add('w')
        with self.assertRaises(RuntimeError):
            controller.key('s', True)
        controller.key('w', False)
        controller.x.XSendEvent.assert_not_called()
        self.assertFalse(controller.held)

    def test_selector_and_camel_case_commands_generate_shifted_keys(self):
        controller = self.controller()
        for name in ('at', 'D', 'C'):
            controller.key(name, True)
            event = controller.x.XSendEvent.call_args.args[-1]._obj.key
            self.assertEqual(event.state, 1)
        controller.key('w', True)
        self.assertEqual(controller.x.XSendEvent.call_args.args[-1]._obj.key.state, 0)

    def test_close_preserves_user_focus_and_restores_xlib_handler_even_on_error(self):
        controller = self.controller()
        controller.focus.return_value = 777
        controller.x.XCloseDisplay.side_effect = RuntimeError('lost connection')
        with self.assertRaises(RuntimeError):
            controller.close()
        controller.x.XSetInputFocus.assert_not_called()
        controller.x.XSetErrorHandler.assert_called_once_with(8)
        self.assertIsNone(controller.display)
        self.assertFalse(controller.held)

    def test_window_error_is_reported_without_aborting_cleanup(self):
        controller = self.controller()
        controller.held.add('w')
        controller.errors.append('BadWindow')
        errors = controller.close()
        self.assertIn('BadWindow', errors)
        controller.x.XCloseDisplay.assert_called_once()
        controller.x.XSetErrorHandler.assert_called_once()
        self.assertIsNone(controller.display)

    def test_only_new_command_success_is_admitted(self):
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / 'latest.log'
            old = 'Set the time to 6000\n'
            log.write_text(old + 'unrelated server message\n')
            self.assertIsNone(flight.command_acknowledgment(log, len(old), ('Set the time to 6000',)))
            log.write_text(old + 'Incorrect argument for command\n')
            with self.assertRaises(RuntimeError):
                flight.command_acknowledgment(log, len(old), ('Set the time to 6000',))
            log.write_text(old + 'Set the time to 6000\n')
            self.assertEqual(flight.command_acknowledgment(log, len(old), ('Set the time to 6000',)),
                             'Set the time to 6000')

    def test_wrapper_exit_zero_does_not_hide_client_crash_or_validation_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            meta = root / 'meta_run.txt'
            base = 'timeout=true\nend_epoch=123\nexit_code=143\n'
            meta.write_text(base)
            self.assertEqual(flight.runtime_outcome(root, 0)['status'], 'bounded_timeout_reaped')
            for text in ('timeout=false\nend_epoch=123\nexit_code=0\n',
                         base.replace('143', '1'), base + 'memory_guard_triggered=true\n',
                         base + 'cleanup_client_core_dumping=true\n'):
                meta.write_text(text)
                with self.assertRaises(RuntimeError):
                    flight.runtime_outcome(root, 0)
            meta.write_text(base)
            diagnostics = root / 'validation_events_run.log'
            diagnostics.write_text('VUID-VkImageLayout-layout-00000')
            with self.assertRaises(RuntimeError):
                flight.runtime_outcome(root, 0)
            diagnostics.unlink()
            (root / 'crash_reports_run.txt').write_text('/copied-world/crash.txt\n')
            with self.assertRaises(RuntimeError):
                flight.runtime_outcome(root, 0)

    def test_legacy_frozen_launch_explicitly_enters_the_world_without_benchmark(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'DevUtils/Common').mkdir(parents=True)
            shell = root / 'DevUtils/Common/capture_runner.sh'
            shell.touch()
            engine, command = flight.launch_command(root, root / 'output', 'opengl', 'off', 'Origin')
            self.assertEqual(engine, shell)
            client_args = command[command.index('--client-args') + 1]
            self.assertIn('--quickPlaySingleplayer=Origin', client_args)
            self.assertIn('--width 1280 --height 720', client_args)
            self.assertIn('enableShaders=false', client_args)
            _, enabled_command = flight.launch_command(root, root / 'output', 'opengl', 'on', 'Origin')
            self.assertIn('enableShaders=true', enabled_command[enabled_command.index('--client-args') + 1])
            self.assertNotIn('--skip-tests', command)
            self.assertNotIn('--deterministic-camera-capture', command)

    def test_inherited_shader_state_cannot_masquerade_as_vanilla(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'config').mkdir()
            path = root / 'config/iris.properties'
            path.write_text('#Iris config\nenableShaders=true\nshaderPack=Pack.zip\n')
            with self.assertRaises(RuntimeError):
                flight.verify_shader_configuration(root, 'off')
            self.assertEqual(flight.verify_shader_configuration(root, 'on')['shader_pack'], 'Pack.zip')
            path.write_text('enableShaders=false\nshaderPack=Pack.zip\n')
            self.assertEqual(flight.verify_shader_configuration(root, 'off')['iris_enable_shaders'], 'false')

    def test_generation_disabled_does_not_imply_renderer_disabled(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'config').mkdir()
            path = root / 'config/DistantHorizons.toml'
            for mode in ('DEFAULT', 'DEBUG', ''):
                path.write_text('[client.advanced.debugging]\nrendererMode="' + mode + '"\n'
                                '[client.worldGenerator]\nenableDistantGeneration=false\n')
                with self.assertRaises(RuntimeError):
                    flight.verify_dh_disabled(root)
            path.write_text('[client.advanced.debugging]\nrendererMode="DISABLED"\n')
            self.assertEqual(flight.verify_dh_disabled(root)['dh_renderer_mode'], 'DISABLED')

    def test_shader_archive_content_is_verified_even_when_the_name_and_enabled_state_match(self):
        import hashlib
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'config').mkdir()
            (root / 'shaderpacks').mkdir()
            (root / 'config/iris.properties').write_text('enableShaders=true\nshaderPack=Pack.zip\n')
            archive = root / 'shaderpacks/Pack.zip'
            archive.write_bytes(b'original pack')
            expected = hashlib.sha256(archive.read_bytes()).hexdigest()
            result = flight.verify_shader_configuration(root, 'on', expected)
            self.assertEqual(result['shader_pack_sha256'], expected)
            archive.write_bytes(b'replaced pack with extra declarations')
            with self.assertRaisesRegex(RuntimeError, 'archive differs'):
                flight.verify_shader_configuration(root, 'on', expected)

    def test_missing_selected_archive_rejects_shader_observation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'config').mkdir()
            (root / 'config/iris.properties').write_text('enableShaders=true\nshaderPack=Pack.zip\n')
            with self.assertRaisesRegex(RuntimeError, 'archive is missing'):
                flight.verify_shader_configuration(root, 'on', '0' * 64)

    def test_disabled_shader_observation_does_not_require_an_unused_archive(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'config').mkdir()
            (root / 'config/iris.properties').write_text('enableShaders=false\nshaderPack=Pack.zip\n')
            result = flight.verify_shader_configuration(root, 'off', '0' * 64)
            self.assertEqual(result['iris_enable_shaders'], 'false')
            self.assertNotIn('shader_pack_sha256', result)

    def test_server_join_and_shader_compilation_do_not_admit_input_on_loading_screen(self):
        route = '[MattMC shaders] frame 77 shader route active'
        loading = {'status': 'verified', 'windowProperties': 'WM_NAME = "Minecraft* 1.21.10"'}
        playing = {'status': 'verified', 'windowProperties': 'WM_NAME = "Minecraft* 1.21.10 - Singleplayer"'}
        self.assertFalse(flight.input_ready(loading, 'rust-vulkan', 'on', route))
        self.assertFalse(flight.input_ready(playing, 'rust-vulkan', 'on', ''))
        self.assertTrue(flight.input_ready(playing, 'rust-vulkan', 'on', route))
        self.assertFalse(flight.input_ready(playing, 'rust-vulkan', 'on',
                         route + '\n[MattMC shaders] frame 78 shader route vanilla fallback: missing source'))
        self.assertTrue(flight.input_ready(playing, 'opengl', 'off', ''))
        self.assertFalse(flight.input_ready({'status': 'unverified', 'windowProperties': ' - Singleplayer'},
                                         'opengl', 'off', ''))

    def test_orphan_cleanup_never_signals_a_reused_pid(self):
        root = Path('/owned/capture')
        with mock.patch.object(flight, 'client_pid', return_value=(42, root / 'game_dir_1')), \
                mock.patch.object(flight, 'process_start', side_effect=['old', 'new']), \
                mock.patch.object(flight.os, 'pidfd_open', return_value=123), \
                mock.patch.object(flight.os, 'close') as close, \
                mock.patch.object(flight.signal, 'pidfd_send_signal') as kill:
            with self.assertRaises(RuntimeError):
                flight.stop_orphaned_client(root)
            kill.assert_not_called()
            close.assert_called_once_with(123)

    def test_orphan_cleanup_never_signals_a_client_outside_the_owned_root(self):
        root = Path('/owned/capture')
        with mock.patch.object(flight, 'client_pid', return_value=(42, Path('/someone/game_dir_1'))), \
                mock.patch.object(flight.os, 'pidfd_open') as open_pid:
            with self.assertRaises(RuntimeError):
                flight.stop_orphaned_client(root)
            open_pid.assert_not_called()

    def test_observer_exit_race_is_reaped_without_signaling_another_group(self):
        observer = mock.Mock(pid=222)
        observer.poll.return_value = None
        with mock.patch.object(flight.os, 'killpg', side_effect=ProcessLookupError):
            flight.stop_observer(observer)
        observer.wait.assert_called_once_with(timeout=10)

    def test_stalled_observer_and_decoder_are_killed_and_reaped(self):
        observer = mock.Mock(pid=222)
        observer.poll.return_value = None
        observer.wait.side_effect = [subprocess.TimeoutExpired('video', 10), -9]
        with mock.patch.object(flight.os, 'killpg') as kill:
            flight.stop_observer(observer)
        self.assertEqual(kill.call_args_list, [mock.call(222, signal.SIGTERM), mock.call(222, signal.SIGKILL)])
        self.assertEqual(observer.wait.call_count, 2)


if __name__ == '__main__':
    unittest.main()

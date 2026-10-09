import json
import os
from contextlib import closing
from pathlib import Path
import shutil
import sqlite3
import struct
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
import RunOrdinaryPerformance as ordinary


class OrdinaryPerformanceTest(unittest.TestCase):
    def test_requested_jdk_controls_actual_child_launch_with_stale_inherited_java(self):
        java = shutil.which('java')
        if os.name != 'posix' or not java:
            self.skipTest('requires POSIX and JDK 25')
        jdk = Path(java).resolve().parent
        if 'version "25' not in subprocess.run([java, '-version'], capture_output=True, text=True).stderr:
            self.skipTest('requires JDK 25')
        actual_popen = subprocess.Popen
        launches = []
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            old_bin = root / 'old-jdk/bin'
            old_bin.mkdir(parents=True)
            old_java = old_bin / 'java'
            old_java.write_text('#!/bin/sh\nexit 92\n')
            old_java.chmod(0o755)
            args = SimpleNamespace(repo=root, frozen_repo=root, run_source=root,
                shaders='off', world='Test', width=1920, height=1080, fullscreen=True,
                startup_timeout=30, seconds=5, render_distance=12, dh='on',
                heap_gb=2, minimap='visible')
            command = [sys.executable, 'unused-capture-engine', '--client-args', '',
                       '--max-secs', '30', '--dump-secs', '20', '--validation', 'off']

            def probe_child(command, **kwargs):
                launches.append(kwargs['env'])
                # Exercise actual executable resolution and the game VM flags,
                # without opening a game window for this launch regression.
                return actual_popen(['java', '-version'], **kwargs)

            with patch.dict(os.environ, {'JAVA_HOME': str(old_bin.parent),
                    'PATH': str(old_bin) + os.pathsep + os.environ.get('PATH', '')}), \
                    patch.object(ordinary.flight, 'launch_command', return_value=(None, command)), \
                    patch.object(ordinary.flight.artifact_retention, 'ensure_marker'), \
                    patch.object(ordinary.flight, 'client_pid', return_value=None), \
                    patch.object(ordinary.validation, 'stop_leftover_clients', return_value=0), \
                    patch.object(ordinary.validation, 'read_client_health', return_value={
                        'exceptions': 0, 'terrain_failures': 0}), \
                    patch.object(ordinary.subprocess, 'Popen', side_effect=probe_child):
                result = ordinary.run_client(args, 'current', root / 'observation', root / 'agent.jar', jdk)
            self.assertEqual(len(launches), 1)
            self.assertEqual(launches[0]['JAVA_HOME'], str(jdk.parent))
            self.assertEqual(launches[0]['PATH'].split(os.pathsep)[0], str(jdk))
            self.assertIn('version "25', (root / 'observation/driver.log').read_text())
            self.assertEqual(result['engine_exit_code'], 0)

    def test_preflight_created_output_can_complete_and_retains_summary(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / 'comparison'
            with patch.object(ordinary, 'verify_source', return_value={}), \
                    patch.object(ordinary.flight, 'flight_disk_preflight', side_effect=lambda p: p.mkdir()), \
                    patch.object(ordinary, 'build_agent', return_value=root / 'observer.jar'), \
                    patch.object(ordinary, 'run_client', return_value={'status': 'complete'}):
                self.assertEqual(ordinary.main(['--run-source', str(root), '--output', str(output),
                                               '--side', 'current']), 0)
            self.assertEqual(json.loads((output / 'summary.json').read_text())['protocol'], 'ordinary-gameplay-v1')

    def test_frame_aggregation_retains_long_stalls_and_rejects_truncation(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'frames.bin'
            path.write_bytes(struct.pack('>I?', 3, False) + b''.join(
                struct.pack('>qqq', epoch, duration, duration) for epoch, duration in
                [(1000, 10_000_000), (1010, 10_000_000), (2010, 1_000_000_000)]))
            result = ordinary.frame_summary(path, 0, 3000)
            self.assertEqual(result['frames'], 3)
            self.assertEqual(result['max_frame_ms'], 1000)
            self.assertEqual(result['frames_over_100ms'], 1)
            self.assertAlmostEqual(result['fps_from_frame_intervals'], 3 / 1.02)
            path.write_bytes(path.read_bytes()[:-1])
            with self.assertRaisesRegex(RuntimeError, 'truncated'):
                ordinary.frame_summary(path, 0, 3000)

    def test_runtime_validation_rejects_false_gameplay_and_failed_motion(self):
        sample = dict(world=True, screen='none', debug_visible=False, throttle='NONE',
                      overflow=False, minimap_hidden=False, minimap_allowed=True, x=1, z=1)
        ordinary.validate_samples([sample], 'visible')
        for key, value in [('world', False), ('screen', 'loading'), ('debug_visible', True),
                           ('throttle', 'AFK'), ('overflow', True), ('minimap_hidden', True), ('minimap_allowed', False)]:
            with self.assertRaises(RuntimeError):
                ordinary.validate_samples([{**sample, key: value}], 'visible')
        with self.assertRaisesRegex(RuntimeError, 'did not move'):
            ordinary.validate_samples([sample, sample], 'visible', True)
        ordinary.validate_samples([sample, {**sample, 'x': 3}], 'visible', True)

    def test_frozen_incompatible_database_is_rejected_without_writing_source(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            save = root / 'saves/Test'
            save.mkdir(parents=True)
            (save / 'level.dat').write_bytes(b'fixture')
            database = save / 'DistantHorizons.sqlite'
            with closing(sqlite3.connect(database)) as connection:
                connection.execute('CREATE TABLE FullData(DataFormatVersion INTEGER)')
                connection.execute('INSERT INTO FullData VALUES(4)')
                connection.commit()
            before = database.read_bytes()
            with self.assertRaisesRegex(ValueError, 'Frozen cannot read'):
                ordinary.verify_source(root, 'Test', True)
            self.assertEqual(database.read_bytes(), before)
            ordinary.verify_source(root, 'Test', False)

    def test_frame_agent_transforms_early_and_normal_returns_with_no_external_dependencies(self):
        java = shutil.which('java')
        if not java:
            self.skipTest('JDK 25 unavailable')
        jdk = Path(java).resolve().parent
        if not (jdk / 'javac').is_file() or not (jdk / 'jar').is_file():
            self.skipTest('requires a complete JDK 25, including javac and jar')
        if 'version "25' not in subprocess.run([java, '-version'], capture_output=True, text=True).stderr:
            self.skipTest('requires JDK 25 classfile API')
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            agent = ordinary.build_agent(root, jdk)
            with zipfile.ZipFile(agent) as archive:
                self.assertNotIn('GameplayFrameHooks.class', archive.namelist())
            with zipfile.ZipFile(root / 'observer-hooks.jar') as archive:
                self.assertIn('GameplayFrameHooks.class', archive.namelist())
            fixture = root / 'Fixture.java'
            fixture.write_text('public class Fixture { public void runTick(boolean early) { if (early) return; } '
                               'public static void drawElements() {} '
                               'public void renderLodBuffers() { drawElements(); drawElements(); } }')
            harness = root / 'AgentCheck.java'
            harness.write_text('''import java.nio.file.*;
public class AgentCheck {
 public static void main(String[] args) throws Exception {
  byte[] transformed=GameplayPerformanceObserver.instrument(Files.readAllBytes(Path.of(args[0])));
  Class<?> fixture=new ClassLoader() { Class<?> load() { return defineClass("Fixture",transformed,0,transformed.length); } }.load();
  var active=GameplayPerformanceObserver.class.getDeclaredField("active");active.setAccessible(true);active.setBoolean(null,true);
  GameplayFrameHooks.begin=GameplayPerformanceObserver::beginFrame;
  GameplayFrameHooks.end=GameplayPerformanceObserver::endFrame;
  GameplayFrameHooks.draw=GameplayPerformanceObserver::recordDhDraw;
  Object instance=fixture.getConstructor().newInstance();
  fixture.getMethod("runTick",boolean.class).invoke(instance,true);
  fixture.getMethod("runTick",boolean.class).invoke(instance,false);
  var count=GameplayPerformanceObserver.class.getDeclaredField("count");count.setAccessible(true);
  if(count.getInt(null)!=2)throw new AssertionError("missed return path");
  fixture.getMethod("renderLodBuffers").invoke(instance);
  var draws=GameplayPerformanceObserver.class.getDeclaredField("dhDrawCalls");draws.setAccessible(true);
  if(draws.getLong(null)!=2)throw new AssertionError("missed DH draws");
 }
}''')
            classpath = str(root / 'observer-classes') + os.pathsep + str(root / 'observer-hooks.jar')
            subprocess.run([str(jdk / 'javac'), '--add-modules', 'jdk.attach', '-cp', classpath, '-d', str(root),
                            str(fixture), str(harness)], check=True, capture_output=True)
            subprocess.run([java, '--add-modules', 'jdk.attach', '-cp', str(root) + os.pathsep + classpath,
                            'AgentCheck', str(root / 'Fixture.class')], check=True, capture_output=True)


if __name__ == '__main__':
    unittest.main()

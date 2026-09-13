import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'Common'))
from client_memory import ClientMemoryObserver,process_sample,java_main,attach_memory_observation


class ClientMemoryTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name);self.capture=self.root/'capture';self.capture.mkdir()
        self.game=self.capture/'game_dir_test';self.game.mkdir()
        self.meta=self.capture/'meta_test.txt';self.meta.write_text(f'run_id=test\nisolated_game_dir={self.game}\n')
        self.proc=self.root/'proc';self.proc.mkdir()
        self.java=self.root/'java';self.java.touch()
        self.client=self.process(100)

    def process(self,pid,*,main='net.fabricmc.loader.impl.launch.knot.KnotClient',cwd=None,start=55,rss=1234,hwm=2345):
        p=self.proc/str(pid);p.mkdir()
        (p/'exe').symlink_to(self.java)
        (p/'cwd').symlink_to(cwd or self.game)
        (p/'cmdline').write_bytes(b'\0'.join(x.encode() for x in ('java','-Xmx8G','-cp','libraries',main,'--accessToken','not-recorded'))+b'\0')
        (p/'stat').write_text(f'{pid} (java (worker)) '+' '.join(['S']+['0']*18+[str(start)]))
        (p/'status').write_text(f'VmRSS:\t{rss} kB\nVmHWM:\t{hwm} kB\n')
        return p

    def observer(self,**kw):return ClientMemoryObserver(self.capture,self.root,proc_root=self.proc,**kw)

    def test_provenance_peaks_and_terminal_state_without_an_enforcement_setting(self):
        o=self.observer();o.tick(1)
        self.assertFalse(o.receipt(terminal=True)['complete'])
        (self.client/'status').write_text('VmRSS: 1400 kB\nVmHWM: 2500 kB\n');o.tick(2)
        r=o.receipt(terminal=True)
        self.assertTrue(r['complete']);self.assertFalse(o.receipt(terminal=False)['complete'])
        self.assertEqual((1400,2500,2),(r['peak_rss_kb'],r['peak_hwm_kb'],r['sample_count']))
        self.assertEqual(55,r['identity']['start_ticks']);self.assertFalse(r['enforcement'])
        self.assertNotIn('not-recorded',json.dumps(r));self.assertNotIn('accessToken',json.dumps(r))

    def test_gradle_other_run_and_misleading_main_arguments_are_not_clients(self):
        for main in ('org.gradle.wrapper.GradleWrapperMain','other.Main'):
            (self.client/'cmdline').write_bytes(f'java\0{main}\0net.fabricmc.loader.impl.launch.knot.KnotClient\0'.encode())
            self.assertIsNone(process_sample(self.client,self.game))
        self.assertEqual('other.Main',java_main(['java','-cp','net.fabricmc.loader.impl.launch.knot.KnotClient','other.Main']))
        self.assertIsNone(java_main(['java','-jar','other.jar','net.minecraft.client.main.Main']))
        self.assertIsNone(java_main(['java','@unknown','net.minecraft.client.main.Main']))
        other=self.root/'other';other.mkdir();self.process(101,cwd=other)
        o=self.observer();o.tick(1);o.tick(2);self.assertIsNone(o.receipt(terminal=True)['peak_rss_kb'])

    def test_stale_metadata_and_external_directories_fail_closed(self):
        o=self.observer(prior_metadata=[self.meta]);o.tick(1);o.tick(2);self.assertFalse(o.receipt(terminal=True)['complete'])
        self.meta.write_text('run_id=test\nisolated_game_dir=/tmp/game_dir_test\n')
        o=self.observer();o.tick(1);self.assertFalse(o.receipt(terminal=True)['complete'])

    def test_ambiguous_clients_and_pid_reuse_invalidate_measurement(self):
        o=self.observer();o.tick(1);o.tick(2);self.process(101);o.tick(3)
        self.assertIn('ambiguous-client-process',o.receipt(terminal=True)['errors'])
        self.assertFalse(o.receipt(terminal=True)['complete'])
        for p in (self.proc/'101').iterdir():p.unlink()
        (self.proc/'101').rmdir()
        o=self.observer();o.tick(1);o.tick(2)
        (self.client/'stat').write_text('100 (java) '+' '.join(['S']+['0']*18+['56']))
        o.tick(3);self.assertIn('client-process-identity-changed',o.receipt(terminal=True)['errors'])
        self.assertFalse(o.receipt(terminal=True)['complete'])

    def test_identity_must_survive_read_and_missing_or_malformed_rss_is_not_zero(self):
        original=Path.read_text;reads=0
        def changed(p,*a,**kw):
            nonlocal reads
            text=original(p,*a,**kw)
            if p==self.client/'stat':
                reads+=1
                if reads==2:return text.rsplit(' ',1)[0]+' 56'
            return text
        with patch.object(Path,'read_text',changed):self.assertIsNone(process_sample(self.client,self.game))
        for status in ('','VmRSS: nope kB\n','VmRSS: 1200 kB\nVmHWM: 100 kB\n'):
            (self.client/'status').write_text(status);self.assertIsNone(process_sample(self.client,self.game))

    def test_bounded_history_preserves_early_peak_and_skips_subsecond_polls(self):
        o=self.observer();o.tick(1);o.tick(1.5)
        self.assertEqual(1,o.count)
        (self.client/'status').write_text('VmRSS: 100 kB\nVmHWM: 2345 kB\n')
        for i in range(2,302):o.tick(i)
        r=o.receipt(terminal=True)
        self.assertTrue(r['complete']);self.assertEqual(128,len(r['retained_samples']))
        self.assertEqual(301,r['sample_count']);self.assertEqual(173,r['discarded_history_count'])
        self.assertEqual(1234,r['peak_rss_kb'])

    def test_normalized_metrics_require_a_complete_observation(self):
        o=self.observer();o.tick(1);o.tick(2)
        artifact={'metrics':{'rss_and_native_memory':{'peak_rss_kb':77}}}
        invalid=copy.deepcopy(artifact);attach_memory_observation(invalid,o.receipt(terminal=False),'receipt.json')
        self.assertEqual(77,invalid['metrics']['rss_and_native_memory']['peak_rss_kb'])
        attach_memory_observation(artifact,o.receipt(terminal=True),'receipt.json')
        self.assertEqual(1234,artifact['metrics']['rss_and_native_memory']['peak_rss_kb'])
        self.assertEqual(77,artifact['metrics']['rss_and_native_memory']['legacy_guard_peak_rss_kb'])

if __name__=='__main__':unittest.main()

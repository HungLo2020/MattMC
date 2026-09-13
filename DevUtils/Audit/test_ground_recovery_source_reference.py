import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'Common'))
import graphics_harness as h
from ground_recovery_source_reference import select,source_key
import test_ground_special_foil_reference as ground_tests


class GroundRecoverySourceReferenceTest(unittest.TestCase):
    def document(self,frame=5,sprite='minecraft:item/recovery_compass_02'):
        fixture=ground_tests.GroundSpecialFoilReferenceTest().recovery_fixture()[3]
        for item in fixture['items']:
            item['extracted']['sources']['renderedFrameIndex']=frame
        for q in fixture['items'][1]['extracted']['sources']['quads']:q['sprite']=sprite
        return dict(droppedItemFoilFixture=fixture,captures=[dict(renderedFrameIndex=frame,
            poseName='initial',dimension='minecraft:overworld',shaderEnabled=False,gameTime=6000,
            requestedYaw=105,observedYaw=105,requestedPitch=10,observedPitch=10,
            position=dict(x=150.5,y=100,z=530.5),window=dict(width=1280,height=720))])

    def test_source_key_preserves_variant_and_validates_own_frame(self):
        self.assertEqual(source_key(self.document()),source_key(self.document(6)))
        self.assertNotEqual(source_key(self.document()),source_key(self.document(sprite='minecraft:item/recovery_compass_01')))
        ordinary=self.document();ordinary['droppedItemFoilFixture']=ground_tests.GroundSpecialFoilReferenceTest().fixture()[3]
        self.assertIsInstance(source_key(ordinary,recovery=False),str)
        with self.assertRaises(ValueError):source_key(ordinary)
        wrong=self.document();wrong['captures'][0]['renderedFrameIndex']=6
        with self.assertRaises(ValueError):source_key(wrong)
        wrong=self.document();wrong['droppedItemFoilFixture']['items'][1]['extracted']['sources']['complete']=False
        with self.assertRaises(ValueError):source_key(wrong)

    def test_selection_uses_sources_before_pixels_and_rejects_unhealthy_evidence(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);paths=[root/n for n in ('current.json','frozen01.json','frozen02.json')]
            docs={str(p):self.document(5+i,'minecraft:item/recovery_compass_01' if i==1 else 'minecraft:item/recovery_compass_02') for i,p in enumerate(paths)}
            for i,p in enumerate(paths):
                p.write_text(json.dumps(dict(tool='capture',mode=dict(name='current-rust-vulkan-shaders-off' if i==0 else 'frozen-opengl-shaders-off'),
                    capture=dict(exit_code=0),validation=dict(complete=True,crash_free=True,device_loss_free=True,
                    orphan_process_detected=False,rss_guard_triggered=False,vulkan_validation_clean=i==0),
                    metrics=dict(rss_and_native_memory=dict(client_observation=dict(complete=True))))))
            with mock.patch.object(h,'deterministic_capture_document',side_effect=lambda p:docs[str(p)]), \
                 mock.patch.object(h,'compare_workloads',return_value=dict(comparable=True)), \
                 mock.patch('PIL.Image.open',side_effect=AssertionError('selection must not read pixels')):
                r=select(paths[0],paths[1:]);self.assertTrue(r['passed'])
                self.assertEqual(r['selected_artifact'],str(paths[2]))
                self.assertEqual([x['source_and_fixture_match'] for x in r['candidates']],[False,True])
                self.assertFalse(select(paths[0],[paths[1]])['passed'])
                docs[str(paths[2])]['captures'][0]['observedYaw']=106
                self.assertFalse(select(paths[0],[paths[2]])['passed'])
                docs[str(paths[2])]['captures'][0]['observedYaw']=105
                with self.assertRaises(ValueError):select(paths[0],[paths[1],paths[1]])
                with self.assertRaises(ValueError):select(paths[0],[])
                a=json.loads(paths[1].read_text());a['validation']['complete']=False;paths[1].write_text(json.dumps(a))
                with self.assertRaises(ValueError):select(paths[0],paths[1:])

    def test_source_match_does_not_waive_workload_equivalence(self):
        with tempfile.TemporaryDirectory() as temporary:
            paths=[Path(temporary)/n for n in ('c','f')]
            for p in paths:p.write_text('{}')
            doc=self.document();key=source_key(doc)
            with mock.patch('ground_recovery_source_reference.capture',return_value=({},doc,key)), \
                 mock.patch.object(h,'compare_workloads',return_value=dict(comparable=False)):
                self.assertFalse(select(paths[0],[paths[1]])['passed'])


if __name__=='__main__':unittest.main()

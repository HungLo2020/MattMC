from pathlib import Path
import sys
import unittest
from unittest import mock
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'Common'))
import graphics_harness as h
import test_ground_special_foil_reference as ground_tests
from normal_special_foil_reference import report


class NormalSpecialFoilReferenceTest(unittest.TestCase):
    def fixture(self):
        native,owner,ack,_=ground_tests.GroundSpecialFoilReferenceTest().fixture()
        image='/current/01_initial.png';ack['screenshot']=image
        doc=dict(worldDecalFoilAdmission=dict(schema='rust-owned-world-decal-foil-v1',normalRoute=True,
                    privateFlagsPresent=False),captures=[dict(renderedFrameIndex=5,screenshot=image)])
        artifacts=[]
        for mode in ('current-rust-vulkan-shaders-off','frozen-opengl-shaders-off'):
            artifacts.append(dict(tool='capture',mode=dict(name=mode),capture=dict(exit_code=0),
                validation=dict(complete=True,crash_free=True,device_loss_free=True,
                    orphan_process_detected=False,rss_guard_triggered=False,vulkan_validation_clean=True),
                metrics=dict(rss_and_native_memory=dict(client_observation=dict(complete=True,
                    provider='linux-proc-status',sample_count=10,peak_rss_kb=100,peak_hwm_kb=100)))))
        pair=dict(current_artifact='/current/artifact.json',baseline_artifact='/frozen/artifact.json',
                  current_image=image,baseline_image='/frozen/01_initial.png')
        return [dict(passed=True,pairs=[pair]),dict(passed=True,pairs=[dict(passed=True,ground=dict(
            passed=True,native=dict(passed=True)))]),doc,artifacts,native,owner,ack]

    def evaluate(self,values):
        visual,oracle,doc,artifacts,native,owner,ack=values
        def read(path):
            name=str(path)
            if name=='/current/artifact.json':return artifacts[0]
            if name=='/frozen/artifact.json':return artifacts[1]
            if name.endswith('.decal-inputs.json'):return native
            if name.endswith('.ack.json'):return ack
            if 'gameplay-correlation-frame-' in name:return owner
            raise AssertionError('unexpected evidence '+name)
        with mock.patch.object(h,'read_json',side_effect=read),mock.patch.object(h,'deterministic_capture_document',return_value=doc):
            return report(visual,oracle,True)

    def test_normal_admission_requires_full_oracle_and_current_route_receipt(self):
        self.assertTrue(self.evaluate(self.fixture())['capability_admitted'])
        self.assertFalse(report({}, {},False)['capability_admitted'])
        for key,value in (('normalRoute',False),('normalRoute',1),('privateFlagsPresent',True),
                          ('privateFlagsPresent',0),('schema','private')):
            docs=self.fixture();docs[2]['worldDecalFoilAdmission'][key]=value
            self.assertFalse(self.evaluate(docs)['passed'])
        docs=self.fixture();docs[2].pop('worldDecalFoilAdmission')
        self.assertFalse(self.evaluate(docs)['passed'])
        docs=self.fixture();docs[1]['passed']=False
        self.assertFalse(self.evaluate(docs)['passed'])
        docs=self.fixture();docs[1]['pairs'][0].pop('ground')
        self.assertFalse(self.evaluate(docs)['passed'])

    def test_normal_admission_rejects_wrong_frame_owner_mode_and_missing_memory(self):
        for index,path,value in ((2,['captures',0,'renderedFrameIndex'],4),
                                 (3,[0,'mode','name'],'current-java-vulkan-shaders-off'),
                                 (3,[1,'metrics','rss_and_native_memory','client_observation','complete'],False),
                                 (3,[0,'validation','vulkan_validation_clean'],False),
                                 (5,['java_vulkan_frame_execution'],True),
                                 (6,['screenshot'],'/old/frame.png')):
            docs=self.fixture();target=docs[index]
            for key in path[:-1]:target=target[key]
            target[path[-1]]=value
            with self.subTest(path=path):self.assertFalse(self.evaluate(docs)['passed'])

    def test_hand_scope_requires_its_native_oracle_too(self):
        docs=self.fixture();row=docs[1]['pairs'][0];row['held']=row.pop('ground');row['held']['context']='held-clock'
        self.assertFalse(self.evaluate(docs)['passed'])
        docs[4]['semantic_instances']=docs[4]['semantic_instances'][:1];docs[4]['draws']=docs[4]['draws'][:1]
        docs[4]['semantic_instances'][0].update(first_person=True,context='first-person')
        self.assertEqual(self.evaluate(docs)['pairs'][0]['context'],'held-clock')
        row['held']['native']['passed']=False
        self.assertFalse(self.evaluate(docs)['passed'])


if __name__=='__main__':unittest.main()

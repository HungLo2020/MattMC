import copy
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'Common'))
from ground_foil_timing_reference import observed_world,matches_native

class GroundFoilTimingReferenceTest(unittest.TestCase):
    def timing(self):
        return dict(enabled=True,complete=True,frameSequence=20,ground=dict(enabled=True,complete=True,schema='ground-foil-frame-timing-v1',
            renderedFrameIndex=12,frameSequence=20,samples=[dict(provider='semantic-world',meshKey=str(i),
                clockMillis=85003,speed=.5,strength=.5,scaledTicks=340012) for i in (1,2)]))

    def test_semantic_clock_requires_typed_unique_captured_frame_evidence(self):
        self.assertEqual(2,len(observed_world(self.timing(),12,'semantic-world')))
        for path,value in ((['complete'],False),(['ground','renderedFrameIndex'],13),(['ground','renderedFrameIndex'],12.0),(['ground','frameSequence'],21),
            (['ground','samples',0,'scaledTicks'],True),(['ground','samples',0,'clockMillis'],85004),
            (['ground','samples',0,'meshKey'],'02'),(['ground','samples',0,'meshKey'],'2'),
            (['ground','samples',0,'meshKey'],str(2**64)),(['ground','samples',0,'speed'],float('nan'))):
            with self.subTest(path=path):
                doc=self.timing();target=doc
                for k in path[:-1]:target=target[k]
                target[path[-1]]=value
                with self.assertRaises(ValueError):observed_world(doc,12,'semantic-world')

    def test_frozen_observes_existing_world_state_without_inventing_per_item_clocks(self):
        doc=self.timing();doc['ground']['samples']=[dict(provider='frozen-world-state',scaledTicks=340012)]
        self.assertEqual(1,len(observed_world(doc,12,'frozen-world-state')))
        for samples in ([],doc['ground']['samples']*3,[dict(provider='semantic-world',scaledTicks=340012)]):
            wrong=copy.deepcopy(doc);wrong['ground']['samples']=samples
            with self.assertRaises(ValueError):observed_world(wrong,12,'frozen-world-state')

    def test_phase_alignment_checks_every_native_clock_and_natural_cycle(self):
        from ground_foil_timing_reference import paired_ticks,phase_ticks
        current=[dict(scaledTicks=340012),dict(scaledTicks=340016)]
        self.assertEqual(16,paired_ticks([dict(scaledTicks=670000)],current,10000)['max_distance'])
        with self.assertRaises(ValueError):paired_ticks([dict(scaledTicks=340032)],current,10000)
        with self.assertRaises(ValueError):phase_ticks(current,40000)
        with self.assertRaises(ValueError):phase_ticks(current,True)
        with self.assertRaises(ValueError):phase_ticks([dict(scaledTicks=340012),dict(scaledTicks=340040)],10000)

    def test_native_match_requires_both_actual_world_payloads(self):
        samples=observed_world(self.timing(),12,'semantic-world')
        receipt=dict(complete=True,schema='world-decal-submission-inputs-v1',deterministic_rendered_frame_index=12,semantic_instances=[dict(mesh_key=i,context='world',first_person=False,
            clock_millis=85003,speed=.5,strength=.5,scaled_ticks=340012) for i in (1,2)])
        self.assertTrue(matches_native(samples,receipt,12))
        with self.assertRaises(ValueError):matches_native(samples,receipt,13)
        for key,value in (('mesh_key',1),('clock_millis',85003.0),('scaled_ticks',340016),('clock_millis',85004),('first_person',True),('context','first-person')):
            wrong=copy.deepcopy(receipt);wrong['semantic_instances'][1][key]=value
            with self.assertRaises(ValueError):matches_native(samples,wrong,12)

if __name__=='__main__':unittest.main()

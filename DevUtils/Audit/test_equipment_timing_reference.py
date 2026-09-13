import copy
from pathlib import Path
import sys
import unittest
from PIL import Image
from unittest.mock import patch
from contextlib import ExitStack
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'Common'))
import equipment_timing_reference as ref

class EquipmentTimingTest(unittest.TestCase):
    def test_inventory_temporal_pixels_require_visible_matching_motion(self):
        images = [Image.new('RGB', (1280, 720)) for _ in range(4)]
        for x in range(397, 451):
            for y in range(134, 174):
                images[2].putpixel((x, y), (8, 2, 16))
                images[3].putpixel((x, y), (8, 2, 16))
        for x in range(453, 493):
            for y in range(135, 185):
                images[2].putpixel((x, y), (12, 6, 24))
                images[3].putpixel((x, y), (12, 6, 24))
        result = ref.compare_inventory_temporal_images(*images)
        self.assertTrue(result['passed'])
        self.assertFalse(result['capability_admitted'])
        still = images.copy()
        still[2] = still[0]
        with self.assertRaises(ValueError):
            ref.compare_inventory_temporal_images(*still)
        mismatched = [image.copy() for image in images]
        mismatched[3].putpixel((397, 134), (30, 30, 30))
        with self.assertRaises(ValueError):
            ref.compare_inventory_temporal_images(*mismatched)

    def test_inventory_clocks_require_exact_preview_scope_and_phase(self):
        preview=dict(schema='inventory-preview-inputs-v1',enabled=True,complete=True,
                     viewStable=True,renderedFrameIndex=7)
        current=dict(schema='equipment-foil-clock-observation-v1',enabled=True,complete=True,
            renderedFrameIndex=7,requestedPhase=10000,requestedPoseStep=4000,inventoryPreview=preview,
            samples=[dict(provider='semantic-armor-orthographic',batchSequence=0,
                clockMillis=167505,speed=0.5,strength=0.5,scaledTicks=670020) for _ in range(4)])
        frozen=copy.deepcopy(current)
        frozen['samples']=[dict(provider='frozen-armor-state',scaledTicks=10024)]
        self.assertTrue(ref.validate_inventory_clocks(current,7,current=True,phase=10000)['passed'])
        self.assertEqual(4,ref.paired_ticks(
            ref.validate_inventory_clocks(current,7,current=True,phase=10000),
            ref.validate_inventory_clocks(frozen,7,current=False,phase=10000)))
        for mutate in (
            lambda value:value['samples'].pop(),
            lambda value:value['samples'][0].update(provider='semantic-armor'),
            lambda value:value['samples'][0].update(speed=0.0),
            lambda value:value['inventoryPreview'].update(viewStable=False),
            lambda value:value.update(renderedFrameIndex=8),
        ):
            bad=copy.deepcopy(current);mutate(bad)
            with self.assertRaises(ValueError):
                ref.validate_inventory_clocks(bad,7,current=True,phase=10000)

    def test_stepped_geometry_pair_requires_full_equal_inputs_in_every_observation(self):
        model=dict(ageInTicks=1.0,attackTime=0.0,aggressive=False,leftArm=[0]*9,rightArm=[0]*9,
                   remainingParts={name:[0]*9 for name in ('head','body','leftLeg','rightLeg','root')})
        transform=dict(modelView=[0]*16,normal=[0]*9,state=[0]*8,lightCoords=0)
        receipt=dict(schema='equipment-foil-clock-observation-v1',enabled=True,complete=True,
                     renderedFrameIndex=7,zombiePosesComplete=True,zombiePoses=[model],
                     entityTransformsComplete=True,entityTransforms=[transform],
                     simulation=dict(schema='equipment-game-tick-step-v1',pose=0,targetTicks=0,
                         clientTicks=0,serverTicks=0,clientFrozen=True,serverFrozen=True,complete=True))
        candidate=copy.deepcopy(receipt);candidate['entityTransforms']*=2
        result=ref.paired_stepped_geometry(receipt,candidate,7,7,0)
        self.assertEqual(2,result['current_transform_count'])
        self.assertFalse(result['capability_admitted'])
        for side in (0,1):
            for key,size in [('modelView',16),('normal',9),('state',8),('lightCoords',1)]:
                for index in range(size):
                    pair=[copy.deepcopy(receipt),copy.deepcopy(candidate)]
                    pair[side]['entityTransforms'].append(copy.deepcopy(transform))
                    entry=pair[side]['entityTransforms'][-1]
                    if key=='lightCoords':entry[key]=1
                    else:entry[key][index]=0.000001
                    with self.assertRaises(ValueError):ref.paired_stepped_geometry(*pair,7,7,0)
        for side in (0,1):
            pair=[copy.deepcopy(receipt),copy.deepcopy(candidate)]
            del pair[side]['simulation']
            with self.assertRaises(ValueError):ref.paired_stepped_geometry(*pair,7,7,0)
        legacy=copy.deepcopy(receipt);del legacy['zombiePoses'][0]['remainingParts']
        with self.assertRaises(ValueError):ref.paired_stepped_geometry(legacy,legacy,7,7,0)
        with self.assertRaises(ValueError):ref.paired_stepped_geometry(receipt,candidate,8,7,0)
        with self.assertRaises(ValueError):ref.paired_stepped_geometry(receipt,candidate,7,7,1)

    def test_full_model_pair_rejects_missing_coverage_and_each_changed_part(self):
        pose=dict(ageInTicks=1.0,attackTime=0.0,aggressive=False,leftArm=[0]*9,rightArm=[0]*9,
                  remainingParts={name:[0]*9 for name in ('head','body','leftLeg','rightLeg','root')})
        receipt=dict(schema='equipment-foil-clock-observation-v1',enabled=True,complete=True,
                     renderedFrameIndex=7,zombiePosesComplete=True,zombiePoses=[pose])
        self.assertEqual(7,ref.paired_zombie_poses(receipt,receipt,7,7)['observed_parts'])
        legacy=copy.deepcopy(receipt);del legacy['zombiePoses'][0]['remainingParts']
        self.assertEqual(2,ref.paired_zombie_poses(legacy,legacy,7,7)['observed_parts'])
        with self.assertRaises(ValueError):ref.paired_zombie_poses(receipt,legacy,7,7)
        for name in pose['remainingParts']:
            for index in range(9):
                bad=copy.deepcopy(receipt);bad['zombiePoses'][0]['remainingParts'][name][index]=0.000001
                with self.assertRaises(ValueError):ref.paired_zombie_poses(receipt,bad,7,7)
            for value in ([0]*8,[float('nan')]*9,[True]*9):
                bad=copy.deepcopy(receipt);bad['zombiePoses'][0]['remainingParts'][name]=value
                with self.assertRaises(ValueError):ref.validate_zombie_pose(bad,7)

    def test_entity_transform_evidence_rejects_stale_unbounded_and_invalid_values(self):
        entry=dict(modelView=[0]*16,normal=[0]*9,state=[0]*8,lightCoords=0)
        receipt=dict(schema='equipment-foil-clock-observation-v1',enabled=True,
                     renderedFrameIndex=7,entityTransformsComplete=True,entityTransforms=[entry])
        self.assertEqual([entry],ref.validate_entity_transforms(receipt,7))
        for key,value in [('renderedFrameIndex',True),('renderedFrameIndex',8),
                          ('enabled',False),('entityTransformsComplete',False),
                          ('entityTransforms',[]),('entityTransforms',[entry]*17)]:
            with self.assertRaises(ValueError):ref.validate_entity_transforms(dict(receipt,**{key:value}),7)
        for key,size in [('modelView',16),('normal',9),('state',8)]:
            for value in ([0]*(size-1),[float('inf')]*size,[True]*size):
                bad=copy.deepcopy(receipt);bad['entityTransforms'][0][key]=value
                with self.assertRaises(ValueError):ref.validate_entity_transforms(bad,7)
        for value in (-1,True,0.5):
            bad=copy.deepcopy(receipt);bad['entityTransforms'][0]['lightCoords']=value
            with self.assertRaises(ValueError):ref.validate_entity_transforms(bad,7)

    def test_simulation_steps_require_both_replicas_and_normal_pose_age(self):
        for index in range(5):
            simulation=dict(schema='equipment-game-tick-step-v1',pose=index,targetTicks=index*10,
                clientTicks=index*10,serverTicks=index*10,clientFrozen=True,serverFrozen=True,complete=True)
            ref.validate_simulation(simulation,index)
            for key,value in [('clientTicks',index*10+1),('serverTicks',index*10-1),
                              ('clientFrozen',False),('complete',False),('pose',True)]:
                with self.assertRaises(ValueError):ref.validate_simulation(dict(simulation,**{key:value}),index)
            receipt=dict(schema='equipment-foil-clock-observation-v1',enabled=True,complete=True,
                renderedFrameIndex=7,zombiePosesComplete=True,simulation=simulation,
                zombiePoses=[dict(ageInTicks=float(index*10+1),attackTime=0.0,aggressive=False,
                                  leftArm=[0]*9,rightArm=[0]*9)])
            self.assertTrue(ref.validate_zombie_pose(receipt,7)['passed'])
            receipt['zombiePoses'][0]['ageInTicks']+=0.01
            with self.assertRaises(ValueError):ref.validate_zombie_pose(receipt,7)

    def test_diagnostic_execution_does_not_claim_or_modify_parity_acceptance(self):
        health=dict(complete=False,crash_free=True,device_loss_free=True,vulkan_validation_clean=True,
            deterministic_capture_complete=True,workload_entered=True,frame_sampler_validity_passed=True,
            frame_samples_complete=True,subsystem_complete=True,rss_guard_triggered=False,orphan_process_detected=False)
        a=dict(mode=dict(name='current-rust-vulkan-shaders-off'),tool='capture',capture=dict(exit_code=0),
            validation=health,metrics=dict(rss_and_native_memory=dict(client_observation=dict(
                complete=True,provider='linux-proc-status',sample_count=5,peak_rss_kb=100,peak_hwm_kb=100))))
        before=copy.deepcopy(a)
        self.assertFalse(ref.validate_current_timing_execution(a)['capability_admitted'])
        self.assertEqual(before,a)
        for key in health:
            if key=='complete':continue
            bad=copy.deepcopy(a);del bad['validation'][key]
            with self.assertRaises(ValueError):ref.validate_current_timing_execution(bad)
        for code in (True,-9,1):
            bad=copy.deepcopy(a);bad['capture']['exit_code']=code
            with self.assertRaises(ValueError):ref.validate_current_timing_execution(bad)
        bad=copy.deepcopy(a);bad['metrics']['rss_and_native_memory']['client_observation']['complete']=False
        with self.assertRaises(ValueError):ref.validate_current_timing_execution(bad)

    def test_current_selection_requires_each_native_capture_and_observed_clock(self):
        import graphics_harness as h
        import equipment_reference as equipment
        files={Path('artifact.json'): {}}
        captures=[]
        for index in range(5):
            frame=20+index
            image=Path('capture')/f'{index}.png'
            captures.append(dict(screenshot=str(image),renderedFrameIndex=frame))
            files[image.with_name(f'capture_request_{index}.ack.json')]=dict(
                status='captured',captureMethod='rust-vulkan-final-output',screenshot=str(image))
            files[Path('whole_frame_gameplay_attachments')/f'gameplay-correlation-frame-{frame}.json']={'frame':frame}
            clocks=[2500+index*1000+i for i in range(4)]
            native=dict(schema='equipment-submission-inputs-v1',complete=True,
                        gameplay_frame_id=frame,deterministic_rendered_frame_index=frame,
                        semantic_instances=[dict(context='world',mesh_key=i+1,
                            foil=dict(kind='Armor',clock_millis=clock,speed=0.5,strength=0.5))
                            for i,clock in enumerate(clocks)])
            files[Path(str(image)+'.equipment-inputs.json')]=native
            files[Path(str(image)+'.equipment-timing.json')]=dict(
                schema='equipment-foil-clock-observation-v1',enabled=True,complete=True,
                renderedFrameIndex=frame,requestedPhase=10000,zombiePosesComplete=True,
                zombiePoses=[dict(ageInTicks=100.0,attackTime=0.0,aggressive=False,leftArm=[0]*9,rightArm=[0]*9)],
                samples=[dict(provider='semantic-armor',meshKey=str(i+1),clockMillis=clock,
                              speed=0.5,strength=0.5,scaledTicks=clock*4) for i,clock in enumerate(clocks)])
        with ExitStack() as stack:
            stack.enter_context(patch.object(h,'read_json',side_effect=lambda p:files[Path(p)]))
            stack.enter_context(patch.object(h,'deterministic_capture_document',return_value={}))
            stack.enter_context(patch.object(equipment,'validate_document',return_value=captures))
            health=stack.enter_context(patch.object(ref,'validate_current_timing_execution'))
            native_check=stack.enter_context(patch.object(equipment,'validate_native'))
            self.assertEqual([10006,14006,18006,22006,26006],ref.current_phase_centers('artifact.json',10000))
            health.assert_called_once_with({})
            self.assertEqual(5,native_check.call_count)
            self.assertEqual([[10000+i*4000,10012+i*4000] for i in range(5)],
                             ref.current_phase_targets('artifact.json',10000)['ranges'])
            ref.current_phase_targets('artifact.json',10000,per_face=True)
            self.assertTrue(all(call.kwargs['per_face'] is True for call in native_check.call_args_list[-5:]))
            with self.assertRaises(ValueError):ref.current_phase_targets('artifact.json',10000,stepped=True)
            native_check.side_effect=ValueError('invalid native ownership')
            with self.assertRaises(ValueError):ref.current_phase_centers('artifact.json',10000)
            native_check.side_effect=None
            files[Path('capture/4.png.equipment-timing.json')]['renderedFrameIndex']=20
            with self.assertRaises(ValueError):ref.current_phase_centers('artifact.json',10000)

    def test_pose_evidence_rejects_stale_incomplete_and_inconsistent_inputs(self):
        pose=dict(ageInTicks=100.0,attackTime=0.0,aggressive=False,
                  leftArm=[5,2,0,-1.4,0.1,0.05,1,1,1],
                  rightArm=[-5,2,0,-1.4,-0.1,-0.05,1,1,1])
        receipt=dict(schema='equipment-foil-clock-observation-v1',enabled=True,complete=True,
                     renderedFrameIndex=7,zombiePosesComplete=True,zombiePoses=[pose,copy.deepcopy(pose)])
        self.assertTrue(ref.validate_zombie_pose(receipt,7)['passed'])
        for key,value in [('renderedFrameIndex',True),('complete',False),('zombiePosesComplete',False),
                          ('zombiePoses',[]),('zombiePoses',[pose]*17)]:
            with self.assertRaises(ValueError):ref.validate_zombie_pose(dict(receipt,**{key:value}),7)
        for key,value in [('ageInTicks',float('nan')),('attackTime',0.1),('aggressive',0),
                          ('leftArm',[0]*8),('rightArm',[float('inf')]*9)]:
            bad=copy.deepcopy(receipt);bad['zombiePoses'][0][key]=value
            with self.assertRaises(ValueError):ref.validate_zombie_pose(bad,7)
        bad=copy.deepcopy(receipt);bad['zombiePoses'][1]['leftArm'][3]+=0.001
        with self.assertRaises(ValueError):ref.validate_zombie_pose(bad,7)
        with self.assertRaises(ValueError):ref.validate_zombie_pose(receipt,8)

    def test_pair_requires_actual_arm_pose_equivalence(self):
        pose=dict(ageInTicks=100.0,attackTime=0.0,aggressive=False,
                  leftArm=[5,2,0,-1.4,0.1,0.05,1,1,1],rightArm=[-5,2,0,-1.4,-0.1,-0.05,1,1,1])
        a=dict(schema='equipment-foil-clock-observation-v1',enabled=True,complete=True,
               renderedFrameIndex=7,zombiePosesComplete=True,zombiePoses=[pose])
        b=copy.deepcopy(a);b['renderedFrameIndex']=9;b['zombiePoses'][0]['ageInTicks']=200.0
        self.assertFalse(ref.paired_zombie_poses(a,b,7,9)['capability_admitted'])
        for arm in ('leftArm','rightArm'):
            for index in range(9):
                bad=copy.deepcopy(b);bad['zombiePoses'][0][arm][index]+=0.000001
                with self.assertRaises(ValueError):ref.paired_zombie_poses(a,bad,7,9)

    def test_native_clock_must_match_all_actual_mesh_inputs(self):
        r=dict(schema='equipment-foil-clock-observation-v1',enabled=True,complete=True,renderedFrameIndex=7,
            samples=[dict(provider='semantic-armor',meshKey=str(i),clockMillis=100+i,speed=0.0,strength=0.5,scaledTicks=0)
                     for i in range(1,5)])
        native=dict(schema='equipment-submission-inputs-v1',complete=True,deterministic_rendered_frame_index=7,
            semantic_instances=[dict(context='world',mesh_key=i,foil=dict(kind='Armor',clock_millis=100+i,speed=0.0,strength=0.5))
                                for i in range(1,5)])
        self.assertTrue(ref.validate_stopped(r,7,native)['passed'])
        for key,value in [('clockMillis',999),('meshKey','99'),('speed',0.5),('scaledTicks',1),('provider','frozen-armor-state')]:
            b=copy.deepcopy(r);b['samples'][0][key]=value
            with self.assertRaises(ValueError):ref.validate_stopped(b,7,native)
        b=copy.deepcopy(r);b['samples'][1]=b['samples'][0]
        with self.assertRaises(ValueError):ref.validate_stopped(b,7,native)
        with self.assertRaises(ValueError):ref.validate_stopped(r,8,native)

    def test_frozen_missing_stale_and_wrong_provider_rejected(self):
        r=dict(schema='equipment-foil-clock-observation-v1',enabled=True,complete=True,renderedFrameIndex=7,
            samples=[dict(provider='frozen-armor-state',scaledTicks=0)])
        self.assertFalse(ref.validate_stopped(r,7)['capability_admitted'])
        for key,value in [('enabled',False),('complete',False),('renderedFrameIndex',True),('samples',[])]:
            with self.assertRaises(ValueError):ref.validate_stopped(dict(r,**{key:value}),7)
        with self.assertRaises(ValueError):ref.validate_stopped(r,8)

    def test_moving_phases_require_five_authored_targets_and_close_paired_clocks(self):
        def frozen(ticks,phase=10000):
            return dict(schema='equipment-foil-clock-observation-v1',enabled=True,complete=True,
                renderedFrameIndex=7,requestedPhase=phase,samples=[dict(provider='frozen-armor-state',scaledTicks=ticks)])
        a=ref.validate_clocks(frozen(340020),7,phase=10000,pose=0)
        b=ref.validate_clocks(frozen(10032),7,phase=10000,pose=0)
        self.assertEqual(12,ref.paired_ticks(a,b))
        b=ref.validate_clocks(frozen(10040),7,phase=10000,pose=0)
        with self.assertRaises(ValueError):ref.paired_ticks(a,b)
        with self.assertRaises(ValueError):ref.validate_clocks(frozen(10020),7,phase=10000,pose=1)
        with self.assertRaises(ValueError):ref.validate_clocks(frozen(0),7,phase=10000,pose=0)
        for pose in range(5):
            self.assertTrue(ref.validate_clocks(frozen(10020+pose*4000),7,phase=10000,pose=pose)['passed'])

    def test_pose_spacing_is_explicit_and_cannot_relabel_old_captures(self):
        for pose in range(5):
            receipt=dict(schema='equipment-foil-clock-observation-v1',enabled=True,complete=True,
                renderedFrameIndex=7,requestedPhase=10000,requestedPoseStep=8000,
                samples=[dict(provider='frozen-armor-state',scaledTicks=10020+pose*8000)])
            self.assertTrue(ref.validate_clocks(receipt,7,phase=10000,pose=pose,step=8000)['passed'])
            with self.assertRaises(ValueError):ref.validate_clocks(receipt,7,phase=10000,pose=pose)
            del receipt['requestedPoseStep']
            with self.assertRaises(ValueError):ref.validate_clocks(receipt,7,phase=10000,pose=pose,step=8000)

if __name__=='__main__':unittest.main()

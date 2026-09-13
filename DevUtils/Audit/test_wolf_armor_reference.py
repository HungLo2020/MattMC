import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'Common'))
from wolf_armor_reference import report
import graphics_harness as harness

class WolfArmorReferenceTest(unittest.TestCase):
    def test_unrelated_capture_is_unrequested_and_never_admitted(self):
        self.assertEqual(dict(requested=False,passed=True,capability_admitted=False),report('zombie',[]))
    def test_wolf_scenario_parses_but_cannot_pass_without_actual_proof(self):
        args=harness.parse_args(['capture','--world-mesh-model-scenario','wolf',
                                '--jvm-arg=-Dmattmc.dev.graphicsAuditWolfArmor=high'])
        result=report(args.world_mesh_model_scenario,args.jvm_arg)
        self.assertFalse(result['passed']);self.assertFalse(result['capability_admitted'])
        self.assertEqual('high',result['mode']);self.assertIn('paired fixture proof',result['reason'])
    def test_wrong_missing_duplicate_and_unknown_scope_fail(self):
        for scenario,values in [('cow',['high']),('wolf',[]),('wolf',['high','high']),('wolf',['unknown'])]:
            result=report(scenario,['-Dmattmc.dev.graphicsAuditWolfArmor='+v for v in values])
            self.assertTrue(result['requested']);self.assertFalse(result['passed'])
            self.assertIn('one explicit',result['reason'])

    def test_all_independent_poses_reject_wrong_shared_or_current_pixels(self):
        from PIL import Image
        from wolf_armor_anchors import HIGH_CRACK_REFERENCES
        from wolf_armor_reference import compare_images
        for pose,row in enumerate(HIGH_CRACK_REFERENCES):
            image=Image.new('RGB',(1280,720))
            for x,y,pixels in row['probes']:
                for i,rgb in enumerate(pixels):image.putpixel((x-1+i%3,y-1+i//3),tuple(rgb))
            self.assertTrue(compare_images(image,image,pose)['passed'])
            for x,y,_ in row['probes']:
                bad=image.copy();bad.putpixel((x,y),(255,255,255))
                with self.assertRaises(ValueError):compare_images(image,bad,pose)
                with self.assertRaises(ValueError):compare_images(bad,bad,pose)

    def test_inputs_reject_stale_malformed_and_incomplete_geometry(self):
        import copy
        from wolf_armor_reference import validate_inputs
        paths=['root','root/body','root/head','root/head/real_head','root/left_front_leg','root/left_hind_leg',
               'root/right_front_leg','root/right_hind_leg','root/tail','root/tail/real_tail','root/upper_body']
        state=dict(values=[0.0]*12,angry=False,sitting=False,baby=False,light=15728640,
                   texture='minecraft:textures/entity/wolf/wolf.png')
        receipt=dict(schema='wolf-model-inputs-v1',enabled=True,complete=True,renderedFrameIndex=7,
            gpuReadback=False,capabilityAdmitted=False,
            models=[dict(state=state,parts={p:dict(pose=[0.0]*9,visible=True,skipDraw=False) for p in paths})],
            transforms=[dict(state=state,modelView=[0.0]*16,normal=[0.0]*9)])
        self.assertFalse(validate_inputs(receipt,7)['native_draw_coverage_verified'])
        for key,value in [('renderedFrameIndex',True),('renderedFrameIndex',8),('complete',False),('models',[]),
                          ('transforms',[]),('gpuReadback',True)]:
            bad=copy.deepcopy(receipt);bad[key]=value
            with self.assertRaises(ValueError):validate_inputs(bad,7)
        for value in [float('nan'),True,0]:
            bad=copy.deepcopy(receipt);bad['models'][0]['parts']['root']['pose'][0]=value
            with self.assertRaises(ValueError):validate_inputs(bad,7)
        bad=copy.deepcopy(receipt);del bad['models'][0]['parts']['root/head/real_head']
        with self.assertRaises(ValueError):validate_inputs(bad,7)

        from wolf_armor_reference import paired_inputs
        receipt['simulation']=dict(schema='wolf-game-tick-step-v1',pose=0,targetTicks=0,
            clientTicks=0,serverTicks=0,clientFrozen=True,serverFrozen=True,complete=True)
        state['values'][0]=1.0
        current=copy.deepcopy(receipt);current['renderedFrameIndex']=19
        current['models'].append(copy.deepcopy(current['models'][0]))
        result=paired_inputs(receipt,current,7,19,0)
        self.assertTrue(result['passed']);self.assertFalse(result['native_draw_coverage_verified'])
        for key,lane in [('modelView',0),('normal',4)]:
            bad=copy.deepcopy(current);bad['transforms'][0][key][lane]=0.000001
            with self.assertRaisesRegex(ValueError,'transforms differ'):paired_inputs(receipt,bad,7,19,0)
        bad=copy.deepcopy(current);bad['models'][1]['parts']['root/tail/real_tail']['pose'][3]=0.000001
        with self.assertRaisesRegex(ValueError,'models differ'):paired_inputs(receipt,bad,7,19,0)
        bad=copy.deepcopy(current);bad['models'][1]['parts']['root/body']['visible']=False
        with self.assertRaisesRegex(ValueError,'models differ'):paired_inputs(receipt,bad,7,19,0)
        with self.assertRaises(ValueError):paired_inputs(receipt,current,7,7,0)
        with self.assertRaises(ValueError):paired_inputs(receipt,current,7,19,1)

    def test_simulation_requires_exact_replication_and_stopped_state(self):
        from wolf_armor_reference import validate_simulation
        for pose in range(5):
            receipt=dict(schema='wolf-game-tick-step-v1',pose=pose,targetTicks=pose*10,
                clientTicks=pose*10,serverTicks=pose*10,clientFrozen=True,serverFrozen=True,complete=True)
            validate_simulation(receipt,pose)
            for key,value in [('pose',True),('clientTicks',pose*10+1),('serverTicks',pose*10-1),
                              ('clientFrozen',False),('serverFrozen',False),('complete',False)]:
                with self.assertRaises(ValueError):validate_simulation(dict(receipt,**{key:value}),pose)
        with self.assertRaises(ValueError):validate_simulation({},True)
        with self.assertRaises(ValueError):validate_simulation({},5)

if __name__=='__main__':unittest.main()

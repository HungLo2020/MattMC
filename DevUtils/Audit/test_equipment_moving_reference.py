"""Moving equipment must retain every frame's independent parity evidence."""
import copy
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from PIL import Image
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'Common'))
import equipment_reference as ref
from equipment_moving_anchors import MOVING_REFERENCES
import test_equipment_reference as native_test


def anchored(pose, phase=10000):
    image=Image.new('RGB',(1280,720))
    for x,y,pixels in MOVING_REFERENCES[phase][pose]['probes']:
        for i,rgb in enumerate(pixels):image.putpixel((x-1+i%3,y-1+i//3),tuple(rgb))
    return image


class MovingEquipmentTest(unittest.TestCase):
    def test_all_five_pixel_anchors_and_regions_are_required(self):
        for pose in range(5):
            image=anchored(pose)
            self.assertTrue(ref.compare_images(image,image,'foil',phase=10000,pose=pose)['passed'])
            for x,y,_ in MOVING_REFERENCES[10000][pose]['probes']:
                bad=image.copy();bad.putpixel((x,y),(255,255,255))
                for a,b in ((image,bad),(bad,bad)):
                    with self.assertRaises(ValueError):ref.compare_images(a,b,'foil',phase=10000,pose=pose)
            bad=image.copy();bad.paste((255,255,255),(600,365,675,400))
            with self.assertRaises(ValueError):ref.compare_images(image,bad,'foil',phase=10000,pose=pose)
        for phase,pose in ((50000,0),(True,0),(10000,True),(10000,5)):
            with self.assertRaises(ValueError):ref.compare_images(image,image,'foil',phase=phase,pose=pose)

    def test_second_phase_has_independent_frozen_anchors_for_every_pose(self):
        for pose in range(5):
            image=anchored(pose,40000)
            self.assertTrue(ref.compare_images(image,image,'foil',phase=40000,pose=pose)['passed'])
            with self.assertRaises(ValueError):
                ref.compare_images(anchored(pose),anchored(pose),'foil',phase=40000,pose=pose)
            for x,y,_ in MOVING_REFERENCES[40000][pose]['probes']:
                bad=image.copy();bad.putpixel((x,y),(255,255,255))
                with self.assertRaises(ValueError):ref.compare_images(image,bad,'foil',phase=40000,pose=pose)

    def test_reference_cannot_be_ignored_or_used_outside_second_phase(self):
        for args in ([],['-Dmattmc.dev.graphicsAuditEquipment=base'],
                     ['-Dmattmc.dev.graphicsAuditEquipment=foil']):
            result=ref.report({},args,Path('reference.json'))
            self.assertTrue(result['requested']);self.assertFalse(result['passed'])
        args=['-Dmattmc.dev.graphicsAuditEquipment=foil','-Dmattmc.dev.equipmentFoilPhase=40000',
              '-Dmattmc.dev.equipmentFoilPoseStep=8000','-Dmattmc.dev.equipmentTickStepping=true',
              '-Dmattmc.dev.equipmentFoilTiming=true']
        result=ref.report({'passed':True,'pairs':[{}]},args)
        self.assertFalse(result['passed']);self.assertIn('reference',result['reason'])

    def test_moving_request_cannot_omit_or_duplicate_constraints(self):
        values=dict(equipmentFoilPhase='10000',equipmentFoilPoseStep='8000',
                    equipmentTickStepping='true',equipmentFoilTiming='true')
        args=['-Dmattmc.dev.'+k+'='+v for k,v in values.items()]
        self.assertEqual(10000,ref.requested_moving_phase(args))
        self.assertIsNone(ref.requested_moving_phase([]))
        for index in range(len(args)):
            with self.assertRaises(ValueError):ref.requested_moving_phase(args[:index]+args[index+1:])
            with self.assertRaises(ValueError):ref.requested_moving_phase(args+[args[index]])
        for key in values:
            bad=dict(values);bad[key]='invalid'
            with self.assertRaises(ValueError):ref.requested_moving_phase(['-Dmattmc.dev.'+k+'='+v for k,v in bad.items()])

    def fixture(self,root,phase=10000):
        fixture=dict(schema='equipped-zombie-moving-v1',requested=True,mode='foil',ready=True,
            speed=0.5,strength=0.5,complete=True,x=146.6949012917378,y=99.57557514877936,
            z=529.4806875161336,yaw=-75.9375,bodyYaw=-75.9375,headYaw=-75.9375,
            equipment=[dict(slot=s,item='minecraft:diamond_'+i,count=1,foil=True)
                for s,i in [('head','helmet'),('chest','chestplate'),('legs','leggings'),('feet','boots')]])
        reload=dict(schema='normal-world-resource-reload-v1',requested=True,futureComplete=True,
                    complete=True,presentations=2,selectedBefore=['vanilla'],selectedAtCapture=['vanilla'])
        docs=[dict(equipmentFixture=copy.deepcopy(fixture),worldResourceReload=copy.deepcopy(reload),captures=[]) for _ in range(2)]
        files={}
        for pose in range(5):
            for side in range(2):
                image=root/str(side)/f'{pose}.png';image.parent.mkdir(exist_ok=True)
                anchored(pose,phase).save(image)
                frame=7+pose*10
                c=dict(screenshot=str(image),renderedFrameIndex=frame,window=dict(width=1280,height=720),
                    gameTime=6000,dimension='minecraft:overworld',position=dict(x=150.5,y=100.0,z=530.5),
                    shaderEnabled='false',requestedYaw=105.0,observedYaw=105.0,requestedPitch=10.0,observedPitch=10.0)
                docs[side]['captures'].append(c)
                model=dict(ageInTicks=float(pose*10+1),attackTime=0.0,aggressive=False,leftArm=[0]*9,rightArm=[0]*9,
                    remainingParts={n:[0]*9 for n in ('head','body','leftLeg','rightLeg','root')})
                timing=dict(schema='equipment-foil-clock-observation-v1',enabled=True,complete=True,
                    renderedFrameIndex=frame,requestedPhase=phase,requestedPoseStep=8000,
                    simulation=dict(schema='equipment-game-tick-step-v1',pose=pose,targetTicks=pose*10,clientTicks=pose*10,
                        serverTicks=pose*10,clientFrozen=True,serverFrozen=True,complete=True),
                    zombiePosesComplete=True,zombiePoses=[model],entityTransformsComplete=True,
                    entityTransforms=[dict(modelView=[0]*16,normal=[0]*9,state=[0]*8,lightCoords=0)])
                ticks=phase+20+pose*8000
                if not side:timing['samples']=[dict(provider='frozen-armor-state',scaledTicks=ticks)]
                else:
                    native,owner,ack,_=native_test.EquipmentNativeReferenceTest().evidence()
                    for obj in (native,owner):
                        obj.update(gameplay_frame_id=8+pose*10,correlation_id=8+pose*10,
                                   gal_submission_id=9+pose*10,deterministic_rendered_frame_index=frame)
                    owner['present_completed_submission_id']=9+pose*10
                    ack.update(renderedFrameIndex=frame,status='captured',captureMethod='rust-vulkan-final-output',screenshot=str(image))
                    ack['wholeFramePresentationCorrelation'].update(gameplayFrameId=8+pose*10,correlationId=8+pose*10,submissionId=9+pose*10)
                    for s in native['semantic_instances'][4:]:s['foil'].update(clock_millis=ticks//4,speed=0.5)
                    for d in native['draws'][:4]:d['uploaded_instances'][0]['material'][3]=114
                    for d in native['draws'][4:]:
                        row=d['uploaded_instances'][0]['foil'];row[2]=-(ticks%110000)/110000;row[6]=(ticks%30000)/30000
                    timing['samples']=[dict(provider='semantic-armor',scaledTicks=ticks,meshKey=str(s['mesh_key']),
                        clockMillis=ticks//4,speed=0.5,strength=0.5) for s in native['semantic_instances'][4:]]
                    files[Path(str(image)+'.equipment-inputs.json')]=native
                    files[image.with_name('capture_request_'+image.stem+'.ack.json')]=ack
                    files[image.parent.parent/'whole_frame_gameplay_attachments'/f'gameplay-correlation-frame-{8+pose*10}.json']=owner
                files[Path(str(image)+'.equipment-timing.json')]=timing
        return docs,files

    def test_pair_requires_all_frames_native_ownership_timing_and_geometry(self):
        with tempfile.TemporaryDirectory() as tmp:
            docs,files=self.fixture(Path(tmp))
            with patch('graphics_harness.read_json',side_effect=lambda path:files[Path(path)]):
                result=ref.paired_moving_frames(*docs,10000)
                self.assertEqual(5,len(result['frames']));self.assertFalse(result['capability_admitted'])
                for pose in range(5):
                    for side in range(2):
                        path=Path(docs[side]['captures'][pose]['screenshot']+'.equipment-timing.json')
                        original=copy.deepcopy(files[path])
                        for mutate in (lambda t:t.update(complete=False),
                            lambda t:t['simulation'].update(serverFrozen=False),
                            lambda t:t['entityTransforms'][0]['normal'].__setitem__(0,0.000001)):
                            mutate(files[path])
                            with self.assertRaises(ValueError):ref.paired_moving_frames(*docs,10000)
                            files[path]=copy.deepcopy(original)
                    path=Path(docs[1]['captures'][pose]['screenshot']+'.equipment-inputs.json')
                    original=copy.deepcopy(files[path])
                    for mutate in (lambda n:n.update(complete=False),lambda n:n['draws'].pop(),
                        lambda n:n['draws'][0]['uploaded_instances'][0]['material'].__setitem__(3,50)):
                        mutate(files[path])
                        with self.assertRaises(ValueError):ref.paired_moving_frames(*docs,10000)
                        files[path]=copy.deepcopy(original)
                for side in range(2):
                    bad=copy.deepcopy(docs);bad[side]['captures'].pop()
                    with self.assertRaises(ValueError):ref.paired_moving_frames(*bad,10000)
                path=Path(docs[0]['captures'][0]['screenshot']+'.equipment-timing.json')
                files[path]['samples'][0]['scaledTicks']+=17
                with self.assertRaises(ValueError):ref.paired_moving_frames(*docs,10000)


if __name__=='__main__':unittest.main()

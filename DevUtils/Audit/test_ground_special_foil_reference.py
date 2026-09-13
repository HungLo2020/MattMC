import copy
import sys
import unittest
from pathlib import Path
from PIL import Image
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'Common'))
from ground_special_foil_reference import native_inputs,pixels,SCOPES

class GroundSpecialFoilReferenceTest(unittest.TestCase):
    def fixture(self):
        from test_held_special_foil_reference import HeldSpecialFoilReferenceTest
        receipt,owner,ack,_=HeldSpecialFoilReferenceTest().fixture()
        sem=receipt['semantic_instances'][0];sem.update(context='world',first_person=False)
        draw=receipt['draws'][0]
        draw['uploaded_instances'][0]['first_projected_uvs']=[0,-1/128,0,0,1/128,0,1/128,-1/128]
        other=copy.deepcopy(sem);other['mesh_key']+=1;receipt['semantic_instances'].append(other)
        other_draw=copy.deepcopy(draw);other_draw.update(mesh_key=other['mesh_key'],vertex_count=72,index_count=108,payload_bytes=624)
        receipt['draws'].append(other_draw)
        fixture=dict(fixture='dropped-special-foil-v1',stackCount=1,complete=True,frozenSimulation=True,items=[])
        for index,(name,scope) in enumerate(SCOPES.items()):
            source=dict(schema='ground-foil-frame-sources-v1',complete=True,renderedFrameIndex=5,quads=[dict(layer=0,
                sprite=scope['sprite'],face='south',positions=[v for row in draw['first_source_vertices'] for v in row['position']],
                atlasUvs=[0,0,0,1,1,1,1,0]) for _ in range(scope['vertices']//4)])
            fixture['items'].append(dict(item=name,count=1,foil=True,tickCount=0,bobOffset=1.5707964,position=[index,2,3],
                extracted=dict(age=1.0,bobOffset=1.5707964,copies=1,seed=1,light=15728640,sources=source)))
        return [receipt,owner,ack,fixture]

    def test_ground_native_requires_two_world_draws_and_selected_resolved_sources(self):
        self.assertTrue(native_inputs(*self.fixture())['passed'])
        mutations=[(0,['semantic_instances',0,'first_person'],True),(0,['semantic_instances',0,'context'],'first-person'),
            (0,['semantic_instances',1,'mesh_key'],23),(0,['draws',1,'mesh_key'],23),
            (0,['draws',0,'uploaded_instances',0,'first_projected_uvs'],[0,-1/96,0,0,1/96,0,1/96,-1/96]),
            (0,['draws',0,'sampler','address_u'],'ClampToEdge'),(1,['java_vulkan_frame_execution'],True),
            (3,['items',0,'extracted','sources','renderedFrameIndex'],4),
            (3,['items',0,'extracted','sources','quads',0,'positions'],[1]*12),
            (3,['items',1,'extracted','sources','quads',0,'sprite'],'minecraft:item/compass_07')]
        for document,path,value in mutations:
            with self.subTest(path=path):
                docs=copy.deepcopy(self.fixture());target=docs[document]
                for key in path[:-1]:target=target[key]
                target[path[-1]]=value
                # Replace all copied source positions to remove the matching quad.
                if path[-1]=='positions':
                    for q in docs[3]['items'][0]['extracted']['sources']['quads']:q['positions']=value
                with self.assertRaises(ValueError):native_inputs(*docs)
        docs=self.fixture();docs[0]['draws'].pop()
        with self.assertRaises(ValueError):native_inputs(*docs)

    def test_moving_ground_requires_visible_change_in_both_items_and_every_material_group(self):
        from ground_special_foil_reference import moving_change
        before=Image.new('RGB',(1280,720),(100,90,80));after=before.copy()
        for scope in SCOPES.values():
            for x,y,rgb in scope['probes']:
                before.putpixel((x,y),rgb);after.putpixel((x,y),tuple(v+10 for v in rgb))
        self.assertTrue(moving_change(before,before,after,after)['passed'])
        self.assertFalse(moving_change(before,before,before,before)['passed'])
        for name,groups in [('minecraft:clock',[(0,1,2,4,6,7),(3,),(5,)]),
                            ('minecraft:compass',[(0,1,2,6,7),(3,),(4,5)])]:
            for group in groups:
                partial=after.copy()
                for i in group:
                    x,y,rgb=SCOPES[name]['probes'][i];partial.putpixel((x,y),rgb)
                self.assertFalse(moving_change(before,before,partial,partial)['passed'])
        with self.assertRaises(ValueError):moving_change(before,before,after,before)

    def test_moving_native_uses_each_actual_clock_and_uploaded_translation(self):
        docs=self.fixture();receipt=docs[0];samples=[]
        for i,semantic in enumerate(receipt['semantic_instances']):
            clock=85003+i;ticks=clock*4;semantic.update(clock_millis=clock,speed=.5,scaled_ticks=ticks)
            samples.append(dict(provider='semantic-world',meshKey=str(semantic['mesh_key']),clockMillis=clock,
                                speed=.5,strength=.5,scaledTicks=ticks))
            rows=receipt['draws'][i]['uploaded_instances'][0]['foil_rows'];rows[2]=-(ticks%110000)/110000;rows[6]=(ticks%30000)/30000
        timing=dict(enabled=True,complete=True,frameSequence=10,ground=dict(enabled=True,complete=True,
            schema='ground-foil-frame-timing-v1',renderedFrameIndex=5,frameSequence=10,samples=samples))
        self.assertTrue(native_inputs(*docs,phase=10000,timing=timing)['passed'])
        with self.assertRaises(ValueError):native_inputs(*docs,phase=40000,timing=timing)
        receipt['draws'][1]['uploaded_instances'][0]['foil_rows'][2]=0
        with self.assertRaises(ValueError):native_inputs(*docs,phase=10000,timing=timing)

    def recovery_fixture(self):
        import math
        docs=self.fixture();receipt,_,_,fixture=docs
        fixture['fixture']='dropped-recovery-special-foil-v1'
        item=fixture['items'][1];item['item']='minecraft:recovery_compass'
        source=item['extracted']['sources'];source['quads']=[copy.deepcopy(source['quads'][0]) for _ in range(22)]
        for quad in source['quads']:quad['sprite']='minecraft:item/recovery_compass_02'
        receipt['draws'][1].update(vertex_count=88,index_count=132,payload_bytes=752)
        for item,semantic,draw in zip(fixture['items'],receipt['semantic_instances'],receipt['draws']):
            angle=item['extracted']['age']/20+item['extracted']['bobOffset'];c,s=math.cos(angle),math.sin(angle)
            semantic['model_pose']=[c*.5,0,-s*.5,0,0,.5,0,0,s*.5,0,c*.5,0,
                item['position'][0]-150.5-.25*(c+s),0,item['position'][2]-530.5+.25*(s-c),1]
            draw['uploaded_instances'][0]['model_pose']=semantic['model_pose'].copy()
        return docs

    def test_recovery_equal_size_draws_require_fixture_pose_identity_and_exact_source(self):
        docs=self.recovery_fixture()
        self.assertTrue(native_inputs(*docs,recovery=True)['passed'])
        docs[0]['semantic_instances'].reverse();docs[0]['draws'].reverse()
        self.assertTrue(native_inputs(*docs,recovery=True)['passed'])
        for path,value in ((['semantic_instances',0,'model_pose',12],0),
                           (['semantic_instances',1,'model_pose',0],.5),
                           (['draws',1,'vertex_count'],72)):
            docs=self.recovery_fixture();target=docs[0]
            for key in path[:-1]:target=target[key]
            target[path[-1]]=value
            with self.assertRaises(ValueError):native_inputs(*docs,recovery=True)
        docs=self.recovery_fixture()
        docs[0]['semantic_instances'][1]['model_pose']=docs[0]['semantic_instances'][0]['model_pose'].copy()
        with self.assertRaises(ValueError):native_inputs(*docs,recovery=True)
        docs=self.recovery_fixture()
        for quad in docs[3]['items'][1]['extracted']['sources']['quads']:quad['sprite']='minecraft:item/recovery_compass_03'
        with self.assertRaises(ValueError):native_inputs(*docs,recovery=True)
        with self.assertRaises(ValueError):native_inputs(*self.recovery_fixture())
        with self.assertRaises(ValueError):native_inputs(*self.recovery_fixture(),recovery=True,phase=10000,timing={})

    def test_recovery_pixels_have_their_own_frozen_oracle(self):
        from ground_special_foil_reference import RECOVERY_SCOPES
        frozen=Image.new('RGB',(1280,720),(100,90,80))
        for scope in RECOVERY_SCOPES.values():
            for x,y,rgb in scope['probes']:frozen.putpixel((x,y),rgb)
        self.assertTrue(pixels(frozen,frozen,recovery=True)['passed'])
        for x,y,_ in RECOVERY_SCOPES['minecraft:recovery_compass']['probes']:
            wrong=frozen.copy();wrong.putpixel((x,y),(0,0,0))
            self.assertFalse(pixels(frozen,wrong,recovery=True)['passed'])
            self.assertFalse(pixels(wrong,wrong,recovery=True)['passed'])
        with self.assertRaises(ValueError):pixels(frozen,frozen,10000,recovery=True,recovery_sprite="minecraft:item/recovery_compass_03")

    def test_recovery_moving_requires_visible_changes_in_all_material_groups_and_each_clock(self):
        from ground_special_foil_reference import moving_change,RECOVERY_VARIANTS
        for sprite,scopes in RECOVERY_VARIANTS.items():
            before=Image.new('RGB',(1280,720),(100,90,80));after=before.copy()
            for scope in scopes.values():
                for x,y,rgb in scope['probes']:
                    before.putpixel((x,y),rgb);after.putpixel((x,y),tuple(v+10 for v in rgb))
            self.assertTrue(moving_change(before,before,after,after,recovery=True,recovery_sprite=sprite)['passed'])
            for group in ((0,1,2,3,4),(5,7),(6,)):
                wrong=after.copy()
                for i in group:
                    x,y,rgb=scopes['minecraft:recovery_compass']['probes'][i];wrong.putpixel((x,y),rgb)
                self.assertFalse(moving_change(before,before,wrong,wrong,recovery=True,recovery_sprite=sprite)['passed'])
        docs=self.recovery_fixture();receipt=docs[0];samples=[]
        for i,semantic in enumerate(receipt['semantic_instances']):
            clock=85003+i;ticks=clock*4;semantic.update(clock_millis=clock,speed=.5,scaled_ticks=ticks)
            samples.append(dict(provider='semantic-world',meshKey=str(semantic['mesh_key']),clockMillis=clock,
                                speed=.5,strength=.5,scaledTicks=ticks))
            rows=receipt['draws'][i]['uploaded_instances'][0]['foil_rows'];rows[2]=-(ticks%110000)/110000;rows[6]=(ticks%30000)/30000
        timing=dict(enabled=True,complete=True,frameSequence=10,ground=dict(enabled=True,complete=True,
            schema='ground-foil-frame-timing-v1',renderedFrameIndex=5,frameSequence=10,samples=samples))
        self.assertTrue(native_inputs(*docs,recovery=True,phase=10000,timing=timing)['passed'])
        receipt['draws'][1]['uploaded_instances'][0]['foil_rows'][2]=0
        with self.assertRaises(ValueError):native_inputs(*docs,recovery=True,phase=10000,timing=timing)

    def test_pixels_require_frozen_materials_and_whole_ground_regions(self):
        frozen=Image.new('RGB',(1280,720),(100,90,80))
        for scope in SCOPES.values():
            for x,y,rgb in scope['probes']:frozen.putpixel((x,y),rgb)
        self.assertTrue(pixels(frozen,frozen)['passed'])
        for scope in SCOPES.values():
            for x,y,_ in scope['probes']:
                wrong=frozen.copy();wrong.putpixel((x,y),(0,0,0))
                self.assertFalse(pixels(frozen,wrong)['passed']);self.assertFalse(pixels(wrong,wrong)['passed'])
            wrong=frozen.copy();wrong.paste((0,0,0),scope['box'])
            for x,y,rgb in scope['probes']:wrong.putpixel((x,y),rgb)
            self.assertFalse(pixels(frozen,wrong)['passed'])

if __name__=='__main__':unittest.main()

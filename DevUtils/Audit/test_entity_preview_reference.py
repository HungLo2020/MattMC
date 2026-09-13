import copy
from pathlib import Path
import sys
import unittest
from PIL import Image
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'Common'))
import entity_preview_reference as ref


class EntityPreviewReferenceTest(unittest.TestCase):
    def fixture(self):
        return dict(schema='entity-preview-fixture-v1', requested=True, mode='horse',
            serverSpawned=True, entityId=4, clientReplicated=True, screenOpen=True,
            mouseCentered=True, previewReady=True, complete=True)

    def preview(self, frame=7):
        return dict(schema='inventory-preview-inputs-v1', enabled=True, complete=True,
            capabilityAdmitted=False, viewStable=True, renderedFrameIndex=frame,
            observations=[dict(bounds=[151.0,55.0,203.0,107.0], translation=[0.0,1.05,0.0],
                rotation=[0.0,-0.13446018,0.990919,0.0],
                cameraRotation=[-0.13446018,0.0,0.0,0.990919], scales=[17.0,1.0,1.0],
                angles=[165.3437,-14.656311,15.454812], animation=[2.0,0.0,0.0,0.0],
                invisible=False,invisibleToPlayer=False,pose='STANDING',light=15728880)],
            screenMouse=[dict(stage='background',stored=[213.0,120.0],supplied=[213.0,120.0]),
                         dict(stage='render-return',stored=[213.0,120.0],supplied=[213.0,120.0])])

    def test_fixture_and_inputs_fail_closed(self):
        ref.validate_horse_fixture(self.fixture())
        current=self.preview(9);current['observations'][0]['animation'][0]=84.0
        self.assertTrue(ref.paired_horse_preview_inputs(self.preview(),current,7,9)['passed'])
        for key,value in [('complete',False),('entityId',0),('mode','player')]:
            bad=self.fixture();bad[key]=value
            with self.assertRaises(ValueError):ref.validate_horse_fixture(bad)
        for key,value in [('bounds',[0]*4),('light',0),('pose','CROUCHING')]:
            bad=copy.deepcopy(current);bad['observations'][0][key]=value
            with self.assertRaises(ValueError):ref.paired_horse_preview_inputs(self.preview(),bad,7,9)

    def test_smithing_fixture_fails_closed(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='smithing',
            serverSpawned=False,entityId=-1,clientReplicated=False,serverMenuOpened=True,
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_smithing_fixture(receipt)
        for key,value in [('mode','horse'),('entityId',0),('serverMenuOpened',False),('complete',False)]:
            bad=dict(receipt);bad[key]=value
            with self.assertRaises(ValueError):ref.validate_smithing_fixture(bad)

    def test_equipped_horse_fixture_requires_replicated_equipment(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-equipped',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseEquipmentPopulated=True,horseBodyItem='minecraft:diamond_horse_armor',
            horseSaddleItem='minecraft:saddle',screenOpen=True,mouseCentered=True,
            previewReady=True,complete=True)
        ref.validate_equipped_horse_fixture(receipt)
        for key,value in [('mode','horse'),('horseEquipmentPopulated',False),
                          ('horseBodyItem','minecraft:iron_horse_armor'),
                          ('horseSaddleItem','minecraft:air')]:
            bad=dict(receipt);bad[key]=value
            with self.assertRaises(ValueError):ref.validate_equipped_horse_fixture(bad)

    def test_baby_horse_fixture_requires_replicated_age_and_empty_equipment(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-baby',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseStateReady=True,horseBaby=True,horseEquipmentPopulated=False,
            horseBodyItem='',horseSaddleItem='',screenOpen=True,mouseCentered=True,
            previewReady=True,complete=True)
        ref.validate_baby_horse_fixture(receipt)
        for key,value in [('mode','horse'),('horseStateReady',False),('horseBaby',False),
                          ('horseEquipmentPopulated',True),('horseBodyItem','minecraft:diamond_horse_armor')]:
            bad=dict(receipt);bad[key]=value
            with self.assertRaises(ValueError):ref.validate_baby_horse_fixture(bad)

    def test_iron_equipped_horse_fixture_requires_iron_and_saddle(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-iron-equipped',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseEquipmentPopulated=True,horseBodyItem='minecraft:iron_horse_armor',
            horseSaddleItem='minecraft:saddle',screenOpen=True,mouseCentered=True,
            previewReady=True,complete=True)
        ref.validate_iron_equipped_horse_fixture(receipt)
        for key,value in [('mode','horse-equipped'),('horseEquipmentPopulated',False),
                          ('horseBodyItem','minecraft:diamond_horse_armor'),('horseSaddleItem','')]:
            bad=dict(receipt);bad[key]=value
            with self.assertRaises(ValueError):ref.validate_iron_equipped_horse_fixture(bad)

    def test_gold_equipped_horse_fixture_requires_gold_and_saddle(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-gold-equipped',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseEquipmentPopulated=True,horseBodyItem='minecraft:golden_horse_armor',
            horseSaddleItem='minecraft:saddle',screenOpen=True,mouseCentered=True,
            previewReady=True,complete=True)
        ref.validate_gold_equipped_horse_fixture(receipt)
        for key,value in [('mode','horse-equipped'),('horseEquipmentPopulated',False),
                          ('horseBodyItem','minecraft:diamond_horse_armor'),('horseSaddleItem','')]:
            bad=dict(receipt);bad[key]=value
            with self.assertRaises(ValueError):ref.validate_gold_equipped_horse_fixture(bad)

    def test_copper_equipped_horse_fixture_requires_copper_and_saddle(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-copper-equipped',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseEquipmentPopulated=True,horseBodyItem='minecraft:copper_horse_armor',
            horseSaddleItem='minecraft:saddle',screenOpen=True,mouseCentered=True,
            previewReady=True,complete=True)
        ref.validate_copper_equipped_horse_fixture(receipt)
        for key,value in [('mode','horse-equipped'),('horseEquipmentPopulated',False),
                          ('horseBodyItem','minecraft:diamond_horse_armor'),('horseSaddleItem','')]:
            bad=dict(receipt);bad[key]=value
            with self.assertRaises(ValueError):ref.validate_copper_equipped_horse_fixture(bad)

    def test_leather_equipped_horse_fixture_requires_leather_and_saddle(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-leather-equipped',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseEquipmentPopulated=True,horseBodyItem='minecraft:leather_horse_armor',
            horseSaddleItem='minecraft:saddle',screenOpen=True,mouseCentered=True,
            previewReady=True,complete=True)
        ref.validate_leather_equipped_horse_fixture(receipt)
        for key,value in [('mode','horse-equipped'),('horseEquipmentPopulated',False),
                          ('horseBodyItem','minecraft:diamond_horse_armor'),('horseSaddleItem','')]:
            bad=dict(receipt);bad[key]=value
            with self.assertRaises(ValueError):ref.validate_leather_equipped_horse_fixture(bad)

    def test_dyed_leather_equipped_horse_fixture_requires_leather_and_saddle(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-dyed-leather-equipped',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseEquipmentPopulated=True,horseBodyItem='minecraft:leather_horse_armor',horseBodyDyeRgb=0x3366CC,
            horseSaddleItem='minecraft:saddle',screenOpen=True,mouseCentered=True,
            previewReady=True,complete=True)
        ref.validate_dyed_leather_equipped_horse_fixture(receipt)
        for key,value in [('mode','horse-equipped'),('horseEquipmentPopulated',False),
                          ('horseBodyItem','minecraft:diamond_horse_armor'),('horseBodyDyeRgb',0x3366CD),('horseSaddleItem','')]:
            bad=dict(receipt);bad[key]=value
            with self.assertRaises(ValueError):ref.validate_dyed_leather_equipped_horse_fixture(bad)

    def test_marked_horse_fixture_requires_exact_replicated_variant(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-marked',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseStateReady=True,horseBaby=False,horseEquipmentPopulated=False,
            horseVariant='white',horseMarkings='white_dots',horseBodyItem='',horseSaddleItem='',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_marked_horse_fixture(receipt)
        for key,value in [('mode','horse'),('horseStateReady',False),('horseBaby',True),
                          ('horseVariant','black'),('horseMarkings','none'),
                          ('horseBodyItem','minecraft:diamond_horse_armor')]:
            bad=dict(receipt);bad[key]=value
            with self.assertRaises(ValueError):ref.validate_marked_horse_fixture(bad)

    def test_white_marking_horse_fixture_requires_exact_replicated_marking(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-marking-white',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseStateReady=True,horseBaby=False,horseEquipmentPopulated=False,
            horseVariant='white',horseMarkings='white',horseBodyItem='',horseSaddleItem='',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_white_marking_horse_fixture(receipt)
        bad=dict(receipt);bad['horseMarkings']='white_dots'
        with self.assertRaises(ValueError):ref.validate_white_marking_horse_fixture(bad)

    def test_white_field_horse_fixture_requires_exact_replicated_marking(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-marking-white-field',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseStateReady=True,horseBaby=False,horseEquipmentPopulated=False,
            horseVariant='white',horseMarkings='white_field',horseBodyItem='',horseSaddleItem='',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_white_field_horse_fixture(receipt)
        bad=dict(receipt);bad['horseMarkings']='white'
        with self.assertRaises(ValueError):ref.validate_white_field_horse_fixture(bad)

    def test_black_dots_horse_fixture_requires_exact_replicated_marking(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-marking-black-dots',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseStateReady=True,horseBaby=False,horseEquipmentPopulated=False,
            horseVariant='white',horseMarkings='black_dots',horseBodyItem='',horseSaddleItem='',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_black_dots_horse_fixture(receipt)
        bad=dict(receipt);bad['horseMarkings']='white_dots'
        with self.assertRaises(ValueError):ref.validate_black_dots_horse_fixture(bad)

    def test_black_horse_fixture_requires_exact_replicated_variant(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-black',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseStateReady=True,horseBaby=False,horseEquipmentPopulated=False,
            horseVariant='black',horseMarkings='none',horseBodyItem='',horseSaddleItem='',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_black_horse_fixture(receipt)
        for key,value in [('mode','horse'),('horseStateReady',False),('horseBaby',True),
                          ('horseVariant','white'),('horseMarkings','white_dots')]:
            bad=dict(receipt);bad[key]=value
            with self.assertRaises(ValueError):ref.validate_black_horse_fixture(bad)

    def test_brown_horse_fixture_requires_exact_replicated_variant(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-brown',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseStateReady=True,horseBaby=False,horseEquipmentPopulated=False,
            horseVariant='brown',horseMarkings='none',horseBodyItem='',horseSaddleItem='',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_brown_horse_fixture(receipt)
        bad=dict(receipt);bad['horseVariant']='black'
        with self.assertRaises(ValueError):ref.validate_brown_horse_fixture(bad)

    def test_creamy_horse_fixture_requires_exact_replicated_variant(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-creamy',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseStateReady=True,horseBaby=False,horseEquipmentPopulated=False,
            horseVariant='creamy',horseMarkings='none',horseBodyItem='',horseSaddleItem='',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_creamy_horse_fixture(receipt)
        bad=dict(receipt);bad['horseVariant']='brown'
        with self.assertRaises(ValueError):ref.validate_creamy_horse_fixture(bad)

    def test_chestnut_horse_fixture_requires_exact_replicated_variant(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-chestnut',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseStateReady=True,horseBaby=False,horseEquipmentPopulated=False,
            horseVariant='chestnut',horseMarkings='none',horseBodyItem='',horseSaddleItem='',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_chestnut_horse_fixture(receipt)
        bad=dict(receipt);bad['horseVariant']='creamy'
        with self.assertRaises(ValueError):ref.validate_chestnut_horse_fixture(bad)

    def test_gray_horse_fixture_requires_exact_replicated_variant(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-gray',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseStateReady=True,horseBaby=False,horseEquipmentPopulated=False,
            horseVariant='gray',horseMarkings='none',horseBodyItem='',horseSaddleItem='',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_gray_horse_fixture(receipt)
        bad=dict(receipt);bad['horseVariant']='chestnut'
        with self.assertRaises(ValueError):ref.validate_gray_horse_fixture(bad)

    def test_dark_brown_horse_fixture_requires_exact_replicated_variant(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-dark-brown',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseStateReady=True,horseBaby=False,horseEquipmentPopulated=False,
            horseVariant='dark_brown',horseMarkings='none',horseBodyItem='',horseSaddleItem='',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_dark_brown_horse_fixture(receipt)
        bad=dict(receipt);bad['horseVariant']='gray'
        with self.assertRaises(ValueError):ref.validate_dark_brown_horse_fixture(bad)

    def test_saddled_skeleton_horse_fixture_requires_type_and_saddle(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='skeleton-horse-saddled',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseStateReady=True,equineType='minecraft:skeleton_horse',horseBaby=False,
            horseEquipmentPopulated=False,horseBodyItem='',horseSaddleItem='minecraft:saddle',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_saddled_skeleton_horse_fixture(receipt)
        for key,value in [('mode','horse'),('horseStateReady',False),('equineType','minecraft:horse'),
                          ('horseBaby',True),('horseBodyItem','minecraft:diamond_horse_armor'),
                          ('horseSaddleItem','')]:
            bad=dict(receipt);bad[key]=value
            with self.assertRaises(ValueError):ref.validate_saddled_skeleton_horse_fixture(bad)

    def test_saddled_baby_skeleton_horse_fixture_requires_age_type_and_saddle(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='skeleton-horse-baby-saddled',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseStateReady=True,equineType='minecraft:skeleton_horse',horseBaby=True,
            horseEquipmentPopulated=False,horseBodyItem='',horseSaddleItem='minecraft:saddle',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_saddled_baby_skeleton_horse_fixture(receipt)
        for key,value in [('horseStateReady',False),('equineType','minecraft:horse'),
                          ('horseBaby',False),('horseSaddleItem','')]:
            bad=dict(receipt);bad[key]=value
            with self.assertRaises(ValueError):ref.validate_saddled_baby_skeleton_horse_fixture(bad)

    def test_saddled_zombie_horse_fixture_requires_type_and_saddle(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='zombie-horse-saddled',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseStateReady=True,equineType='minecraft:zombie_horse',horseBaby=False,
            horseEquipmentPopulated=False,horseBodyItem='',horseSaddleItem='minecraft:saddle',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_saddled_zombie_horse_fixture(receipt)
        for key,value in [('equineType','minecraft:skeleton_horse'),('horseSaddleItem','')]:
            bad=dict(receipt);bad[key]=value
            with self.assertRaises(ValueError):ref.validate_saddled_zombie_horse_fixture(bad)

    def test_saddled_baby_zombie_horse_fixture_requires_age(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='zombie-horse-baby-saddled',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseStateReady=True,equineType='minecraft:zombie_horse',horseBaby=True,
            horseEquipmentPopulated=False,horseBodyItem='',horseSaddleItem='minecraft:saddle',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_saddled_baby_zombie_horse_fixture(receipt)
        bad=dict(receipt);bad['horseBaby']=False
        with self.assertRaises(ValueError):ref.validate_saddled_baby_zombie_horse_fixture(bad)

    def test_marked_baby_fixture_requires_age_and_markings_together(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-baby-marked',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseStateReady=True,horseBaby=True,horseEquipmentPopulated=False,
            horseVariant='white',horseMarkings='white_dots',horseBodyItem='',horseSaddleItem='',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_baby_marked_horse_fixture(receipt)
        for key,value in [('horseBaby',False),('horseMarkings','none'),('horseStateReady',False)]:
            bad=dict(receipt);bad[key]=value
            with self.assertRaises(ValueError):ref.validate_baby_marked_horse_fixture(bad)

    def test_equipped_marked_fixture_requires_layers_and_equipment_together(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-marked-equipped',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseStateReady=True,horseBaby=False,horseEquipmentPopulated=True,
            horseVariant='white',horseMarkings='white_dots',
            horseBodyItem='minecraft:diamond_horse_armor',horseSaddleItem='minecraft:saddle',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_equipped_marked_horse_fixture(receipt)
        for key,value in [('horseEquipmentPopulated',False),('horseMarkings','none'),
                          ('horseBodyItem','minecraft:iron_horse_armor')]:
            bad=dict(receipt);bad[key]=value
            with self.assertRaises(ValueError):ref.validate_equipped_marked_horse_fixture(bad)

    def equipped_baby_geometry(self,frame=7,current=False):
        base=self.baby_geometry(frame,current)
        pose=copy.deepcopy(base['models'][0]['sourcePose'])
        state=list(base['models'][0]['state'])
        identity=list(base['completedDraws'][0]['view']) if not current else [1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0]
        equipment_view=list(identity);equipment_view[14]=0.001953125
        for texture,quads,vertices,indices in (
                ('minecraft:textures/entity/equipment/horse_body/diamond.png',72,288,432),
                ('minecraft:textures/entity/equipment/horse_saddle/saddle.png',102,408,612)):
            model=dict(texture=texture,pipeline='minecraft:pipeline/armor_cutout_no_cull',
                tint=-1,light=15728880,overlay=655360,sourcePose=copy.deepcopy(pose),
                state=list(state),quads=[dict(part='/body')]*quads)
            if current:model['entityPipGuardPixels']=1
            base['models'].append(model)
            if not current:
                base['completedDraws'].append(dict(texture=texture,pipeline=model['pipeline'],
                    depthCompare='LEQUAL_DEPTH_TEST',depthWrite=True,cull=False,blend='none',
                    vertices=vertices,indices=indices,view=equipment_view))
        return base

    def test_equipped_baby_fixture_geometry_owner_and_pixels_fail_closed(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-baby-equipped',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseStateReady=True,horseBaby=True,horseEquipmentPopulated=True,
            horseBodyItem='minecraft:diamond_horse_armor',horseSaddleItem='minecraft:saddle',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_equipped_baby_horse_fixture(receipt)
        frozen=self.equipped_baby_geometry();current=self.equipped_baby_geometry(9,True)
        self.assertTrue(ref.paired_equipped_baby_horse_geometry(frozen,current,7,9)['passed'])
        bad=copy.deepcopy(current);bad['models'][2]['quads'].pop()
        with self.assertRaises(ValueError):ref.paired_equipped_baby_horse_geometry(frozen,bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=3,gui_entity_preview_draws=4,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=984,gui_entity_preview_indices=1476)
        self.assertEqual(9,ref.validate_equipped_horse_owner(owner,7)['gal_submission_id'])
        baseline=Image.new('RGB',(1280,720));candidate=baseline.copy();candidate.putpixel((450,160),(32,32,32))
        self.assertTrue(ref.compare_equipped_baby_horse_images(baseline,candidate)['passed'])
        candidate.putpixel((450,160),(33,33,33))
        with self.assertRaises(ValueError):ref.compare_equipped_baby_horse_images(baseline,candidate)

    def test_iron_equipped_baby_fixture_geometry_owner_and_pixels_fail_closed(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-baby-iron-equipped',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseStateReady=True,horseBaby=True,horseEquipmentPopulated=True,
            horseBodyItem='minecraft:iron_horse_armor',horseSaddleItem='minecraft:saddle',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_iron_equipped_baby_horse_fixture(receipt)
        frozen=self.equipped_baby_geometry();current=self.equipped_baby_geometry(9,True)
        for geometry in (frozen,current):
            for model in geometry['models']:
                if model['texture'].endswith('/diamond.png'):
                    model['texture']='minecraft:textures/entity/equipment/horse_body/iron.png'
            for draw in geometry['completedDraws']:
                if draw['texture'].endswith('/diamond.png'):
                    draw['texture']='minecraft:textures/entity/equipment/horse_body/iron.png'
        baby_frozen=self.preview();baby_current=self.preview(9)
        for preview in (baby_frozen,baby_current):
            preview['observations'][0]['translation']=[0.0,0.65,0.0]
            preview['observations'][0]['scales']=[17.0,1.0,0.5]
        self.assertTrue(ref.paired_iron_equipped_baby_horse_preview_inputs(baby_frozen,baby_current,7,9)['passed'])
        self.assertTrue(ref.paired_iron_equipped_baby_horse_geometry(frozen,current,7,9)['passed'])
        bad=copy.deepcopy(current);bad['models'][2]['quads'].pop()
        with self.assertRaises(ValueError):ref.paired_iron_equipped_baby_horse_geometry(frozen,bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=3,gui_entity_preview_draws=4,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=984,gui_entity_preview_indices=1476)
        self.assertEqual(9,ref.validate_iron_equipped_baby_horse_owner(owner,7)['gal_submission_id'])
        baseline=Image.new('RGB',(1280,720));candidate=baseline.copy();candidate.putpixel((450,160),(32,32,32))
        self.assertTrue(ref.compare_iron_equipped_baby_horse_images(baseline,candidate)['passed'])
        candidate.putpixel((450,160),(33,33,33))
        with self.assertRaises(ValueError):ref.compare_iron_equipped_baby_horse_images(baseline,candidate)

    def test_gold_equipped_baby_fixture_geometry_owner_and_pixels_fail_closed(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-baby-gold-equipped',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseStateReady=True,horseBaby=True,horseEquipmentPopulated=True,
            horseBodyItem='minecraft:golden_horse_armor',horseSaddleItem='minecraft:saddle',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_gold_equipped_baby_horse_fixture(receipt)
        frozen=self.equipped_baby_geometry();current=self.equipped_baby_geometry(9,True)
        for geometry in (frozen,current):
            for model in geometry['models']:
                if model['texture'].endswith('/diamond.png'):
                    model['texture']='minecraft:textures/entity/equipment/horse_body/gold.png'
            for draw in geometry['completedDraws']:
                if draw['texture'].endswith('/diamond.png'):
                    draw['texture']='minecraft:textures/entity/equipment/horse_body/gold.png'
        baby_frozen=self.preview();baby_current=self.preview(9)
        for preview in (baby_frozen,baby_current):
            preview['observations'][0]['translation']=[0.0,0.65,0.0]
            preview['observations'][0]['scales']=[17.0,1.0,0.5]
        self.assertTrue(ref.paired_gold_equipped_baby_horse_preview_inputs(baby_frozen,baby_current,7,9)['passed'])
        self.assertTrue(ref.paired_gold_equipped_baby_horse_geometry(frozen,current,7,9)['passed'])
        bad=copy.deepcopy(current);bad['models'][2]['quads'].pop()
        with self.assertRaises(ValueError):ref.paired_gold_equipped_baby_horse_geometry(frozen,bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=3,gui_entity_preview_draws=4,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=984,gui_entity_preview_indices=1476)
        self.assertEqual(9,ref.validate_gold_equipped_baby_horse_owner(owner,7)['gal_submission_id'])
        baseline=Image.new('RGB',(1280,720));candidate=baseline.copy();candidate.putpixel((450,160),(32,32,32))
        self.assertTrue(ref.compare_gold_equipped_baby_horse_images(baseline,candidate)['passed'])
        candidate.putpixel((450,160),(33,33,33))
        with self.assertRaises(ValueError):ref.compare_gold_equipped_baby_horse_images(baseline,candidate)

    def test_copper_equipped_baby_fixture_geometry_owner_and_pixels_fail_closed(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-baby-copper-equipped',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseStateReady=True,horseBaby=True,horseEquipmentPopulated=True,
            horseBodyItem='minecraft:copper_horse_armor',horseSaddleItem='minecraft:saddle',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_copper_equipped_baby_horse_fixture(receipt)
        frozen=self.equipped_baby_geometry();current=self.equipped_baby_geometry(9,True)
        for geometry in (frozen,current):
            for model in geometry['models']:
                if model['texture'].endswith('/diamond.png'):
                    model['texture']='minecraft:textures/entity/equipment/horse_body/copper.png'
            for draw in geometry['completedDraws']:
                if draw['texture'].endswith('/diamond.png'):
                    draw['texture']='minecraft:textures/entity/equipment/horse_body/copper.png'
        baby_frozen=self.preview();baby_current=self.preview(9)
        for preview in (baby_frozen,baby_current):
            preview['observations'][0]['translation']=[0.0,0.65,0.0]
            preview['observations'][0]['scales']=[17.0,1.0,0.5]
        self.assertTrue(ref.paired_copper_equipped_baby_horse_preview_inputs(baby_frozen,baby_current,7,9)['passed'])
        self.assertTrue(ref.paired_copper_equipped_baby_horse_geometry(frozen,current,7,9)['passed'])
        bad=copy.deepcopy(current);bad['models'][2]['quads'].pop()
        with self.assertRaises(ValueError):ref.paired_copper_equipped_baby_horse_geometry(frozen,bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=3,gui_entity_preview_draws=4,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=984,gui_entity_preview_indices=1476)
        self.assertEqual(9,ref.validate_copper_equipped_baby_horse_owner(owner,7)['gal_submission_id'])
        baseline=Image.new('RGB',(1280,720));candidate=baseline.copy();candidate.putpixel((450,160),(32,32,32))
        self.assertTrue(ref.compare_copper_equipped_baby_horse_images(baseline,candidate)['passed'])
        candidate.putpixel((450,160),(33,33,33))
        with self.assertRaises(ValueError):ref.compare_copper_equipped_baby_horse_images(baseline,candidate)

    def test_leather_equipped_baby_fixture_geometry_owner_and_pixels_fail_closed(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-baby-leather-equipped',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseStateReady=True,horseBaby=True,horseEquipmentPopulated=True,
            horseBodyItem='minecraft:leather_horse_armor',horseSaddleItem='minecraft:saddle',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_leather_equipped_baby_horse_fixture(receipt)
        frozen=self.equipped_baby_geometry();current=self.equipped_baby_geometry(9,True)
        for geometry in (frozen,current):
            for model in geometry['models']:
                if model['texture'].endswith('/diamond.png'):
                    model['texture']='minecraft:textures/entity/equipment/horse_body/leather.png'
                    model['tint']=-6265536
            for draw in geometry['completedDraws']:
                if draw['texture'].endswith('/diamond.png'):
                    draw['texture']='minecraft:textures/entity/equipment/horse_body/leather.png'
        baby_frozen=self.preview();baby_current=self.preview(9)
        for preview in (baby_frozen,baby_current):
            preview['observations'][0]['translation']=[0.0,0.65,0.0]
            preview['observations'][0]['scales']=[17.0,1.0,0.5]
        self.assertTrue(ref.paired_leather_equipped_baby_horse_preview_inputs(baby_frozen,baby_current,7,9)['passed'])
        self.assertTrue(ref.paired_leather_equipped_baby_horse_geometry(frozen,current,7,9)['passed'])
        bad=copy.deepcopy(current);bad['models'][2]['quads'].pop()
        with self.assertRaises(ValueError):ref.paired_leather_equipped_baby_horse_geometry(frozen,bad,7,9)
        bad=copy.deepcopy(current)
        next(model for model in bad['models'] if model['texture'].endswith('/leather.png'))['tint']=-1
        with self.assertRaises(ValueError):ref.paired_leather_equipped_baby_horse_geometry(frozen,bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=3,gui_entity_preview_draws=4,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=984,gui_entity_preview_indices=1476)
        self.assertEqual(9,ref.validate_leather_equipped_baby_horse_owner(owner,7)['gal_submission_id'])
        baseline=Image.new('RGB',(1280,720));candidate=baseline.copy();candidate.putpixel((450,160),(32,32,32))
        self.assertTrue(ref.compare_leather_equipped_baby_horse_images(baseline,candidate)['passed'])
        candidate.putpixel((450,160),(33,33,33))
        with self.assertRaises(ValueError):ref.compare_leather_equipped_baby_horse_images(baseline,candidate)

    def test_dyed_leather_equipped_baby_fixture_geometry_owner_and_pixels_fail_closed(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,mode='horse-baby-dyed-leather-equipped',
            serverSpawned=True,entityId=4,clientReplicated=True,serverMenuOpened=True,
            horseStateReady=True,horseBaby=True,horseEquipmentPopulated=True,
            horseBodyItem='minecraft:leather_horse_armor',horseBodyDyeRgb=0x3366CC,horseSaddleItem='minecraft:saddle',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_dyed_leather_equipped_baby_horse_fixture(receipt)
        frozen=self.equipped_baby_geometry();current=self.equipped_baby_geometry(9,True)
        for geometry in (frozen,current):
            for model in geometry['models']:
                if model['texture'].endswith('/diamond.png'):
                    model['texture']='minecraft:textures/entity/equipment/horse_body/leather.png'
                    model['tint']=-13408564
            for draw in geometry['completedDraws']:
                if draw['texture'].endswith('/diamond.png'):
                    draw['texture']='minecraft:textures/entity/equipment/horse_body/leather.png'
        baby_frozen=self.preview();baby_current=self.preview(9)
        for preview in (baby_frozen,baby_current):
            preview['observations'][0]['translation']=[0.0,0.65,0.0]
            preview['observations'][0]['scales']=[17.0,1.0,0.5]
        self.assertTrue(ref.paired_dyed_leather_equipped_baby_horse_preview_inputs(baby_frozen,baby_current,7,9)['passed'])
        self.assertTrue(ref.paired_dyed_leather_equipped_baby_horse_geometry(frozen,current,7,9)['passed'])
        bad=copy.deepcopy(current);bad['models'][2]['quads'].pop()
        with self.assertRaises(ValueError):ref.paired_dyed_leather_equipped_baby_horse_geometry(frozen,bad,7,9)
        bad=copy.deepcopy(current)
        next(model for model in bad['models'] if model['texture'].endswith('/leather.png'))['tint']=-6265536
        with self.assertRaises(ValueError):ref.paired_dyed_leather_equipped_baby_horse_geometry(frozen,bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=3,gui_entity_preview_draws=4,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=984,gui_entity_preview_indices=1476)
        self.assertEqual(9,ref.validate_dyed_leather_equipped_baby_horse_owner(owner,7)['gal_submission_id'])
        baseline=Image.new('RGB',(1280,720));candidate=baseline.copy();candidate.putpixel((450,160),(32,32,32))
        self.assertTrue(ref.compare_dyed_leather_equipped_baby_horse_images(baseline,candidate)['passed'])
        candidate.putpixel((450,160),(33,33,33))
        with self.assertRaises(ValueError):ref.compare_dyed_leather_equipped_baby_horse_images(baseline,candidate)

    def marked_geometry(self,frame=7,current=False):
        receipt=self.baby_geometry(frame,current);base=receipt['models'][0]
        base['state'][1]=1.0;base['sourcePose']['modelView'][13]+=20.40001
        marking=copy.deepcopy(base);marking['texture']='minecraft:textures/entity/horse/horse_markings_whitedots.png'
        marking['pipeline']='minecraft:pipeline/entity_translucent';receipt['models'].append(marking)
        if not current:
            receipt['completedDraws'].append(dict(texture=marking['texture'],pipeline=marking['pipeline'],
                depthCompare='LEQUAL_DEPTH_TEST',depthWrite=True,cull=False,
                blend='BlendFunction[sourceColor=SRC_ALPHA, destColor=ONE_MINUS_SRC_ALPHA, sourceAlpha=ONE, destAlpha=ONE_MINUS_SRC_ALPHA]',
                vertices=288,indices=432,view=list(receipt['completedDraws'][0]['view'])))
        return receipt

    def test_marked_horse_geometry_owner_and_pixels_fail_closed(self):
        frozen=self.marked_geometry();current=self.marked_geometry(9,True)
        self.assertTrue(ref.paired_marked_horse_geometry(frozen,current,7,9)['passed'])
        bad=copy.deepcopy(current);bad['models'][1]['pipeline']='minecraft:pipeline/entity_cutout_no_cull'
        with self.assertRaises(ValueError):ref.paired_marked_horse_geometry(frozen,bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=2,gui_entity_preview_draws=3,
            gui_entity_preview_material_mask=384,gui_entity_preview_vertices=576,gui_entity_preview_indices=864)
        self.assertEqual(9,ref.validate_marked_horse_owner(owner,7)['gal_submission_id'])
        bad_owner=dict(owner);bad_owner['gui_entity_preview_material_mask']=128
        with self.assertRaises(ValueError):ref.validate_marked_horse_owner(bad_owner,7)
        baseline=Image.new('RGB',(1280,720));candidate=baseline.copy()
        for n in range(400):candidate.putpixel((450+n%164,160+n//164),(4,4,4))
        self.assertTrue(ref.compare_marked_horse_images(baseline,candidate)['passed'])
        for x in range(450,614):
            for y in range(160,324):candidate.putpixel((x,y),(5,5,5))
        with self.assertRaises(ValueError):ref.compare_marked_horse_images(baseline,candidate)

    def test_white_marking_horse_geometry_uses_white_layer_texture(self):
        frozen=self.marked_geometry();current=self.marked_geometry(9,True)
        for receipt in (frozen,current):
            receipt['models'][1]['texture']='minecraft:textures/entity/horse/horse_markings_white.png'
        frozen['completedDraws'][1]['texture']='minecraft:textures/entity/horse/horse_markings_white.png'
        self.assertTrue(ref.paired_white_marking_horse_geometry(frozen,current,7,9)['passed'])
        bad=copy.deepcopy(current)
        bad['models'][1]['texture']='minecraft:textures/entity/horse/horse_markings_whitedots.png'
        with self.assertRaises(ValueError):ref.paired_white_marking_horse_geometry(frozen,bad,7,9)

    def test_white_field_horse_geometry_uses_white_field_layer_texture(self):
        frozen=self.marked_geometry();current=self.marked_geometry(9,True)
        for receipt in (frozen,current):
            receipt['models'][1]['texture']='minecraft:textures/entity/horse/horse_markings_whitefield.png'
        frozen['completedDraws'][1]['texture']='minecraft:textures/entity/horse/horse_markings_whitefield.png'
        self.assertTrue(ref.paired_white_field_horse_geometry(frozen,current,7,9)['passed'])

    def test_black_dots_horse_geometry_uses_black_dots_layer_texture(self):
        frozen=self.marked_geometry();current=self.marked_geometry(9,True)
        for receipt in (frozen,current):
            receipt['models'][1]['texture']='minecraft:textures/entity/horse/horse_markings_blackdots.png'
        frozen['completedDraws'][1]['texture']='minecraft:textures/entity/horse/horse_markings_blackdots.png'
        self.assertTrue(ref.paired_black_dots_horse_geometry(frozen,current,7,9)['passed'])

    def test_black_horse_geometry_owner_and_pixels_fail_closed(self):
        frozen=self.baby_geometry();current=self.baby_geometry(9,True)
        for receipt in (frozen,current):
            model=receipt['models'][0]
            model['texture']='minecraft:textures/entity/horse/horse_black.png'
            model['state'][1]=1.0
            model['sourcePose']['modelView'][13]+=20.40001
        frozen['completedDraws'][0]['texture']='minecraft:textures/entity/horse/horse_black.png'
        self.assertTrue(ref.paired_black_horse_geometry(frozen,current,7,9)['passed'])
        bad=copy.deepcopy(current);bad['models'][0]['texture']='minecraft:textures/entity/horse/horse_white.png'
        with self.assertRaises(ValueError):ref.paired_black_horse_geometry(frozen,bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=1,gui_entity_preview_draws=2,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=288,gui_entity_preview_indices=432)
        self.assertEqual(9,ref.validate_black_horse_owner(owner,7)['gal_submission_id'])
        baseline=Image.new('RGB',(1280,720));candidate=baseline.copy();candidate.putpixel((450,160),(4,4,4))
        self.assertTrue(ref.compare_black_horse_images(baseline,candidate)['passed'])
        candidate.putpixel((450,160),(5,5,5))
        with self.assertRaises(ValueError):ref.compare_black_horse_images(baseline,candidate)

    def test_brown_horse_geometry_uses_brown_texture(self):
        frozen=self.baby_geometry();current=self.baby_geometry(9,True)
        for receipt in (frozen,current):
            model=receipt['models'][0];model['texture']='minecraft:textures/entity/horse/horse_brown.png'
            model['state'][1]=1.0;model['sourcePose']['modelView'][13]+=20.40001
        frozen['completedDraws'][0]['texture']='minecraft:textures/entity/horse/horse_brown.png'
        self.assertTrue(ref.paired_brown_horse_geometry(frozen,current,7,9)['passed'])

    def test_creamy_horse_geometry_uses_creamy_texture(self):
        frozen=self.baby_geometry();current=self.baby_geometry(9,True)
        for receipt in (frozen,current):
            model=receipt['models'][0];model['texture']='minecraft:textures/entity/horse/horse_creamy.png'
            model['state'][1]=1.0;model['sourcePose']['modelView'][13]+=20.40001
        frozen['completedDraws'][0]['texture']='minecraft:textures/entity/horse/horse_creamy.png'
        self.assertTrue(ref.paired_creamy_horse_geometry(frozen,current,7,9)['passed'])

    def test_chestnut_horse_geometry_uses_chestnut_texture(self):
        frozen=self.baby_geometry();current=self.baby_geometry(9,True)
        for receipt in (frozen,current):
            model=receipt['models'][0];model['texture']='minecraft:textures/entity/horse/horse_chestnut.png'
            model['state'][1]=1.0;model['sourcePose']['modelView'][13]+=20.40001
        frozen['completedDraws'][0]['texture']='minecraft:textures/entity/horse/horse_chestnut.png'
        self.assertTrue(ref.paired_chestnut_horse_geometry(frozen,current,7,9)['passed'])

    def test_gray_horse_geometry_uses_gray_texture(self):
        frozen=self.baby_geometry();current=self.baby_geometry(9,True)
        for receipt in (frozen,current):
            model=receipt['models'][0];model['texture']='minecraft:textures/entity/horse/horse_gray.png'
            model['state'][1]=1.0;model['sourcePose']['modelView'][13]+=20.40001
        frozen['completedDraws'][0]['texture']='minecraft:textures/entity/horse/horse_gray.png'
        self.assertTrue(ref.paired_gray_horse_geometry(frozen,current,7,9)['passed'])

    def test_dark_brown_horse_geometry_uses_dark_brown_texture(self):
        frozen=self.baby_geometry();current=self.baby_geometry(9,True)
        for receipt in (frozen,current):
            model=receipt['models'][0];model['texture']='minecraft:textures/entity/horse/horse_darkbrown.png'
            model['state'][1]=1.0;model['sourcePose']['modelView'][13]+=20.40001
        frozen['completedDraws'][0]['texture']='minecraft:textures/entity/horse/horse_darkbrown.png'
        self.assertTrue(ref.paired_dark_brown_horse_geometry(frozen,current,7,9)['passed'])

    def test_saddled_skeleton_horse_geometry_owner_and_pixels_fail_closed(self):
        frozen=self.equipped_baby_geometry();current=self.equipped_baby_geometry(9,True)
        for receipt in (frozen,current):
            base,saddle=receipt['models'][0],receipt['models'][2]
            base['texture']='minecraft:textures/entity/horse/horse_skeleton.png'
            saddle['texture']='minecraft:textures/entity/equipment/skeleton_horse_saddle/saddle.png'
            for model in (base,saddle):
                model['state'][1]=1.0;model['sourcePose']['modelView'][13]+=20.40001
            receipt['models']=[base,saddle]
        frozen['completedDraws'][0]['texture']='minecraft:textures/entity/horse/horse_skeleton.png'
        frozen['completedDraws'][2]['texture']='minecraft:textures/entity/equipment/skeleton_horse_saddle/saddle.png'
        frozen['completedDraws']=[frozen['completedDraws'][0],frozen['completedDraws'][2]]
        self.assertTrue(ref.paired_saddled_skeleton_horse_geometry(frozen,current,7,9)['passed'])
        bad=copy.deepcopy(current);bad['models'][1]['quads'].pop()
        with self.assertRaises(ValueError):ref.paired_saddled_skeleton_horse_geometry(frozen,bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=2,gui_entity_preview_draws=3,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=696,gui_entity_preview_indices=1044)
        self.assertEqual(9,ref.validate_saddled_skeleton_horse_owner(owner,7)['gal_submission_id'])
        baseline=Image.new('RGB',(1280,720));candidate=baseline.copy();candidate.putpixel((450,160),(160,160,160))
        self.assertTrue(ref.compare_saddled_skeleton_horse_images(baseline,candidate)['passed'])
        candidate.putpixel((450,160),(161,161,161))
        with self.assertRaises(ValueError):ref.compare_saddled_skeleton_horse_images(baseline,candidate)
        candidate=baseline.copy()
        for n in range(161):candidate.putpixel((450+n%164,160+n//164),(65,65,65))
        with self.assertRaises(ValueError):ref.compare_saddled_skeleton_horse_images(baseline,candidate)

    def test_saddled_baby_skeleton_horse_selects_baby_models(self):
        frozen=self.equipped_baby_geometry();current=self.equipped_baby_geometry(9,True)
        for receipt in (frozen,current):
            base,saddle=receipt['models'][0],receipt['models'][2]
            base['texture']='minecraft:textures/entity/horse/horse_skeleton.png'
            saddle['texture']='minecraft:textures/entity/equipment/skeleton_horse_saddle/saddle.png'
            receipt['models']=[base,saddle]
        frozen['completedDraws'][0]['texture']='minecraft:textures/entity/horse/horse_skeleton.png'
        frozen['completedDraws'][2]['texture']='minecraft:textures/entity/equipment/skeleton_horse_saddle/saddle.png'
        frozen['completedDraws']=[frozen['completedDraws'][2],frozen['completedDraws'][0]]
        self.assertTrue(ref.paired_saddled_baby_skeleton_horse_geometry(frozen,current,7,9)['passed'])
        bad=copy.deepcopy(current);bad['models'][1]['state'][1]=1.0
        with self.assertRaises(ValueError):ref.paired_saddled_baby_skeleton_horse_geometry(frozen,bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=2,gui_entity_preview_draws=3,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=696,gui_entity_preview_indices=1044)
        self.assertEqual(9,ref.validate_saddled_baby_skeleton_horse_owner(owner,7)['gal_submission_id'])

    def test_saddled_zombie_horse_selects_zombie_resources(self):
        frozen=self.equipped_baby_geometry();current=self.equipped_baby_geometry(9,True)
        for receipt in (frozen,current):
            base,saddle=receipt['models'][0],receipt['models'][2]
            base['texture']='minecraft:textures/entity/horse/horse_zombie.png'
            saddle['texture']='minecraft:textures/entity/equipment/zombie_horse_saddle/saddle.png'
            for model in (base,saddle):
                model['state'][1]=1.0;model['sourcePose']['modelView'][13]+=20.40001
            receipt['models']=[base,saddle]
        frozen['completedDraws'][0]['texture']='minecraft:textures/entity/horse/horse_zombie.png'
        frozen['completedDraws'][2]['texture']='minecraft:textures/entity/equipment/zombie_horse_saddle/saddle.png'
        frozen['completedDraws']=[frozen['completedDraws'][0],frozen['completedDraws'][2]]
        self.assertTrue(ref.paired_saddled_zombie_horse_geometry(frozen,current,7,9)['passed'])
        bad=copy.deepcopy(current);bad['models'][0]['texture']='minecraft:textures/entity/horse/horse_skeleton.png'
        with self.assertRaises(ValueError):ref.paired_saddled_zombie_horse_geometry(frozen,bad,7,9)

    def test_saddled_baby_zombie_horse_selects_baby_models(self):
        frozen=self.equipped_baby_geometry();current=self.equipped_baby_geometry(9,True)
        for receipt in (frozen,current):
            base,saddle=receipt['models'][0],receipt['models'][2]
            base['texture']='minecraft:textures/entity/horse/horse_zombie.png'
            saddle['texture']='minecraft:textures/entity/equipment/zombie_horse_saddle/saddle.png'
            receipt['models']=[base,saddle]
        frozen['completedDraws'][0]['texture']='minecraft:textures/entity/horse/horse_zombie.png'
        frozen['completedDraws'][2]['texture']='minecraft:textures/entity/equipment/zombie_horse_saddle/saddle.png'
        frozen['completedDraws']=[frozen['completedDraws'][2],frozen['completedDraws'][0]]
        self.assertTrue(ref.paired_saddled_baby_zombie_horse_geometry(frozen,current,7,9)['passed'])
    def equipped_marked_geometry(self,frame=7,current=False,baby=False):
        receipt=self.marked_geometry(frame,current);base=receipt['models'][0]
        if baby:
            for model in receipt['models']:
                model['state'][1]=0.5
        view=[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.001953125,1.0]
        equipment=(
                ('minecraft:textures/entity/equipment/horse_body/diamond.png',72,288,432),
                ('minecraft:textures/entity/equipment/horse_saddle/saddle.png',102,408,612))
        for texture,quads,vertices,indices in (equipment if baby else reversed(equipment)):
            model=dict(texture=texture,pipeline='minecraft:pipeline/armor_cutout_no_cull',tint=-1,
                light=15728880,overlay=655360,sourcePose=copy.deepcopy(base['sourcePose']),
                state=list(base['state']),quads=[dict(part='/body')]*quads)
            if current:model['entityPipGuardPixels']=1
            receipt['models'].append(model)
            if not current:receipt['completedDraws'].append(dict(texture=texture,pipeline=model['pipeline'],
                depthCompare='LEQUAL_DEPTH_TEST',depthWrite=True,cull=False,blend='none',
                vertices=vertices,indices=indices,view=view))
        return receipt

    def test_equipped_marked_geometry_and_owner_fail_closed(self):
        frozen=self.equipped_marked_geometry();current=self.equipped_marked_geometry(9,True)
        self.assertTrue(ref.paired_equipped_marked_horse_geometry(frozen,current,7,9)['passed'])
        bad=copy.deepcopy(current);bad['models'][2]['quads'].pop()
        with self.assertRaises(ValueError):ref.paired_equipped_marked_horse_geometry(frozen,bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=4,gui_entity_preview_draws=5,
            gui_entity_preview_material_mask=384,gui_entity_preview_vertices=1272,gui_entity_preview_indices=1908)
        self.assertEqual(9,ref.validate_equipped_marked_horse_owner(owner,7)['gal_submission_id'])
        owner['gui_entity_preview_draws']=4
        with self.assertRaises(ValueError):ref.validate_equipped_marked_horse_owner(owner,7)

    def test_equipped_baby_marked_fixture_geometry_owner_and_pixels_fail_closed(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,
            mode='horse-baby-marked-equipped',serverSpawned=True,entityId=41,
            clientReplicated=True,serverMenuOpened=True,horseStateReady=True,horseBaby=True,
            horseEquipmentPopulated=True,horseVariant='white',horseMarkings='white_dots',
            horseBodyItem='minecraft:diamond_horse_armor',horseSaddleItem='minecraft:saddle',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_equipped_baby_marked_horse_fixture(receipt)
        bad=dict(receipt);bad['horseBaby']=False
        with self.assertRaises(ValueError):ref.validate_equipped_baby_marked_horse_fixture(bad)
        frozen=self.equipped_marked_geometry(baby=True)
        current=self.equipped_marked_geometry(9,True,baby=True)
        self.assertTrue(ref.paired_equipped_baby_marked_horse_geometry(frozen,current,7,9)['passed'])
        bad=copy.deepcopy(current);bad['models'][1]['state'][1]=1.0
        with self.assertRaises(ValueError):ref.paired_equipped_baby_marked_horse_geometry(frozen,bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=4,gui_entity_preview_draws=5,
            gui_entity_preview_material_mask=384,gui_entity_preview_vertices=1272,gui_entity_preview_indices=1908)
        self.assertEqual(9,ref.validate_equipped_baby_marked_horse_owner(owner,7)['gal_submission_id'])
        baseline=Image.new('RGB',(1280,720));candidate=baseline.copy();candidate.putpixel((450,160),(32,32,32))
        self.assertTrue(ref.compare_equipped_baby_marked_horse_images(baseline,candidate)['passed'])
        candidate.putpixel((450,160),(33,33,33))
        with self.assertRaises(ValueError):ref.compare_equipped_baby_marked_horse_images(baseline,candidate)

    def test_chested_donkey_fixture_requires_type_chest_columns_and_saddle(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,
            mode='donkey-chested-equipped',serverSpawned=True,entityId=41,
            clientReplicated=True,serverMenuOpened=True,horseStateReady=True,horseBaby=False,
            horseEquipmentPopulated=False,equineType='minecraft:donkey',horseHasChest=True,
            horseInventoryColumns=5,horseBodyItem='',horseSaddleItem='minecraft:saddle',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_chested_donkey_fixture(receipt)
        for key,value in (('equineType','minecraft:mule'),('horseHasChest',False),
                          ('horseInventoryColumns',0),('horseSaddleItem','')):
            bad=dict(receipt);bad[key]=value
            with self.assertRaises(ValueError):ref.validate_chested_donkey_fixture(bad)

    def test_chested_mule_fixture_requires_type_chest_columns_and_saddle(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,
            mode='mule-chested-equipped',serverSpawned=True,entityId=41,
            clientReplicated=True,serverMenuOpened=True,horseStateReady=True,horseBaby=False,
            horseEquipmentPopulated=False,equineType='minecraft:mule',horseHasChest=True,
            horseInventoryColumns=5,horseBodyItem='',horseSaddleItem='minecraft:saddle',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_chested_mule_fixture(receipt)
        for key,value in (('equineType','minecraft:donkey'),('horseHasChest',False),
                          ('horseInventoryColumns',0),('horseSaddleItem','')):
            bad=dict(receipt);bad[key]=value
            with self.assertRaises(ValueError):ref.validate_chested_mule_fixture(bad)

    def test_baby_chested_donkey_fixture_requires_baby_with_complete_equipment(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,
            mode='donkey-baby-chested-equipped',serverSpawned=True,entityId=41,
            clientReplicated=True,serverMenuOpened=True,horseStateReady=True,horseBaby=True,
            horseEquipmentPopulated=False,equineType='minecraft:donkey',horseHasChest=True,
            horseInventoryColumns=5,horseBodyItem='',horseSaddleItem='minecraft:saddle',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_baby_chested_donkey_fixture(receipt)
        for key,value in (('horseBaby',False),('horseHasChest',False),('horseSaddleItem','')):
            bad=dict(receipt);bad[key]=value
            with self.assertRaises(ValueError):ref.validate_baby_chested_donkey_fixture(bad)

    def test_baby_chested_mule_fixture_requires_baby_with_complete_equipment(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,
            mode='mule-baby-chested-equipped',serverSpawned=True,entityId=41,
            clientReplicated=True,serverMenuOpened=True,horseStateReady=True,horseBaby=True,
            horseEquipmentPopulated=False,equineType='minecraft:mule',horseHasChest=True,
            horseInventoryColumns=5,horseBodyItem='',horseSaddleItem='minecraft:saddle',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_baby_chested_mule_fixture(receipt)
        for key,value in (('horseBaby',False),('horseHasChest',False),('horseSaddleItem','')):
            bad=dict(receipt);bad[key]=value
            with self.assertRaises(ValueError):ref.validate_baby_chested_mule_fixture(bad)

    def test_chested_blue_llama_fixture_requires_type_variant_chest_and_decor(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,
            mode='llama-chested-blue-carpet',serverSpawned=True,entityId=41,
            clientReplicated=True,serverMenuOpened=True,horseStateReady=True,horseBaby=False,
            horseEquipmentPopulated=False,equineType='minecraft:llama',horseHasChest=True,
            horseInventoryColumns=5,llamaVariant='creamy',llamaStrength=5,llamaDecorPopulated=True,
            horseBodyItem='minecraft:blue_carpet',horseSaddleItem='',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_chested_blue_llama_fixture(receipt)
        for key,value in (('equineType','minecraft:donkey'),('horseHasChest',False),
                          ('llamaVariant','white'),('llamaStrength',4),('llamaDecorPopulated',False),('horseBodyItem','')):
            bad=dict(receipt);bad[key]=value
            with self.assertRaises(ValueError):ref.validate_chested_blue_llama_fixture(bad)

    def test_baby_chested_blue_llama_fixture_requires_baby_with_complete_decor(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,
            mode='llama-baby-chested-blue-carpet',serverSpawned=True,entityId=41,
            clientReplicated=True,serverMenuOpened=True,horseStateReady=True,horseBaby=True,
            horseEquipmentPopulated=False,equineType='minecraft:llama',horseHasChest=True,
            horseInventoryColumns=5,llamaVariant='creamy',llamaStrength=5,llamaDecorPopulated=True,
            horseBodyItem='minecraft:blue_carpet',horseSaddleItem='',
            screenOpen=True,mouseCentered=True,previewReady=True,complete=True)
        ref.validate_baby_chested_blue_llama_fixture(receipt)
        for key,value in (('horseBaby',False),('llamaStrength',4),('llamaDecorPopulated',False)):
            bad=dict(receipt);bad[key]=value
            with self.assertRaises(ValueError):ref.validate_baby_chested_blue_llama_fixture(bad)

    def donkey_geometry(self,frame=7,current=False):
        pose=dict(modelView=[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,78.0,37.0,20.0,1.0],
                  normal=[1.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,1.0])
        specs=[('minecraft:textures/entity/equipment/donkey_saddle/saddle.png','minecraft:pipeline/armor_cutout_no_cull',114,456,684,True),
               ('minecraft:textures/entity/horse/donkey.png','minecraft:pipeline/entity_cutout_no_cull',84,336,504,False)]
        models=[];draws=[]
        identity=[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0]
        for texture,pipeline,quads,vertices,indices,offset in specs:
            model=dict(texture=texture,pipeline=pipeline,tint=-1,light=15728880,overlay=655360,
                sourcePose=copy.deepcopy(pose),state=[3.0,1.0,165.3437,-14.656311,15.454812],
                quads=[dict(part='/body')]*quads)
            if current:
                model['entityPipGuardPixels']=1;model['sourcePose']['modelView'][12:14]=[79.0,38.0]
                model['state'][0]=76.0
            models.append(model)
            if not current:
                view=list(identity)
                if offset:view[14]=0.001953125
                draws.append(dict(texture=texture,pipeline=pipeline,depthCompare='LEQUAL_DEPTH_TEST',
                    depthWrite=True,cull=False,blend='none',vertices=vertices,indices=indices,view=view))
        return dict(schema='equipment-model-geometry-v1',enabled=True,renderedFrameIndex=frame,
            complete=True,gpuReadback=False,capabilityAdmitted=False,models=models,completedDraws=draws)

    def test_chested_donkey_inputs_geometry_owner_and_pixels_fail_closed(self):
        frozen=self.preview();current=self.preview(9)
        for receipt in (frozen,current):receipt['observations'][0]['translation']=[0.0,1.0,0.0]
        self.assertTrue(ref.paired_chested_donkey_preview_inputs(frozen,current,7,9)['passed'])
        fg=self.donkey_geometry();cg=self.donkey_geometry(9,True)
        self.assertTrue(ref.paired_chested_donkey_geometry(fg,cg,7,9)['passed'])
        bad=copy.deepcopy(cg);bad['models'][0]['quads'].pop()
        with self.assertRaises(ValueError):ref.paired_chested_donkey_geometry(fg,bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=2,gui_entity_preview_draws=3,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=792,gui_entity_preview_indices=1188)
        self.assertEqual(9,ref.validate_chested_donkey_owner(owner,7)['gal_submission_id'])
        bad=dict(owner);bad['gui_entity_preview_indices']=1187
        with self.assertRaises(ValueError):ref.validate_chested_donkey_owner(bad,7)
        baseline=Image.new('RGB',(1280,720));candidate=baseline.copy();candidate.putpixel((450,160),(16,16,16))
        self.assertTrue(ref.compare_chested_donkey_images(baseline,candidate)['passed'])
        candidate.putpixel((450,160),(17,17,17))
        with self.assertRaises(ValueError):ref.compare_chested_donkey_images(baseline,candidate)

    def test_baby_chested_donkey_inputs_geometry_owner_and_pixels_fail_closed(self):
        frozen=self.preview();current=self.preview(9)
        for receipt in (frozen,current):
            receipt['observations'][0]['translation']=[0.0,0.625,0.0]
            receipt['observations'][0]['scales']=[17.0,1.0,0.5]
        self.assertTrue(ref.paired_baby_chested_donkey_preview_inputs(frozen,current,7,9)['passed'])
        fg=self.donkey_geometry();cg=self.donkey_geometry(9,True)
        for geometry in (fg,cg):
            for model in geometry['models']:
                model['state'][1]=0.5
                model['quads'][-6:]=[dict(part='/body/tail') for _ in range(6)]
        fg['models'].reverse();fg['completedDraws'].reverse()
        self.assertTrue(ref.paired_baby_chested_donkey_geometry(fg,cg,7,9)['passed'])
        bad=copy.deepcopy(cg);bad['models'][0]['state'][1]=1.0
        with self.assertRaises(ValueError):ref.paired_baby_chested_donkey_geometry(fg,bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=2,gui_entity_preview_draws=3,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=792,gui_entity_preview_indices=1188)
        self.assertEqual(9,ref.validate_baby_chested_donkey_owner(owner,7)['gal_submission_id'])
        bad=dict(owner);bad['gui_entity_preview_indices']=1187
        with self.assertRaises(ValueError):ref.validate_baby_chested_donkey_owner(bad,7)
        baseline=Image.new('RGB',(1280,720));candidate=baseline.copy();candidate.putpixel((450,160),(16,16,16))
        self.assertTrue(ref.compare_baby_chested_donkey_images(baseline,candidate)['passed'])
        candidate.putpixel((450,160),(17,17,17))
        with self.assertRaises(ValueError):ref.compare_baby_chested_donkey_images(baseline,candidate)

    def mule_geometry(self,frame=7,current=False):
        pose=dict(modelView=[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,78.0,57.767014,20.399181,1.0],
                  normal=[1.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,1.0])
        specs=[('minecraft:textures/entity/equipment/mule_saddle/saddle.png','minecraft:pipeline/armor_cutout_no_cull',114,456,684,True),
               ('minecraft:textures/entity/horse/mule.png','minecraft:pipeline/entity_cutout_no_cull',84,336,504,False)]
        models=[];draws=[]
        identity=[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0]
        for texture,pipeline,quads,vertices,indices,offset in specs:
            model=dict(texture=texture,pipeline=pipeline,tint=-1,light=15728880,overlay=655360,
                sourcePose=copy.deepcopy(pose),state=[3.0,1.0,165.3437,-14.656311,15.454812],
                quads=[dict(part='/body')]*quads)
            if current:
                model['entityPipGuardPixels']=1;model['sourcePose']['modelView'][12:14]=[79.0,58.767014]
                model['state'][0]=81.0
            models.append(model)
            if not current:
                view=list(identity)
                if offset:view[14]=0.001953125
                draws.append(dict(texture=texture,pipeline=pipeline,depthCompare='LEQUAL_DEPTH_TEST',
                    depthWrite=True,cull=False,blend='none',vertices=vertices,indices=indices,view=view))
        return dict(schema='equipment-model-geometry-v1',enabled=True,renderedFrameIndex=frame,
            complete=True,gpuReadback=False,capabilityAdmitted=False,models=models,completedDraws=draws)

    def test_chested_mule_inputs_geometry_owner_and_pixels_fail_closed(self):
        frozen=self.preview();current=self.preview(9)
        self.assertTrue(ref.paired_chested_mule_preview_inputs(frozen,current,7,9)['passed'])
        fg=self.mule_geometry();cg=self.mule_geometry(9,True)
        self.assertTrue(ref.paired_chested_mule_geometry(fg,cg,7,9)['passed'])
        bad=copy.deepcopy(cg);bad['models'][0]['quads'].pop()
        with self.assertRaises(ValueError):ref.paired_chested_mule_geometry(fg,bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=2,gui_entity_preview_draws=3,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=792,gui_entity_preview_indices=1188)
        self.assertEqual(9,ref.validate_chested_mule_owner(owner,7)['gal_submission_id'])
        bad=dict(owner);bad['gui_entity_preview_indices']=1187
        with self.assertRaises(ValueError):ref.validate_chested_mule_owner(bad,7)
        baseline=Image.new('RGB',(1280,720));candidate=baseline.copy();candidate.putpixel((450,160),(80,80,80))
        self.assertTrue(ref.compare_chested_mule_images(baseline,candidate)['passed'])
        candidate.putpixel((450,160),(81,81,81))
        with self.assertRaises(ValueError):ref.compare_chested_mule_images(baseline,candidate)

    def test_baby_chested_mule_inputs_geometry_owner_and_pixels_fail_closed(self):
        frozen=self.preview();current=self.preview(9)
        for receipt in (frozen,current):
            receipt['observations'][0]['translation']=[0.0,0.65,0.0]
            receipt['observations'][0]['scales']=[17.0,1.0,0.5]
        self.assertTrue(ref.paired_baby_chested_mule_preview_inputs(frozen,current,7,9)['passed'])
        fg=self.mule_geometry();cg=self.mule_geometry(9,True)
        for geometry in (fg,cg):
            for model in geometry['models']:
                model['state'][1]=0.5
                model['quads'][-6:]=[dict(part='/body/tail') for _ in range(6)]
        fg['models'].reverse();fg['completedDraws'].reverse()
        self.assertTrue(ref.paired_baby_chested_mule_geometry(fg,cg,7,9)['passed'])
        bad=copy.deepcopy(cg);bad['models'][0]['state'][1]=1.0
        with self.assertRaises(ValueError):ref.paired_baby_chested_mule_geometry(fg,bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=2,gui_entity_preview_draws=3,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=792,gui_entity_preview_indices=1188)
        self.assertEqual(9,ref.validate_baby_chested_mule_owner(owner,7)['gal_submission_id'])
        bad=dict(owner);bad['gui_entity_preview_indices']=1187
        with self.assertRaises(ValueError):ref.validate_baby_chested_mule_owner(bad,7)
        baseline=Image.new('RGB',(1280,720));candidate=baseline.copy();candidate.putpixel((450,160),(80,80,80))
        self.assertTrue(ref.compare_baby_chested_mule_images(baseline,candidate)['passed'])
        candidate.putpixel((450,160),(81,81,81))
        with self.assertRaises(ValueError):ref.compare_baby_chested_mule_images(baseline,candidate)

    def llama_geometry(self,frame=7,current=False,baby=False):
        pose=dict(modelView=[49.34051,-3.438642,-12.437425,0.0,0.0,49.155888,-13.590393,0.0,
            -12.904022,-13.148175,-47.556404,0.0,78.0,40.80951 if baby else 64.65201,20.399181,1.0],
            normal=[0.967461,-0.06742435,-0.2438711,0.0,0.96384096,-0.2664783,-0.25302005,-0.25780737,-0.93247855])
        specs=[('minecraft:textures/entity/llama/creamy.png','minecraft:pipeline/entity_cutout_no_cull',False),
               ('minecraft:textures/entity/equipment/llama_body/blue.png','minecraft:pipeline/armor_cutout_no_cull',True)]
        models=[];draws=[]
        identity=[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0]
        for texture,pipeline,offset in specs:
            model=dict(texture=texture,pipeline=pipeline,tint=-1,light=15728880,overlay=655360,
                sourcePose=copy.deepcopy(pose),state=[3.0,0.5 if baby else 1.0,165.3437,-14.656311,15.454812],
                quads=[dict(part='/body')]*(54 if baby else 66))
            if current:
                model['entityPipGuardPixels']=1
                model['sourcePose']['modelView'][12:14]=[79.0,41.80951 if baby else 65.65201]
                model['state'][0]=78.0
            models.append(model)
            if not current:
                view=list(identity)
                if offset:view[14]=0.001953125
                draws.append(dict(texture=texture,pipeline=pipeline,depthCompare='LEQUAL_DEPTH_TEST',
                    depthWrite=True,cull=False,blend='none',vertices=216 if baby else 264,
                    indices=324 if baby else 396,view=view))
        if current:models.reverse()
        return dict(schema='equipment-model-geometry-v1',enabled=True,renderedFrameIndex=frame,
            complete=True,gpuReadback=False,capabilityAdmitted=False,models=models,completedDraws=draws)

    def test_chested_blue_llama_inputs_geometry_owner_and_pixels_fail_closed(self):
        frozen=self.preview();current=self.preview(9)
        for receipt in (frozen,current):receipt['observations'][0]['translation']=[0.0,1.185,0.0]
        self.assertTrue(ref.paired_chested_blue_llama_preview_inputs(frozen,current,7,9)['passed'])
        bad_inputs=copy.deepcopy(current);bad_inputs['observations'][0]['translation']=[0.0,1.18,0.0]
        with self.assertRaises(ValueError):ref.paired_chested_blue_llama_preview_inputs(frozen,bad_inputs,7,9)
        fg=self.llama_geometry();cg=self.llama_geometry(9,True)
        self.assertTrue(ref.paired_chested_blue_llama_geometry(fg,cg,7,9)['passed'])
        bad=copy.deepcopy(cg);bad['models'][0]['quads'].pop()
        with self.assertRaises(ValueError):ref.paired_chested_blue_llama_geometry(fg,bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=2,gui_entity_preview_draws=3,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=528,gui_entity_preview_indices=792)
        self.assertEqual(9,ref.validate_chested_blue_llama_owner(owner,7)['gal_submission_id'])
        bad=dict(owner);bad['gui_entity_preview_vertices']=527
        with self.assertRaises(ValueError):ref.validate_chested_blue_llama_owner(bad,7)
        baseline=Image.new('RGB',(1280,720));candidate=baseline.copy();candidate.putpixel((450,160),(64,64,64))
        self.assertTrue(ref.compare_chested_blue_llama_images(baseline,candidate)['passed'])
        candidate.putpixel((450,160),(65,65,65))
        with self.assertRaises(ValueError):ref.compare_chested_blue_llama_images(baseline,candidate)

    def test_baby_chested_blue_llama_inputs_geometry_owner_and_pixels_fail_closed(self):
        frozen=self.preview();current=self.preview(9)
        for receipt in (frozen,current):
            receipt['observations'][0]['translation']=[0.0,0.7175,0.0]
            receipt['observations'][0]['scales']=[17.0,1.0,0.5]
        self.assertTrue(ref.paired_baby_chested_blue_llama_preview_inputs(frozen,current,7,9)['passed'])
        bad_inputs=copy.deepcopy(current);bad_inputs['observations'][0]['translation']=[0.0,0.71,0.0]
        with self.assertRaises(ValueError):ref.paired_baby_chested_blue_llama_preview_inputs(frozen,bad_inputs,7,9)
        fg=self.llama_geometry(baby=True);cg=self.llama_geometry(9,True,baby=True)
        self.assertTrue(ref.paired_baby_chested_blue_llama_geometry(fg,cg,7,9)['passed'])
        bad=copy.deepcopy(cg);bad['models'][0]['state'][1]=1.0
        with self.assertRaises(ValueError):ref.paired_baby_chested_blue_llama_geometry(fg,bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=2,gui_entity_preview_draws=3,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=432,gui_entity_preview_indices=648)
        self.assertEqual(9,ref.validate_baby_chested_blue_llama_owner(owner,7)['gal_submission_id'])
        bad=dict(owner);bad['gui_entity_preview_indices']=647
        with self.assertRaises(ValueError):ref.validate_baby_chested_blue_llama_owner(bad,7)
        baseline=Image.new('RGB',(1280,720));candidate=baseline.copy()
        for n in range(200):candidate.putpixel((450+n%164,160+n//164),(20,20,20))
        self.assertTrue(ref.compare_baby_chested_blue_llama_images(baseline,candidate)['passed'])
        for x in range(450,614):
            for y in range(160,324):candidate.putpixel((x,y),(1,1,1))
        with self.assertRaises(ValueError):ref.compare_baby_chested_blue_llama_images(baseline,candidate)

    def baby_geometry(self,frame=7,current=False):
        pose=dict(modelView=[49.34051,-3.438642,-12.437425,0.0,0.0,49.155888,-13.590393,0.0,
            -12.904022,-13.148175,-47.556404,0.0,78.0,37.367004,20.399181,1.0],
            normal=[0.967461,-0.06742435,-0.2438711,0.0,0.96384096,-0.2664783,-0.25302005,-0.25780737,-0.93247855])
        model=dict(texture='minecraft:textures/entity/horse/horse_white.png',
            pipeline='minecraft:pipeline/entity_cutout_no_cull',tint=-1,light=15728880,
            overlay=655360,sourcePose=pose,state=[3.0,0.5,165.3437,-14.656311,15.454812],
            quads=[dict(part='/body')]*72)
        draw=dict(texture=model['texture'],pipeline=model['pipeline'],depthCompare='LEQUAL_DEPTH_TEST',
            depthWrite=True,cull=False,blend='none',vertices=288,indices=432,
            view=[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0])
        if current:
            model['entityPipGuardPixels']=1;model['sourcePose']['modelView'][12:14]=[79.0,38.367004]
            model['state'][0]=106.0
        return dict(schema='equipment-model-geometry-v1',enabled=True,renderedFrameIndex=frame,
            complete=True,gpuReadback=False,capabilityAdmitted=False,models=[model],
            completedDraws=[] if current else [draw])

    def test_baby_horse_inputs_geometry_owner_and_pixels_fail_closed(self):
        frozen=self.preview();current=self.preview(9)
        for receipt in (frozen,current):
            receipt['observations'][0]['translation']=[0.0,0.65,0.0]
            receipt['observations'][0]['scales']=[17.0,1.0,0.5]
        self.assertTrue(ref.paired_baby_horse_preview_inputs(frozen,current,7,9)['passed'])
        self.assertTrue(ref.paired_baby_horse_geometry(
            self.baby_geometry(),self.baby_geometry(9,True),7,9)['passed'])
        bad=self.baby_geometry(9,True);bad['models'][0]['quads'].pop()
        with self.assertRaises(ValueError):ref.paired_baby_horse_geometry(self.baby_geometry(),bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=1,gui_entity_preview_draws=2,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=288,gui_entity_preview_indices=432)
        self.assertEqual(9,ref.validate_baby_horse_owner(owner,7)['gal_submission_id'])
        baseline=Image.new('RGB',(1280,720));candidate=baseline.copy();candidate.putpixel((450,160),(4,4,4))
        self.assertTrue(ref.compare_baby_horse_images(baseline,candidate)['passed'])
        candidate.putpixel((450,160),(5,5,5))
        with self.assertRaises(ValueError):ref.compare_baby_horse_images(baseline,candidate)

    def test_equipped_smithing_fixture_requires_synced_result(self):
        receipt=dict(schema='entity-preview-fixture-v1',requested=True,
            mode='smithing-netherite-chestplate',serverSpawned=False,entityId=-1,
            clientReplicated=False,serverMenuOpened=True,recipePopulated=True,
            resultItem='minecraft:netherite_chestplate',screenOpen=True,mouseCentered=True,
            previewReady=True,complete=True)
        ref.validate_equipped_smithing_fixture(receipt)
        for key,value in [('mode','smithing'),('recipePopulated',False),
                          ('resultItem','minecraft:diamond_chestplate'),('complete',False)]:
            bad=dict(receipt);bad[key]=value
            with self.assertRaises(ValueError):ref.validate_equipped_smithing_fixture(bad)

    def test_owner_requires_exact_presented_horse_work(self):
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=1,gui_entity_preview_draws=2,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=288,gui_entity_preview_indices=432)
        self.assertEqual(9,ref.validate_horse_owner(owner,7)['gal_submission_id'])
        for key,value in [('gui_entity_preview_draws',1),('java_vulkan_frame_execution',True),
                          ('present_completed_submission_id',8)]:
            bad=dict(owner);bad[key]=value
            with self.assertRaises(ValueError):ref.validate_horse_owner(bad,7)

    def equipped_horse_geometry(self,frame=7,current=False):
        specs=[('minecraft:textures/entity/equipment/horse_body/diamond.png',72,288,432),
               ('minecraft:textures/entity/equipment/horse_saddle/saddle.png',102,408,612)]
        models=[];draws=[]
        view=[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,
              0.0,0.0,0.001953125,1.0]
        for texture,quads,vertices,indices in specs:
            model=dict(texture=texture,pipeline='minecraft:pipeline/armor_cutout_no_cull',
                tint=-1,light=15728880,overlay=655360,quads=[dict(part='/body')]*quads)
            if current:model['entityPipGuardPixels']=1
            models.append(model)
            draws.append(dict(texture=texture,pipeline=model['pipeline'],depthCompare='LEQUAL_DEPTH_TEST',
                depthWrite=True,cull=False,blend='none',vertices=vertices,indices=indices,view=view))
        return dict(schema='equipment-model-geometry-v1',enabled=True,renderedFrameIndex=frame,
            complete=True,gpuReadback=False,capabilityAdmitted=False,models=models,
            completedDraws=[] if current else draws)

    def test_equipped_horse_geometry_owner_and_pixels_fail_closed(self):
        self.assertTrue(ref.paired_equipped_horse_geometry(
            self.equipped_horse_geometry(),self.equipped_horse_geometry(9,True),7,9)['passed'])
        bad=self.equipped_horse_geometry(9,True);bad['models'][1]['quads'].pop()
        with self.assertRaises(ValueError):
            ref.paired_equipped_horse_geometry(self.equipped_horse_geometry(),bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=3,gui_entity_preview_draws=4,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=984,
            gui_entity_preview_indices=1476)
        self.assertEqual(9,ref.validate_equipped_horse_owner(owner,7)['gal_submission_id'])
        bad_owner=dict(owner);bad_owner['gui_entity_preview_indices']=1475
        with self.assertRaises(ValueError):ref.validate_equipped_horse_owner(bad_owner,7)
        frozen=Image.new('RGB',(1280,720));current=frozen.copy();current.putpixel((450,160),(80,80,80))
        self.assertTrue(ref.compare_equipped_horse_images(frozen,current)['passed'])
        current.putpixel((450,160),(81,81,81))
        with self.assertRaises(ValueError):ref.compare_equipped_horse_images(frozen,current)

    def test_iron_equipped_horse_inputs_geometry_owner_and_pixels_fail_closed(self):
        self.assertTrue(ref.paired_iron_equipped_horse_preview_inputs(self.preview(),self.preview(9),7,9)['passed'])
        fg=self.equipped_horse_geometry();cg=self.equipped_horse_geometry(9,True)
        for geometry in (fg,cg):
            geometry['models'][0]['texture']='minecraft:textures/entity/equipment/horse_body/iron.png'
            for draw in geometry['completedDraws']:
                if draw['texture'].endswith('/diamond.png'):
                    draw['texture']='minecraft:textures/entity/equipment/horse_body/iron.png'
        self.assertTrue(ref.paired_iron_equipped_horse_geometry(fg,cg,7,9)['passed'])
        bad=copy.deepcopy(cg);bad['models'][0]['quads'].pop()
        with self.assertRaises(ValueError):ref.paired_iron_equipped_horse_geometry(fg,bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=3,gui_entity_preview_draws=4,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=984,gui_entity_preview_indices=1476)
        self.assertEqual(9,ref.validate_iron_equipped_horse_owner(owner,7)['gal_submission_id'])
        bad=dict(owner);bad['gui_entity_preview_vertices']=983
        with self.assertRaises(ValueError):ref.validate_iron_equipped_horse_owner(bad,7)
        frozen=Image.new('RGB',(1280,720));current=frozen.copy();current.putpixel((450,160),(80,80,80))
        self.assertTrue(ref.compare_iron_equipped_horse_images(frozen,current)['passed'])
        current.putpixel((450,160),(81,81,81))
        with self.assertRaises(ValueError):ref.compare_iron_equipped_horse_images(frozen,current)

    def test_gold_equipped_horse_inputs_geometry_owner_and_pixels_fail_closed(self):
        self.assertTrue(ref.paired_gold_equipped_horse_preview_inputs(self.preview(),self.preview(9),7,9)['passed'])
        fg=self.equipped_horse_geometry();cg=self.equipped_horse_geometry(9,True)
        for geometry in (fg,cg):
            geometry['models'][0]['texture']='minecraft:textures/entity/equipment/horse_body/gold.png'
            for draw in geometry['completedDraws']:
                if draw['texture'].endswith('/diamond.png'):
                    draw['texture']='minecraft:textures/entity/equipment/horse_body/gold.png'
        self.assertTrue(ref.paired_gold_equipped_horse_geometry(fg,cg,7,9)['passed'])
        bad=copy.deepcopy(cg);bad['models'][0]['quads'].pop()
        with self.assertRaises(ValueError):ref.paired_gold_equipped_horse_geometry(fg,bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=3,gui_entity_preview_draws=4,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=984,gui_entity_preview_indices=1476)
        self.assertEqual(9,ref.validate_gold_equipped_horse_owner(owner,7)['gal_submission_id'])
        frozen=Image.new('RGB',(1280,720));current=frozen.copy();current.putpixel((450,160),(80,80,80))
        self.assertTrue(ref.compare_gold_equipped_horse_images(frozen,current)['passed'])
        current.putpixel((450,160),(81,81,81))
        with self.assertRaises(ValueError):ref.compare_gold_equipped_horse_images(frozen,current)

    def test_copper_equipped_horse_inputs_geometry_owner_and_pixels_fail_closed(self):
        self.assertTrue(ref.paired_copper_equipped_horse_preview_inputs(self.preview(),self.preview(9),7,9)['passed'])
        fg=self.equipped_horse_geometry();cg=self.equipped_horse_geometry(9,True)
        for geometry in (fg,cg):
            geometry['models'][0]['texture']='minecraft:textures/entity/equipment/horse_body/copper.png'
            for draw in geometry['completedDraws']:
                if draw['texture'].endswith('/diamond.png'):
                    draw['texture']='minecraft:textures/entity/equipment/horse_body/copper.png'
        self.assertTrue(ref.paired_copper_equipped_horse_geometry(fg,cg,7,9)['passed'])
        bad=copy.deepcopy(cg);bad['models'][0]['quads'].pop()
        with self.assertRaises(ValueError):ref.paired_copper_equipped_horse_geometry(fg,bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=3,gui_entity_preview_draws=4,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=984,gui_entity_preview_indices=1476)
        self.assertEqual(9,ref.validate_copper_equipped_horse_owner(owner,7)['gal_submission_id'])
        frozen=Image.new('RGB',(1280,720));current=frozen.copy();current.putpixel((450,160),(80,80,80))
        self.assertTrue(ref.compare_copper_equipped_horse_images(frozen,current)['passed'])
        current.putpixel((450,160),(81,81,81))
        with self.assertRaises(ValueError):ref.compare_copper_equipped_horse_images(frozen,current)

    def test_leather_equipped_horse_inputs_geometry_owner_and_pixels_fail_closed(self):
        self.assertTrue(ref.paired_leather_equipped_horse_preview_inputs(self.preview(),self.preview(9),7,9)['passed'])
        fg=self.equipped_horse_geometry();cg=self.equipped_horse_geometry(9,True)
        for geometry in (fg,cg):
            geometry['models'][0]['texture']='minecraft:textures/entity/equipment/horse_body/leather.png'
            geometry['models'][0]['tint']=-6265536
            for draw in geometry['completedDraws']:
                if draw['texture'].endswith('/diamond.png'):
                    draw['texture']='minecraft:textures/entity/equipment/horse_body/leather.png'
        self.assertTrue(ref.paired_leather_equipped_horse_geometry(fg,cg,7,9)['passed'])
        bad=copy.deepcopy(cg);bad['models'][0]['quads'].pop()
        with self.assertRaises(ValueError):ref.paired_leather_equipped_horse_geometry(fg,bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=3,gui_entity_preview_draws=4,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=984,gui_entity_preview_indices=1476)
        self.assertEqual(9,ref.validate_leather_equipped_horse_owner(owner,7)['gal_submission_id'])
        frozen=Image.new('RGB',(1280,720));current=frozen.copy();current.putpixel((450,160),(80,80,80))
        self.assertTrue(ref.compare_leather_equipped_horse_images(frozen,current)['passed'])
        current.putpixel((450,160),(81,81,81))
        with self.assertRaises(ValueError):ref.compare_leather_equipped_horse_images(frozen,current)

    def test_dyed_leather_equipped_horse_inputs_geometry_owner_and_pixels_fail_closed(self):
        self.assertTrue(ref.paired_dyed_leather_equipped_horse_preview_inputs(self.preview(),self.preview(9),7,9)['passed'])
        fg=self.equipped_horse_geometry();cg=self.equipped_horse_geometry(9,True)
        for geometry in (fg,cg):
            geometry['models'][0]['texture']='minecraft:textures/entity/equipment/horse_body/leather.png'
            geometry['models'][0]['tint']=-13408564
            for draw in geometry['completedDraws']:
                if draw['texture'].endswith('/diamond.png'):
                    draw['texture']='minecraft:textures/entity/equipment/horse_body/leather.png'
        self.assertTrue(ref.paired_dyed_leather_equipped_horse_geometry(fg,cg,7,9)['passed'])
        bad=copy.deepcopy(cg);bad['models'][0]['quads'].pop()
        with self.assertRaises(ValueError):ref.paired_dyed_leather_equipped_horse_geometry(fg,bad,7,9)
        bad=copy.deepcopy(cg);bad['models'][0]['tint']=-6265536
        with self.assertRaises(ValueError):ref.paired_dyed_leather_equipped_horse_geometry(fg,bad,7,9)
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=3,gui_entity_preview_draws=4,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=984,gui_entity_preview_indices=1476)
        self.assertEqual(9,ref.validate_dyed_leather_equipped_horse_owner(owner,7)['gal_submission_id'])
        frozen=Image.new('RGB',(1280,720));current=frozen.copy();current.putpixel((450,160),(80,80,80))
        self.assertTrue(ref.compare_dyed_leather_equipped_horse_images(frozen,current)['passed'])
        current.putpixel((450,160),(81,81,81))
        with self.assertRaises(ValueError):ref.compare_dyed_leather_equipped_horse_images(frozen,current)

    def test_pixels_require_the_inspected_crop(self):
        frozen=Image.new('RGB',(1280,720));current=frozen.copy()
        self.assertTrue(ref.compare_horse_images(frozen,current)['passed'])
        for x in range(450,614):
            for y in range(160,324):current.putpixel((x,y),(5,5,5))
        with self.assertRaises(ValueError):ref.compare_horse_images(frozen,current)

    def smithing_preview(self, frame=7):
        return dict(schema='inventory-preview-inputs-v1',enabled=True,complete=True,
            capabilityAdmitted=False,viewStable=True,renderedFrameIndex=frame,
            observations=[dict(bounds=[246.0,57.0,286.0,117.0],translation=[0.0,1.0,0.0],
                rotation=[0.0,-0.21643962,0.976296,-0.0],scales=[25.0,1.0,1.0],
                angles=[-150.0,155.0,25.0],animation=[1.0,0.0,0.0,0.0],invisible=False,
                invisibleToPlayer=False,pose='STANDING',light=15728880,
                armorStand=dict(marker=False,small=False,showArms=True,showBasePlate=False,wiggle=6001.0,
                    head=[0.0,0.0,0.0],body=[0.0,0.0,0.0],leftArm=[-10.0,0.0,-10.0],
                    rightArm=[-15.0,0.0,10.0],leftLeg=[-1.0,0.0,-1.0],rightLeg=[1.0,0.0,1.0]))],
            screenMouse=[dict(stage='background',stored=[213.0,120.0],supplied=[213.0,120.0]),
                         dict(stage='render-return',stored=[213.0,120.0],supplied=[213.0,120.0])])

    def test_smithing_inputs_allow_only_proven_non_driving_differences(self):
        current=self.smithing_preview(9)
        current['observations'][0]['angles'][1]=154.0
        current['observations'][0]['armorStand']['wiggle']=5990.0
        self.assertTrue(ref.paired_smithing_preview_inputs(self.smithing_preview(),current,7,9)['passed'])
        for path,value in [('showBasePlate',True),('showArms',False),('wiggle',4.9)]:
            bad=copy.deepcopy(current);bad['observations'][0]['armorStand'][path]=value
            with self.assertRaises(ValueError):ref.paired_smithing_preview_inputs(self.smithing_preview(),bad,7,9)

    def test_smithing_owner_and_pixels_fail_closed(self):
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=1,gui_entity_preview_draws=2,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=216,gui_entity_preview_indices=324)
        self.assertEqual(9,ref.validate_smithing_owner(owner,7)['gal_submission_id'])
        bad=dict(owner);bad['gui_entity_preview_vertices']=288
        with self.assertRaises(ValueError):ref.validate_smithing_owner(bad,7)
        frozen=Image.new('RGB',(1280,720));current=frozen.copy()
        self.assertTrue(ref.compare_smithing_images(frozen,current)['passed'])
        for x in range(720,880):
            for y in range(150,370):current.putpixel((x,y),(5,5,5))
        with self.assertRaises(ValueError):ref.compare_smithing_images(frozen,current)

    def equipped_geometry(self, frame=7, current=False):
        model=dict(texture='minecraft:textures/entity/equipment/humanoid/netherite.png',
            pipeline='minecraft:pipeline/armor_cutout_no_cull',tint=-1,light=15728880,
            overlay=655360,sourcePose=dict(
                modelView=[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,
                           60.0,62.0,3.0,1.0],normal=[1.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,1.0]),
            state=[1.0,1.0,-150.0,151.0,25.0],quads=[dict(part='/body',cube=0)]*18)
        draw=dict(texture=model['texture'],pipeline=model['pipeline'],
            depthCompare='LEQUAL_DEPTH_TEST',depthWrite=True,cull=False,blend='none',
            vertices=72,indices=108,
            view=[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,
                  0.0,0.0,0.001953125,1.0])
        if current:
            model['entityPipGuardPixels']=1
            model['sourcePose']['modelView'][12:14]=[61.0,63.0]
            model['state'][3]=154.0
        return dict(schema='equipment-model-geometry-v1',enabled=True,renderedFrameIndex=frame,
            complete=True,gpuReadback=False,capabilityAdmitted=False,models=[model],
            completedDraws=[] if current else [draw])

    def test_equipped_smithing_geometry_normalizes_only_measured_guard_and_hidden_yaw(self):
        result=ref.paired_equipped_smithing_geometry(
            self.equipped_geometry(),self.equipped_geometry(9,True),7,9)
        self.assertTrue(result['passed'])
        for mutation in ('guard','mesh','draw'):
            bad=self.equipped_geometry(9,True)
            if mutation=='guard':bad['models'][0]['entityPipGuardPixels']=2
            elif mutation=='mesh':bad['models'][0]['quads'].pop()
            else:bad['completedDraws']=[dict()]
            with self.assertRaises(ValueError):
                ref.paired_equipped_smithing_geometry(self.equipped_geometry(),bad,7,9)

    def test_equipped_smithing_owner_and_pixels_fail_closed(self):
        owner=dict(deterministic_rendered_frame_index=7,gameplay_frame_id=8,gal_submission_id=9,
            present_completed_submission_id=9,same_acquired_presented_image=True,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,
            gui_entity_preview_items=1,gui_entity_preview_batches=2,gui_entity_preview_draws=3,
            gui_entity_preview_material_mask=128,gui_entity_preview_vertices=288,
            gui_entity_preview_indices=432)
        self.assertEqual(9,ref.validate_equipped_smithing_owner(owner,7)['gal_submission_id'])
        bad=dict(owner);bad['gui_entity_preview_batches']=1
        with self.assertRaises(ValueError):ref.validate_equipped_smithing_owner(bad,7)
        frozen=Image.new('RGB',(1280,720));current=frozen.copy()
        current.putpixel((720,150),(16,16,16))
        self.assertTrue(ref.compare_equipped_smithing_images(frozen,current)['passed'])
        current.putpixel((720,150),(17,17,17))
        with self.assertRaises(ValueError):ref.compare_equipped_smithing_images(frozen,current)


if __name__ == '__main__': unittest.main()

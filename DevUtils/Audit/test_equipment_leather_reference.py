"""Leather CPU prerequisite rejects substituted layers, tint and flush order."""
import copy
import unittest
import equipment_leather_reference as ref


def evidence():
    quad=dict(part='/body',cube=0,normal=[0.0,0.0,1.0],
              vertices=[[0.0,0.0,0.0,0.0,0.0]]*4)
    models=[]
    for texture,counts in [(ref.BODY,[18,12,12]),(ref.LEGS,[18]),
            (ref.GLINT,[18,18,12,12]),(ref.BODY_OVERLAY,[18,12,12]),(ref.LEGS_OVERLAY,[18])]:
        for count in counts:
            models.append(dict(texture=texture,pipeline='minecraft:pipeline/'+('glint' if texture==ref.GLINT else 'armor_cutout_no_cull'),
                tint=-1 if texture in (ref.BODY_OVERLAY,ref.LEGS_OVERLAY) else ref.TINT,
                light=15728640,overlay=655360,quads=[copy.deepcopy(quad) for _ in range(count)]))
    draws=[]
    for texture,vertices,indices in [(ref.BODY,168,252),(ref.LEGS,72,108),
            (ref.BODY_OVERLAY,168,252),(ref.LEGS_OVERLAY,72,108),(ref.GLINT,240,360)]:
        glint=texture==ref.GLINT
        draws.append(dict(texture=texture,vertices=vertices,indices=indices,
            pipeline='minecraft:pipeline/'+('glint' if glint else 'armor_cutout_no_cull'),
            depthCompare='EQUAL_DEPTH_TEST' if glint else 'LEQUAL_DEPTH_TEST',depthWrite=not glint,cull=False,
            blend='BlendFunction[sourceColor=SRC_COLOR, destColor=ONE, sourceAlpha=ZERO, destAlpha=ONE]' if glint else 'none',
            view=[1.0 if i%5==0 else 0.0 for i in range(16)]))
    return dict(schema='equipment-model-geometry-v1',enabled=True,complete=True,gpuReadback=False,
                capabilityAdmitted=False,renderedFrameIndex=12,models=models,completedDraws=draws)


class LeatherCpuReferenceTest(unittest.TestCase):
    def test_model_and_draw_membership_tint_and_pipeline_are_required(self):
        good=evidence();self.assertFalse(ref.validate_frozen_geometry(good,12)['capability_admitted'])
        for index in range(12):
            for key,value in [('tint',False),('tint',0),('pipeline','minecraft:pipeline/entity_cutout')]:
                bad=copy.deepcopy(good);bad['models'][index][key]=value
                with self.assertRaises(ValueError):ref.validate_frozen_geometry(bad,12)
            bad=copy.deepcopy(good);bad['models'].pop(index)
            with self.assertRaises(ValueError):ref.validate_frozen_geometry(bad,12)
        for index in range(5):
            bad=copy.deepcopy(good);bad['completedDraws'].pop(index)
            with self.assertRaises(ValueError):ref.validate_frozen_geometry(bad,12)
        bad=copy.deepcopy(good);bad['models'][0]['texture']=ref.BODY_OVERLAY;bad['models'][0]['tint']=-1
        with self.assertRaises(ValueError):ref.validate_frozen_geometry(bad,12)

    def test_actual_glint_flush_must_follow_all_layers(self):
        good=evidence()
        for index in range(4):
            bad=copy.deepcopy(good);bad['completedDraws'].insert(index,bad['completedDraws'].pop())
            with self.assertRaises(ValueError):ref.validate_frozen_geometry(bad,12)
        for key,value in [('depthWrite',True),('depthCompare','LEQUAL_DEPTH_TEST'),('blend','none'),('indices',True)]:
            bad=copy.deepcopy(good);bad['completedDraws'][-1][key]=value
            with self.assertRaises(ValueError):ref.validate_frozen_geometry(bad,12)

    def test_stale_nonfinite_and_wrongly_typed_sources_are_rejected(self):
        good=evidence()
        for frame in (11,True,12.0):
            with self.assertRaises(ValueError):ref.validate_frozen_geometry(good,frame)
        for key,value in [('renderedFrameIndex',12.0),('gpuReadback',True),('complete',1)]:
            bad=copy.deepcopy(good);bad[key]=value
            with self.assertRaises(ValueError):ref.validate_frozen_geometry(bad,12)
        for value in (float('nan'),float('inf'),True):
            bad=copy.deepcopy(good);bad['models'][0]['quads'][0]['vertices'][0][0]=value
            with self.assertRaises(ValueError):ref.validate_frozen_geometry(bad,12)

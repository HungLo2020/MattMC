import copy
from pathlib import Path
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'Common'))
from equipment_trim_reference import FROZEN_SOURCE_PIXELS, paired_sources, validate_sources


class TrimSourceTest(unittest.TestCase):
    def receipt(self):
        rows=[]
        for layer in ('HUMANOID', 'HUMANOID_LEGGINGS', 'HUMANOID', 'HUMANOID'):
            rows.append(dict(sprite='minecraft:trims/entity/'+layer.lower()+'/spire_gold',
                atlas='minecraft:textures/atlas/armor_trims.png',layer=layer,asset='minecraft:diamond',
                width=64,height=32,x=128,y=64,u0=.0625,u1=.09375,v0=.0625,v1=.09375,
                rgbaSha256=FROZEN_SOURCE_PIXELS[layer],depthTest='EQUAL_DEPTH_TEST',depthWrite=True))
        return dict(schema='equipment-trim-cpu-sources-v1',enabled=True,complete=True,
                    gpuReadback=False,renderedFrameIndex=7,sources=rows)

    def test_requires_actual_source_identity_but_allows_different_atlas_placement(self):
        a=self.receipt();b=copy.deepcopy(a);b['renderedFrameIndex']=8
        for s in b['sources']:s.update(x=256,u0=.125,u1=.15625)
        result=paired_sources(a,b,7,8,decal=True)
        self.assertTrue(result['passed'])
        self.assertFalse(result['capability_admitted'])
        self.assertFalse(result['native_texture_correspondence_verified'])
        for s in b['sources']:s['rgbaSha256']='cd'*32
        with self.assertRaises(ValueError):paired_sources(a,b,7,8,decal=True)
        with self.assertRaises(ValueError):paired_sources(b,b,8,8,decal=True)

    def test_rejects_stale_missing_mispacked_and_wrong_material_sources(self):
        a=self.receipt();validate_sources(a,7,decal=True)
        variants=[]
        for key,value in [('complete',False),('renderedFrameIndex',True),('gpuReadback',True)]:
            b=copy.deepcopy(a);b[key]=value;variants.append(b)
        b=copy.deepcopy(a);b['sources'].pop();variants.append(b)
        for key,value in [('sprite','minecraft:missingno'),('depthWrite',False),
                          ('depthTest','LEQUAL_DEPTH_TEST'),('width',True),('x',129),
                          ('rgbaSha256','not-a-hash'),('u1',float('nan'))]:
            b=copy.deepcopy(a);b['sources'][0][key]=value;variants.append(b)
        for b in variants:
            with self.assertRaises(ValueError):validate_sources(b,7,decal=True)

    def test_native_bound_pixels_uv_membership_and_limits_are_required(self):
        import hashlib
        import io
        from unittest.mock import patch
        from PIL import Image
        from equipment_trim_reference import native_atlas_sources
        image=Image.new('RGBA',(128,64),(20,30,40,255))
        image.paste((50,60,70,255),(0,32,64,64))
        hashes={layer:hashlib.sha256(image.crop((0,y,64,y+32)).tobytes()).hexdigest()
                for layer,y in [('HUMANOID',0),('HUMANOID_LEGGINGS',32)]}
        sources=self.receipt()
        for s in sources['sources']:
            y=32 if s['layer']=='HUMANOID_LEGGINGS' else 0
            s.update(x=0,y=y,u0=0.0,u1=.5,v0=y/64,v1=(y+32)/64,rgbaSha256=hashes[s['layer']])
        def encoded(im):
            stream=io.BytesIO();im.save(stream,format='PNG');return stream.getvalue().hex()
        native=dict(schema='equipment-submission-inputs-v1',complete=True,gpu_readback=False,
            capability_admitted=False,deterministic_rendered_frame_index=7,
            texture_sources=[dict(texture_id=42,width=128,height=64,encoding='png-rgba8-hex',
                                  rgba_xxh32='01234567',png_hex=encoded(image))],draws=[{} for _ in range(8)])
        for s in sources['sources']:
            native['draws'].append(dict(depth_policy=3,depth_compare='Equal',depth_write=True,
                standard_foil=False,texture_id=42,texture_width=128,texture_height=64,
                texture_rgba_xxh32='01234567',mesh_vertex_bytes=320,
                mesh_uvs=[[u,v] for u in [s['u0'],s['u1']] for v in [s['v0'],s['v1']]]))
        with patch.dict(FROZEN_SOURCE_PIXELS,hashes):
            self.assertTrue(native_atlas_sources(native,sources,7)['native_texture_correspondence_verified'])
            variants=[]
            broken=image.copy();broken.putpixel((20,20),(255,0,0,255))
            b=copy.deepcopy(native);b['texture_sources'][0]['png_hex']=encoded(broken);variants.append(b)
            for key,value in [('texture_id',43),('mesh_uvs',[[0.1,0.1]]),('depth_write',False),
                              ('texture_width',129),('mesh_vertex_bytes',True)]:
                b=copy.deepcopy(native);b['draws'][8][key]=value;variants.append(b)
            b=copy.deepcopy(native);b['texture_sources'][0]['width']=10**9;variants.append(b)
            b=copy.deepcopy(native);b['deterministic_rendered_frame_index']=8;variants.append(b)
            for b in variants:
                with self.assertRaises(ValueError):native_atlas_sources(b,sources,7)
            ordinary=copy.deepcopy(native);ordinary_sources=copy.deepcopy(sources)
            for row in ordinary_sources['sources']:row['depthTest']='LEQUAL_DEPTH_TEST'
            # Base draws share policy1 but do not bind the trim atlas.
            for draw in ordinary['draws'][:8]:draw.update(depth_policy=1,texture_id=99)
            for draw in ordinary['draws'][8:]:draw.update(depth_policy=1,depth_compare='LessOrEqual')
            self.assertTrue(native_atlas_sources(ordinary,ordinary_sources,7,decal=False)['passed'])
            for key,value in [('depth_policy',3),('depth_policy',True),('depth_compare','Equal'),
                              ('texture_id',99),('depth_write',False)]:
                broken=copy.deepcopy(ordinary);broken['draws'][8][key]=value
                with self.assertRaises(ValueError):native_atlas_sources(broken,ordinary_sources,7,decal=False)
            broken=copy.deepcopy(ordinary);broken['draws'][0]['texture_id']=42
            with self.assertRaises(ValueError):native_atlas_sources(broken,ordinary_sources,7,decal=False)
            with self.assertRaises(ValueError):native_atlas_sources(ordinary,ordinary_sources,7,decal=0)



if __name__=='__main__':unittest.main()

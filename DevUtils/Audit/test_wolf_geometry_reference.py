import copy
import struct
import unittest
from test_wolf_draw_reference import fixture
from wolf_geometry_reference import packed_normal,compare_geometry


class WolfGeometryReferenceTest(unittest.TestCase):
    def test_normal_quantization_uses_signed_java_rounding(self):
        self.assertEqual([0.0,-1.0,0.0],packed_normal([0.0,-1.0,0.0]))
        self.assertEqual([0.0]*3,packed_normal([0.0]*3))
        value=struct.unpack('<f',struct.pack('<f',90/127))[0]
        self.assertEqual([value,value,0.0],packed_normal([1.0,1.0,0.0]))
        with self.assertRaises(ValueError):packed_normal([float('nan'),0.0,0.0])

    def test_full_source_correspondence_rejects_packed_data_and_binding_changes(self):
        a,b=fixture();a['models']=[dict(state=dict(light=15728640))];b['gal_submission_id']=20
        a['geometrySources']=[];payloads=[]
        for x in (0.0,0.25):
            quad=dict(part='/head',cube=0,normal=[0.0,-1.0,0.0],vertices=[[x,0.0,0.0,0.0,0.0]]*4)
            a['geometrySources'].append([copy.deepcopy(quad) for _ in range(66)])
            # Independent explicit byte oracle for an axis-aligned white,
            # sky-lit vertex; do not invoke the source packer under test.
            vertex=struct.pack('<18f2I',x,0.,0.,0.,1.,1.,1.,0.,1.,0.,-1.,1.,0.,1.,0.,0.,0.,0.,0,1)
            indices=[j for base in range(0,264,4) for j in (base,base+1,base+2,base+2,base+3,base)]
            payloads.append(dict(encoding='packed-source-hex-v1',vertex_stride=80,index_type='U32',
                                 vertex_hex=(vertex*264).hex(),index_hex=struct.pack('<396I',*indices).hex()))
        for i,d in enumerate(b['draws']):
            d.update(geometry_bindings_verified=True,cull_mode='None',front_face='Clockwise',depth_bias=None,
                     geometry_uploads=dict(contents_match_source=True,vertex_submission_id=10,index_submission_id=11),
                     mesh_source_geometry=copy.deepcopy(payloads[0 if i==0 else 1]))
        result=compare_geometry(a,b,7,19)
        self.assertTrue(result['source_geometry_correspondence_verified'])
        self.assertTrue(result['geometry_upload_contents_verified']);self.assertFalse(result['capability_admitted'])
        undamaged_a,undamaged_b=copy.deepcopy(a),copy.deepcopy(b)
        undamaged_a['completedDraws'].pop(1)
        for key in ('draws','semantic_instances','texture_sources'):undamaged_b[key].pop(1)
        self.assertEqual([0,1],compare_geometry(undamaged_a,undamaged_b,7,19,mode='none')['source_membership'])
        undamaged_b['draws'][1]['mesh_source_geometry']=copy.deepcopy(payloads[0])
        with self.assertRaises(ValueError):compare_geometry(undamaged_a,undamaged_b,7,19,mode='none')
        mutations=[lambda d:d.update(cull_mode='Back'),lambda d:d.update(geometry_bindings_verified=False),
                   lambda d:d.update(depth_bias='bias'),lambda d:d['mesh_source_geometry'].update(vertex_stride=True),
                   lambda d:d['mesh_source_geometry'].update(vertex_hex='00'),
                   lambda d:d['mesh_source_geometry'].update(index_hex='00'*1584),
                   lambda d:d['mesh_source_geometry'].update(vertex_hex=payloads[0]['vertex_hex'])]
        mutations.extend([lambda d:d.update(geometry_uploads=None),
                          lambda d:d['geometry_uploads'].update(vertex_submission_id=True),
                          lambda d:d['geometry_uploads'].update(index_submission_id=20),
                          lambda d:d['geometry_uploads'].update(contents_match_source=False)])
        for mutate in mutations:
            bad=copy.deepcopy(b);mutate(bad['draws'][1])
            with self.assertRaises(ValueError):compare_geometry(a,bad,7,19)
        for offset in (0,12,16,28,36,48,52,64,72,76):
            bad=copy.deepcopy(b);source=bad['draws'][0]['mesh_source_geometry']
            data=bytearray.fromhex(source['vertex_hex']);data[offset]^=1;source['vertex_hex']=data.hex()
            with self.assertRaises(ValueError):compare_geometry(a,bad,7,19)

if __name__=='__main__':unittest.main()

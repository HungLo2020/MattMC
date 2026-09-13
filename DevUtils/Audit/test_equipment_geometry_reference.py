import copy
import struct
import unittest
import equipment_geometry_reference as ref
from test_equipment_leather_reference import evidence


def native_for(frozen):
    draws=[]
    for row in frozen['models']:
        vertices,indices=ref.packed_source(row)
        draws.append(dict(geometry_bindings_verified=True,cull_mode='None',front_face='Clockwise',depth_bias=None,
            geometry_uploads=dict(contents_match_source=True,vertex_submission_id=4,index_submission_id=5),
            mesh_source_geometry=dict(encoding='packed-source-hex-v1',vertex_stride=80,index_type='U16',
                vertex_hex=vertices.hex(),index_hex=struct.pack('<'+str(len(indices))+'H',*indices).hex())))
    return dict(schema='equipment-submission-inputs-v1',complete=True,gpu_readback=False,capability_admitted=False,
                deterministic_rendered_frame_index=13,gal_submission_id=20,draws=draws)


class EquipmentGeometryTest(unittest.TestCase):
    def test_axis_aligned_pack_has_independent_color_light_normal_and_index_oracle(self):
        row=evidence()['models'][0];row['light']=0xE00070
        vertices,indices=ref.packed_source(row)
        expected=struct.pack('<18f2I',0,0,0,0,1,1,1,0,1,0,0,1,7/15,14/15,1,0,0,0,0,1)
        self.assertEqual(vertices[:80],expected)
        self.assertEqual(indices[:12],(0,1,2,2,3,0,4,5,6,6,7,4))
        self.assertEqual(len(vertices),18*4*80)
        row['light']=True
        with self.assertRaises(ValueError):ref.packed_source(row)

    def test_exact_bytes_prior_uploads_membership_and_binding_required(self):
        frozen=evidence();native=native_for(frozen)
        self.assertFalse(ref.compare_geometry(frozen,native,12,13)['capability_admitted'])
        for index in range(12):
            bad=copy.deepcopy(native);v=bytearray.fromhex(bad['draws'][index]['mesh_source_geometry']['vertex_hex']);v[12]^=1
            bad['draws'][index]['mesh_source_geometry']['vertex_hex']=v.hex()
            with self.assertRaises(ValueError):ref.compare_geometry(frozen,bad,12,13)
            bad=copy.deepcopy(native);bad['draws'][index]['geometry_uploads']['vertex_submission_id']=20
            with self.assertRaises(ValueError):ref.compare_geometry(frozen,bad,12,13)
            bad=copy.deepcopy(native);bad['draws'][index]['geometry_bindings_verified']=False
            with self.assertRaises(ValueError):ref.compare_geometry(frozen,bad,12,13)
        bad=copy.deepcopy(native);bad['draws'].pop()
        with self.assertRaises(ValueError):ref.compare_geometry(frozen,bad,12,13)
        bad=copy.deepcopy(native);bad['draws'][0]['mesh_source_geometry']['index_hex']='0000'
        with self.assertRaises(ValueError):ref.compare_geometry(frozen,bad,12,13)
        with self.assertRaises(ValueError):ref.compare_geometry(frozen,native,12,14)

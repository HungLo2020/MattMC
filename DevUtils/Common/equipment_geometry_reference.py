"""Exact packed equipment geometry against independent Frozen CPU sources.

Texture/tint/draw-order correspondence and ownership must be composed separately.
"""
from collections import Counter
import re
import struct
from equipment_leather_reference import validate_frozen_geometry
from equipment_reference import require
from wolf_geometry_reference import f32, packed_normal
from wolf_draw_reference import float32_vector


def packed_source(row):
    light=row['light'];quads=row['quads']
    require(type(light) is int and 0<=light<=0xF000F0 and light&~0xF000F0==0,
            'invalid equipment packed light')
    require(isinstance(quads,list) and len(quads) in (12,18), 'unsupported equipment source size')
    block,sky=f32(((light>>4)&15)/15.0),f32(((light>>20)&15)/15.0)
    vertices=bytearray();indices=[]
    for quad in quads:
        normal=packed_normal(quad['normal']);base=len(vertices)//80
        for point in quad['vertices']:
            float32_vector(point,5);x,y,z,u,v=map(f32,point)
            lanes=[x,y,z,u,1.0,1.0,1.0,v,1.0,normal[0],normal[1],1.0,
                   block,sky,normal[2],0.0,u,v]
            vertices.extend(struct.pack('<18f2I',*lanes,0,1))
        indices.extend([base,base+1,base+2,base+2,base+3,base])
    return bytes(vertices),tuple(indices)


def compare_geometry(frozen,native,frozen_frame,current_frame):
    validate_frozen_geometry(frozen,frozen_frame)
    require(isinstance(native,dict) and native.get('schema')=='equipment-submission-inputs-v1'
            and native.get('complete') is True and native.get('gpu_readback') is False
            and native.get('capability_admitted') is False
            and type(current_frame) is int and current_frame>0
            and type(native.get('deterministic_rendered_frame_index')) is int
            and native['deterministic_rendered_frame_index']==current_frame,'missing selected equipment geometry receipt')
    submission=native.get('gal_submission_id')
    require(type(submission) is int and submission>0,'missing equipment submission')
    expected=Counter(packed_source(row) for row in frozen['models'])
    draws=native.get('draws');require(isinstance(draws,list) and len(draws)==12,'missing equipment draws')
    observed=Counter()
    for draw in draws:
        require(isinstance(draw,dict) and draw.get('geometry_bindings_verified') is True
                and draw.get('cull_mode')=='None' and draw.get('front_face') in ('Clockwise','CounterClockwise')
                and 'depth_bias' in draw and draw['depth_bias'] is None,'missing equipment geometry/raster bindings')
        uploads=draw.get('geometry_uploads')
        require(isinstance(uploads,dict) and uploads.get('contents_match_source') is True
                and all(type(uploads.get(k)) is int and 0<uploads[k]<submission
                    for k in ('vertex_submission_id','index_submission_id')),'missing accepted prior equipment geometry uploads')
        source=draw.get('mesh_source_geometry')
        require(isinstance(source,dict) and source.get('encoding')=='packed-source-hex-v1'
                and type(source.get('vertex_stride')) is int and source['vertex_stride']==80
                and source.get('index_type') in ('U16','U32'),'invalid equipment geometry encoding')
        vertices=source.get('vertex_hex');indices=source.get('index_hex')
        require(isinstance(vertices,str) and len(vertices) in (48*80*2,72*80*2)
                and re.fullmatch('[0-9a-f]+',vertices),'invalid bounded equipment vertices')
        count=len(vertices)//160;width,code=(2,'H') if source['index_type']=='U16' else (4,'I')
        require(isinstance(indices,str) and len(indices)==count//4*6*width*2
                and re.fullmatch('[0-9a-f]+',indices),'invalid bounded equipment indices')
        decoded=tuple(struct.unpack('<'+str(count//4*6)+code,bytes.fromhex(indices)))
        observed[(bytes.fromhex(vertices),decoded)]+=1
    require(observed==expected,'equipment packed geometry differs from Frozen sources')
    return dict(passed=True,capability_admitted=False,source_geometry_correspondence_verified=True,
                geometry_upload_contents_verified=True,scope='packed geometry multiset only; texture/tint/order/owner separate')

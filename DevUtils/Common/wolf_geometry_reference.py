"""Bounded Frozen model-local geometry versus native packed source bytes.

Includes accepted upload correspondence; admission requires composed ownership
and fresh paired gameplay acceptance.
"""
import math
import re
import struct
from equipment_reference import require
from wolf_draw_reference import float32_vector, compare_draws


def f32(value):
    try:
        result=struct.unpack('<f',struct.pack('<f',value))[0]
    except (OverflowError,struct.error) as error:
        raise ValueError('wolf geometry float overflow') from error
    require(math.isfinite(result),'nonfinite wolf geometry arithmetic')
    return result


def packed_normal(normal):
    float32_vector(normal,3)
    x,y,z=map(f32,normal)
    length=f32(math.sqrt(f32(f32(f32(x*x)+f32(y*y))+f32(z*z))))
    if length<=f32(0.00001):return [0.0]*3
    # Java Math.round(float) rounds ties toward positive infinity. Each
    # arithmetic operation here rounds to the declared float32 source format.
    return [f32(math.floor(f32(f32(v/length)*127.0)+0.5)/127.0) for v in (x,y,z)]


def packed_source(quads, light):
    require(isinstance(quads,list) and len(quads)==66,'wolf source must contain all 66 quads')
    require(type(light) is int and light==15728640,'unsupported wolf geometry light')
    vertices=bytearray();indices=[]
    for quad in quads:
        require(isinstance(quad,dict) and set(quad)=={'part','cube','normal','vertices'}
                and isinstance(quad['part'],str) and len(quad['part'])<=256
                and type(quad['cube']) is int and 0<=quad['cube']<64,
                'invalid Frozen wolf quad identity')
        normal=packed_normal(quad['normal']);points=quad['vertices']
        require(isinstance(points,list) and len(points)==4,'invalid Frozen wolf quad')
        base=len(vertices)//80
        for point in points:
            float32_vector(point,5)
            x,y,z,u,v=map(f32,point)
            lanes=[x,y,z,u,1.0,1.0,1.0,v,1.0,normal[0],normal[1],1.0,
                   0.0,1.0,normal[2],0.0,u,v]
            vertices.extend(struct.pack('<18f2I',*lanes,0,1))
        indices.extend([base,base+1,base+2,base+2,base+3,base])
    return bytes(vertices),indices


def compare_geometry(frozen,native,frozen_frame,current_frame,mode="high"):
    compare_draws(frozen,native,frozen_frame,current_frame,mode)
    sources=frozen.get('geometrySources')
    require(isinstance(sources,list) and len(sources)==2,'missing distinct Frozen wolf geometry sources')
    models=frozen.get('models')
    require(isinstance(models,list) and models and isinstance(models[0],dict), 'missing Frozen wolf light source')
    light=models[0].get('state',{}).get('light')
    expected=[packed_source(source,light) for source in sources]
    require(expected[0][0]!=expected[1][0],'duplicate Frozen wolf geometry sources')
    matches=[]
    submission=native.get('gal_submission_id')
    require(type(submission) is int and submission>0,'missing selected wolf submission')
    for draw in native['draws']:
        uploads=draw.get('geometry_uploads')
        require(isinstance(uploads,dict) and uploads.get('contents_match_source') is True
                and all(type(uploads.get(k)) is int and 0<uploads[k]<submission
                        for k in ('vertex_submission_id','index_submission_id')),
                'missing preceding accepted wolf geometry uploads')
        require(draw.get('geometry_bindings_verified') is True and draw.get('cull_mode')=='None'
                and draw.get('front_face') in ('Clockwise','CounterClockwise')
                and 'depth_bias' in draw and draw['depth_bias'] is None,
                'missing actual wolf geometry/raster binding proof')
        source=draw.get('mesh_source_geometry')
        require(isinstance(source,dict) and source.get('encoding')=='packed-source-hex-v1'
                and type(source.get('vertex_stride')) is int and source['vertex_stride']==80
                and source.get('index_type') in ('U16','U32'),'invalid packed wolf geometry encoding')
        def decode(key,size):
            value=source.get(key)
            require(isinstance(value,str) and len(value)==size*2 and re.fullmatch('[0-9a-f]+',value),
                    'invalid bounded wolf geometry bytes')
            return bytes.fromhex(value)
        vertices=decode('vertex_hex',264*80)
        width,code=(2,'H') if source['index_type']=='U16' else (4,'I')
        indices=list(struct.unpack('<396'+code,decode('index_hex',396*width)))
        matching=[i for i,(v,idx) in enumerate(expected) if v==vertices and idx==indices]
        require(len(matching)==1,'native packed wolf geometry differs from Frozen source')
        matches.append(matching[0])
    require(matches[0]!=matches[1] and (mode=='none' or matches[1]==matches[2]),
            'wolf body/cracks/armor source membership mismatch')
    return dict(passed=True,source_geometry_correspondence_verified=True,
                geometry_bindings_verified=True,geometry_upload_contents_verified=True,
                capability_admitted=False,source_membership=matches)

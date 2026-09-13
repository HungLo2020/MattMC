"""Frozen r728 draw declarations and bounded native texture correspondence.

Supplemental only: this does not prove complete geometry or presentation ownership.
"""
import hashlib
import io
import math
import re
import struct
from PIL import Image
from equipment_reference import require

# Independently inspected Frozen resource declarations; RGBA hashes are from
# the corresponding Frozen vanilla assets, never from a Current screenshot.
LAYERS = (
    ('minecraft:textures/entity/wolf/wolf.png', 'entity_cutout_no_cull',
     '9076a0b63c534b81dd8b31a40fa88fa357efc9996ab14f37c1bd7b7e50d1f598', 0, None),
    ('minecraft:textures/entity/wolf/wolf_armor_crackiness_high.png', 'armor_translucent',
     '22caa164b346a6a4e99c7735d40366dc3afc97ae4002cfe627916f6874ea2f52', 0, 'Perspective'),
    ('minecraft:textures/entity/equipment/wolf_body/armadillo_scute.png', 'armor_cutout_no_cull',
     '3c78afe0a75de5fe1aac2694239366f1f7109e59e60c7b770877e434dc2ead25', 1, 'Perspective'),
)
ALPHA = 'BlendFunction[sourceColor=SRC_ALPHA, destColor=ONE_MINUS_SRC_ALPHA, sourceAlpha=ONE, destAlpha=ONE_MINUS_SRC_ALPHA]'


def layers(mode):
    require(mode in ('high','low','medium','none'),'unsupported wolf draw reference')
    if mode=='none':return (LAYERS[0],LAYERS[2])
    if mode=='high':return LAYERS
    if mode=='medium':
        # Frozen r737 actual declaration and independently decoded Frozen asset.
        crack=('minecraft:textures/entity/wolf/wolf_armor_crackiness_medium.png', 'armor_translucent',
               'fc8ad85c22905a3de69fed9be5ee7088b649b12c567b6830c30c80aac13e9610',0,'Perspective')
        return (LAYERS[0],crack,LAYERS[2])
    # Frozen r735 actual declaration and independently decoded Frozen asset.
    crack=('minecraft:textures/entity/wolf/wolf_armor_crackiness_low.png', 'armor_translucent',
           '1272b62b2d978282a6a003d1f400f2cd22ca7b15638b02790c99ad01990da53c',0,'Perspective')
    return (LAYERS[0],crack,LAYERS[2])


def float32_vector(value, size):
    require(isinstance(value,list) and len(value)==size
            and all(type(v) is float and math.isfinite(v) for v in value), 'invalid draw float vector')
    try:
        return struct.pack('<'+'f'*size,*value)
    except (OverflowError,struct.error) as error:
        raise ValueError('draw vector exceeds float32') from error


def compare_draws(frozen, native, frozen_frame, current_frame,mode="high"):
    require(type(frozen_frame) is int and frozen_frame>0 and type(current_frame) is int and current_frame>0,
            'invalid selected wolf frame')
    require(isinstance(frozen,dict) and frozen.get('schema')=='wolf-model-inputs-v1'
            and frozen.get('complete') is True and frozen.get('enabled') is True
            and frozen.get('gpuReadback') is False and frozen.get('capabilityAdmitted') is False
            and type(frozen.get('renderedFrameIndex')) is int and frozen['renderedFrameIndex']==frozen_frame,
            'missing selected Frozen wolf draw observations')
    require(isinstance(native,dict) and native.get('schema')=='wolf-submission-inputs-v1'
            and native.get('complete') is True and native.get('gpu_readback') is False
            and native.get('capability_admitted') is False
            and type(native.get('deterministic_rendered_frame_index')) is int
            and native['deterministic_rendered_frame_index']==current_frame, 'missing selected native wolf draws')
    observed=frozen.get('completedDraws');draws=native.get('draws');sources=native.get('texture_sources')
    semantics=native.get('semantic_instances')
    expected_layers=layers(mode)
    require(all(isinstance(v,list) and len(v)==len(expected_layers) for v in (observed,draws,sources,semantics)),
            'wolf draw/source count differs from exact crackiness membership')
    textures={}
    for source in sources:
        require(isinstance(source,dict),'malformed wolf texture source')
        identity=source.get('texture_id');encoded=source.get('png_hex')
        require(type(identity) is int and 0<identity<2**32 and identity not in textures
                and type(source.get('width')) is int and type(source.get('height')) is int
                and (source['width'],source['height'])==(64,32)
                and source.get('encoding')=='png-rgba8-hex' and isinstance(encoded,str)
                and 0<len(encoded)<=32768 and len(encoded)%2==0
                and re.fullmatch('[0-9a-f]+',encoded), 'invalid bounded wolf texture')
        with Image.open(io.BytesIO(bytes.fromhex(encoded))) as image:
            require(image.format=='PNG' and image.mode=='RGBA' and image.size==(64,32),
                    'wolf PNG differs from declared dimensions')
            textures[identity]=hashlib.sha256(image.tobytes()).hexdigest()
    remaining={}
    for source in semantics:
        require(isinstance(source,dict) and source.get('context')=='world'
                and source.get('entity_identity')=='minecraft:wolf' and source.get('foil') is None,
                'unexpected wolf semantic source')
        key=(source.get('mesh_key'),source.get('mesh_generation'))
        require(all(type(v) is int and v>0 for v in key) and key not in remaining,'duplicate wolf mesh source')
        remaining[key]=source
    commands=[];used=set()
    for index,(baseline,draw,layer) in enumerate(zip(observed,draws,expected_layers)):
        texture,pipeline,digest,order,projection=layer
        cracked=pipeline=='armor_translucent'
        require(isinstance(baseline,dict) and isinstance(draw,dict),'invalid wolf draw')
        require(baseline.get('textureDeclaration')==texture
                and baseline.get('pipeline')=='minecraft:pipeline/'+pipeline
                and baseline.get('depthCompare')=='LEQUAL_DEPTH_TEST' and baseline.get('depthWrite') is True
                and baseline.get('cull') is False and baseline.get('blend')==(ALPHA if cracked else 'none')
                and type(baseline.get('vertices')) is int and baseline['vertices']==264
                and type(baseline.get('indices')) is int and baseline['indices']==396,
                'Frozen wolf draw order or contract changed')
        identity=draw.get('texture_id')
        require(type(identity) is int and identity in textures and identity not in used
                and textures[identity]==digest,'native wolf draw order or texture differs from Frozen')
        used.add(identity)
        mesh=(draw.get('mesh_key'),draw.get('mesh_generation'))
        require(all(type(v) is int and v>0 for v in mesh),'invalid wolf draw mesh identity')
        source=remaining.pop(mesh,None)
        require(source is not None and type(source.get('model_submission_order')) is int
                and source['model_submission_order']==order and source.get('projection')==projection
                and type(source.get('flags')) is int and source['flags']==(0 if projection is None else 4),
                'native wolf authored order or layering mismatch')
        require(draw.get('entity_identity')=='minecraft:wolf' and draw.get('projection')==projection
                and type(draw.get('index_count')) is int and draw['index_count']==396
                and type(draw.get('instance_count')) is int and draw['instance_count']==1
                and draw.get('standard_foil') is False and draw.get('depth_compare')=='LessOrEqual'
                and draw.get('depth_write') is True and type(draw.get('depth_policy')) is int and draw['depth_policy']==1
                and type(draw.get('material_mode')) is int and draw['material_mode']==(7 if cracked else 2)
                and draw.get('blend')==('Alpha' if cracked else 'Disabled')
                and draw.get('program')=='vulkanic:builtin/'+('direct_model_translucent_cutout_v1' if cracked else 'direct_terrain_cutout_v1'),
                'native wolf pipeline or draw count mismatch')
        require(float32_vector(draw.get('view_matrix'),16)==float32_vector(baseline.get('view'),16),
                'native wolf view differs from Frozen float32 inputs')
        require(type(draw.get('command_index')) is int and draw['command_index']>0,'missing native draw command')
        commands.append(draw['command_index'])
    require(not remaining and commands==sorted(set(commands)), 'wolf draw membership or execution order mismatch')
    return dict(passed=True,capability_admitted=False,draws=len(expected_layers),texture_correspondence_verified=True,
                geometry_correspondence_verified=False,presentation_ownership_verified=False)


def compare_owned_inputs(frozen, current, native, owner, ack, capture, *, pose,mode="high"):
    """Compose selected presentation and actual uploads; mesh geometry remains unproven."""
    from shield_static_blocking_reference import validate_owner
    from wolf_armor_reference import paired_inputs
    require(all(isinstance(v,dict) for v in (frozen,current,native,owner,ack,capture)),
            'missing wolf selected-frame documents')
    validate_owner(owner,ack,capture)
    for key in ('gameplay_frame_id','correlation_id','gal_submission_id','deterministic_rendered_frame_index'):
        require(type(native.get(key)) is int and native[key]>0 and native[key]==owner.get(key),
                'wolf native receipt differs from completed presentation')
    frame=native['deterministic_rendered_frame_index']
    paired_inputs(frozen,current,frozen.get('renderedFrameIndex'),frame,pose=pose)
    result=compare_draws(frozen,native,frozen.get('renderedFrameIndex'),frame,mode)
    expected_pose=float32_vector(frozen['transforms'][0]['modelView'],16)
    semantics={(s['mesh_key'],s['mesh_generation']):s for s in native['semantic_instances']}
    for index,draw in enumerate(native['draws']):
        cracked=layers(mode)[index][1]=='armor_translucent'
        semantic=semantics[draw['mesh_key'],draw['mesh_generation']]
        require(type(semantic.get('section_index')) is int and semantic['section_index']==2**32-1
                and type(draw.get('section_index')) is int and draw['section_index']==0,
                'wolf section membership mismatch')
        uploads=draw.get('uploaded_instances')
        require(isinstance(uploads,list) and len(uploads)==1 and isinstance(uploads[0],dict),
                'missing actual wolf instance upload')
        upload=uploads[0]
        require(float32_vector(semantic.get('model_pose'),16)==expected_pose
                and float32_vector(upload.get('model_pose'),16)==expected_pose,
                'wolf semantic or uploaded transform differs from Frozen')
        material=[0.0,0.0,0.0,82.0] if cracked else [0.1,0.0,0.0,114.0]
        require(upload.get('foil') is None
                and float32_vector(upload.get('material'),4)==float32_vector(material,4),
                'wolf uploaded material mismatch')
        sampler=draw.get('sampler')
        fields=re.search(r'\", min_filter: (\w+), mag_filter: (\w+), mip_filter: (\w+), address_u: (\w+), address_v: (\w+), address_w: (\w+), comparison: (\w+) \}$',sampler) if isinstance(sampler,str) else None
        require(fields is not None and fields.groups()==
                ('Nearest','Nearest','Nearest','ClampToEdge','ClampToEdge','ClampToEdge','None'),
                'wolf actual sampler mismatch')
    result.update(presentation_ownership_verified=True,instance_uploads_verified=True,
                  paired_cpu_inputs_verified=True)
    return result

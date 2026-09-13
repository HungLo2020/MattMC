"""Strict independently observed leather CPU contract; never capability admission."""
from collections import Counter
import math
from held_special_foil_reference import require, integer

PREFIX = 'minecraft:textures/entity/equipment/'
BODY = PREFIX + 'humanoid/leather.png'
LEGS = PREFIX + 'humanoid_leggings/leather.png'
BODY_OVERLAY = PREFIX + 'humanoid/leather_overlay.png'
LEGS_OVERLAY = PREFIX + 'humanoid_leggings/leather_overlay.png'
GLINT = 'minecraft:textures/misc/enchanted_glint_armor.png'
TINT = -13408564


def validate_frozen_geometry(receipt, frame):
    require(isinstance(receipt, dict) and receipt.get('schema') == 'equipment-model-geometry-v1'
            and receipt.get('enabled') is True and receipt.get('complete') is True
            and receipt.get('gpuReadback') is False and receipt.get('capabilityAdmitted') is False
            and integer(frame, 1) and type(receipt.get('renderedFrameIndex')) is int
            and receipt['renderedFrameIndex'] == frame, 'missing selected Frozen leather geometry')
    models, draws = receipt.get('models'), receipt.get('completedDraws')
    require(isinstance(models, list) and len(models) == 12
            and isinstance(draws, list) and len(draws) == 5, 'leather model/draw membership differs')
    expected = Counter({(BODY, 18): 1, (BODY, 12): 2, (LEGS, 18): 1,
        (BODY_OVERLAY, 18): 1, (BODY_OVERLAY, 12): 2, (LEGS_OVERLAY, 18): 1,
        (GLINT, 18): 2, (GLINT, 12): 2})
    observed = Counter()
    for row in models:
        require(isinstance(row, dict) and set(row) == {'texture','pipeline','tint','light','overlay','quads'},
                'invalid leather source fields')
        texture, quads = row['texture'], row['quads']
        require(texture in {BODY, LEGS, BODY_OVERLAY, LEGS_OVERLAY, GLINT}
                and isinstance(quads, list) and len(quads) in (12,18), 'wrong leather texture/geometry')
        require(type(row['tint']) is int and row['tint'] == (-1 if texture in (BODY_OVERLAY, LEGS_OVERLAY) else TINT)
                and integer(row['light']) and integer(row['overlay']), 'wrong leather tint/light/overlay')
        require(row['pipeline'] == ('minecraft:pipeline/glint' if texture == GLINT else 'minecraft:pipeline/armor_cutout_no_cull'), 'wrong leather pipeline')
        observed[(texture,len(quads))] += 1
        for quad in quads:
            require(isinstance(quad, dict) and set(quad)=={'part','cube','normal','vertices'}
                    and isinstance(quad['part'],str) and integer(quad['cube']), 'invalid leather quad identity')
            require(isinstance(quad['vertices'],list) and len(quad['vertices'])==4, 'invalid leather quad vertices')
            for values, size in [(quad['normal'],3)] + [(v,5) for v in quad['vertices']]:
                require(isinstance(values,list) and len(values)==size
                        and all(type(v) in (float,int) and math.isfinite(v) for v in values), 'invalid leather geometry values')
    require(observed == expected, 'missing or substituted leather source')
    expected_draws = Counter({(BODY,168,252):1, (LEGS,72,108):1,
        (BODY_OVERLAY,168,252):1, (LEGS_OVERLAY,72,108):1, (GLINT,240,360):1})
    observed_draws=Counter()
    for draw in draws:
        require(isinstance(draw,dict) and type(draw.get('vertices')) is int
                and type(draw.get('indices')) is int, 'invalid leather draw counts')
        observed_draws[(draw.get('texture'),draw['vertices'],draw['indices'])]+=1
        view=draw.get('view')
        require(isinstance(view,list) and len(view)==16 and all(type(v) in (int,float) and math.isfinite(v) for v in view),
                'invalid leather draw view')
        glint=draw.get('texture')==GLINT
        require(draw.get('pipeline')==('minecraft:pipeline/glint' if glint else 'minecraft:pipeline/armor_cutout_no_cull')
                and draw.get('depthCompare')==('EQUAL_DEPTH_TEST' if glint else 'LEQUAL_DEPTH_TEST')
                and draw.get('depthWrite') is (not glint) and draw.get('cull') is False
                and draw.get('blend')==('BlendFunction[sourceColor=SRC_COLOR, destColor=ONE, sourceAlpha=ZERO, destAlpha=ONE]' if glint else 'none'),
                'wrong leather draw state')
    require(observed_draws==expected_draws and draws[-1]['texture']==GLINT,
            'leather requires all bases/overlays before the actual glint flush')
    return dict(passed=True, capability_admitted=False, models=12, draws=5,
                scope='Frozen CPU sources and ordinary draw-return observations only')

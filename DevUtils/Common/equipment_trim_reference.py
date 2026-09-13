"""Strict CPU trim-source comparison; supplemental evidence, never admission."""
from collections import Counter
import re

from equipment_reference import require

# Independently observed Frozen Java OpenGL r711 CPU sprites, all five poses.
# Provenance: equipment-trim-sources-private-r711/frozen-full-pose-reference.json.
FROZEN_SOURCE_PIXELS = {
    'HUMANOID': 'cf045420097b09f4a92f22d7c4a0ff1a98aae733d53e12fa6850a1a50cf2bb4d',
    'HUMANOID_LEGGINGS': '4b5e3d3eef1f5efc23fc329c6b8164e3bd8ea85e3a9432f32115519d43d16abe',
}

def validate_sources(receipt, frame, *, decal):
    require(type(frame) is int and frame > 0 and type(decal) is bool,
            'invalid trim source scope')
    require(isinstance(receipt, dict) and receipt.get('schema') == 'equipment-trim-cpu-sources-v1'
            and receipt.get('enabled') is True and receipt.get('complete') is True
            and receipt.get('gpuReadback') is False
            and type(receipt.get('renderedFrameIndex')) is int
            and receipt['renderedFrameIndex'] == frame, 'missing or stale trim CPU source receipt')
    sources = receipt.get('sources')
    require(isinstance(sources, list) and len(sources) == 4, 'trim requires four source layers')
    keys = {'sprite', 'atlas', 'layer', 'asset', 'width', 'height', 'x', 'y',
            'u0', 'u1', 'v0', 'v1', 'rgbaSha256', 'depthTest', 'depthWrite'}
    seen = {}
    for source in sources:
        require(isinstance(source, dict) and source.keys() == keys, 'unexpected trim source fields')
        layer = source['layer']
        require(layer in ('HUMANOID', 'HUMANOID_LEGGINGS')
                and source['sprite'] == 'minecraft:trims/entity/' + layer.lower() + '/spire_gold'
                and source['atlas'] == 'minecraft:textures/atlas/armor_trims.png'
                and source['asset'] == 'minecraft:diamond', 'wrong trim source resource')
        require(type(source['width']) is int and type(source['height']) is int
                and (source['width'], source['height']) == (64, 32)
                and all(type(source[k]) is int and source[k] >= 0 for k in ('x', 'y')),
                'unsupported trim sprite dimensions')
        require(isinstance(source['rgbaSha256'], str)
                and re.fullmatch('[0-9a-f]{64}', source['rgbaSha256']) is not None,
                'missing trim pixel identity')
        require(source['rgbaSha256'] == FROZEN_SOURCE_PIXELS[layer],
                'trim CPU pixels differ from independently observed Frozen source')
        require(source['depthTest'] == ('EQUAL_DEPTH_TEST' if decal else 'LEQUAL_DEPTH_TEST')
                and source['depthWrite'] is True, 'wrong trim depth contract')
        for axis, size in (('u', 'width'), ('v', 'height')):
            low, high = source[axis+'0'], source[axis+'1']
            require(type(low) is float and type(high) is float and 0 <= low < high <= 1,
                    'invalid trim UV bounds')
            atlas_size = source[size] / (high-low)
            require(atlas_size.is_integer() and 1 <= atlas_size <= 16384
                    and int(atlas_size) & (int(atlas_size)-1) == 0
                    and low*atlas_size == source['x' if axis == 'u' else 'y'],
                    'trim UV bounds disagree with atlas placement')
        require(layer not in seen or seen[layer] == source, 'duplicate trim layers disagree')
        seen[layer] = source
    require(Counter(s['layer'] for s in sources) == Counter(HUMANOID=3, HUMANOID_LEGGINGS=1),
            'trim source membership differs from equipped slots')
    return sources


def paired_sources(frozen, current, frozen_frame, current_frame, *, decal):
    baseline = validate_sources(frozen, frozen_frame, decal=decal)
    candidate = validate_sources(current, current_frame, decal=decal)
    # Atlas packing may differ between backends; resource pixels must not.
    def identity(source):
        return tuple(source[k] for k in ('sprite', 'atlas', 'layer', 'asset', 'width', 'height',
                                        'rgbaSha256', 'depthTest', 'depthWrite'))
    require(Counter(map(identity, baseline)) == Counter(map(identity, candidate)),
            'paired trim CPU resources differ')
    return dict(passed=True, capability_admitted=False, source_layers=4,
                native_texture_correspondence_verified=False)


def native_atlas_sources(native, source_receipt, frame, *, decal=True):
    """Match actual bound native atlas pixels/UVs to independent CPU sprites.

    Supplemental to the selected submission/owner/semantic pipeline verifier;
    this function alone never authorizes a capture or a rendering capability.
    """
    import hashlib
    import io
    import math
    from PIL import Image
    sources = validate_sources(source_receipt, frame, decal=decal)
    require(isinstance(native, dict) and native.get('complete') is True
            and native.get('schema') == 'equipment-submission-inputs-v1'
            and native.get('gpu_readback') is False and native.get('capability_admitted') is False
            and type(native.get('deterministic_rendered_frame_index')) is int
            and native['deterministic_rendered_frame_index'] == frame, 'missing selected native trim inputs')
    textures = native.get('texture_sources')
    require(isinstance(textures, list) and 1 <= len(textures) <= 4, 'missing bounded native atlas bytes')
    decoded = {}
    total_bytes = 0
    for texture in textures:
        require(isinstance(texture, dict), 'invalid native atlas source')
        identity, width, height = (texture.get(k) for k in ('texture_id', 'width', 'height'))
        require(type(identity) is int and 0 < identity < 2**32 and identity not in decoded
                and type(width) is int and type(height) is int and width > 0 and height > 0
                and width*height*4 <= 8*1024*1024, 'invalid native atlas identity/dimensions')
        total_bytes += width*height*4
        require(total_bytes <= 16*1024*1024, 'native atlas aggregate exceeds capture bound')
        encoded = texture.get('png_hex')
        require(texture.get('encoding') == 'png-rgba8-hex' and isinstance(encoded, str)
                and 0 < len(encoded) <= 2*1024*1024 and len(encoded)%2 == 0
                and re.fullmatch('[0-9a-f]+', encoded) is not None, 'invalid bounded native atlas encoding')
        with Image.open(io.BytesIO(bytes.fromhex(encoded))) as image:
            require(image.format == 'PNG' and image.mode == 'RGBA' and image.size == (width, height),
                    'native atlas PNG disagrees with source descriptor')
            decoded[identity] = (image.copy(), texture.get('rgba_xxh32'))
    draws = native.get('draws')
    require(isinstance(draws, list) and len(draws) == 12, 'trim requires complete twelve-draw membership')
    # Ordinary trim and base armor share LEQUAL/write. Identify ordinary trim
    # by the actual captured bound atlas, then require its declared depth policy.
    trims = [d for d in draws if isinstance(d, dict)
             and (d.get('depth_policy') == 3 if decal else
                  type(d.get('texture_id')) is int and d['texture_id'] in decoded)]
    require(len(trims) == 4, 'missing four native trim atlas draws')
    matched = Counter()
    used_textures = set()
    for draw in trims:
        identity = draw.get('texture_id')
        require(type(identity) is int and identity in decoded, 'trim draw lacks its bound atlas source')
        image, texture_hash = decoded[identity]
        require(type(draw.get('depth_policy')) is int and draw['depth_policy'] == (3 if decal else 1)
                and draw.get('depth_compare') == ('Equal' if decal else 'LessOrEqual')
                and draw.get('depth_write') is True
                and draw.get('standard_foil') is False
                and (draw.get('texture_width'), draw.get('texture_height')) == image.size
                and isinstance(texture_hash, str) and re.fullmatch('[0-9a-f]{8}', texture_hash)
                and draw.get('texture_rgba_xxh32') == texture_hash, 'trim draw/source descriptor mismatch')
        uvs = draw.get('mesh_uvs')
        require(isinstance(uvs, list) and 1 <= len(uvs) <= 256
                and type(draw.get('mesh_vertex_bytes')) is int
                and draw['mesh_vertex_bytes'] == len(uvs)*80
                and all(isinstance(uv, list) and len(uv) == 2
                        and all(type(v) is float and math.isfinite(v) for v in uv) for uv in uvs),
                'missing complete native packed mesh UVs')
        candidates = []
        for source in {s['layer']: s for s in sources}.values():
            if not all(source['u0'] <= u <= source['u1'] and source['v0'] <= v <= source['v1'] for u, v in uvs):
                continue
            x, y, w, h = (source[k] for k in ('x', 'y', 'width', 'height'))
            require(x+w <= image.width and y+h <= image.height
                    and source['u0']*image.width == x and source['u1']*image.width == x+w
                    and source['v0']*image.height == y and source['v1']*image.height == y+h,
                    'native bound atlas dimensions disagree with sprite UVs')
            require(hashlib.sha256(image.crop((x,y,x+w,y+h)).tobytes()).hexdigest() == source['rgbaSha256'],
                    'bound native atlas pixels differ from Frozen trim source')
            candidates.append(source['layer'])
        require(len(candidates) == 1, 'native trim UVs do not identify exactly one source sprite')
        matched[candidates[0]] += 1
        used_textures.add(identity)
    require(matched == Counter(HUMANOID=3, HUMANOID_LEGGINGS=1)
            and used_textures == decoded.keys(), 'native trim sprite/atlas membership differs')
    return dict(passed=True, capability_admitted=False, native_texture_correspondence_verified=True,
                trim_draws=4, atlas_resources=len(decoded))

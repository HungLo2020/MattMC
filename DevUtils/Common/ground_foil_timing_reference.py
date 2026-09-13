"""Strict capture-local world glint observations, independent of pixel admission."""
import math
import re
from held_special_foil_reference import require


def bounded_int(value,minimum=0,maximum=2**63-1):
    return type(value) is int and minimum<=value<=maximum


def observed_world(timing,frame,provider):
    require(provider in ('frozen-world-state','semantic-world'),'unknown ground timing provider')
    require(isinstance(timing,dict) and timing.get('enabled') is True and timing.get('complete') is True,
            'missing complete companion GUI timing')
    ground=timing.get('ground')
    require(isinstance(ground,dict) and ground.get('schema')=='ground-foil-frame-timing-v1'
            and ground.get('enabled') is True and ground.get('complete') is True,
            'missing complete captured ground timing')
    require(bounded_int(frame,1) and bounded_int(ground.get('renderedFrameIndex'),1)
            and ground['renderedFrameIndex']==frame and bounded_int(ground.get('frameSequence'),1)
            and bounded_int(timing.get('frameSequence'),1) and ground['frameSequence']==timing['frameSequence'],
            'ground timing does not identify the selected screenshot frame')
    samples=ground.get('samples')
    require(isinstance(samples,list) and len(samples) in ((2,) if provider=='semantic-world' else (1,2)),
            'ground timing requires two semantic items or one/two actual world state applications')
    keys=[]
    for sample in samples:
        require(isinstance(sample,dict) and sample.get('provider')==provider
                and bounded_int(sample.get('scaledTicks')),'invalid ground timing sample')
        if provider=='semantic-world':
            mesh=sample.get('meshKey')
            require(isinstance(mesh,str) and re.fullmatch('[1-9][0-9]{0,19}',mesh)
                    and int(mesh)<=2**64-1,'ground timing needs canonical unsigned mesh identity')
            require(bounded_int(sample.get('clockMillis'))
                    and all(type(sample.get(k)) in (int,float) and math.isfinite(sample[k]) and 0<=sample[k]<=1
                            for k in ('speed','strength')),'invalid copied ground clock settings')
            require(sample['scaledTicks']==min(2**63-1,int(float(sample['clockMillis'])*sample['speed']*8)),
                    'ground scaled ticks differ from copied clock')
            keys.append(mesh)
    require(len(set(keys))==len(keys),'duplicate ground semantic clock identity')
    return samples


def matches_native(samples,receipt,frame):
    require(isinstance(receipt,dict) and receipt.get('complete') is True
            and receipt.get('schema')=='world-decal-submission-inputs-v1'
            and bounded_int(frame,1) and bounded_int(receipt.get('deterministic_rendered_frame_index'),1)
            and receipt['deterministic_rendered_frame_index']==frame,'missing selected native ground timing evidence')
    semantics=receipt.get('semantic_instances')
    require(isinstance(semantics,list) and len(semantics)==len(samples)==2,'native ground timing count mismatch')
    used=[]
    for sample in samples:
        matched=[s for s in semantics if isinstance(s,dict) and type(s.get('mesh_key')) is int
                 and s['mesh_key']==int(sample['meshKey'])]
        require(len(matched)==1,'ground clock does not identify a unique native semantic item')
        semantic=matched[0]
        require(semantic.get('context')=='world' and semantic.get('first_person') is False,'ground clock cannot identify a hand draw')
        for observed,native in (('clockMillis','clock_millis'),('scaledTicks','scaled_ticks'),('speed','speed'),('strength','strength')):
            require((bounded_int(semantic.get(native)) if native in ('clock_millis','scaled_ticks') else
                    type(semantic.get(native)) in (int,float) and math.isfinite(semantic[native]))
                    and semantic.get(native)==sample[observed],
                    'observed world clock differs from submitted native payload')
        used.append(semantic['mesh_key'])
    require(len(set(used))==2,'ground clocks cannot reuse a native item')
    return True


def phase_ticks(samples,phase):
    require(type(phase) is int and phase in (10000,40000),'unsupported moving ground phase')
    ticks=[s['scaledTicks'] for s in samples]
    require(bool(ticks) and all(bounded_int(t,1) and (t-phase)%330000<=512 for t in ticks),
            'ground clocks missed the requested natural phase')
    require(all(min((a-b)%330000,(b-a)%330000)<=16 for a in ticks for b in ticks),
            'ground clocks within one capture are not aligned')
    return ticks


def paired_ticks(baseline,current,phase):
    first,second=phase_ticks(baseline,phase),phase_ticks(current,phase)
    distance=max(min((a-b)%330000,(b-a)%330000) for a in first for b in second)
    require(distance<=16,'ground captures differ from one or more actual world clocks')
    return dict(baseline=first,current=second,max_distance=distance)

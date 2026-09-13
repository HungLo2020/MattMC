"""Select a retained Frozen capture using observed sources, before pixel comparison."""
from pathlib import Path
import hashlib
import json


def source_key(document,*,recovery=True):
    import graphics_harness as h
    from held_special_foil_reference import require
    fixture=document.get('droppedItemFoilFixture',{})
    require(h.dropped_item_foil_fixture_valid(fixture,special=True,recovery=recovery),
            'source reference requires the actual ground recovery fixture')
    captures=document.get('captures',[])
    require(len(captures)==1 and type(captures[0].get('renderedFrameIndex')) is int,
            'source reference requires one selected capture')
    copied=json.loads(json.dumps(fixture))
    for item in copied['items']:
        frame=item['extracted']['sources'].pop('renderedFrameIndex')
        require(frame==captures[0]['renderedFrameIndex'],'source reference is from another frame')
    return hashlib.sha256(json.dumps(copied,sort_keys=True,separators=(',',':')).encode()).hexdigest()


def capture(path,mode,*,recovery=True):
    import graphics_harness as h
    from held_special_foil_reference import require
    artifact=h.read_json(path)
    require(isinstance(artifact,dict) and artifact.get('mode',{}).get('name')==mode
            and artifact.get('tool')=='capture' and type(artifact.get('capture',{}).get('exit_code')) is int
            and artifact['capture']['exit_code']==0,'source reference requires an actual successful capture in the exact mode')
    health=artifact.get('validation',{})
    require(all(health.get(k) is True for k in ('complete','crash_free','device_loss_free'))
            and all(health.get(k) is False for k in ('orphan_process_detected','rss_guard_triggered')),
            'source reference capture is unhealthy')
    require(mode=='frozen-opengl-shaders-off' or health.get('vulkan_validation_clean') is True,
            'source reference Current validation failed')
    memory=artifact.get('metrics',{}).get('rss_and_native_memory',{}).get('client_observation',{})
    require(memory.get('complete') is True,'source reference lacks complete process-memory observation')
    document=h.deterministic_capture_document(path) or {}
    return artifact,document,source_key(document,recovery=recovery)


def select(current,references):
    import graphics_harness as h
    from held_special_foil_reference import require
    require(1<=len(references)<=8,'source reference bank must contain one to eight Frozen artifacts')
    paths=sorted(Path(p).resolve() for p in references)
    require(len(set(paths))==len(paths),'duplicate source reference artifacts')
    current=Path(current).resolve()
    candidate,document,key=capture(current,'current-rust-vulkan-shaders-off')
    rows=[]
    for path in paths:
        baseline,other,other_key=capture(path,'frozen-opengl-shaders-off')
        match=(other_key==key and h.deterministic_visual_fixture_equivalence(other,document)['status']=='passed'
               and h.compare_workloads(baseline,candidate,cross_repository=True)['comparable'])
        rows.append(dict(artifact=str(path),artifact_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                         source_key=other_key,source_and_fixture_match=match))
    matched=[row for row in rows if row['source_and_fixture_match']]
    return dict(schema='ground-recovery-source-reference-v1',requested=True,passed=bool(matched),
                current_artifact=str(current),current_artifact_sha256=hashlib.sha256(current.read_bytes()).hexdigest(),
                source_key=key,candidates=rows,selected_artifact=matched[0]['artifact'] if matched else None,
                selection='exact observed source and fixture data only; pixels are checked after selection',
                capability_admitted=False)

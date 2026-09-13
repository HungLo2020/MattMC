"""Normal world/hand SPECIAL route proof, in addition to the complete pixel oracle."""
from pathlib import Path
from held_special_foil_reference import require,validate_native_frame


def route_evidence(document):
    route=document.get('worldDecalFoilAdmission',{})
    require(route.get('schema')=='rust-owned-world-decal-foil-v1' and route.get('normalRoute') is True
            and route.get('privateFlagsPresent') is False,'missing normal SPECIAL route without legacy flags')
    return route


def report(visual,oracle,requested=False):
    result=dict(requested=requested,passed=not requested,capability_admitted=False,pairs=[])
    if not requested:return result
    import graphics_harness as h
    try:
        pairs,checks=visual.get('pairs',[]),oracle.get('pairs',[])
        require(visual.get('passed') is True and oracle.get('passed') is True and bool(pairs)
                and len(pairs)==len(checks),'normal SPECIAL requires complete paired pixel/source/native checks')
        for pair,check in zip(pairs,checks):
            keys=[key for key in ('ground','held') if key in check]
            require(len(keys)==1,'normal SPECIAL requires one explicit world or hand scope')
            scope=check[keys[0]]
            require(check.get('passed') is True and isinstance(scope,dict) and scope.get('passed') is True
                    and scope.get('native',{}).get('passed') is True,'GUI-only evidence cannot admit world/hand SPECIAL')
            current=Path(pair['current_artifact']);doc=h.deterministic_capture_document(current) or {}
            route=route_evidence(doc)
            for key,mode in (('current_artifact','current-rust-vulkan-shaders-off'),
                             ('baseline_artifact','frozen-opengl-shaders-off')):
                artifact=h.read_json(Path(pair[key]));health=artifact.get('validation',{})
                require(artifact.get('mode',{}).get('name')==mode and artifact.get('tool')=='capture'
                        and type(artifact.get('capture',{}).get('exit_code')) is int and artifact['capture']['exit_code']==0
                        and all(health.get(k) is True for k in ('complete','crash_free','device_loss_free'))
                        and all(health.get(k) is False for k in ('orphan_process_detected','rss_guard_triggered')),
                        'normal SPECIAL requires healthy actual paired executions')
                require(key!='current_artifact' or health.get('vulkan_validation_clean') is True,
                        'normal SPECIAL Vulkan validation failed')
                memory=artifact.get('metrics',{}).get('rss_and_native_memory',{}).get('client_observation',{})
                require(memory.get('complete') is True and memory.get('provider')=='linux-proc-status'
                        and all(type(memory.get(k)) is int and memory[k]>0 for k in ('sample_count','peak_rss_kb','peak_hwm_kb')),
                        'normal SPECIAL requires complete paired process-memory observation')
            image=Path(pair['current_image']);native=h.read_json(Path(str(image)+'.decal-inputs.json'))
            owner=h.read_json(current.parent/'capture'/'whole_frame_gameplay_attachments'/
                              f"gameplay-correlation-frame-{native.get('gameplay_frame_id')}.json")
            ack=h.read_json(image.with_name('capture_request_01_initial.ack.json'))
            captures=doc.get('captures',[])
            require(len(captures)==1 and type(captures[0].get('renderedFrameIndex')) is int
                    and captures[0]['renderedFrameIndex']==native.get('deterministic_rendered_frame_index')
                    and captures[0].get('screenshot')==str(image) and ack.get('screenshot')==str(image),
                    'normal SPECIAL route must identify the selected native frame')
            first_person=keys[0]=='held'
            semantics=native.get('semantic_instances')
            require(isinstance(semantics,list) and len(semantics)==(1 if first_person else 2)
                    and all(isinstance(value,dict) and value.get('first_person') is first_person
                            and value.get('context')==('first-person' if first_person else 'world') for value in semantics),
                    'normal SPECIAL scope differs from actual native semantics')
            validate_native_frame(native,owner,ack)
            result['pairs'].append(dict(passed=True,context='ground' if 'ground' in check else scope.get('context'),
                current_artifact=str(current),baseline_artifact=pair['baseline_artifact'],
                correlation=native['correlation_id'],submission=native['gal_submission_id'],route=route))
        result.update(passed=True,capability_admitted=True)
    except (KeyError,TypeError,ValueError,OSError) as error:
        result.update(passed=False,reason=str(error))
    return result

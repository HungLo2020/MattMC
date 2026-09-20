"""Wolf fixture request gate; real paired/native proof is still required."""

def report(scenario, jvm_args, visual=None):
    values=[arg.split('=',1)[1] for arg in (jvm_args or [])
            if arg.startswith('-Dmattmc.dev.graphicsAuditWolfArmor=')]
    requested=scenario=='wolf' or bool(values)
    result=dict(requested=requested,passed=not requested,capability_admitted=False)
    if not requested:return result
    if scenario!='wolf' or len(values)!=1 or values[0] not in ('none','low','medium','high'):
        result['reason']='wolf armor requires one explicit crackiness mode and the wolf scenario'
    else:
        result['mode']=values[0]
        from equipment_reference import require
        from wolf_acceptance import validate_pair
        try:
            require(values[0] in ('high','low','medium','none'),'wolf crackiness mode has no complete inspected reference')
            require(isinstance(visual,dict) and visual.get('passed') is True
                    and isinstance(visual.get('pairs'),list) and visual['pairs'],
                    'wolf armor requires complete paired fixture proof')
            result['pairs']=[validate_pair(pair,mode=values[0]) for pair in visual['pairs']]
            result['passed']=True
            result['scope']=f'private adult pale wolf {values[0]}-crack armor, five stepped front poses and vanilla reload'
        except (OSError,KeyError,TypeError,ValueError,IndexError) as error:
            result['reason']=str(error)
    return result


def validate_fixture(receipt,mode="high"):
    """Exact independently observed damaged-armor fixture; supplemental only."""
    from equipment_reference import require
    require(mode in ('high','low','medium','none'),'unsupported wolf fixture mode')
    expected=dict(schema='ordinary-wolf-armor-v1',requested=True,mode=mode,ready=True,
        item='minecraft:wolf_armor',count=1,damage={'high':51,'low':6,'medium':32,'none':0}[mode],maxDamage=64,crackiness=mode.upper(),
        foil=False,baby=False,tame=False,x=146.6949012917378,y=99.57557514877936,
        z=529.4806875161336,yaw=-75.9375,headYaw=-75.9375,bodyYaw=-75.9375,variant='minecraft:pale')
    require(isinstance(receipt,dict) and receipt.keys()==expected.keys()
            and all(type(receipt[k]) is type(v) and receipt[k]==v for k,v in expected.items()),
            'wolf armor differs from independently inspected crackiness fixture')


def compare_images(frozen,current,pose,mode="high"):
    from equipment_reference import require
    from wolf_armor_anchors import HIGH_CRACK_REFERENCES,REGION
    from wolf_low_anchors import LOW_CRACK_REFERENCES
    from wolf_medium_anchors import MEDIUM_CRACK_REFERENCES
    from wolf_none_anchors import NONE_CRACK_REFERENCES
    require(mode in ("high","low","medium","none"),"unsupported wolf pixel reference")
    references={"high":HIGH_CRACK_REFERENCES,"low":LOW_CRACK_REFERENCES,"medium":MEDIUM_CRACK_REFERENCES,"none":NONE_CRACK_REFERENCES}[mode]
    from PIL import ImageChops,ImageStat
    require(type(pose) is int and 0<=pose<5,'uninspected wolf pose')
    require(frozen.size==current.size==(1280,720),'wolf requires 1280x720')
    frozen,current=frozen.convert('RGB'),current.convert('RGB')
    errors=[]
    for x,y,expected in references[pose]['probes']:
        box=(x-1,y-1,x+2,y+2)
        baseline=list(frozen.crop(box).get_flattened_data());candidate=list(current.crop(box).get_flattened_data())
        require(all(abs(a-b)<=2 for rgb,ref in zip(baseline,expected) for a,b in zip(rgb,ref)),
                'Frozen wolf anchor changed')
        error=max(abs(a-b) for rgb,ref in zip(candidate,baseline) for a,b in zip(rgb,ref))
        require(error<=2,'wolf probe mismatch');errors.append(error)
    means=ImageStat.Stat(ImageChops.difference(frozen.crop(REGION),current.crop(REGION))).mean
    require(all(v<=2 for v in means),'wolf region mismatch')
    return dict(passed=True,capability_admitted=False,probe_errors=errors,region_mean_rgb=means)


def validate_inputs(receipt, frame, *, pose=None):
    """Bounded CPU pose evidence only; cannot establish draw/source ownership."""
    import math
    from equipment_reference import require
    require(type(frame) is int and frame>0 and isinstance(receipt,dict)
            and receipt.get('schema')=='wolf-model-inputs-v1'
            and receipt.get('enabled') is True and receipt.get('complete') is True
            and receipt.get('gpuReadback') is False and receipt.get('capabilityAdmitted') is False
            and type(receipt.get('renderedFrameIndex')) is int and receipt['renderedFrameIndex']==frame,
            'missing or stale wolf model inputs')
    def vector(value,size):
        require(isinstance(value,list) and len(value)==size
                and all(type(v) is float and math.isfinite(v) for v in value),'invalid wolf numeric input')
    def state(value):
        require(isinstance(value,dict) and value.keys()=={'values','angry','sitting','baby','light','texture'},
                'missing wolf state fields')
        vector(value['values'],12)
        require(all(type(value[k]) is bool for k in ('angry','sitting','baby'))
                and type(value['light']) is int and 0<=value['light']<=0xFFFFFFFF
                and value['texture']=='minecraft:textures/entity/wolf/wolf.png','unsupported wolf state')
    models,transforms=receipt.get('models'),receipt.get('transforms')
    require(isinstance(models,list) and 1<=len(models)<=16 and isinstance(transforms,list)
            and 1<=len(transforms)<=16,'missing or excessive wolf observations')
    paths={'root','root/body','root/head','root/head/real_head','root/left_front_leg','root/left_hind_leg',
           'root/right_front_leg','root/right_hind_leg','root/tail','root/tail/real_tail','root/upper_body'}
    for row in models:
        require(isinstance(row,dict) and row.keys()=={'state','parts'},'invalid wolf model observation')
        state(row['state']);parts=row['parts']
        require(isinstance(parts,dict) and parts.keys()==paths,'incomplete wolf part membership')
        for part in parts.values():
            require(isinstance(part,dict) and part.keys()=={'pose','visible','skipDraw'}
                    and type(part['visible']) is bool and type(part['skipDraw']) is bool,'invalid wolf part')
            vector(part['pose'],9)
    for row in transforms:
        require(isinstance(row,dict) and row.keys()=={'state','modelView','normal'},'invalid wolf transform')
        state(row['state']);vector(row['modelView'],16);vector(row['normal'],9)
    if pose is not None or 'simulation' in receipt:
        observed=receipt.get('simulation')
        if pose is None:
            require(isinstance(observed,dict),'missing wolf simulation receipt')
            pose=observed.get('pose')
        validate_simulation(observed,pose)
        require(all(row['state']['values'][0]==pose*10+1 for row in models+transforms),
                'wolf model age differs from settled simulation pose')
    return dict(passed=True,capability_admitted=False,native_draw_coverage_verified=False,
                models=len(models),transforms=len(transforms))


def paired_inputs(frozen,current,frozen_frame,current_frame,pose):
    """Exact stepped CPU geometry correspondence; native draw proof is separate."""
    from equipment_reference import require
    validate_inputs(frozen,frozen_frame,pose=pose)
    validate_inputs(current,current_frame,pose=pose)
    # Layer setup can observe the same pose more than once. Every observation
    # must agree; comparing only one would hide a mismatched armor/crack pose.
    for key in ('models','transforms'):
        expected=frozen[key][0]
        require(all(row==expected for row in frozen[key]+current[key]),
                'wolf paired '+key+' differ')
    return dict(passed=True,capability_admitted=False,native_draw_coverage_verified=False,
                pose=pose,frozen_models=len(frozen['models']),current_models=len(current['models']),
                frozen_transforms=len(frozen['transforms']),current_transforms=len(current['transforms']))


def validate_simulation(receipt,pose):
    from equipment_reference import require
    require(type(pose) is int and 0<=pose<5,'invalid wolf simulation pose')
    expected=dict(schema='wolf-game-tick-step-v1',pose=pose,targetTicks=pose*10,
        clientTicks=pose*10,serverTicks=pose*10,clientFrozen=True,serverFrozen=True,complete=True)
    require(isinstance(receipt,dict) and receipt.keys()==expected.keys()
            and all(type(receipt[k]) is type(v) and receipt[k]==v for k,v in expected.items()),
            'wolf simulation incomplete, mismatched or unfrozen')

#!/usr/bin/env python3
"""Pinned real FULL generation, with the original chunk audit and fallen-body trap.

Requires explicit complete candidate classes and native directory. No builds or
Frozen edits occur. --reopen reads the saved world on a second server launch.
"""
import argparse, contextlib, copy, hashlib, io, json, os, shutil, subprocess, sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[5]
sys.path.insert(0,str(ROOT/'DevUtils/tests/worldgen'))
import VerifyFullGeneration as full
import VerifyGenerationFailure as failure
JAVA=Path('/usr/lib/jvm/java-25-openjdk-amd64/bin')
full.JDK=JAVA

def sha(path):
    with Path(path).open('rb')as stream:return hashlib.file_digest(stream,'sha256').hexdigest()

def guard_selected_runtime(root, side, receipt, report_dir):
    """Guard the runtime actually reused, excluding unrelated historical sides.

    Recompile the archived observer against its original classpath and require
    byte-identical classes in its retained JAR. Never substitute a new observer
    or silently remap an overwritten class directory.
    """
    import zipfile
    command=json.loads((root/(side+'-fresh/command.json')).read_text())
    entries=command[command.index('-cp')+1].split(os.pathsep)
    agent=next(Path(arg.split(':',1)[1]) for arg in command if arg.startswith('-javaagent:'))
    expected_deps=json.loads((ROOT/'build/fallen-tree/s0029-complete-cost-1/result.json').read_text())['input_images']
    selected={}
    for entry in entries:
        path=Path(entry)
        if path.is_dir():
            expected={p:h for p,h in receipt['class_images'].items() if Path(p).is_relative_to(path)}
            actual={str(p):sha(p) for p in path.rglob('*.class')}
            assert actual==expected,('Selected reference classes changed',entry)
            selected.update(expected)
        elif path!=agent:
            assert expected_deps.get(entry)==sha(path),('Selected reference dependency changed/unpinned',entry)
            selected[entry]=sha(path)
    native=next((Path(arg.split('=',1)[1])/'mattmc_rust-linux-x64.so' for arg in command if arg.startswith('-Dmattmc.rust.natives.dir=')),None)
    if native is not None:
        assert receipt['native_images'].get(str(native))==sha(native)
        selected[str(native)]=sha(native)
    sources=[root/'observer-source'/Path(p).name for p in receipt['sources']]
    assert all(sha(root/'observer-source'/Path(p).name)==h for p,h in receipt['sources'].items())
    report_dir.mkdir(parents=True,exist_ok=False)
    with(report_dir/'compile.log').open('w')as log:
        subprocess.run([str(JAVA/'javac'),'-g','-nowarn','-cp',os.pathsep.join(e for e in entries if Path(e)!=agent),
            '-d',str(report_dir/'classes'),*map(str,sources)],stdout=log,stderr=subprocess.STDOUT,check=True)
    with zipfile.ZipFile(agent)as jar:
        archived={name:hashlib.sha256(jar.read(name)).hexdigest() for name in jar.namelist() if name.endswith('.class')}
    rebuilt={str(p.relative_to(report_dir/'classes')):sha(p) for p in (report_dir/'classes').rglob('*.class')}
    assert rebuilt==archived,('Archived observer is not reproducible',root,side)
    selected[str(agent)]=sha(agent)
    unused_changed=[p for group in ('class_images','native_images') for p,h in receipt[group].items()
        if p not in selected and (not Path(p).exists()or sha(Path(p))!=h)]
    report={'root':str(root),'side':side,'selected_images':selected,'archived_observer_reproduced':True,
        'observer_classes':len(archived),'unused_changed_historical_images':unused_changed,
        'original_receipt_sha256':sha(root/'result.json')}
    (report_dir/'result.json').write_text(json.dumps(report,indent=2)+'\n')
    return report

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--current-classpath',type=Path,required=True)
    parser.add_argument('--current-native',type=Path,required=True)
    parser.add_argument('--reference',type=Path,default=ROOT/'build/fallen-tree/s0027-pre-3f1d-2')
    parser.add_argument('--stage',choices=['dimensions','biomes'],required=True)
    parser.add_argument('--radius',type=int,default=2)
    parser.add_argument('--resume',action='store_true')
    parser.add_argument('--reopen',action='store_true')
    parser.add_argument('--timeout',type=int,default=3600)
    parser.add_argument('--reuse-baselines',type=Path,help='Reuse guarded original Frozen/pre runs; launch Current only')
    parser.add_argument('--reuse-pre-current',type=Path,help='Use this guarded actual earlier Current run as the explicitly pinned new pre; preserve its original side identity')
    parser.add_argument('--fresh-pre',action='store_true',help='Reuse Frozen only and launch the explicitly pinned pre and Current')
    parser.add_argument('--known-dimensions-debt',action='store_true',help='Report inherited Frozen debt separately; still require exact pre/Current and own reopen')
    args=parser.parse_args();out=args.output.resolve()
    assert not(args.reuse_pre_current and args.fresh_pre)
    assert not(args.reuse_pre_current or args.fresh_pre)or args.reuse_baselines
    assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False)
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=full.FROZEN,text=True).strip()==full.FROZEN_HEAD
    assert not subprocess.check_output(['git','status','--porcelain','--','src','build.gradle'],cwd=full.FROZEN)
    classpaths={'frozen':(args.reference/'frozen-classpath.txt').read_text().strip(),'pre':(args.reference/'classpath.txt').read_text().strip(),'current':args.current_classpath.read_text().strip()}
    natives={'pre':(args.reference/'native').resolve(),'current':args.current_native.resolve()}
    full.native_dir=lambda side,unused:natives['pre'if side=='reference'else side]
    full.NATIVE=natives['current']/'mattmc_rust-linux-x64.so'
    sources=[*sorted(full.AGENT.glob('*.java')),Path(__file__).with_name('FallenFullAgent.java')]
    original={str(path):sha(path)for path in sources}
    classes={str(path):sha(path)for cp in classpaths.values()for entry in cp.split(os.pathsep)if Path(entry).is_dir()for path in Path(entry).rglob('*.class')}
    libraries={str(path/'mattmc_rust-linux-x64.so'):sha(path/'mattmc_rust-linux-x64.so')for path in natives.values()}
    resource_manifest=json.loads((args.reference/'manifest.json').read_text());resources={**resource_manifest['resource_images'],**resource_manifest['frozen_resource_images']}
    (out/'observer-source').mkdir()
    for path in sources:shutil.copy2(path,out/'observer-source'/path.name)
    result={'scope':'Real fixed-seed FULL serialized chunks/entities and actual fallen adapter consumption; no acceptance timing claim','stage':args.stage,'resume':args.resume,'reopen':args.reopen,'radius':args.radius,'frozen_head':full.FROZEN_HEAD,'sources':original,'class_images':classes,'native_images':libraries,'runs':{},'comparisons':{},'passed':False}
    def save():(out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    save();env=os.environ.copy();env['JAVA_HOME']=str(JAVA.parent)
    runtime=argparse.Namespace(seed=42,radius=args.radius,rounds=1,warmups=0,dimensions=''if args.stage=='dimensions'else'minecraft:overworld',timing=False,workers=1,centers='minecraft:the_end:0:0'if args.stage=='dimensions'else'',biomes=''if args.stage=='dimensions'else'minecraft:forest,minecraft:birch_forest,minecraft:old_growth_birch_forest,minecraft:jungle,minecraft:taiga',structures='',cpus='2-7',timeout=args.timeout,resume=args.resume,heap='8G',remove_worlds=False,jvm_arg=[])
    try:
        regions={}
        if args.reuse_baselines:
            baseline=args.reuse_baselines.resolve();prior=json.loads((baseline/'result.json').read_text())
            assert prior['guards_pass'] and prior['stage']==args.stage and prior['radius']==args.radius and prior['resume']==args.resume and prior['reopen']==args.reopen
            assert all(Path(p).exists()and sha(p)==h for p,h in resources.items())
            for path,digest in prior['sources'].items():assert sha(baseline/'observer-source'/Path(path).name)==digest
            for side in('frozen','pre'):
                if side=='pre' and args.fresh_pre:continue
                if side=='pre' and args.reuse_pre_current:
                    pinned=args.reuse_pre_current.resolve();actual=json.loads((pinned/'result.json').read_text())
                    assert actual['guards_pass'] and actual['stage']==args.stage and actual['radius']==args.radius and actual['resume']==args.resume and actual['reopen']==args.reopen
                    proof=guard_selected_runtime(pinned,'current',actual,out/'reference-proof-pre');result.setdefault('reference_proofs',{})['pre']=proof
                    # Bind the archived actual Current to this new pre's complete
                    # selected classes and actual mapped native launch argument.
                    assert all(actual['class_images'].get(p)==h for p,h in resource_manifest['complete_classes'].items())
                    command=json.loads((pinned/'current-fresh/command.json').read_text())
                    library=next(Path(arg.split('=',1)[1])/'mattmc_rust-linux-x64.so' for arg in command if arg.startswith('-Dmattmc.rust.natives.dir='))
                    assert sha(library)==sha(natives['pre']/'mattmc_rust-linux-x64.so')==resource_manifest['native_sha256']
                    for path in sources:assert sha(path)==sha(pinned/'observer-source'/path.name)
                    regions['pre']=json.loads((pinned/'current-fresh/regions.json').read_text())
                    result['runs']['pre']={'reused_from':str(pinned),'original_side':'current','pinned_pre_commit':resource_manifest['pre_commit'],'receipt_sha256':sha(pinned/'result.json'),'original':actual['runs']['current']}
                    continue
                proof=guard_selected_runtime(baseline,side,prior,out/('reference-proof-'+side));result.setdefault('reference_proofs',{})[side]=proof
                regions[side]=json.loads((baseline/(side+'-fresh/regions.json')).read_text());result['runs'][side]={'reused_from':str(baseline),'original':prior['runs'][side]}
            old=(baseline/'observer-source/FallenFullAgent.java').read_text();new=Path(__file__).with_name('FallenFullAgent.java').read_text()
            for path in sources:
                if path.name!='FallenFullAgent.java':assert sha(path)==sha(baseline/'observer-source'/path.name)
            if old!=new:
                # The sole allowed observer delta sorts the audit copy of an
                # unordered collection. Empty old dimension lists prove it
                # cannot alter these reference fingerprints.
                branch='''                        if(audit&&name.equals("fingerprint"))return new AdviceAdapter(Opcodes.ASM9,method,access,name,desc){
                            protected void onMethodEnter(){loadArg(0);invokeStatic(Type.getObjectType("fullgeneration/FallenFullAgent"),new Method("canonicalBlockEntities","(Ljava/lang/Object;)V"));}
                        };
'''
                stripped=new.replace(branch,'');a=stripped.index('    /** SerializableChunkData');b=stripped.index('    public static void awaitEntities',a);stripped=stripped[:a]+stripped[b:];assert stripped==old
                import VerifyBlockEntityObserver as membership
                for side in('frozen','pre'):
                    archived=json.loads((baseline/(side+'-fresh/regions.json')).read_text())
                    dumps=membership.chunks(baseline/(side+'-fresh/chunks.txt.gz'));assert len(dumps)==sum(r['chunks']for r in archived);assert all(not membership.entries(data[0])[0]for data in dumps.values())
                result['reference_observer_impact']='Only audit collection sorting differs; all125 original dimension block-entity lists are empty on each reference'
            result['reused_baseline_receipt_sha256']=sha(baseline/'result.json');save()
        prepared={}
        launch_sides=('pre','current')if args.fresh_pre else(('current',)if args.reuse_baselines else tuple(classpaths))
        for side in launch_sides:
            cp=classpaths[side]
            target=out/(side+'-agent');target.mkdir()
            with(out/(side+'-compile.log')).open('w')as log:subprocess.run([str(JAVA/'javac'),'-g','-nowarn','-cp',cp,'-d',str(target),*map(str,sources)],stdout=log,stderr=subprocess.STDOUT,check=True)
            manifest=out/(side+'-MANIFEST.MF');manifest.write_text('Premain-Class: fullgeneration.FallenFullAgent\n\n')
            jar=out/(side+'-agent.jar');subprocess.run([str(JAVA/'jar'),'cfm',str(jar),str(manifest),'-C',str(target),'.'],check=True,capture_output=True)
            prepared[side]=(cp,jar)
        for side in launch_sides:
            selected=copy.copy(runtime);selected.jvm_arg=[f'-Dfallen.fullRequireNative={str(side=="current").lower()}','-Dfallen.fullAwaitEntities=true']
            launch_side='reference'if side=='pre'else side
            rows,process=full.launch(launch_side,side+'-fresh',*prepared[side],out,selected,env)
            assert process['exit_code']==0,process
            counter=json.loads((out/(side+'-fresh/fallen-consumer.json')).read_text());assert side!='current'or counter['javaEntries']==0
            assert rows and all(len(row['fingerprints'])==row['chunks']for row in rows)
            regions[side]=rows;result['runs'][side]={'process':process,'fallen':counter,'regions':len(rows),'chunks':sum(row['chunks']for row in rows)};save()
            if args.reopen:
                # The existing restart helper copies the actual saved server.
                # Later -D properties explicitly replace its diagnostic defaults.
                full.NATIVE=natives.get(side,natives['current'])/'mattmc_rust-linux-x64.so'
                props={'audit.hash':'true','audit.dimensions':selected.dimensions,'audit.biomes':selected.biomes,'audit.centers':selected.centers,'audit.resume':'false','fallen.fullAwaitEntities':'true','fallen.fullRequireNative':str(side=='current').lower()}
                printed=io.StringIO()
                with contextlib.redirect_stdout(printed):
                    reopen=failure.run_server(side+'-reopen',*prepared[side],out,selected,env,props,world=out/(side+'-fresh/server'))
                (out/(side+'-reopen-process.txt')).write_text(printed.getvalue())
                print(printed.getvalue(),end='',flush=True)
                assert any(line.startswith(side+'-reopen exit 0 ')for line in printed.getvalue().splitlines()),'Reopen server did not exit successfully'
                assert(reopen/'complete.json').exists(),reopen
                reopened=json.loads((reopen/'regions.json').read_text());assert len(reopened)==len(rows)
                report=full.compare(rows,reopened);result['comparisons'][side+'-reopen']=report;save();assert report['identical']
        assert len({len(rows)for rows in regions.values()})==1
        for side in('pre','current'):
            report=full.compare(regions['frozen'],regions[side]);result['comparisons']['frozen-'+side]=report;save()
        result['comparisons']['pre-current']=full.compare(regions['pre'],regions['current']);save()
        strict=all(report['identical']for report in result['comparisons'].values())
        if args.stage=='biomes':
            frozen=result['runs']['frozen'];counter=(frozen['original']if'reused_from'in frozen else frozen)['fallen'];assert counter['javaEntries']>0,'No actual fallen feature in biome controls'
        result['passed']=strict
        if args.known_dimensions_debt:
            assert args.stage=='dimensions' and args.reuse_baselines
            assert result['comparisons']['pre-current']['identical']
            assert all(r['identical']for name,r in result['comparisons'].items()if name.endswith('-reopen'))
            # No field is omitted: exact equality to the guarded pre image
            # preserves precisely its original Frozen discrepancy.
            result['candidate_checks_pass']=True;result['known_reference_debt']=not strict
        else:assert strict
    finally:
        selected_references={p:h for proof in result.get('reference_proofs',{}).values()for p,h in proof['selected_images'].items()}
        result['selected_reference_guards_pass']=all(Path(p).exists()and sha(Path(p))==h for p,h in selected_references.items())
        result['guards_pass']=result['selected_reference_guards_pass']and all(Path(path).exists()and sha(Path(path))==digest for images in(original,classes,libraries,resources)for path,digest in images.items());result['passed']=result['passed']and result['guards_pass'];save()
    print('FALLEN FULL',args.stage,'PASS'if result['passed']else'KNOWN REFERENCE DEBT',result['runs'],flush=True);assert result['guards_pass']and(result['passed']or result.get('candidate_checks_pass'))

if __name__=='__main__':main()

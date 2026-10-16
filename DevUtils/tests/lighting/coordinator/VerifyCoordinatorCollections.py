#!/usr/bin/env python3
"""Frozen/pre/current callback-order contracts of native-backed collections."""
import json
import os
import sys
from pathlib import Path

HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE.parent/'threaded'))
from VerifyThreadedLighting import Campaign, arguments, images, sha, ROOT


def main():
    parser=arguments(__doc__)
    parser.add_argument('--references-only',action='store_true')
    parser.add_argument('--split-audit',action='store_true')
    parser.add_argument('--messages-audit',action='store_true')
    parser.add_argument('--effects-audit',action='store_true')
    args=parser.parse_args()
    campaign=Campaign(args)
    sides=('frozen','control') if args.references_only else ('frozen','control','current')
    campaign.result['references_only']=args.references_only
    campaign.result['scope']='Actual protected collection facade contracts; no server/client/performance claim'
    mode='messages' if args.messages_audit else 'effects' if args.effects_audit else 'splitters' if args.split_audit else None
    campaign.result['audit']=mode or 'collections'
    archive=campaign.output/'driver-source';archive.mkdir()
    for source in HERE.iterdir():
        if source.suffix in ('.java','.py'):
            campaign.result['driver_sha256'][str(source.relative_to(ROOT))]=sha(source)
            (archive/source.name).write_bytes(source.read_bytes())
    try:
        for side in sides:
            campaign.run([str(campaign.jdk/'javac'),'-g','-nowarn','-cp',campaign.classpaths[side],'-d',str(campaign.drivers[side]),str(HERE/'VerifyCoordinatorCollections.java')],'compile-collections-'+side+'.log')
        campaign.class_images=images({**campaign.classpaths,**{'observer-'+s:str(p) for s,p in campaign.drivers.items()}})
        campaign.result['class_images']=campaign.class_images
        for side in sides:
            trace=campaign.output/(side+'-trace.txt')
            command=campaign.java(side,'VerifyCoordinatorCollections',[trace,*([mode] if mode else [])]);command[command.index('net.minecraft.server.level.VerifyCoordinatorCollections')]='net.minecraft.world.level.lighting.VerifyCoordinatorCollections'
            campaign.run(command,side+'.log')
            campaign.result[side]={'sha256':sha(trace),'rows':len(trace.read_text().splitlines())}
        rows=[campaign.result[s] for s in sides]
        campaign.result['passed']=all(row==rows[0] for row in rows)
        a=(campaign.output/'frozen-trace.txt').read_text().splitlines();b=(campaign.output/(('control' if args.references_only else 'current')+'-trace.txt')).read_text().splitlines()
        campaign.result['first_differences']=[{'line':i+1,'frozen':x,'current':y} for i,(x,y) in enumerate(zip(a,b)) if x!=y][:30]
    finally:
        campaign.seal()
    print(json.dumps({k:campaign.result[k] for k in ('passed','guards_pass','references_only',*sides,'first_differences')}))
    if not campaign.result['passed'] or not campaign.result['guards_pass']:raise SystemExit('Collection contract mismatch/changed inputs; raw observations retained')


if __name__=='__main__':main()

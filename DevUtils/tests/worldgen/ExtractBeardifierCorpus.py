#!/usr/bin/env python3
"""Read saved profiler regions without modifying them; extract ordered geometry.

This small reader targets the zlib-compressed NBT region files produced by the
recorded profiling run. The normal verification driver uses the committed corpus
and does not need these saved worlds.
"""
import struct
import zlib
import pathlib
import json

class Reader:
    def __init__(self,b):self.b=b;self.p=0
    def take(self,n):v=self.b[self.p:self.p+n];self.p+=n;return v
    def num(self,f):return struct.unpack('>'+f,self.take(struct.calcsize(f)))[0]
    def string(self):return self.take(self.num('H')).decode('utf8')
    def tag(self,t):
        if t in range(1,7):return self.num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
        if t==7:return list(self.take(self.num('i')))
        if t==8:return self.string()
        if t==9:
            sub=self.num('B');n=self.num('i');return [self.tag(sub) for _ in range(n)]
        if t==10:
            d={}
            while (s:=self.num('B')):name=self.string();d[name]=self.tag(s)
            return d
        if t in (11,12):return [self.num('i' if t==11 else 'q') for _ in range(self.num('i'))]
        raise ValueError(t)
def chunks(path):
    with path.open('rb') as f:
        headers=f.read(4096)
        for i in range(1024):
            offset=int.from_bytes(headers[i*4:i*4+3],'big')
            if not offset:continue
            f.seek(offset*4096);n=int.from_bytes(f.read(4),'big');kind=f.read(1)[0];b=f.read(n-1)
            if kind!=2:continue
            r=Reader(zlib.decompress(b));t=r.num('B');r.string();yield r.tag(t)
if __name__ == '__main__':
    import argparse,hashlib
    parser=argparse.ArgumentParser(description='Read saved profiler regions and extract ordered structure geometry for Beardifier replay.')
    parser.add_argument('--profile',type=pathlib.Path,required=True)
    parser.add_argument('--output',type=pathlib.Path,required=True)
    args=parser.parse_args()
    structures=[];counts={};regions=[]
    for run,seed in [('baseline-seed42',42),('baseline-seed-negative',-123456789)]:
        folder=args.profile/run/'server/world/region'
        for path in sorted(folder.glob('r.*.mca')):
            rx,rz=map(int,path.stem.split('.')[1:])
            if not (127<=rx<=137 and 127<=rz<=129):continue
            used=False
            for chunk in chunks(path):
                for name,start in chunk.get('structures',{}).get('starts',{}).items():
                    if not start.get('Children'):continue
                    namespace,kind=name.split(':')
                    definition=pathlib.Path('src/main/resources/data')/namespace/'worldgen/structure'/f'{kind}.json'
                    if not definition.exists():continue
                    adjustment=json.loads(definition.read_text()).get('terrain_adaptation','none')
                    if adjustment=='none' or counts.get((seed,adjustment),0)>=2:continue
                    children=[]
                    for child in start['Children']:
                        jigsaw=child.get('id')=='minecraft:jigsaw'
                        children.append({'box':child['BB'],'rigid':not jigsaw or child['pool_element']['projection']=='rigid',
                            'delta':child.get('ground_level_delta',0) if jigsaw else 0,
                            'junctions':[[j['source_x'],j['source_ground_y'],j['source_z']] for j in child.get('junctions',[])]})
                    structures.append({'seed':seed,'id':name,'start':[start['ChunkX'],start['ChunkZ']],
                            'adjustment':adjustment,'children':children})
                    counts[seed,adjustment]=counts.get((seed,adjustment),0)+1;used=True
            if used:regions.append({'path':str(path.relative_to(args.profile)),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
    payload={'provenance':{'profile_revision':'0719ec4bd5f4956ceb9f341c9c641ad7294ef632','regions':regions,
        'selection':'First two saved structures per seed and non-none adjustment in measured Overworld regions, lexical region order. Each structure is replayed independently.'},'structures':structures}
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(payload,separators=(',',':'))+'\n')
    print('Extracted',len(structures),'structures',counts)

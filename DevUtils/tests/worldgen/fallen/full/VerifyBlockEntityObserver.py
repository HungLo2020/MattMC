#!/usr/bin/env python3
"""Check complete NBT membership before/after the audit-only list ordering fix."""
import argparse, collections, gzip, hashlib, json
from pathlib import Path

def balanced(text,start):
    stack=[];quote=None;escape=False
    for i in range(start,len(text)):
        c=text[i]
        if quote:
            if escape:escape=False
            elif c=='\\':escape=True
            elif c==quote:quote=None
        elif c in ('"',"'"):quote=c
        elif c in '[{':stack.append(c)
        elif c in ']}':
            assert stack.pop()==('[' if c==']' else '{')
            if not stack:return i+1
    raise AssertionError('Incomplete canonical NBT')

def entries(text):
    key='"block_entities":';start=text.index(key)+len(key);assert text[start]=='['
    end=balanced(text,start);items=[];i=start+1
    while i<end-1:
        assert text[i]=='{';j=balanced(text,i);items.append(text[i:j]);assert text[j]==',';i=j+1
    return items,text[:start]+text[end:],text[start:end]

def chunks(path):
    rows={};key=None
    with gzip.open(path,'rt') as stream:
        lines=iter(stream)
        for line in lines:
            text=line.rstrip('\n')
            if not text:continue
            fields=text.split()
            if len(fields)==3 and fields[0].startswith('minecraft:') and ',' in fields[2]:
                key=text;assert key not in rows;rows[key]=(next(lines).rstrip('\n'),[])
            else:
                assert key is not None;rows[key][1].append(text)
    return rows

def digest(items):return hashlib.sha256(('['+''.join(s+',' for s in sorted(items))+']').encode()).hexdigest()[:24]

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--before',type=Path,required=True);p.add_argument('--after',type=Path,required=True);p.add_argument('--output',type=Path,required=True);args=p.parse_args()
    old=chunks(args.before/'chunks.txt.gz');new=chunks(args.after/'chunks.txt.gz');assert old.keys()==new.keys()
    fingerprints={r['dimension']+' '+r['label']+' '+pos:fp for r in json.loads((args.after/'regions.json').read_text()) for pos,fp in r['fingerprints'].items()}
    changed=[];total=0;nonempty=0
    for key,(text,entities) in old.items():
        a,rest,raw=entries(text);b,other,ordered=entries(new[key][0]);assert collections.Counter(a)==collections.Counter(b),key
        assert rest==other and entities==new[key][1],key
        assert digest(a)==fingerprints[key]['block_entities'],key
        assert b==sorted(b),key
        if raw!=ordered:changed.append(key)
        if a:
            nonempty+=1;assert digest(a+[a[0]])!=digest(a),'Duplicate lost'
            modified=a.copy();modified[0]=modified[0].replace('{','{"observer_negative":1,',1)
            assert digest(modified)!=digest(a),'NBT field mutation lost'
        total+=len(a)
    assert nonempty>0
    report={'passed':True,'chunks':len(old),'complete_block_entity_compounds':total,'nonempty_chunks':nonempty,'ordering_changed_chunks':changed,'all_other_NBT_and_entities_exact':True,'field_mutation_and_duplicate_negative_controls':True}
    args.output.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))

if __name__=='__main__':main()

#!/usr/bin/env python3
"""Check real item component decoding, hopper copies and save/reload in a disposable server.

Uses the existing dedicated-server console harness; never builds classes or opens
an existing world. This is runtime/save coverage, not client/menu or cost evidence.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[4]
HARNESS = ROOT / 'DevUtils/tests/server/VerifyComposterTransfers.py'
spec = importlib.util.spec_from_file_location('item_component_server', HARNESS)
harness = importlib.util.module_from_spec(spec)
spec.loader.exec_module(harness)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--classpath-file', type=Path, required=True)
    parser.add_argument('--native-dir', type=Path, help='omit for the Java-only Frozen reference')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--port', type=int, default=25584)
    parser.add_argument('--timeout', type=float, default=120)
    args = parser.parse_args()
    output = args.output.resolve(); output.mkdir(parents=True, exist_ok=False)
    world = output / 'server'; world.mkdir()
    properties = {
        'server-ip':'127.0.0.1', 'server-port':args.port, 'level-name':'world',
        'level-seed':24, 'level-type':'minecraft:flat',
        'generator-settings':json.dumps({'biome':'minecraft:plains','layers':[{'block':'minecraft:bedrock','height':1}]}),
        'generate-structures':'false','view-distance':2,'simulation-distance':2,
        'pause-when-empty-seconds':0,'enable-rcon':'false','enable-query':'false',
        'motd':'Disposable item component runtime verification',
    }
    (world/'server.properties').write_text(''.join(f'{k}={v}\n' for k,v in properties.items()))
    classpath = args.classpath_file.resolve(strict=True).read_text().strip()
    native = args.native_dir.resolve(strict=True) if args.native_dir else None
    harness.CLASS_NAMES += [
        'net/minecraft/world/item/ItemStack.class',
        'net/minecraft/world/item/enchantment/ItemEnchantments.class',
        'net/minecraft/core/component/DataComponentPatch.class',
        'net/minecraft/core/component/PatchedDataComponentMap.class',
        'net/minecraft/world/level/block/entity/ChestBlockEntity.class',
    ]
    def binary_pins():
        pins={}
        for name in harness.CLASS_NAMES:
            for entry in classpath.split(os.pathsep):
                path=Path(entry)
                if path.is_dir() and (path/name).is_file():
                    pins[name]={'source':str(path/name),'sha256':harness.sha256(path/name)};break
                if path.is_file() and zipfile.is_zipfile(path):
                    with zipfile.ZipFile(path) as archive:
                        if name in archive.namelist():
                            import hashlib
                            pins[name]={'source':str(path)+'!/'+name,'sha256':hashlib.sha256(archive.read(name)).hexdigest()};break
            else:raise RuntimeError('Missing runtime class: '+name)
        pins['natives']={str(p):harness.sha256(p) for p in native.glob('*mattmc*') if p.is_file()} if native else {}
        if native and not pins['natives']:raise RuntimeError('No MattMC native image in '+str(native))
        return pins
    pins = binary_pins()
    command = [str(args.java.resolve(strict=True)),'-Xmx2G','-Xms512M','--enable-native-access=ALL-UNNAMED',
               *(['-Dmattmc.rust.natives.dir='+str(native)] if native else []),'-cp',classpath,'net.minecraft.server.Main','--nogui']
    server = harness.Server(command,world,output,args.timeout)
    result = {'passed':False,'scope':'dedicated-server item decoding, hopper transfer, exact serialized components and save/reload; no client/menu/performance claim',
              'command':command,'binary_pins':pins,'driver_sha256':harness.sha256(Path(__file__)),
              'harness_sha256':harness.sha256(HARNESS),'checks':[],'startup_seconds':[],'native_mappings':[]}
    def start():
        begin=time.monotonic()
        server.start()
        result['startup_seconds'].append(time.monotonic()-begin)
        mappings=[line for line in Path(f'/proc/{server.process.pid}/maps').read_text().splitlines() if 'mattmc_rust' in line]
        result['native_mappings'].append(mappings)
        if native:assert mappings and all(str(native) in line for line in mappings),'intended native image not loaded'
    def check(label,condition):
        actual=server.condition(condition)
        result['checks'].append({'label':label,'condition':condition,'passed':actual,'run':server.run})
        if not actual:raise AssertionError(label+': '+condition)
        print('PASS '+label,flush=True)
    sword='{Slot:0b,id:"minecraft:diamond_sword",count:1,components:{"minecraft:damage":7,"minecraft:enchantments":{"minecraft:unbreaking":3}}}'
    sticks='{Slot:1b,id:"minecraft:stick",count:3,components:{"minecraft:max_stack_size":16,"minecraft:enchantments":{"minecraft:unbreaking":2}}}'
    def component_count(prefix,pos,slot):
        server.command_batch([f'execute store result storage mattmc:item_runtime component_keys int 1 run data get block {pos} Items[{slot}].components'])
        check(prefix+' exact component membership', 'if data storage mattmc:item_runtime {component_keys:2}')
    def destination(prefix):
        check(prefix+' sword and scalar/enchantment values','if data block 3 64 2 {Items:['+sword+']}')
        check(prefix+' merged sticks and scalar/enchantment values','if data block 3 64 2 {Items:['+sticks+']}')
        check(prefix+' exact destination membership','unless data block 3 64 2 Items[2]')
        check(prefix+' source retired all members','unless data block 2 64 2 Items[0]')
        component_count(prefix+' sword','3 64 2',0)
        component_count(prefix+' sticks','3 64 2',1)
    try:
        start()
        server.command_batch(['forceload add 0 0 15 15','setblock 3 64 2 minecraft:chest',
            'setblock 2 64 2 minecraft:hopper[facing=east]{TransferCooldown:0}',
            'item replace block 2 64 2 container.0 with minecraft:diamond_sword[minecraft:damage=7,minecraft:enchantments={"minecraft:unbreaking":3}] 1',
            'item replace block 2 64 2 container.1 with minecraft:stick[minecraft:max_stack_size=16,minecraft:enchantments={"minecraft:unbreaking":2}] 3'])
        check('decoded sword payload','if data block 2 64 2 {Items:['+sword+']}')
        check('decoded primitive and enchantment payload','if data block 2 64 2 {Items:['+sticks+']}')
        component_count('decoded sword','2 64 2',0)
        component_count('decoded sticks','2 64 2',1)
        server.step(40);destination('after transfer')
        server.stop(save=True);start();destination('after reload');server.stop(save=True)
        assert pins==binary_pins(),'compiled image changed during runtime check'
        result['passed']=True
    except Exception as failure:
        result['failure']=f'{type(failure).__name__}: {failure}'
        print(result['failure'],file=sys.stderr)
    finally:
        try:server.stop(save=True)
        except Exception as failure:
            result['passed']=False;result['cleanup_failure']=f'{type(failure).__name__}: {failure}'
        server.commands.close()
        result.update(server_exits=server.exits,tick_steps=server.tick_steps,startup_diagnostics=server.startup_diagnostics)
        (output/'receipt.json').write_text(json.dumps(result,indent=2))
    return 0 if result['passed'] else 1


if __name__=='__main__':sys.exit(main())

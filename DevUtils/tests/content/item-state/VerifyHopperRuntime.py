#!/usr/bin/env python3
"""Check native hopper transfers in a disposable server: plain stacks into a chest
and a double chest, and a real dropped item entity collected by a hopper, before
and after save/reload.

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
    parser.add_argument('--port', type=int, default=25586)
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
        'motd':'Disposable hopper runtime verification',
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
        'net/minecraft/world/level/block/entity/HopperBlockEntity.class',
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
    result = {'passed':False,'scope':'dedicated-server native hopper eject into chest and double chest, item-entity collection, exact saved contents and save/reload; no client/menu/performance claim',
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
    def destination(prefix):
        check(prefix+' chest received all plain sticks','if data block 3 64 2 {Items:[{id:"minecraft:stick",count:20}]}')
        check(prefix+' chest received all dirt','if data block 3 64 2 {Items:[{id:"minecraft:dirt",count:5}]}')
        check(prefix+' feeding hopper is empty','unless data block 2 64 2 Items[0]')
        check(prefix+' double chest right half received the sticks','if data block 6 64 2 {Items:[{Slot:0b,id:"minecraft:stick",count:4}]}')
        check(prefix+' double chest left half unchanged','if data block 6 64 3 {Items:[{Slot:26b,id:"minecraft:dirt",count:64}]}')
        check(prefix+' dropped item entity collected into the chest','if data block 11 64 2 {Items:[{Slot:0b,id:"minecraft:stick",count:7}]}')
        check(prefix+' collecting hopper is empty','unless data block 10 64 2 Items[0]')
        check(prefix+' no item entity left','unless entity @e[type=minecraft:item]')
    try:
        start()
        commands=['forceload add 0 0 15 15','setblock 3 64 2 minecraft:chest',
            'setblock 2 64 2 minecraft:hopper[facing=east]{TransferCooldown:0}',
            'item replace block 2 64 2 container.0 with minecraft:stick 20',
            'item replace block 2 64 2 container.1 with minecraft:dirt 5',
            # Double chest facing west: the left half's partner is to its north.
            'setblock 6 64 3 minecraft:chest[type=left,facing=west]','setblock 6 64 2 minecraft:chest[type=right,facing=west]']
        commands+=[f'item replace block 6 64 3 container.{slot} with minecraft:dirt 64' for slot in range(27)]
        commands+=['setblock 5 64 3 minecraft:hopper[facing=east]{TransferCooldown:0}','item replace block 5 64 3 container.0 with minecraft:stick 4',
            'setblock 11 64 2 minecraft:chest','setblock 10 64 2 minecraft:hopper[facing=east]{TransferCooldown:0}',
            'summon minecraft:item 10.5 65.1 2.5 {Item:{id:"minecraft:stick",count:7},PickupDelay:0}']
        server.command_batch(commands)
        server.step(300);destination('after transfer')
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

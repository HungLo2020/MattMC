#!/usr/bin/env python3
"""Find transient pixel changes in a bounded lossless terrain observation.

Three-frame agreement is a triage metric, not a flicker/parity acceptance gate.
Camera motion, texture aliasing and animation can produce the same signature.
"""
from __future__ import annotations
import argparse
from collections import deque
import json
from pathlib import Path
import shutil
import subprocess
import numpy as np
from PIL import Image


def returning_mask(previous: np.ndarray, current: np.ndarray, following: np.ndarray) -> np.ndarray:
    a,b,c = (frame.astype(np.int16) for frame in (previous,current,following))
    neighbors_agree = np.abs(a-c).max(axis=2) <= 24
    middle_changes = np.minimum(np.abs(a-b).max(axis=2),np.abs(c-b).max(axis=2)) > 48
    return neighbors_agree & middle_changes


def sampled_triads(frames, lag: int):
    """Retain at most nine samples, including duplicate displayed frames."""
    if lag not in (1, 2, 4):
        raise ValueError('sample lag must be 1, 2 or 4')
    window = deque(maxlen=2 * lag + 1)
    for index, frame in enumerate(frames):
        window.append(frame)
        if len(window) == window.maxlen:
            yield index - lag, window[0], window[lag], window[-1]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('observation', type=Path)
    parser.add_argument('--sample-lag', type=int, choices=(1, 2, 4), default=1,
                        help='spacing between the three samples; larger lags cover repeated display samples')
    parser.add_argument('--rank-by', choices=('fraction', 'tiles'), default='fraction',
                        help='prioritize coherent changed tiles or total returning-pixel fraction')
    args = parser.parse_args()
    meta = json.loads((args.observation / 'video.json').read_text())
    if meta.get('status') != 'complete':
        raise RuntimeError('observation did not complete')
    x,y,width,height = meta['region']
    count = meta['captured_frames']
    if not 2*args.sample_lag+1 <= count <= 1204 or width*height > 1280*720:
        raise RuntimeError('observation exceeds bounded frame or pixel count')
    output = args.observation / ('transient-analysis' if args.sample_lag == 1
                                else f'transient-analysis-lag-{args.sample_lag}')
    if args.rank_by == 'tiles':
        output = output.with_name(output.name + '-tiles')
    output.mkdir(exist_ok=False)
    ffmpeg = shutil.which('ffmpeg')
    if not ffmpeg:
        raise RuntimeError('ffmpeg is required')
    rows,top = [],[]
    def rank(row):
        return ((row['full_returning_tiles'],row['returning_fraction'])
                if args.rank_by == 'tiles' else (row['returning_fraction'],))
    frame_bytes = width*height*3
    decoded = 0
    with (output/'decode.log').open('w') as log:
        decoder = subprocess.Popen([ffmpeg,'-nostdin','-v','error','-threads','1','-i',str(args.observation/meta['video']),
            '-threads','1','-pix_fmt','rgb24','-fps_mode','passthrough','-f','rawvideo','-'],
            stdout=subprocess.PIPE,stderr=log)
        try:
            def frames():
                nonlocal decoded
                while raw := decoder.stdout.read(frame_bytes):
                    if len(raw) != frame_bytes or decoded >= count:
                        raise RuntimeError('invalid frame or count mismatch')
                    decoded += 1
                    yield np.frombuffer(raw,dtype=np.uint8).reshape(height,width,3)

            for index, prior, middle, current in sampled_triads(frames(), args.sample_lag):
                mask = returning_mask(prior,middle,current)
                fraction = float(mask.mean())
                tile_height,tile_width = height//8,width//8
                tiles = mask[:tile_height*8,:tile_width*8].reshape(tile_height,8,tile_width,8).mean(axis=(1,3))
                row = {'frame':index,'timestamp':meta['frames'][index]['video_timestamp_seconds'],
                    'returning_fraction':fraction,'full_returning_tiles':int((tiles>=.75).sum())}
                rows.append(row)
                if len(top)<3 or rank(row)>rank(top[-1][0]):
                    top.append((row,prior.copy(),middle.copy(),current.copy(),mask.copy()))
                    top.sort(key=lambda record:rank(record[0]),reverse=True)
                    top=top[:3]
            if decoder.wait(timeout=10) or decoded!=count:
                raise RuntimeError('decode failed or frame count mismatch')
        finally:
            if decoder.poll() is None:decoder.kill()
            decoder.wait(timeout=10)
            decoder.stdout.close()
    for rank,(row,a,b,c,mask) in enumerate(top,1):
        Image.fromarray(np.concatenate([a,b,c],axis=1)).save(output/f'candidate-{rank}-frame-{row["frame"]}.png')
        Image.fromarray(mask.astype(np.uint8)*255).save(output/f'candidate-{rank}-mask.png')
    result = {'schema':'terrain-transient-triage-v1','status':'complete','decoded_frames':decoded,
        'sample_lag':args.sample_lag,
        'ranking':args.rank_by,
        'thresholds':{'neighbor_max_channel_difference':24,'middle_min_channel_difference':48},
        'top_candidates':[record[0] for record in top],'frames':rows,
        'limitations':'Unregistered presented-window triads. Motion, aliasing and animation require visual/source correlation; no parity or flicker acceptance.'}
    (output/'summary.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps({'observation':str(args.observation),'top_candidates':result['top_candidates']}))
    return 0


if __name__=='__main__':
    raise SystemExit(main())

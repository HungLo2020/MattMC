#!/usr/bin/env python3
"""Classify queued work from native completion samples, without renderer changes.

Present flags can be reconstructed from consecutive submission IDs and the
count of incomplete present submissions. Already-completed submissions never
need classification. Missing/failed samples invalidate reconstruction until
the timeline catches up; they must not become false overlap evidence.
"""
import argparse
from collections import Counter
import json
from pathlib import Path
import re


class PendingPresents:
    def __init__(self):
        self.last_id = 0
        self.last_completed = 0
        self.uncertain_through = 0
        self.pending = set()

    def sample(self, submission, completed, present_count):
        if submission <= self.last_id:
            raise ValueError('submission IDs did not increase')
        if submission != self.last_id + 1:
            self.uncertain_through = submission - 1
        self.last_id = submission
        if completed is None:
            self.uncertain_through = submission
            self.pending.clear()
            return 'unknown', []
        if not self.last_completed <= completed <= submission:
            raise ValueError('completion timeline regressed or exceeds submitted work')
        self.last_completed = completed
        self.pending = {value for value in self.pending if value > completed}
        if completed < self.uncertain_through:
            self.uncertain_through = submission
            self.pending.clear()
            return 'unknown', []
        if completed == submission:
            if present_count != 0:
                raise ValueError('completed submission retained incomplete presents')
            return 'completed', []
        new_present = present_count - len(self.pending)
        if new_present not in (0, 1) or present_count > 8:
            raise ValueError('present counts cannot describe the retained submission queue')
        prior = sorted(self.pending)
        if new_present:
            self.pending.add(submission)
            return 'present', prior
        return 'offscreen', prior


def analyze(lines, interval=None):
    if interval is not None and not (isinstance(interval[0], int) and isinstance(interval[1], int)
                                    and 0 < interval[0] < interval[1]):
        raise ValueError('video wall-clock interval is invalid')
    tracker = PendingPresents()
    counts, inside = Counter(), Counter()
    witnesses = []
    for line in lines:
        if 'vulkan.submission.ownership ' not in line:
            continue
        fields = dict(re.findall(r'(\w+)=([^\s]+)', line))
        submission = int(fields['id'])
        success = fields.get('gpu_sample') == 'ok'
        kind, prior = tracker.sample(submission,
            int(fields['gpu_completed']) if success else None,
            int(fields['gpu_incomplete_present_submissions']) if success else None)
        counts[kind] += 1
        start, end = fields.get('gpu_sample_wall_start_ns'), fields.get('gpu_sample_wall_end_ns')
        within = (interval is not None and start and end and start.isdecimal() and end.isdecimal()
                  and interval[0] <= int(start) <= int(end) <= interval[1])
        if within:
            inside[kind] += 1
        if kind == 'offscreen' and prior:
            counts['offscreen_queued_while_prior_present_incomplete'] += 1
            if within:
                inside['offscreen_queued_while_prior_present_incomplete'] += 1
                if len(witnesses) < 8:
                    witnesses.append({'submission': submission, 'completed': tracker.last_completed,
                        'prior_present_submissions': prior, 'wall_start_ns': int(start), 'wall_end_ns': int(end)})
    if tracker.last_id == 0:
        raise ValueError('no native ownership samples found')
    return {'status': 'partial' if counts['unknown'] else 'complete',
            'samples': sum(counts[k] for k in ('unknown', 'completed', 'present', 'offscreen')),
            'counts': dict(counts), 'within_video_counts': dict(inside), 'witnesses': witnesses,
            'interpretation': 'Queued offscreen work with unfinished earlier present-image work. Not concurrent execution on one queue, exact semantic-frame identity, a memory hazard, flicker cause/absence or performance acceptance.'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('log', type=Path)
    parser.add_argument('--video', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    interval = None
    if args.video:
        video = json.loads(args.video.read_text())
        if video.get('status') != 'complete':
            parser.error('video must be a completed observation')
        interval = video['ffmpeg_wall_start_ns'], video['ffmpeg_wall_end_ns']
    with args.log.open(errors='replace') as lines:
        result = analyze(lines, interval)
    result.update(log=str(args.log), video=str(args.video) if args.video else None)
    args.output.write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps({'output': str(args.output), 'counts': result['counts'],
                      'within_video_counts': result['within_video_counts']}))


if __name__ == '__main__':
    main()

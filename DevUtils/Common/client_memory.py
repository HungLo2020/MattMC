"""Bounded Linux client RSS observations, independent of memory enforcement.

Identity requires a fresh capture receipt, its isolated cwd, a Java executable,
and the actual client main class. Never records command lines or credentials.
"""
from pathlib import Path
from collections import deque
import re

CLIENT_MAINS = {"net.fabricmc.loader.impl.launch.knot.KnotClient", "net.minecraft.client.main.Main",
                "net.fabricmc.devlaunchinjector.Main"}


def java_main(argv):
    takes_value={"-cp","-classpath","--class-path","-p","--module-path","--add-opens","--add-exports",
                 "--add-reads","--patch-module","--limit-modules","--add-modules"}
    i=1
    while i<len(argv):
        value=argv[i]
        if value in takes_value: i+=2;continue
        if value in ("-jar","-m","--module") or value.startswith("@"): return None
        if value.startswith("-"): i+=1;continue
        return value
    return None


def start_ticks(stat):
    fields=stat[stat.rindex(")")+1:].split()
    value=int(fields[19])
    if value<=0:raise ValueError("invalid process start identity")
    return value


def process_sample(proc, game_dir):
    """Return one consistently identified process observation, or no sample."""
    try:
        before=start_ticks((proc/"stat").read_text())
        if (proc/"exe").resolve(strict=True).name not in ("java","javaw"): return None
        if (proc/"cwd").resolve(strict=True) != game_dir: return None
        payload=(proc/"cmdline").read_bytes()
        if len(payload)>1024*1024:return None
        argv=[v.decode("utf-8",errors="strict") for v in payload.split(b"\0") if v]
        main=java_main(argv)
        if main not in CLIENT_MAINS:return None
        status=(proc/"status").read_text()
        rss=re.search(r"(?m)^VmRSS:\s+(\d+) kB$",status)
        hwm=re.search(r"(?m)^VmHWM:\s+(\d+) kB$",status)
        if not rss or not hwm or start_ticks((proc/"stat").read_text())!=before:return None
        rss,hwm=int(rss[1]),int(hwm[1])
        if rss<=0 or hwm<rss:return None
        return dict(pid=int(proc.name),start_ticks=before,main_class=main,rss_kb=rss,hwm_kb=hwm)
    except (OSError,ValueError,IndexError,UnicodeError):
        return None


class ClientMemoryObserver:
    def __init__(self,capture_dir,artifact_root,prior_metadata=(),*,proc_root=Path('/proc'),started=0.0):
        self.capture_dir=Path(capture_dir)
        self.artifact_root=Path(artifact_root).resolve()
        self.prior_metadata=set(prior_metadata)
        self.proc_root=Path(proc_root)
        self.started=started
        self.last_poll=None
        self.samples=deque(maxlen=128)
        self.count=0
        self.peak_rss=0
        self.peak_hwm=0
        self.identity=None
        self.game_dir=None
        self.metadata=None
        self.errors=set()

    def tick(self,now):
        if self.last_poll is not None and now-self.last_poll<1:return
        self.last_poll=now
        if not self.proc_root.is_dir():return
        fresh=set(self.capture_dir.glob('meta_*.txt'))-self.prior_metadata
        if not fresh:return
        if len(fresh)!=1:self.errors.add('ambiguous-capture-metadata');return
        meta=next(iter(fresh))
        try:
            if meta.stat().st_size>1024*1024:self.errors.add('oversized-metadata');return
            values=dict(line.split('=',1) for line in meta.read_text().splitlines() if '=' in line)
            run_id=values.get('run_id','')
            # Shell receipts identify their run through the filename.
            if not run_id:run_id=meta.stem.removeprefix('meta_')
            if not re.fullmatch(r'[A-Za-z0-9_-]{1,128}',run_id) or meta.name!=f'meta_{run_id}.txt':
                self.errors.add('invalid-run-identity');return
            raw=values.get('isolated_game_dir')
            if not raw:return
            game_dir=Path(raw).resolve(strict=True)
            if game_dir.name!='game_dir_'+run_id or not game_dir.is_relative_to(self.artifact_root):
                self.errors.add('unowned-game-directory');return
            if self.game_dir is not None and (game_dir!=self.game_dir or meta!=self.metadata):
                self.errors.add('capture-identity-changed');return
            self.game_dir,self.metadata=game_dir,meta
            rows=[row for proc in self.proc_root.iterdir() if proc.name.isdecimal()
                  if (row:=process_sample(proc,game_dir)) is not None]
        except (OSError,ValueError,UnicodeError):return
        if len(rows)>1:self.errors.add('ambiguous-client-process');return
        if not rows:return
        row=rows[0]
        identity={k:row[k] for k in ('pid','start_ticks','main_class')}
        if self.identity is not None and identity!=self.identity:
            self.errors.add('client-process-identity-changed');return
        self.identity=identity
        self.count+=1
        self.peak_rss=max(self.peak_rss,row['rss_kb'])
        self.peak_hwm=max(self.peak_hwm,row['hwm_kb'])
        self.samples.append(dict(elapsed_seconds=round(now-self.started,6),rss_kb=row['rss_kb'],hwm_kb=row['hwm_kb']))

    def receipt(self,*,terminal):
        complete=terminal and self.count>=2 and not self.errors
        return dict(schema='isolated-client-memory-v1',provider='linux-proc-status',complete=complete,
                    scope='one isolated client process; sampled RSS and observed kernel high-water mark',
                    terminal=terminal,identity=self.identity,game_dir=str(self.game_dir) if self.game_dir else None,
                    metadata=str(self.metadata) if self.metadata else None,sample_count=self.count,
                    peak_rss_kb=self.peak_rss or None,peak_hwm_kb=self.peak_hwm or None,
                    retained_samples=list(self.samples),discarded_history_count=max(0,self.count-len(self.samples)),
                    interval_seconds=1,history_limit=128,errors=sorted(self.errors),enforcement=False)


def attach_memory_observation(artifact,receipt,path):
    metrics=artifact['metrics']['rss_and_native_memory']
    metrics['client_observation']=dict(path=str(path),**{k:receipt[k] for k in
        ('schema','provider','complete','identity','sample_count','peak_rss_kb','peak_hwm_kb')})
    if receipt['complete']:
        metrics['legacy_guard_peak_rss_kb']=metrics.get('peak_rss_kb')
        metrics['peak_rss_kb']=receipt['peak_rss_kb']
        metrics['peak_hwm_kb']=receipt['peak_hwm_kb']

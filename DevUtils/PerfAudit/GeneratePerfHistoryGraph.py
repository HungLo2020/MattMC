#!/usr/bin/env python3

from __future__ import annotations

import argparse
import datetime as dt
import html
import json
import re
from pathlib import Path


SUMMARY_PATTERN = re.compile(
  r"^(opengl|vulkan)(?:\s+aggregate\s+runs=\d+/\d+)?\s+fps_avg=([0-9.]+)\s+fps_1pct_low=([0-9.]+)"
)
TIMESTAMP_PATTERN = re.compile(r"^(\d{8}_\d{6})$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate MattMC backend performance history graph.")
    parser.add_argument("--logs-dir", required=True, help="Path to logs/auto-profile")
    parser.add_argument("--output", required=True, help="Output HTML file path")
    return parser.parse_args()


def load_runs(logs_dir: Path) -> list[dict]:
    runs: list[dict] = []

    if not logs_dir.is_dir():
        return runs

    for child in sorted(logs_dir.iterdir()):
        if not child.is_dir() or not TIMESTAMP_PATTERN.match(child.name):
            continue

        summary_file = child / "summary.txt"
        if not summary_file.is_file():
            continue

        metrics: dict[str, dict[str, float]] = {}
        for line in summary_file.read_text(encoding="utf-8", errors="replace").splitlines():
            match = SUMMARY_PATTERN.match(line.strip())
            if not match:
                continue
            backend, avg, low = match.groups()
            metrics[backend] = {
                "avg": float(avg),
                "low": float(low),
            }

        if "opengl" not in metrics or "vulkan" not in metrics:
            continue

        timestamp = dt.datetime.strptime(child.name, "%Y%m%d_%H%M%S")
        runs.append(
            {
                "timestamp": child.name,
                "iso": timestamp.isoformat(sep=" ", timespec="seconds"),
                "label": timestamp.strftime("%Y-%m-%d %H:%M:%S"),
                "opengl": metrics["opengl"],
                "vulkan": metrics["vulkan"],
            }
        )

    return runs


def build_html(runs: list[dict], logs_dir: Path) -> str:
    payload = json.dumps(runs, separators=(",", ":"))
    generated_at = dt.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    total_runs = len(runs)
    first_run = runs[0]["label"] if runs else "n/a"
    last_run = runs[-1]["label"] if runs else "n/a"
    rows = "\n".join(
        "<tr>"
        f"<td>{html.escape(run['label'])}</td>"
        f"<td>{run['opengl']['avg']:.4f}</td>"
        f"<td>{run['opengl']['low']:.4f}</td>"
        f"<td>{run['vulkan']['avg']:.4f}</td>"
        f"<td>{run['vulkan']['low']:.4f}</td>"
        "</tr>"
        for run in runs
    )
    if not rows:
        rows = '<tr><td colspan="5">No graphable compare summaries found.</td></tr>'

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>MattMC Performance History</title>
  <style>
    :root {{
      --bg: #f6f1e8;
      --panel: rgba(255,255,255,0.78);
      --ink: #1e1f22;
      --muted: #5d5d66;
      --grid: rgba(30,31,34,0.14);
      --ogl-avg: #1f7a8c;
      --ogl-low: #bfdbf7;
      --vk-avg: #c44536;
      --vk-low: #f6bd60;
      --border: rgba(30,31,34,0.12);
    }}
    * {{ box-sizing: border-box; }}
    body {{
      margin: 0;
      font-family: Georgia, "Iowan Old Style", "Palatino Linotype", serif;
      color: var(--ink);
      background:
        radial-gradient(circle at top left, rgba(196,69,54,0.14), transparent 28%),
        radial-gradient(circle at top right, rgba(31,122,140,0.15), transparent 24%),
        linear-gradient(180deg, #f8f3eb 0%, var(--bg) 100%);
    }}
    main {{
      max-width: 1280px;
      margin: 0 auto;
      padding: 32px 20px 56px;
    }}
    h1 {{
      margin: 0 0 8px;
      font-size: clamp(2rem, 3vw, 3.2rem);
      letter-spacing: -0.03em;
    }}
    p {{ margin: 0; }}
    .lede {{ color: var(--muted); max-width: 72ch; }}
    .stats {{
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 12px;
      margin: 24px 0;
    }}
    .stat {{
      background: var(--panel);
      border: 1px solid var(--border);
      border-radius: 16px;
      padding: 16px 18px;
      backdrop-filter: blur(8px);
    }}
    .stat .label {{ display: block; color: var(--muted); font-size: 0.9rem; margin-bottom: 4px; }}
    .stat .value {{ font-size: 1.15rem; font-weight: 700; }}
    .chart-card {{
      background: var(--panel);
      border: 1px solid var(--border);
      border-radius: 24px;
      padding: 18px 18px 12px;
      margin: 18px 0;
      box-shadow: 0 18px 50px rgba(40, 33, 24, 0.08);
      backdrop-filter: blur(8px);
    }}
    .chart-card h2 {{ margin: 0 0 4px; font-size: 1.35rem; }}
    .chart-card .sub {{ color: var(--muted); margin-bottom: 14px; }}
    .legend {{ display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 10px; color: var(--muted); font-size: 0.95rem; }}
    .legend-item {{ display: inline-flex; align-items: center; gap: 8px; }}
    .swatch {{ width: 14px; height: 14px; border-radius: 999px; }}
    svg {{ width: 100%; height: auto; display: block; overflow: visible; }}
    .table-wrap {{
      margin-top: 24px;
      background: var(--panel);
      border: 1px solid var(--border);
      border-radius: 24px;
      padding: 18px;
      overflow: auto;
    }}
    table {{ width: 100%; border-collapse: collapse; min-width: 760px; }}
    th, td {{ text-align: left; padding: 10px 12px; border-bottom: 1px solid var(--border); }}
    th {{ position: sticky; top: 0; background: #f4eee5; }}
    .axis-label, .tick-label {{ fill: var(--muted); font-size: 12px; font-family: Arial, sans-serif; }}
    .grid-line {{ stroke: var(--grid); stroke-width: 1; }}
    .axis-line {{ stroke: rgba(30,31,34,0.25); stroke-width: 1.2; }}
    .line {{ fill: none; stroke-width: 3; stroke-linejoin: round; stroke-linecap: round; }}
    .dot {{ stroke: white; stroke-width: 1.5; }}
    @media (max-width: 720px) {{
      main {{ padding: 20px 12px 40px; }}
      .chart-card {{ padding: 14px 12px 8px; }}
    }}
  </style>
</head>
<body>
  <main>
    <h1>MattMC Performance History</h1>
    <p class="lede">Automatically regenerated from retained compare summaries under {html.escape(str(logs_dir))}. The graph is overwritten on every performance harness run.</p>

    <section class="stats">
      <div class="stat"><span class="label">Generated</span><span class="value">{html.escape(generated_at)}</span></div>
      <div class="stat"><span class="label">Graphable Runs</span><span class="value">{total_runs}</span></div>
      <div class="stat"><span class="label">First Run</span><span class="value">{html.escape(first_run)}</span></div>
      <div class="stat"><span class="label">Latest Run</span><span class="value">{html.escape(last_run)}</span></div>
    </section>

    <section class="chart-card">
      <h2>OpenGL Performance</h2>
      <p class="sub">Average FPS and 1% low over time.</p>
      <div class="legend">
        <span class="legend-item"><span class="swatch" style="background: var(--ogl-avg)"></span>Average FPS</span>
        <span class="legend-item"><span class="swatch" style="background: var(--ogl-low)"></span>1% Low FPS</span>
      </div>
      <svg id="opengl-chart" viewBox="0 0 1100 420" aria-label="OpenGL performance history chart"></svg>
    </section>

    <section class="chart-card">
      <h2>Vulkan Performance</h2>
      <p class="sub">Average FPS and 1% low over time.</p>
      <div class="legend">
        <span class="legend-item"><span class="swatch" style="background: var(--vk-avg)"></span>Average FPS</span>
        <span class="legend-item"><span class="swatch" style="background: var(--vk-low)"></span>1% Low FPS</span>
      </div>
      <svg id="vulkan-chart" viewBox="0 0 1100 420" aria-label="Vulkan performance history chart"></svg>
    </section>

    <section class="table-wrap">
      <h2>Underlying Data</h2>
      <table>
        <thead>
          <tr>
            <th>Timestamp</th>
            <th>OpenGL Avg FPS</th>
            <th>OpenGL 1% Low</th>
            <th>Vulkan Avg FPS</th>
            <th>Vulkan 1% Low</th>
          </tr>
        </thead>
        <tbody>
          {rows}
        </tbody>
      </table>
    </section>
  </main>
  <script>
    const runs = {payload};

    function renderChart(svgId, backendKey, avgColor, lowColor) {{
      const svg = document.getElementById(svgId);
      const width = 1100;
      const height = 420;
      const margin = {{ top: 18, right: 24, bottom: 72, left: 64 }};
      const innerWidth = width - margin.left - margin.right;
      const innerHeight = height - margin.top - margin.bottom;

      if (!runs.length) {{
        svg.innerHTML = '<text x="50%" y="50%" text-anchor="middle" class="tick-label">No graphable runs found.</text>';
        return;
      }}

      const avgValues = runs.map((run) => run[backendKey].avg);
      const lowValues = runs.map((run) => run[backendKey].low);
      const allValues = avgValues.concat(lowValues);
      const maxValue = Math.max(...allValues, 1);
      const paddedMax = Math.ceil(maxValue * 1.1);
      const yTicks = 5;

      const x = (index) => margin.left + (runs.length === 1 ? innerWidth / 2 : (index / (runs.length - 1)) * innerWidth);
      const y = (value) => margin.top + innerHeight - (value / paddedMax) * innerHeight;

      const linePath = (values) => values.map((value, index) => `${{index === 0 ? 'M' : 'L'}}${{x(index).toFixed(2)}},${{y(value).toFixed(2)}}`).join(' ');

      let markup = '';
      for (let tick = 0; tick <= yTicks; tick += 1) {{
        const ratio = tick / yTicks;
        const value = paddedMax * (1 - ratio);
        const yPos = margin.top + innerHeight * ratio;
        markup += `<line class="grid-line" x1="${{margin.left}}" y1="${{yPos}}" x2="${{width - margin.right}}" y2="${{yPos}}"></line>`;
        markup += `<text class="tick-label" x="${{margin.left - 10}}" y="${{yPos + 4}}" text-anchor="end">${{value.toFixed(0)}}</text>`;
      }}

      markup += `<line class="axis-line" x1="${{margin.left}}" y1="${{margin.top}}" x2="${{margin.left}}" y2="${{margin.top + innerHeight}}"></line>`;
      markup += `<line class="axis-line" x1="${{margin.left}}" y1="${{margin.top + innerHeight}}" x2="${{width - margin.right}}" y2="${{margin.top + innerHeight}}"></line>`;
      markup += `<text class="axis-label" x="${{margin.left - 46}}" y="${{margin.top - 2}}">FPS</text>`;

      const xTickStep = Math.max(1, Math.ceil(runs.length / 8));
      for (let index = 0; index < runs.length; index += xTickStep) {{
        const xPos = x(index);
        const label = runs[index].label.slice(0, 10);
        markup += `<line class="grid-line" x1="${{xPos}}" y1="${{margin.top + innerHeight}}" x2="${{xPos}}" y2="${{margin.top + innerHeight + 6}}"></line>`;
        markup += `<text class="tick-label" transform="translate(${{xPos}},${{margin.top + innerHeight + 22}}) rotate(35)" text-anchor="start">${{label}}</text>`;
      }}
      if ((runs.length - 1) % xTickStep !== 0) {{
        const lastIndex = runs.length - 1;
        const xPos = x(lastIndex);
        const label = runs[lastIndex].label.slice(0, 10);
        markup += `<line class="grid-line" x1="${{xPos}}" y1="${{margin.top + innerHeight}}" x2="${{xPos}}" y2="${{margin.top + innerHeight + 6}}"></line>`;
        markup += `<text class="tick-label" transform="translate(${{xPos}},${{margin.top + innerHeight + 22}}) rotate(35)" text-anchor="start">${{label}}</text>`;
      }}

      markup += `<path class="line" stroke="${{avgColor}}" d="${{linePath(avgValues)}}"></path>`;
      markup += `<path class="line" stroke="${{lowColor}}" d="${{linePath(lowValues)}}"></path>`;

      avgValues.forEach((value, index) => {{
        markup += `<circle class="dot" cx="${{x(index)}}" cy="${{y(value)}}" r="3.8" fill="${{avgColor}}"><title>${{runs[index].label}} avg: ${{value.toFixed(4)}} FPS</title></circle>`;
      }});
      lowValues.forEach((value, index) => {{
        markup += `<circle class="dot" cx="${{x(index)}}" cy="${{y(value)}}" r="3.8" fill="${{lowColor}}"><title>${{runs[index].label}} 1% low: ${{value.toFixed(4)}} FPS</title></circle>`;
      }});

      svg.innerHTML = markup;
    }}

    renderChart('opengl-chart', 'opengl', getComputedStyle(document.documentElement).getPropertyValue('--ogl-avg').trim(), getComputedStyle(document.documentElement).getPropertyValue('--ogl-low').trim());
    renderChart('vulkan-chart', 'vulkan', getComputedStyle(document.documentElement).getPropertyValue('--vk-avg').trim(), getComputedStyle(document.documentElement).getPropertyValue('--vk-low').trim());
  </script>
</body>
</html>
"""


def main() -> int:
    args = parse_args()
    logs_dir = Path(args.logs_dir).resolve()
    output = Path(args.output).resolve()
    runs = load_runs(logs_dir)
    output.write_text(build_html(runs, logs_dir), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
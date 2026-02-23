#!/usr/bin/env python3
"""
Profile the rule engine benchmark and produce a text summary.

Usage:
    ./scripts/profile.py                       # run profiler + analyze
    ./scripts/profile.py --analyze FILE        # analyze existing collapsed stacks
    ./scripts/profile.py --event alloc         # profile allocations instead of CPU
    ./scripts/profile.py --top 40             # show top 40 entries

The --run mode (default) invokes:
    ./gradlew profileBenchmark -PasyncProfilerLib=<path>

Set AP_LIB env var to override the async-profiler library path.
"""

import argparse
import os
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_COLLAPSED = PROJECT_ROOT / "app" / "build" / "benchmark" / "profile.collapsed"
DEFAULT_AP_LIB = (
    Path.home()
    / "async-profiler"
    / "async-profiler-3.0-macos"
    / "lib"
    / "libasyncProfiler.dylib"
)


# ── Profiler runner ──────────────────────────────────────────────────────────


def run_profiler(event="cpu"):
    ap_lib = os.environ.get("AP_LIB", str(DEFAULT_AP_LIB))
    if not Path(ap_lib).exists():
        print(f"ERROR: async-profiler not found at {ap_lib}", file=sys.stderr)
        print("Set AP_LIB env var to the libasyncProfiler path.", file=sys.stderr)
        sys.exit(1)

    print(f"=== Profiling (event={event}) ===")
    print(f"async-profiler: {ap_lib}\n")

    result = subprocess.run(
        [
            str(PROJECT_ROOT / "gradlew"),
            "-p",
            str(PROJECT_ROOT),
            "profileBenchmark",
            f"-PasyncProfilerLib={ap_lib}",
            f"-PprofileEvent={event}",
        ],
        cwd=PROJECT_ROOT,
    )
    if result.returncode != 0:
        sys.exit(result.returncode)

    if not DEFAULT_COLLAPSED.exists():
        print(f"ERROR: expected output at {DEFAULT_COLLAPSED}", file=sys.stderr)
        sys.exit(1)

    return DEFAULT_COLLAPSED


# ── Collapsed-stacks parser ─────────────────────────────────────────────────


def parse_collapsed(path):
    """Parse collapsed stack format: 'frame1;frame2;...;frameN count'."""
    stacks = []
    total_samples = 0
    with open(path) as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.rsplit(None, 1)
            if len(parts) != 2:
                continue
            stack_str, count_str = parts
            try:
                count = int(count_str)
            except ValueError:
                continue
            stacks.append((stack_str.split(";"), count))
            total_samples += count
    return stacks, total_samples


# ── Analysis ─────────────────────────────────────────────────────────────────


def analyze(stacks, total_samples):
    self_time = defaultdict(int)
    total_time = defaultdict(int)
    package_time = defaultdict(int)
    caller_callee = defaultdict(int)

    for frames, count in stacks:
        leaf = frames[-1]
        self_time[leaf] += count
        package_time[_package(leaf)] += count

        seen = set()
        for frame in frames:
            if frame not in seen:
                total_time[frame] += count
                seen.add(frame)

        for i in range(len(frames) - 1):
            caller_callee[(frames[i], frames[i + 1])] += count

    return self_time, total_time, package_time, caller_callee


def _package(frame):
    """Extract package: 'com/ruleengine/index/Trie.find' → 'com/ruleengine/index'."""
    if frame.startswith("[") or ("." not in frame and "/" not in frame):
        return frame
    # Split off method name (after last dot), then class (after last slash)
    class_and_pkg = frame.rsplit(".", 1)[0]  # "com/ruleengine/index/Trie"
    if "/" in class_and_pkg:
        return class_and_pkg.rsplit("/", 1)[0]  # "com/ruleengine/index"
    parts = class_and_pkg.rsplit(".", 1)
    return parts[0] if len(parts) >= 2 else class_and_pkg


def _short(frame):
    """Shorten: 'com/ruleengine/index/Trie.find' → 'Trie.find'."""
    if frame.startswith("[") or ("." not in frame and "/" not in frame):
        return frame
    class_and_pkg = frame.rsplit(".", 1)[0]
    method = frame.rsplit(".", 1)[-1] if "." in frame else ""
    if "/" in class_and_pkg:
        cls = class_and_pkg.rsplit("/", 1)[-1]
    else:
        cls = class_and_pkg.rsplit(".", 1)[-1]
    return f"{cls}.{method}" if method else cls


def _is_app(frame):
    return "com/ruleengine" in frame or "com.ruleengine" in frame


# ── Report printer ───────────────────────────────────────────────────────────

W = 80


def _header(title):
    print(f"\n{'— ' + title + ' ':—<{W}}")
    print(f"  {'%':>6}  {'samples':>8}  name")
    print(f"  {'—':—>6}  {'—':—>8}  {'—':—<40}")


def _rows(data, total, n, fmt=_short):
    for key, count in sorted(data.items(), key=lambda x: -x[1])[:n]:
        pct = 100.0 * count / total
        print(f"  {pct:5.1f}%  {count:>8,}  {fmt(key)}")


def print_report(stacks, total_samples, top_n=25):
    if total_samples == 0:
        print("No samples found in profile.")
        return

    self_time, total_time, package_time, caller_callee = analyze(
        stacks, total_samples
    )

    print("=" * W)
    print(f"  PROFILE SUMMARY — {total_samples:,} samples")
    print("=" * W)

    _header("Top Methods (self time)")
    _rows(self_time, total_samples, top_n)

    _header("Top Methods (total time)")
    _rows(total_time, total_samples, top_n)

    app_self = {m: c for m, c in self_time.items() if _is_app(m)}
    if app_self:
        app_total = sum(app_self.values())
        _header("Application Code (self time)")
        print(
            f"  App code: {app_total:,} samples"
            f" ({100.0 * app_total / total_samples:.1f}% of total)"
        )
        _rows(app_self, total_samples, top_n)

    _header("Package Breakdown (self time)")
    _rows(package_time, total_samples, 15, fmt=lambda x: x)

    app_edges = {
        e: c
        for e, c in caller_callee.items()
        if _is_app(e[0]) or _is_app(e[1])
    }
    if app_edges:
        print(f"\n{'— Hot Call Edges (app code) ':—<{W}}")
        print(f"  {'%':>6}  {'samples':>8}  caller → callee")
        print(f"  {'—':—>6}  {'—':—>8}  {'—':—<40}")
        for (caller, callee), count in sorted(
            app_edges.items(), key=lambda x: -x[1]
        )[:15]:
            pct = 100.0 * count / total_samples
            print(
                f"  {pct:5.1f}%  {count:>8,}  {_short(caller)} → {_short(callee)}"
            )

    print("\n" + "=" * W)


# ── Main ─────────────────────────────────────────────────────────────────────


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--analyze", metavar="FILE", help="Analyze an existing collapsed stacks file"
    )
    parser.add_argument("--event", default="cpu", help="Profile event (default: cpu)")
    parser.add_argument("--top", type=int, default=25, help="Top N entries (default: 25)")
    args = parser.parse_args()

    if args.analyze:
        path = Path(args.analyze)
        if not path.exists():
            print(f"ERROR: {path} not found", file=sys.stderr)
            sys.exit(1)
    else:
        path = run_profiler(event=args.event)

    stacks, total_samples = parse_collapsed(path)
    print()
    print_report(stacks, total_samples, top_n=args.top)


if __name__ == "__main__":
    main()

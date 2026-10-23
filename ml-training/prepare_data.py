"""Split raw image folders into train/val/test, grouped so near-duplicate frames never leak.

Input layout:   raw/<source>/<class>/<file>          class in {real, ai}
Output layout:  data/<source>/<split>/<class>/<file>

Grouping: a file named "<group>__<anything>.png" belongs to group "<group>". The Android
collector names frames "<reelHash>__<timestamp>.png", so every frame of one reel lands in the
same split. Files without "__" are their own group.

    python prepare_data.py --raw raw --out data --cap 5000
"""
import argparse
import random
import shutil
from collections import defaultdict
from pathlib import Path

# Kept local (same values as slopnet.data) so this script runs without torch installed.
CLASSES = ["real", "ai"]
IMG_EXT = {".png", ".jpg", ".jpeg", ".webp", ".bmp"}


def group_of(path: Path) -> str:
    return path.stem.split("__")[0] if "__" in path.stem else path.stem


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--raw", default="raw")
    ap.add_argument("--out", default="data")
    ap.add_argument("--val", type=float, default=0.15)
    ap.add_argument("--test", type=float, default=0.15)
    ap.add_argument("--cap", type=int, default=5000, help="max images per (source, class) for big public sets; 0 = no cap")
    ap.add_argument("--no-cap-sources", default="reels", help="comma list of sources exempt from --cap")
    ap.add_argument("--seed", type=int, default=0)
    args = ap.parse_args()

    exempt = set(args.no_cap_sources.split(","))
    rng = random.Random(args.seed)
    raw, out = Path(args.raw), Path(args.out)
    if out.exists():
        raise SystemExit(f"{out} already exists; delete it first so splits are never mixed")

    for src in sorted(p for p in raw.iterdir() if p.is_dir()):
        for cls in CLASSES:
            files = [p for p in sorted((src / cls).glob("*")) if p.suffix.lower() in IMG_EXT] if (src / cls).is_dir() else []
            if not files:
                continue
            groups = defaultdict(list)
            for f in files:
                groups[group_of(f)].append(f)
            keys = sorted(groups)
            rng.shuffle(keys)
            if args.cap and src.name not in exempt:
                keep, n = [], 0
                for k in keys:
                    if n >= args.cap:
                        break
                    keep.append(k)
                    n += len(groups[k])
                keys = keep

            n_val = max(1, round(len(keys) * args.val)) if len(keys) >= 3 else 0
            n_test = max(1, round(len(keys) * args.test)) if len(keys) >= 3 else 0
            split_of = {}
            for i, k in enumerate(keys):
                split_of[k] = "val" if i < n_val else "test" if i < n_val + n_test else "train"

            counts = defaultdict(int)
            for k in keys:
                for f in groups[k]:
                    dst = out / src.name / split_of[k] / cls
                    dst.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(f, dst / f.name)
                    counts[split_of[k]] += 1
            print(f"{src.name:>10} {cls:>4}: {dict(counts)}")


if __name__ == "__main__":
    main()

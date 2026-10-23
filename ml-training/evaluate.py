"""Pick a high-precision threshold on reels/val, then report honestly on reels/test.

    python evaluate.py --ckpt runs/exp1/best.pt --data data --target-precision 0.97

Writes <run>/threshold.json, <run>/report.json, <run>/confusion_matrix.png.
The test split is only touched after the threshold is frozen from val.
"""
import argparse
import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import torch
from torch.utils.data import DataLoader

from slopnet.data import REEL_SOURCE, FrameDataset, eval_transform, list_samples
from slopnet.model import INPUT_SIZE, load_checkpoint
from train import predict


def metrics(y, p, thr):
    pred = p >= thr
    tp = int(((pred == 1) & (y == 1)).sum())
    fp = int(((pred == 1) & (y == 0)).sum())
    tn = int(((pred == 0) & (y == 0)).sum())
    fn = int(((pred == 0) & (y == 1)).sum())
    return {
        "threshold": float(thr), "tp": tp, "fp": fp, "tn": tn, "fn": fn,
        "accuracy": (tp + tn) / max(len(y), 1),
        "precision": tp / (tp + fp) if tp + fp else None,
        "recall": tp / (tp + fn) if tp + fn else None,
    }


def choose_threshold(y, p, target_precision):
    """Lowest threshold (max recall) whose val precision >= target. Falls back to the max-precision one."""
    best_fallback = (-1.0, 0.5)
    for thr in np.linspace(0.05, 0.995, 190):
        m = metrics(y, p, thr)
        if m["precision"] is None or m["tp"] < 3:
            continue
        if m["precision"] >= target_precision:
            return float(thr), True
        best_fallback = max(best_fallback, (m["precision"], float(thr)))
    return best_fallback[1], False


def run(model, samples, bs, device):
    loader = DataLoader(FrameDataset(samples, eval_transform(INPUT_SIZE)), batch_size=bs, num_workers=2)
    return predict(model, loader, device)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ckpt", default="runs/exp1/best.pt")
    ap.add_argument("--data", default="data")
    ap.add_argument("--target-precision", type=float, default=0.97)
    ap.add_argument("--bs", type=int, default=64)
    args = ap.parse_args()

    device = "cuda" if torch.cuda.is_available() else "cpu"
    run_dir = Path(args.ckpt).parent
    model = load_checkpoint(args.ckpt, device)

    val_s = list_samples(args.data, "val", [REEL_SOURCE])
    test_s = list_samples(args.data, "test", [REEL_SOURCE])
    if not val_s or not test_s:
        raise SystemExit(f"need data/{REEL_SOURCE}/val and /test with both classes")

    pv, yv = run(model, val_s, args.bs, device)
    thr, reached = choose_threshold(yv, pv, args.target_precision)
    (run_dir / "threshold.json").write_text(json.dumps(
        {"threshold": thr, "target_precision": args.target_precision, "target_reached_on_val": reached,
         "val_metrics": metrics(yv, pv, thr), "val_n": int(len(yv))}, indent=2))

    pt, yt = run(model, test_s, args.bs, device)
    report = {
        "test_n": int(len(yt)),
        "at_0.5": metrics(yt, pt, 0.5),
        "at_chosen_threshold": metrics(yt, pt, thr),
        "val_target_precision_reached": reached,
        "note": "Threshold was frozen from val before test was evaluated.",
    }
    (run_dir / "report.json").write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))

    m = report["at_chosen_threshold"]
    cm = np.array([[m["tn"], m["fp"]], [m["fn"], m["tp"]]])
    fig, ax = plt.subplots(figsize=(4, 4))
    ax.imshow(cm, cmap="Blues")
    for (i, j), v in np.ndenumerate(cm):
        ax.text(j, i, str(v), ha="center", va="center")
    ax.set_xticks([0, 1], ["pred real", "pred ai"])
    ax.set_yticks([0, 1], ["true real", "true ai"])
    ax.set_title(f"reels/test @ thr {thr:.2f}")
    fig.tight_layout()
    fig.savefig(run_dir / "confusion_matrix.png", dpi=150)


if __name__ == "__main__":
    main()

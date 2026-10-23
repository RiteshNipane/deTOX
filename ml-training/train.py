"""Fine-tune MobileNetV3 on real-vs-AI frames.

    python train.py --data data --arch small --epochs 12 --out runs/exp1

Best checkpoint is chosen by AUC on the *reels* validation split (the real target
distribution). If no reels split exists it falls back to the mean AUC of all sources.
"""
import argparse
import csv
import json
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
from sklearn.metrics import roc_auc_score
from torch.utils.data import DataLoader, WeightedRandomSampler
from torch.utils.tensorboard import SummaryWriter

from slopnet.data import REEL_SOURCE, FrameDataset, eval_transform, list_samples, train_transform
from slopnet.model import INPUT_SIZE, SlopNet


@torch.no_grad()
def predict(model, loader, device):
    model.eval()
    probs, labels = [], []
    for x, y in loader:
        p = torch.softmax(model(x.to(device)), dim=1)[:, 1]
        probs.append(p.cpu().numpy())
        labels.append(y.numpy())
    return np.concatenate(probs), np.concatenate(labels)


def safe_auc(y, p):
    return float(roc_auc_score(y, p)) if len(set(y.tolist())) == 2 else float("nan")


def evaluate_by_source(model, val_samples, args, device):
    res = {}
    for src in sorted({s[2] for s in val_samples}):
        subset = [s for s in val_samples if s[2] == src]
        loader = DataLoader(FrameDataset(subset, eval_transform(INPUT_SIZE)), batch_size=args.bs, num_workers=args.workers)
        p, y = predict(model, loader, device)
        res[src] = {"n": len(y), "acc": float(((p >= 0.5) == y).mean()), "auc": safe_auc(y, p)}
    return res


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default="data")
    ap.add_argument("--out", default="runs/exp1")
    ap.add_argument("--arch", default="small", choices=["small", "large"])
    ap.add_argument("--epochs", type=int, default=12)
    ap.add_argument("--bs", type=int, default=64)
    ap.add_argument("--lr", type=float, default=3e-4)
    ap.add_argument("--reel-weight", type=float, default=4.0, help="sampling weight of reel screenshots vs public data")
    ap.add_argument("--workers", type=int, default=4)
    ap.add_argument("--seed", type=int, default=0)
    ap.add_argument("--no-pretrained", action="store_true")
    args = ap.parse_args()

    torch.manual_seed(args.seed)
    device = "cuda" if torch.cuda.is_available() else "cpu"
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    (out / "args.json").write_text(json.dumps(vars(args), indent=2))

    train_s = list_samples(args.data, "train")
    val_s = list_samples(args.data, "val")
    if not train_s or not val_s:
        raise SystemExit("empty train or val split; run prepare_data.py first")

    weights = [args.reel_weight if s[2] == REEL_SOURCE else 1.0 for s in train_s]
    sampler = WeightedRandomSampler(weights, num_samples=len(train_s), replacement=True)
    loader = DataLoader(FrameDataset(train_s, train_transform(INPUT_SIZE)), batch_size=args.bs,
                        sampler=sampler, num_workers=args.workers, drop_last=True)

    model = SlopNet(args.arch, pretrained=not args.no_pretrained).to(device)
    opt = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=1e-4)
    sched = torch.optim.lr_scheduler.CosineAnnealingLR(opt, T_max=args.epochs)
    loss_fn = nn.CrossEntropyLoss()
    tb = SummaryWriter(str(out / "tb"))

    best = -1.0
    with open(out / "log.csv", "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["epoch", "train_loss", "select_metric", "val_by_source"])
        for epoch in range(1, args.epochs + 1):
            model.train()
            total, n = 0.0, 0
            for x, y in loader:
                x, y = x.to(device), y.to(device)
                opt.zero_grad()
                loss = loss_fn(model(x), y)
                loss.backward()
                opt.step()
                total += loss.item() * len(y)
                n += len(y)
            sched.step()

            val = evaluate_by_source(model, val_s, args, device)
            if REEL_SOURCE in val and not np.isnan(val[REEL_SOURCE]["auc"]):
                metric = val[REEL_SOURCE]["auc"]
            else:
                aucs = [v["auc"] for v in val.values() if not np.isnan(v["auc"])]
                metric = float(np.mean(aucs)) if aucs else 0.0

            print(f"epoch {epoch:02d} loss {total / max(n, 1):.4f} select {metric:.4f} {val}")
            w.writerow([epoch, total / max(n, 1), metric, json.dumps(val)])
            f.flush()
            tb.add_scalar("train/loss", total / max(n, 1), epoch)
            tb.add_scalar("val/select_auc", metric, epoch)

            ckpt = {"model": model.state_dict(), "arch": args.arch, "epoch": epoch, "metric": metric}
            torch.save(ckpt, out / "last.pt")
            if metric > best:
                best = metric
                torch.save(ckpt, out / "best.pt")
    print(f"best select metric {best:.4f} -> {out / 'best.pt'}")


if __name__ == "__main__":
    main()

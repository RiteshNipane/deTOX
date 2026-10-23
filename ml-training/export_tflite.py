"""PyTorch -> fp32 TFLite (ai-edge-torch) -> int8 (AI Edge Quantizer) + parity check.

    python export_tflite.py --ckpt runs/exp1/best.pt --data data --out export

Quantization recipe: static_wi8_ai8 (int8 weights + activations, calibrated on real frames).
Graph inputs/outputs stay float32 by default, so the Android side feeds [0,1] floats and
reads probabilities, with no input-scale bookkeeping.

Outputs in --out:
    slop_fp32.tflite, slop_int8.tflite, slop_meta.json
Copy slop_int8.tflite (as slop_classifier.tflite) and slop_meta.json into app/src/main/assets/.

NOTE: ai-edge-torch / ai-edge-quantizer APIs move quickly. If a call fails, check the
current docs; the structure (export fp32, then quantize with calibration) stays the same.
"""
import argparse
import json
import random
from pathlib import Path

import numpy as np
import torch
from PIL import Image

from slopnet.data import REEL_SOURCE, list_samples
from slopnet.model import INPUT_SIZE, load_checkpoint


class ExportWrapper(torch.nn.Module):
    """[0,1] RGB NCHW in -> probabilities (N, 2) out; index 1 = AI."""

    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, x):
        return torch.softmax(self.model(x), dim=1)


def load_image(path):
    img = Image.open(path).convert("RGB").resize((INPUT_SIZE, INPUT_SIZE))
    return np.asarray(img, dtype=np.float32) / 255.0  # HWC


def to_layout(hwc, shape):
    """Match the TFLite input shape: (1,3,H,W) NCHW or (1,H,W,3) NHWC."""
    if shape[1] == 3:
        return np.transpose(hwc, (2, 0, 1))[None].astype(np.float32)
    return hwc[None].astype(np.float32)


def calibration_samples(data, n):
    samples = list_samples(data, "val") + list_samples(data, "train")
    reels = [s for s in samples if s[2] == REEL_SOURCE]
    other = [s for s in samples if s[2] != REEL_SOURCE]
    random.Random(0).shuffle(reels)
    random.Random(0).shuffle(other)
    # Prefer reel frames: calibration ranges should match deployment data.
    return (reels + other)[:n]


def run_tflite(path, arrays):
    from ai_edge_litert.interpreter import Interpreter

    it = Interpreter(model_path=str(path))
    it.allocate_tensors()
    inp, out = it.get_input_details()[0], it.get_output_details()[0]
    res = []
    for hwc in arrays:
        it.set_tensor(inp["index"], to_layout(hwc, inp["shape"]))
        it.invoke()
        res.append(it.get_tensor(out["index"])[0].copy())
    return np.array(res)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ckpt", default="runs/exp1/best.pt")
    ap.add_argument("--data", default="data")
    ap.add_argument("--out", default="export")
    ap.add_argument("--calib", type=int, default=200)
    ap.add_argument("--threshold-file", default=None, help="threshold.json from evaluate.py (default: next to ckpt)")
    args = ap.parse_args()

    import ai_edge_torch
    from ai_edge_litert.interpreter import Interpreter
    from ai_edge_quantizer import quantizer

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    fp32, int8 = out / "slop_fp32.tflite", out / "slop_int8.tflite"

    model = ExportWrapper(load_checkpoint(args.ckpt, "cpu")).eval()
    sample = (torch.rand(1, 3, INPUT_SIZE, INPUT_SIZE),)
    ai_edge_torch.convert(model, sample).export(str(fp32))
    print("wrote", fp32)

    # Discover the signature + input name instead of guessing them.
    it = Interpreter(model_path=str(fp32))
    sig_key, sig = next(iter(it.get_signature_list().items()))
    input_name = sig["inputs"][0]
    in_shape = it.get_input_details()[0]["shape"]

    calib = calibration_samples(args.data, args.calib)
    arrays = [load_image(p) for p, _, _ in calib]
    calib_data = {sig_key: [{input_name: to_layout(a, in_shape)} for a in arrays]}

    qt = quantizer.Quantizer(str(fp32))
    qt.load_quantization_recipe("static_wi8_ai8")
    cal = qt.calibrate(calib_data)
    result = qt.quantize(calibration_result=cal)
    if hasattr(result, "export_model"):
        result.export_model(str(int8))
    else:
        int8.write_bytes(result.quantized_model)
    print("wrote", int8, f"({int8.stat().st_size / 1e6:.2f} MB)")

    # Parity: int8 must agree with fp32 on held-out frames, or the quantization hurt.
    val = list_samples(args.data, "val")
    random.Random(1).shuffle(val)
    val = val[:100]
    imgs = [load_image(p) for p, _, _ in val]
    labels = np.array([y for _, y, _ in val])
    p32 = run_tflite(fp32, imgs)[:, 1]
    p8 = run_tflite(int8, imgs)[:, 1]
    parity = {
        "n": len(val),
        "label_agreement_at_0.5": float(((p32 >= 0.5) == (p8 >= 0.5)).mean()),
        "max_abs_prob_diff": float(np.abs(p32 - p8).max()),
        "mean_abs_prob_diff": float(np.abs(p32 - p8).mean()),
        "acc_fp32": float(((p32 >= 0.5) == labels).mean()),
        "acc_int8": float(((p8 >= 0.5) == labels).mean()),
    }
    print(json.dumps(parity, indent=2))

    thr_path = Path(args.threshold_file) if args.threshold_file else Path(args.ckpt).parent / "threshold.json"
    threshold = json.loads(thr_path.read_text())["threshold"] if thr_path.exists() else 0.9
    meta = {"inputSize": INPUT_SIZE, "threshold": threshold, "classes": ["real", "ai"], "parity": parity}
    (out / "slop_meta.json").write_text(json.dumps(meta, indent=2))
    print(f"threshold {threshold:.3f}; now copy:\n  cp {int8} ../app/src/main/assets/slop_classifier.tflite\n  cp {out / 'slop_meta.json'} ../app/src/main/assets/slop_meta.json")


if __name__ == "__main__":
    main()

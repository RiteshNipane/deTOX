# deTOX classifier training (Phase 4)

Binary real-vs-AI classifier: MobileNetV3 -> int8 TFLite, run on-device by `SlopClassifier`.

**Exit criteria:** >= 85% accuracy on *your reel test set*, < 300 ms inference per frame on a phone.

> Status: the code here has been syntax-checked and `prepare_data.py` is tested (grouped split, no leakage).
> `train.py`, `evaluate.py` and `export_tflite.py` have **not been run end to end** yet (written without
> a GPU/network). Expect to fix small API issues on first run, especially in `export_tflite.py`, since
> `ai-edge-torch` / `ai-edge-quantizer` change often.

## The data matters more than the model
Public sets and reels are different distributions:

| Source | Problem for this use case |
|---|---|
| CIFAKE | 32x32 images. Upscaled, they look nothing like a 1080p reel frame. Useful for pretraining signal only. |
| GenImage | Higher resolution but clean PNG/JPEG, no platform re-encoding, no UI chrome. |
| **Your reel frames** | Exactly what the model sees at inference. Compressed, cropped, UI overlays included. |

So: use public sets with a cap (`prepare_data.py --cap`), up-weight reels during training
(`--reel-weight`), select checkpoints and pick the threshold on **reels only**, and report
only reel-test numbers.

## 1. Collect reel frames (in the app)
1. Settings > Developer > "Save training frames": set to `real`, then scroll ~15-20 minutes of reels you
   are confident are real. Switch to `ai`, scroll reels you are confident are AI-generated
   (labelled ones are a good source). Switch back to `off`.
2. Never mix classes in one session; the label is whatever the setting says.
3. Pull the frames:
   ```
   adb pull /sdcard/Android/data/com.detox/files/frames/real raw/reels/real
   adb pull /sdcard/Android/data/com.detox/files/frames/ai   raw/reels/ai
   ```
   Aim for 500-1000 frames per class, from many different reels and creators. Files are named
   `<reelHash>__<timestamp>.png`; frames of one reel stay in one split.
4. Frames are saved at the model input size (224x224, squashed) by the same code path used at
   inference. They include the status bar and any on-screen UI. Frames stay on the phone; check them
   before sharing anywhere (status bar can show notifications).

Public datasets go in `raw/<name>/{real,ai}/`, e.g. `raw/cifake/real`, `raw/genimage/ai`.

## 2. Prepare, train, evaluate
```
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

python prepare_data.py --raw raw --out data --cap 5000
python train.py --data data --arch small --epochs 12 --out runs/exp1
tensorboard --logdir runs
python evaluate.py --ckpt runs/exp1/best.pt --data data --target-precision 0.97
```
`evaluate.py` freezes the threshold on reels/val (highest recall at >= 97% precision), then reports
reels/test: confusion matrix, accuracy, precision, recall. Precision is what protects real content from
being blocked. Val/test splits are small at first; treat early numbers as noisy.

## 3. Export to int8 TFLite
Use a separate venv (ai-edge-torch pins its own torch/tensorflow):
```
pip install -r requirements-export.txt
python export_tflite.py --ckpt runs/exp1/best.pt --data data --out export
cp export/slop_int8.tflite ../app/src/main/assets/slop_classifier.tflite
cp export/slop_meta.json   ../app/src/main/assets/slop_meta.json
```
The script prints an fp32-vs-int8 parity check. If int8 agreement drops below ~97% or accuracy falls,
calibrate on more reel frames (`--calib 400`) or fall back to the `dynamic_wi8_afp32` recipe.

Model contract (must match `SlopClassifier`): input RGB floats in [0,1] (normalisation is inside the
model), output softmax over [real, ai]. Layout NCHW/NHWC is read from the model.

## 4. Benchmark on device
Enable deSLOP, open Reels, and read Logcat (tag `deTOX`): `ml p(ai)=0.123 in 41 ms`.
The exit criterion is p95 < 300 ms. In the app, `ML_MIN_FRAMES` in `ReelTrackerService` (default 1) can be
raised to 2 to require two agreeing frames, trading ~1.5 s of latency for fewer false positives.

"""Dataset helpers. Layout produced by prepare_data.py:

    data/<source>/<split>/<class>/<file>      split in {train,val,test}, class in {real,ai}

The source called "reels" is the self-collected screenshots from the Android app. It is
the one that matters: it is what the on-device model will actually see.
"""
import io
import random
from pathlib import Path

from PIL import Image, ImageFilter
from torch.utils.data import Dataset
from torchvision import transforms as T

CLASSES = ["real", "ai"]
IMG_EXT = {".png", ".jpg", ".jpeg", ".webp", ".bmp"}
REEL_SOURCE = "reels"


def list_samples(root: str, split: str, sources=None):
    """Returns [(path, label, source)] for one split."""
    out = []
    for src_dir in sorted(Path(root).iterdir()):
        if not src_dir.is_dir() or (sources and src_dir.name not in sources):
            continue
        for label, cls in enumerate(CLASSES):
            d = src_dir / split / cls
            if not d.is_dir():
                continue
            for p in sorted(d.iterdir()):
                if p.suffix.lower() in IMG_EXT:
                    out.append((str(p), label, src_dir.name))
    return out


class FrameDataset(Dataset):
    def __init__(self, samples, transform):
        self.samples = samples
        self.transform = transform

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, i):
        path, label, _ = self.samples[i]
        img = Image.open(path).convert("RGB")
        return self.transform(img), label


class RandomJPEG:
    """Re-encode as JPEG. Reel frames are heavily compressed; public datasets are not."""

    def __init__(self, p=0.7, quality=(30, 95)):
        self.p, self.quality = p, quality

    def __call__(self, img):
        if random.random() > self.p:
            return img
        buf = io.BytesIO()
        img.save(buf, format="JPEG", quality=random.randint(*self.quality))
        buf.seek(0)
        return Image.open(buf).convert("RGB")


class RandomBlur:
    def __init__(self, p=0.2, radius=(0.3, 1.2)):
        self.p, self.radius = p, radius

    def __call__(self, img):
        if random.random() > self.p:
            return img
        return img.filter(ImageFilter.GaussianBlur(random.uniform(*self.radius)))


def train_transform(size=224):
    return T.Compose([
        T.Resize((size, size)),  # squash, same as the Android frame grabber
        RandomJPEG(),
        RandomBlur(),
        T.RandomResizedCrop(size, scale=(0.75, 1.0), ratio=(0.9, 1.1)),
        T.ColorJitter(0.2, 0.2, 0.2, 0.02),
        T.ToTensor(),  # [0,1]; normalisation lives inside the model
    ])


def eval_transform(size=224):
    return T.Compose([T.Resize((size, size)), T.ToTensor()])

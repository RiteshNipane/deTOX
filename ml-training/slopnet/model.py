"""MobileNetV3 binary classifier (real=0, ai=1) with normalisation baked in.

The model takes RGB floats in [0, 1], shape (N, 3, H, W). ImageNet mean/std are applied
inside forward(), so the Android side only has to scale pixels to [0, 1].
"""
import torch
import torch.nn as nn
from torchvision import models

MEAN = (0.485, 0.456, 0.406)
STD = (0.229, 0.224, 0.225)
INPUT_SIZE = 224


class SlopNet(nn.Module):
    def __init__(self, arch: str = "small", pretrained: bool = True):
        super().__init__()
        if arch == "small":
            weights = models.MobileNet_V3_Small_Weights.IMAGENET1K_V1 if pretrained else None
            net = models.mobilenet_v3_small(weights=weights)
        elif arch == "large":
            weights = models.MobileNet_V3_Large_Weights.IMAGENET1K_V1 if pretrained else None
            net = models.mobilenet_v3_large(weights=weights)
        else:
            raise ValueError(f"unknown arch {arch!r}")
        in_features = net.classifier[3].in_features
        net.classifier[3] = nn.Linear(in_features, 2)
        self.net = net
        self.register_buffer("mean", torch.tensor(MEAN).view(1, 3, 1, 1))
        self.register_buffer("std", torch.tensor(STD).view(1, 3, 1, 1))

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.net((x - self.mean) / self.std)  # logits (N, 2)


def load_checkpoint(path: str, device: str = "cpu") -> SlopNet:
    ckpt = torch.load(path, map_location=device)
    model = SlopNet(arch=ckpt["arch"], pretrained=False)
    model.load_state_dict(ckpt["model"])
    return model.to(device).eval()

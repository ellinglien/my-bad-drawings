# python make-paper-photo.py <Paper003_1K-JPG_Color.jpg> <out.png>
# A paper photo for the import check: ambientCG's Paper003 (CC0; the app doesn't ship it) under a lamp's falloff,
# warm light and a little sensor noise.
import sys
import numpy as np
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB").resize((900, 900))
a = np.asarray(im).astype(np.float32) / 255
H, W = a.shape[:2]
y, x = np.mgrid[0:H, 0:W]; u = x / W - .3; v = y / H - .2
light = (.98 - .45 * np.sqrt(u * u + v * v))[..., None] * np.array([1, .97, .9])
out = np.clip(a * light * 1.05 + np.random.default_rng(3).normal(0, .01, a.shape), 0, 1)
Image.fromarray((out * 255).astype(np.uint8)).crop((0, 100, 900, 800)).save(sys.argv[2])

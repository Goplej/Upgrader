#!/usr/bin/env python3
"""Generate the pixel-art icons for the Upgrader mod (pure stdlib, no PIL).

Outputs:
  src/main/resources/assets/upgrader/textures/item/upgrader.png  (16x16 item texture)
  src/main/resources/assets/upgrader/icon.png                    (128x128 mod icon)
"""
import os
import struct
import zlib

# '.'=transparent  O=outline  A/B/C=arrow greens  G/H=metal light/mid
ARROW = [
    "................",
    ".......OO.......",
    "......OAABO.....",
    ".....OAABBCO....",
    "....OAABBBCCO...",
    "......OABCO.....",
    "......OABCO.....",
    "......OABCO.....",
    "......OABCO.....",
    "......OABCO.....",
    "......OABCO.....",
    "...OOOOOOOOOO...",
    "..OGGGGGGGGGGO..",
    "..OGHHHHHHHHGO..",
    "...OOOOOOOOOO...",
    "................",
]

COLORS = {
    '.': (0, 0, 0, 0),
    'O': (16, 16, 24, 255),
    'A': (124, 232, 107, 255),
    'B': (63, 168, 76, 255),
    'C': (46, 122, 56, 255),
    'G': (168, 173, 184, 255),
    'H': (110, 116, 128, 255),
}

for row in ARROW:
    assert len(row) == 16, row


def write_png(path, w, h, pixel):
    raw = bytearray()
    for y in range(h):
        raw.append(0)  # filter: none
        for x in range(w):
            raw += struct.pack('4B', *pixel(x, y))

    def chunk(tag, data):
        body = tag + data
        return struct.pack('>I', len(data)) + body + struct.pack('>I', zlib.crc32(body))

    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(bytes(raw), 9))
    png += chunk(b'IEND', b'')

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(png)
    print('wrote', path, f'{w}x{h}')


def sample(x, y, scale):
    ch = ARROW[y // scale][x // scale]
    return COLORS.get(ch, (0, 0, 0, 0))


ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')

write_png(os.path.join(ROOT, 'src/main/resources/assets/upgrader/textures/item/upgrader.png'),
          16, 16, lambda x, y: sample(x, y, 1))
write_png(os.path.join(ROOT, 'src/main/resources/assets/upgrader/icon.png'),
          128, 128, lambda x, y: sample(x, y, 8))

# -*- coding: utf-8 -*-
"""ArchWeaver 界面字形精灵生成脚本。

把下面 GLYPHS 里的 ASCII 点阵画成 PNG，直接写进 mod 的 assets 目录。

产物是 GUI 精灵，用法和原版空槽图标（container/slot/shield 等）完全一致：
  - 放在 textures/gui/sprites/ 下，会被 gui 图集自动收进来，用 blitSprite 按 16x16 画，
    所以这里按 1:1 出图，不做放大。
  - 交给 Slot#getNoItemIcon() 返回，原版会在两层槽位高亮之间绘制它：
      高亮前层 container/slot_highlight_back   白色 a=96/255
      空槽图标（本脚本的产物）
      高亮后层 container/slot_highlight_front  白色 a=32/255
    所以鼠标悬停时图标只吃到后层那点白，555555 -> 6a6a6a，和原版空槽图标一模一样。
    自己 blit 到背景层的话会连前层一起吃掉，变成 a2a2a2。

改字形只需改对应点阵，重跑本脚本：
    python tools/generate-glyph-sprites.py
换颜色（RRGGBB，默认 555555，与原版空槽图标一致）：
    python tools/generate-glyph-sprites.py FFFFFF
"""

import os
import sys

DEFAULT_COLOR = "555555"          # 默认颜色，与原版 container/slot/* 空槽图标一致
FILL = "#"                        # 点阵里表示实心像素的字符，其余字符一律透明
MOD_ID = "archweaver"
SPRITE_DIR = "container/slot"     # 精灵 ID 前缀：产物写在 textures/gui/sprites/<这里>/ 下
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
OUT_DIR = os.path.join(ROOT, "common", "src", "main", "resources", "assets", MOD_ID,
                       "textures", "gui", "sprites", *SPRITE_DIR.split("/"))

# 字形点阵。每行等长，边长即精灵边长；四周留一圈空行的话，字就是内缩居中的。
GLYPHS = {
    # 玩偶主手槽位中央的“主”字，空槽时显示。
    "main_hand": (
        "................",
        "......##........",
        "......#.#.......",
        ".......##.......",
        "...##########...",
        "...#........#...",
        "....###..###....",
        "......#..#......",
        "....###..###....",
        "....#......#....",
        "....###..###....",
        "......#..#......",
        "...####..####...",
        "..#..........#..",
        "..############..",
        "................",
    ),
}


def write_glyph(slug, rows, color):
    """把一个点阵写成精灵 PNG，返回 (路径, 宽, 高)。"""
    from PIL import Image

    height = len(rows)
    width = len(rows[0])
    if any(len(row) != width for row in rows):
        raise SystemExit("字形 %s 的每行长度必须一致，当前为 %s" % (slug, [len(r) for r in rows]))
    if len(color) != 6:
        raise SystemExit("颜色必须是 6 位 RRGGBB，当前为 %s" % color)

    rgb = tuple(int(color[i:i + 2], 16) for i in (0, 2, 4))
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == FILL:
                px[x, y] = rgb + (255,)

    path = os.path.join(OUT_DIR, slug + ".png")
    img.save(path)
    return path, width, height


def main():
    color = (sys.argv[1] if len(sys.argv) > 1 else DEFAULT_COLOR).lstrip("#").upper()
    os.makedirs(OUT_DIR, exist_ok=True)
    for slug, rows in GLYPHS.items():
        path, width, height = write_glyph(slug, rows, color)
        print("ok %s:%s/%s  %dx%d  #%s" % (MOD_ID, SPRITE_DIR, slug, width, height, color))
        print("   ->", path)


if __name__ == "__main__":
    main()

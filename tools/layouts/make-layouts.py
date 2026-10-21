#!/usr/bin/env python3
"""Writes tools/layouts/layouts.txt (RCON setblock commands for the layout candidates) and prints the view points.

Each variant stands in its own plot, 12 blocks apart along x; back row at z=0, the player faces it from the south.
The altar does not exist yet: respawn_anchor stands in for it.
"""
import math
from pathlib import Path

G = -61          # top layer of the flat world (grass); floors replace it
Y = -60          # first layer above the ground
ALTAR = 'minecraft:respawn_anchor'
SKULL = 'minecraft:wither_skeleton_skull[rotation=8]'
cmds = ['time set noon', 'weather clear', 'forceload add -10 -20 60 20',
        'fill -10 -60 -20 60 -50 20 minecraft:air', f'fill -10 {G} -20 60 {G} 20 minecraft:grass_block']
views = []


def put(x, y, z, block):
    cmds.append(f'setblock {x} {y} {z} {block}')


def floor(x0, z0, w, d, block='minecraft:polished_blackstone_bricks'):
    cmds.append(f'fill {x0} {G} {z0} {x0 + w - 1} {G} {z0 + d - 1} {block}')


def look(px, py, pz, cx, cy, cz):
    dx, dy, dz = cx - px, cy - (py + 1.62), cz - pz
    yaw = math.degrees(math.atan2(-dx, dz))
    pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
    if py > Y:
        put(math.floor(px), py - 1, math.floor(pz), 'minecraft:barrier')
    views.append('%.1f,%d,%.1f,%.0f,%.0f' % (px, py, pz, yaw, pitch))


def two_views(x0, d):
    cx, cz = x0 + 1.5, d / 2
    look(cx - 1.6, Y, cz + 3.4, cx, Y + 0.7, cz)       # front left, eye height
    look(cx + 2.4, Y + 2, cz + 3.0, cx, Y + 0.4, cz)   # front right, from above


# A - 3x3 court: floor, two walls with skulls at the back, cauldron | altar | lectern in the middle row
x = 0
floor(x, 0, 3, 3)
for dx in (0, 2):
    put(x + dx, Y, 0, 'minecraft:polished_blackstone_wall')
    put(x + dx, Y + 1, 0, SKULL)
put(x + 1, Y, 0, 'minecraft:soul_lantern')
put(x, Y, 1, 'minecraft:cauldron')
put(x + 1, Y, 1, ALTAR)
put(x + 2, Y, 1, 'minecraft:lectern[facing=south]')
two_views(x, 3)

# B - 3x2 altar table: wall+skull | raised altar | wall+skull, in front cauldron | step | lectern
x = 12
for dx in (0, 2):
    put(x + dx, Y, 0, 'minecraft:polished_blackstone_wall')
    put(x + dx, Y + 1, 0, SKULL)
put(x + 1, Y, 0, 'minecraft:chiseled_polished_blackstone')
put(x + 1, Y + 1, 0, ALTAR)
put(x, Y, 1, 'minecraft:cauldron')
put(x + 1, Y, 1, 'minecraft:polished_blackstone_brick_stairs[facing=north]')
put(x + 2, Y, 1, 'minecraft:lectern[facing=south]')
two_views(x, 2)

# C - 3x2 back wall: two blocks high, wither skulls hung on its outer blocks, in front cauldron | altar | lectern
x = 24
cmds.append(f'fill {x} {Y} 0 {x + 2} {Y} 0 minecraft:polished_blackstone_bricks')
cmds.append(f'fill {x} {Y + 1} 0 {x + 2} {Y + 1} 0 minecraft:chiseled_polished_blackstone')
put(x + 1, Y + 2, 0, 'minecraft:soul_lantern')
put(x, Y, 1, 'minecraft:cauldron')
put(x + 1, Y, 1, ALTAR)
put(x + 2, Y, 1, 'minecraft:lectern[facing=south]')
for dx in (0, 2):
    put(x + dx, Y + 1, 1, 'minecraft:wither_skeleton_wall_skull[facing=south]')
two_views(x, 2)

# D - 3x3 corners: floor, skulls on two diagonal corner pillars, soul lanterns in the other two, altar in the centre
x = 36
floor(x, 0, 3, 3)
for dx, dz in ((0, 0), (2, 2)):
    put(x + dx, Y, dz, 'minecraft:polished_blackstone_wall')
    put(x + dx, Y + 1, dz, SKULL)
for dx, dz in ((2, 0), (0, 2)):
    put(x + dx, Y, dz, 'minecraft:soul_lantern')
put(x, Y, 1, 'minecraft:cauldron')
put(x + 1, Y, 1, ALTAR)
put(x + 2, Y, 1, 'minecraft:lectern[facing=south]')
two_views(x, 3)

Path(__file__).with_name('layouts.txt').write_text('\n'.join(cmds) + '\n')
print(';'.join(views))

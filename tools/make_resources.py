#!/usr/bin/env python3
"""The mod's JSON resources: python3 tools/make_resources.py

Block states, block and item models, loot tables, recipes with their unlocks, tags, the particle and the default
sacrifice values. The language files are written by hand (src/main/resources/assets/skullsanctum/lang).
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / 'src/main/resources'
NS = 'skullsanctum'
ROT = {'north': 0, 'east': 90, 'south': 180, 'west': 270}


def write(path, data):
    file = RES / path
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def tex(name):
    return f'{NS}:block/{name}'


# ---------------------------------------------------------------- sacrificial altar: an enchanting table's size

def altar_model(suffix):
    """The size of an enchanting table: one element, 12 pixels high; the sides take the texture's rows 4 to 16."""
    side, top = tex('sacrificial_altar_side' + suffix), tex('sacrificial_altar_top' + suffix)
    return {
        'parent': 'minecraft:block/block',
        'textures': {'particle': side, 'side': side, 'top': top, 'bottom': tex('sacrificial_altar_bottom')},
        'elements': [{'from': [0, 0, 0], 'to': [16, 12, 16], 'faces': {
            'north': {'texture': '#side', 'cullface': 'north'}, 'east': {'texture': '#side', 'cullface': 'east'},
            'south': {'texture': '#side', 'cullface': 'south'}, 'west': {'texture': '#side', 'cullface': 'west'},
            'up': {'texture': '#top'}, 'down': {'texture': '#bottom', 'cullface': 'down'}}}],
    }


write(f'assets/{NS}/models/block/sacrificial_altar.json', altar_model(''))
write(f'assets/{NS}/models/block/sacrificial_altar_formed.json', altar_model('_formed'))
write(f'assets/{NS}/blockstates/sacrificial_altar.json', {'variants': {
    f'facing={f},formed={str(formed).lower()}': {
        'model': f'{NS}:block/sacrificial_altar' + ('_formed' if formed else ''), **({'y': r} if r else {})}
    for f, r in ROT.items() for formed in (False, True)}})
write(f'assets/{NS}/items/sacrificial_altar.json', {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/sacrificial_altar'}})

# ---------------------------------------------------------------- soul tank: the cauldron, the souls, the flame

write(f'assets/{NS}/models/block/soul_tank.json', {
    'parent': 'minecraft:block/cauldron',
    'textures': {'particle': tex('soul_tank_side'), 'side': tex('soul_tank_side'), 'top': tex('soul_tank_top'),
                 'bottom': tex('soul_tank_bottom'), 'inside': tex('soul_tank_inner')}})
CONTENT = {1: 6, 2: 9, 3: 12, 4: 15}
for level, height in CONTENT.items():
    write(f'assets/{NS}/models/block/soul_tank_content{level}.json', {
        'textures': {'particle': tex('soul_essence'), 'content': tex('soul_essence')},
        'elements': [{'from': [2, height, 2], 'to': [14, height, 14], 'light_emission': 15, 'shade': False,
                      'faces': {'up': {'texture': '#content'}}}]})
flame = []
for angle in (45, -45):
    flame.append({'from': [0, 9, 8], 'to': [16, 25, 8], 'shade': False, 'light_emission': 15,
                  'rotation': {'origin': [8, 8, 8], 'axis': 'y', 'angle': angle, 'rescale': True},
                  'faces': {'north': {'uv': [0, 0, 16, 16], 'texture': '#fire'},
                            'south': {'uv': [0, 0, 16, 16], 'texture': '#fire'}}})
write(f'assets/{NS}/models/block/soul_tank_flame.json', {
    'ambientocclusion': False, 'textures': {'particle': tex('soul_tank_flame'), 'fire': tex('soul_tank_flame')},
    'elements': flame})
write(f'assets/{NS}/blockstates/soul_tank.json', {'multipart': [
    {'apply': {'model': f'{NS}:block/soul_tank'}},
    *[{'when': {'level': str(level)}, 'apply': {'model': f'{NS}:block/soul_tank_content{level}'}} for level in CONTENT],
    {'when': {'burning': 'true'}, 'apply': {'model': f'{NS}:block/soul_tank_flame'}},
]})

# ---------------------------------------------------------------- Head Atlas: the enchanting table's shape, the atlas's textures

write(f'assets/{NS}/models/block/head_atlas.json', {
    'parent': 'minecraft:block/enchanting_table',
    'textures': {'particle': tex('head_atlas_bottom'), 'top': tex('head_atlas_top'), 'side': tex('head_atlas_side'),
                 'bottom': tex('head_atlas_bottom')}})
write(f'assets/{NS}/blockstates/head_atlas.json', {'variants': {'': {'model': f'{NS}:block/head_atlas'}}})
write(f'data/{NS}/loot_table/blocks/head_atlas.json', {
    'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/head_atlas',
    'pools': [{'rolls': 1, 'bonus_rolls': 0, 'condition': {'type': 'minecraft:survives_explosion'},
               'entries': [{'type': 'minecraft:item', 'name': 'minecraft:enchanting_table'}]}]})

# ---------------------------------------------------------------- items, particle

for name in ('soul', 'guide_book'):
    write(f'assets/{NS}/models/item/{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{name}'}})
    write(f'assets/{NS}/items/{name}.json', {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{name}'}})
write(f'assets/{NS}/particles/soul_flame.json', {'textures': [f'{NS}:soul_flame']})
write(f'assets/{NS}/particles/soul_bubble.json', {'textures': [f'{NS}:soul_bubble_{i}' for i in range(5)]})

# ---------------------------------------------------------------- loot, tags

write(f'data/{NS}/loot_table/blocks/sacrificial_altar.json', {
    'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/sacrificial_altar',
    'pools': [{'rolls': 1, 'bonus_rolls': 0, 'condition': {'type': 'minecraft:survives_explosion'},
               'entries': [{'type': 'minecraft:item', 'name': f'{NS}:sacrificial_altar'}]}]})
write(f'data/{NS}/loot_table/blocks/soul_tank.json', {
    'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/soul_tank',
    'pools': [{'rolls': 1, 'bonus_rolls': 0, 'condition': {'type': 'minecraft:survives_explosion'},
               'entries': [{'type': 'minecraft:item', 'name': 'minecraft:cauldron'}]}]})
write('data/minecraft/tags/block/mineable/pickaxe.json', {'values': [f'{NS}:sacrificial_altar', f'{NS}:soul_tank', f'{NS}:head_atlas']})

# ---------------------------------------------------------------- recipes and their unlocks


def unlock(recipe, items):
    criteria = {'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipes': f'{NS}:{recipe}'}}}
    for item in items:
        criteria[f'has_{item.split(":")[1]}'] = {'trigger': 'minecraft:inventory_changed',
                                                 'conditions': {'items': [{'items': item}]}}
    write(f'data/{NS}/advancement/recipes/misc/{recipe}.json', {
        'parent': 'minecraft:recipes/root', 'criteria': criteria,
        'requirements': [list(criteria)], 'rewards': {'recipes': [f'{NS}:{recipe}']}})


write(f'data/{NS}/recipe/sacrificial_altar.json', {
    'type': 'minecraft:crafting_shaped', 'category': 'misc',
    'pattern': ['GOG', 'CSC', 'BBB'],
    'key': {'G': 'minecraft:gold_ingot', 'O': 'minecraft:crying_obsidian', 'C': 'minecraft:chiseled_polished_blackstone',
            'S': 'minecraft:soul_soil', 'B': 'minecraft:polished_blackstone_bricks'},
    'result': {'id': f'{NS}:sacrificial_altar'}})
unlock('sacrificial_altar', ['minecraft:crying_obsidian', 'minecraft:chiseled_polished_blackstone'])
write(f'data/{NS}/recipe/guide_book.json', {
    'type': 'minecraft:crafting_shapeless', 'category': 'misc',
    'ingredients': ['minecraft:book', 'minecraft:wither_skeleton_skull'], 'result': {'id': f'{NS}:guide_book'}})
unlock('guide_book', ['minecraft:book', 'minecraft:wither_skeleton_skull'])

# ---------------------------------------------------------------- what a sacrifice is worth, in souls per item

VALUES = {
    # stone, earth and the like: a stack gives about a soul
    '#minecraft:dirt': 0.015, '#minecraft:sand': 0.015, 'minecraft:gravel': 0.015,
    'minecraft:cobblestone': 0.015, 'minecraft:cobbled_deepslate': 0.015, 'minecraft:stone': 0.02,
    'minecraft:deepslate': 0.02, 'minecraft:netherrack': 0.01, 'minecraft:blackstone': 0.02,
    'minecraft:andesite': 0.015, 'minecraft:diorite': 0.015, 'minecraft:granite': 0.015, 'minecraft:tuff': 0.015,
    'minecraft:end_stone': 0.04, 'minecraft:basalt': 0.02, 'minecraft:soul_sand': 0.05, 'minecraft:soul_soil': 0.05,
    # wood and plants
    '#minecraft:logs': 0.06, '#minecraft:planks': 0.015, '#minecraft:saplings': 0.02, '#minecraft:leaves': 0.005,
    '#minecraft:flowers': 0.02, 'minecraft:wheat': 0.02, 'minecraft:sugar_cane': 0.01, 'minecraft:pumpkin': 0.05,
    'minecraft:melon': 0.05,
    # mob drops
    'minecraft:rotten_flesh': 0.05, 'minecraft:bone': 0.15, 'minecraft:bone_block': 0.45, 'minecraft:string': 0.05,
    'minecraft:spider_eye': 0.1, 'minecraft:gunpowder': 0.15, 'minecraft:ender_pearl': 1, 'minecraft:blaze_rod': 1,
    'minecraft:ghast_tear': 3, 'minecraft:phantom_membrane': 0.5, 'minecraft:slime_ball': 0.2,
    'minecraft:magma_cream': 0.4, 'minecraft:shulker_shell': 4, 'minecraft:echo_shard': 4, 'minecraft:breeze_rod': 1,
    'minecraft:skeleton_skull': 4, 'minecraft:zombie_head': 4, 'minecraft:creeper_head': 6, 'minecraft:piglin_head': 6,
    'minecraft:wither_skeleton_skull': 20, 'minecraft:nether_star': 80, 'minecraft:dragon_head': 40,
    'minecraft:dragon_egg': 200, 'minecraft:totem_of_undying': 12, 'minecraft:heart_of_the_sea': 20,
    # ores and metals
    'minecraft:coal': 0.2, 'minecraft:coal_block': 1.8, 'minecraft:charcoal': 0.1,
    'minecraft:raw_copper': 0.12, 'minecraft:copper_ingot': 0.15, 'minecraft:copper_block': 1.35,
    'minecraft:raw_iron': 0.4, 'minecraft:iron_ingot': 0.5, 'minecraft:iron_block': 4.5, 'minecraft:iron_nugget': 0.05,
    'minecraft:raw_gold': 0.8, 'minecraft:gold_ingot': 1, 'minecraft:gold_block': 9, 'minecraft:gold_nugget': 0.1,
    'minecraft:redstone': 0.1, 'minecraft:redstone_block': 0.9, 'minecraft:lapis_lazuli': 0.25,
    'minecraft:lapis_block': 2.25, 'minecraft:quartz': 0.2, 'minecraft:quartz_block': 0.8,
    'minecraft:amethyst_shard': 0.3, 'minecraft:emerald': 3, 'minecraft:emerald_block': 27,
    'minecraft:diamond': 6, 'minecraft:diamond_block': 54, 'minecraft:ancient_debris': 10,
    'minecraft:netherite_scrap': 10, 'minecraft:netherite_ingot': 45, 'minecraft:netherite_block': 405,
    # what the sanctum is built of is not burned for profit
    'minecraft:polished_blackstone_bricks': 0.02, 'minecraft:chiseled_polished_blackstone': 0.02,
}
# every item named nowhere gives 'default' souls (a stack about one), rare ones their rarity's
write(f'data/{NS}/sacrifice_values/default.json', {'replace': False, 'default': 1 / 64, 'values': VALUES})
print('resources written')

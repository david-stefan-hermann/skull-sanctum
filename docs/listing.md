# Skull Sanctum

A head shop paid in souls, for Minecraft 26.3 (Fabric). Build a small sanctum around a **Sacrificial Altar**: a cauldron
and an enchanting table close to it are enough, the altar suggests a shrine of blackstone around them. Sacrifice blocks
and items at the altar and their **souls** fill the **Soul Tank**, the sanctum's cauldron. Then right-click the
**Head Atlas**, the sanctum's enchanting table, and buy heads with those souls: the classic MHF heads, any player's head,
and over 86,000 from the Heads Database, sorted into categories and searchable by name and tag. Everything is in its own
creative tab, *Skull Sanctum*.

![A formed sanctum: the altar, the bubbling soul tank and the Head Atlas in their blackstone shrine](https://raw.githubusercontent.com/david-stefan-hermann/skull-sanctum/26.3/docs/sanctum.png)

## Download

Get `skullsanctum-<version>.jar` from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/skull-sanctum) or the
[GitHub releases](https://github.com/david-stefan-hermann/skull-sanctum/releases) and put it into `mods/` on the client and the server, next to Fabric API. Requires Minecraft 26.3, Fabric Loader 0.19.5+ and Java 25.

## The guide book

The **Skull Sanctum Guide** is a book with a chapter for every block and every topic of the mod, with the recipes and
pictures, in English and German. Craft it from a book and a wither skeleton skull, or take it from the creative tab.
Right-click opens it, E or Escape closes it; the blocks' tabs are on the left, the topics' on the right.

This page follows the book chapter by chapter and adds the details that do not fit on its pages.

![The guide book open on the Sacrificial Altar chapter](https://raw.githubusercontent.com/david-stefan-hermann/skull-sanctum/26.3/docs/book.png)

## Getting Started

Skull Sanctum turns sacrifices into souls and souls into decorative heads.

1. Craft a Sacrificial Altar and set it down.
2. Put a cauldron and an enchanting table close to it (see The Sanctum), then right-click the altar.
3. Sacrifice blocks and items at the altar. Their souls fill the Soul Tank.
4. Right-click the Head Atlas (the enchanting table of the awake sanctum) and buy heads with your souls.

## Sacrificial Altar

The heart of the sanctum, as large as an enchanting table. Dark stone with gold-bound corners; its green specks glimmer
once a cauldron and an enchanting table stand close to it.

> *"Lay it down and let it go."*

**Crafting** – gold ingots and crying obsidian over chiseled polished blackstone and soul soil, on polished blackstone
bricks:

```
G O G      G = gold ingot, O = crying obsidian
C S C      C = chiseled polished blackstone, S = soul soil
B B B      B = polished blackstone bricks
```

**Use**

- Right-click the altar. When a cauldron and an enchanting table stand with it inside a 3×3×3 box, the sanctum
  awakens: the cauldron becomes the Soul Tank, the enchanting table the Head Atlas. If not, the altar shows a suggested
  shrine as glowing ghost blocks for a few seconds.
- Right-click the awakened altar: put offerings into its nine slots. The screen shows what they give, *Sacrifice* burns
  them. Closing the screen gives back what did not burn.
- Or throw items onto the altar or into the Soul Tank: they burn by themselves, also from droppers and water streams.
- The souls go into the tank, as far as it has room. Breaking the tank or the Head Atlas puts the sanctum to sleep
  again; breaking the altar does the same. Sneak and right-click the awake altar to see what its shrine still lacks.
- The altar faces the player who set it down; the suggested shrine is built around that direction.

## Soul Tank

The sanctum's cauldron, filled with bubbling souls. It holds 4096 souls (see Config) and burns with a green
flame after every sacrifice.

> *"They do not rest. They wait."*

**Crafting** – not crafted: the cauldron of the sanctum turns into it when the sanctum awakens. A cauldron with water in
it works as well; the water is gone when the sanctum wakes.

**Use**

- Right-click the tank: the slot in the middle of the cauldron shows its souls as a stack. Take from it, or put soul
  items into it (they go into the tank at once); shift-click moves a stack either way. Hover the cauldron for the
  exact count.
- Items thrown into the tank are sacrificed. Hoppers and pipes cannot reach the souls.
- A comparator next to the tank reads how full it is.
- Breaking the tank, or the sanctum, drops its souls and gives the cauldron back.

## Soul

The sanctum's currency. Souls can be kept in the tank or carried and stored in chests like any item. Sacrificed again, a
soul gives one soul.

> *"Light as a breath, heavy as a debt."*

**Crafting** – not crafted: the Sacrificial Altar makes souls from sacrifices.

**Use**

- The Head Atlas pays with the souls in the sanctum's tank first, then with the souls you carry.

## The Sanctum

The altar only needs a cauldron and an enchanting table that fit with it into a 3×3×3 box. Everything else is
decoration: the altar suggests a small shrine of blackstone and shows it as ghosts.

**Suggested shrine** – 5 polished blackstone bricks, 3 cracked polished blackstone bricks, 3 gilded blackstone, a
cauldron, a wither skeleton skull, an enchanting table, 2 polished blackstone brick walls, 2 polished blackstone brick
slabs and a polished blackstone brick stair.

**Layout**, seen from the front (the player stands there), the altar at the left of the middle row:

```
top       slab   stair  slab        upside down, over the cauldron    (back row only)
2         wall   .      wall                                          (back row only)
1         gilded cauldron bricks                                      (back)
          ALTAR  skull  table                                         (middle)
floor     bricks cracked bricks                                       (back)
          bricks cracked gilded                                       (middle)
          cracked gilded bricks                                       (front)
```

- When several cauldrons or tables stand in the box, the closest pair becomes the sanctum's.
- An awake sanctum checks every second that its three blocks still stand together; when one is gone it falls asleep:
  the tank turns back into a cauldron and drops its souls, the Head Atlas back into an enchanting table.
- A Head Atlas whose sanctum went without a word (a world editor, for example) turns back into an enchanting table when
  it is clicked.

## Sacrifices

Every item can be sacrificed. What it gives depends on how rare it is; the altar's screen shows the value before anything
burns.

- Stone, earth, wood and everything common give little: a stack is about a soul.
- Metals and gems give more, their blocks nine times as much.
- Mob trophies and treasures give the most.
- Rare items without a value of their own give souls by their rarity: 2 for uncommon, 5 for rare, 10 for epic.
- Fractions of a soul are not lost: the altar keeps them for the next sacrifice.

Some values of the built-in table (souls per item): cobblestone 0.015, a log 0.06, coal 0.2, iron ingot 0.5, gold ingot 1,
diamond 6, emerald 3, ender pearl 1, blaze rod 1, wither skeleton skull 20, nether star 80, dragon egg 200, netherite
ingot 45. See Data packs to change them.

## Buying Heads

The enchanting table of an awake sanctum becomes the Head Atlas. Right-click it to buy heads: the classic MHF heads, any
player's head, and over 86,000 more from the Heads Database, sorted into categories from alphabet to plants.

- Pick a category on the left or search by name and tag. The number beside a category is its price in souls. A search
  word matches the start of a word: *olive* finds Olive, Olive Pizza and the bucket of olives, but not Hololive; heads
  whose name matches come before those found by a tag only.
- Click a head to pick it, then *Buy*; while Shift is held the button reads *× 64* and buys a stack.
- The vial on the right shows the souls in the sanctum's tank; hover over it for the exact number.
- Right-click a head to keep it under Favourites. Favourites are saved with the world, per player.
- Type a player's name into the search: their head comes first, for 5 souls (see Config). The server looks
  the player up by name; a name nobody has is refused.
- Heads are paid with the souls in the tank first, then with the souls you carry.
- The browser closes when you walk more than 8 blocks from the altar or the sanctum falls asleep.
- The browser fetches the pages next to the shown one ahead and asks for their textures early, so turning pages is
  quick; pages already seen are kept while the browser is open.

The catalog comes with the mod: names, categories, tags and texture hashes, no API key needed. The texture images
themselves are not bundled: a head only names its texture on Mojang's texture server, and the game downloads it the
first time the head is shown and caches it on disk, as it does for every custom head. Player heads by name are
looked up by the server.

## Config

`config/skullsanctum.json`, read by the server; the client shows the server's tank capacity and player head price in the
guide book:

```json
{
  "tankCapacity": 4096,
  "defaultPrice": 2,
  "categoryPrices": {
    "classics": 2,
    "alphabet": 1,
    "animals": 3,
    "blocks": 2,
    "decoration": 3,
    "food-drinks": 2,
    "humans": 3,
    "humanoid": 3,
    "miscellaneous": 2,
    "monsters": 4,
    "plants": 2
  },
  "playerHeadPrice": 5,
  "burnTicks": 50
}
```

- `tankCapacity` – souls one soul tank holds (64 to 30000).
- `defaultPrice` – souls a head costs in a category missing from `categoryPrices`.
- `categoryPrices` – souls a head costs, by category.
- `playerHeadPrice` – souls the head of a player looked up by name costs.
- `burnTicks` – how long the soul tank burns after a sacrifice, in ticks.

## Data packs

What an item is worth comes from `data/<namespace>/sacrifice_values/*.json`; the mod's own table is
`data/skullsanctum/sacrifice_values/default.json`. A data pack adds or overrides values with a file of its own:

```json
{
  "replace": false,
  "default": 0.015625,
  "values": {
    "minecraft:diamond": 6,
    "#minecraft:logs": 0.1,
    "minecraft:bedrock": 0
  }
}
```

Files are read in name order, a later one overrides an earlier one, `"replace": true` drops what came before.
`"default"` is what a common item named nowhere gives. An item named directly wins over its tags; a value of 0 means the
altar does not take the item.

## Source

Source and issues: https://github.com/david-stefan-hermann/skull-sanctum

## Credits

- The custom heads are the [Heads Database](https://github.com/TheSilentPro/heads) by TheSilentPro, released under
  CC0 1.0. `tools/make-heads.py` turns it into `src/main/resources/skullsanctum/heads.tsv.gz`.
- The classics are Mojang's MHF heads.

## License

MIT, see [LICENSE](https://github.com/david-stefan-hermann/skull-sanctum/blob/26.3/LICENSE). By BaconCakeFactory.

# SeaPack

SeaPack is a Paper plugin that provides a searchable menu for custom resource-pack
items after ItemsAdder has been removed from the live server.

ItemsAdder is still used offline to build the resource pack. ForcePack can deliver
that pack to players, while SeaPack reads the ItemsAdder cache and current content
configuration to recreate and give the items.

## Requirements

- Paper 1.21.11
- Java 21
- A resource pack built from the same ItemsAdder files used by SeaPack
- ForcePack or another method of sending the resource pack to players

ItemsAdder is not a SeaPack runtime dependency and should not be needed on the live server.
SeaPack never calls the ItemsAdder API; `/sitems import` only copies source files.
SeaPack requires no manual configuration.

## Installation

1. Stop the server.
2. Place `SeaPack-1.0-SNAPSHOT.jar` in the server's `plugins` folder.
3. Start the server once. SeaPack creates `plugins/SeaPack` and copies this
   README into that folder.
4. Place the current ItemsAdder item cache in `plugins/SeaPack`:

   ```text
   plugins/SeaPack/items_ids_cache.yml
   ```

5. Copy the current ItemsAdder `contents` folder here:

   ```text
   plugins/SeaPack/contents/
   ```

6. Restart the server or run `/sitems reload`.

The completed layout should resemble:

```text
plugins/
|-- SeaPack.jar
`-- SeaPack/
    |-- README.md
    |-- customitems/
    |-- items_ids_cache.yml
    `-- contents/
        |-- general/
        |-- sets/
        |-- minecraft/
        `-- other ItemsAdder namespaces...
```

Do not create an extra nested folder such as:

```text
plugins/SeaPack/contents/contents/
```

The folders such as `sets`, `general`, and `minecraft` should be directly inside
`plugins/SeaPack/contents`.

No namespaces need to be listed. SeaPack discovers them from each YAML file's
`info.namespace` value, using the namespace folder name as a fallback.

Folders below `configs` define nested menu categories. For example:

```text
contents/general/configs/furniture/summer/chairs.yml
```

appears as `General > Furniture > Summer`. Every additional folder adds another
level. Items from YAML files directly under `general/configs` appear under a
`General Items` entry when `General` also contains child folders. YAML filenames
do not create categories. Directory and ZIP imports use the same rules. The
special `sets` namespace continues to use SeaPack's item-set grouping.

## Using The Menu

Open the category menu:

```text
/sitems
```

Search for an item:

```text
/sitems backpack
/sitems dragon sword
```

`/sitems` is the only registered SeaPack command.

Inside the menu:

- Click a category to open its child folders or items.
- Category depth follows folders below each namespace's `configs` directory.
- The `sets` category is divided into individual item sets.
- Left-click an item to receive one.
- Shift-click an item to receive its maximum stack size.
- Use the arrows to change pages.

## Custom Names, Lore, And Enchants

Run `/sitems reload` after installing the plugin and placing
the cache/contents files. SeaPack creates editable item files under:

```text
plugins/SeaPack/customitems/
```

Generated files contain only editable configuration data. The complete formats
and indentation are documented below.

The generated layout follows the full menu category path. Normal categories use
one file per item. Existing legacy files directly under the namespace folder stay
active and are never moved or replaced. The `sets` category uses one file per set
group:

```text
plugins/SeaPack/customitems/
|-- general/
|   |-- example_item.yml
|   `-- furniture/
|       `-- summer_chair.yml
|-- crates/
|   `-- crate_key.yml
`-- sets/
    |-- dragonsoul.yml
    `-- summer.yml
```

Normal item files look like:

```yaml
display-name: '<aqua>Example Item</aqua>'
lore:
- '<gray>Custom lore line</gray>'
enchants:
  unbreaking: 3
```

Set files look like:

```yaml
display-name-prefix:
  enabled: false
  format: '<b><gradient:#FF30FF:#46FFFF>CUSTOM {suffix}</gradient></b>'
global-lore:
  enabled: true
  lines:
  - '<gray>Dragonsoul {tool_type}</gray>'
  - '<dark_gray>Base material: {material}</dark_gray>'
enchant-sections:
  tools:
    enabled: false
    materials:
    - '*_PICKAXE'
    - '*_AXE'
    - '*_SHOVEL'
    enchants:
      efficiency: 5
items:
  dragonsoul_sword:
    name-suffix: Sword
    display-name: '<aqua>Dragonsoul Sword</aqua>'
    lore:
    - '<gray>Custom sword lore</gray>'
    enchants:
      sharpness: 5
  dragonsoul_pickaxe:
    name-suffix: Pickaxe
    display-name: '<aqua>Dragonsoul Pickaxe</aqua>'
    lore: []
    enchants:
      fortune: 3
      unbreaking: 3
```

You can edit:

- `display-name`
- `name-suffix` in set files
- `lore`
- `enchants`
- `display-name-prefix.enabled` and `display-name-prefix.format` in set files
- `global-lore.enabled` and `global-lore.lines` in set files
- `enchant-sections` in set files

Use MiniMessage in names and lore, for example `<aqua>Text</aqua>`,
`<gray>Line</gray>`, `<bold>Bold</bold>`, or `<gradient:#00ffff:#ffaa00>Text</gradient>`.
Old `&` color codes still work for existing configs. Enchants use
Bukkit/Minecraft enchantment keys such as `sharpness`, `unbreaking`,
`protection`, or quoted namespaced keys such as `'minecraft:fire_aspect'`.

Names, suffixes, item lore, and global lore support these per-item placeholders:

| Placeholder | Value |
|---|---|
| `{material}` | Readable Bukkit material, such as `Paper` or `Diamond Pickaxe` |
| `{material_key}` | Exact Bukkit material key, such as `PAPER` or `DIAMOND_PICKAXE` |
| `{tool_type}` | Readable item type, such as `Pickaxe`, `Sword`, `Furniture`, or `Plushie` |
| `{tool_type_key}` | Uppercase item type key, such as `PICKAXE` or `FURNITURE` |
| `{suffix}` | The set item's configured or generated name suffix |

For custom items whose Bukkit material is `PAPER`, SeaPack infers `{tool_type}`
from the item key and then its category. For example,
`dragonsoul_crystal_pickaxe` produces `{material}` as `Paper` and `{tool_type}`
as `Pickaxe`.

Do not edit item IDs, material, or CustomModelData in `customitems`. SeaPack does
not read those values from custom item files. It always gets the real item ID,
material, and CustomModelData from the current `items_ids_cache.yml`.

Set `display-name-prefix.enabled` to `true` to make every item in that set use
the same MiniMessage display name template. `{suffix}` is replaced with each
item's `name-suffix`:

```yaml
display-name-prefix:
  enabled: true
  format: '<b><gradient:#FF30FF:#46FFFF>CUSTOM {suffix}</gradient></b>'
items:
  dragonsoul_sword:
    name-suffix: Sword
```

With that example, the sword's display name becomes `CUSTOM Sword` using the
configured bold gradient. When `display-name-prefix.enabled` is `false`, each
item's `display-name` is used directly.

Set `global-lore.enabled` to `true` to append the configured lore lines to every
item in that set file. Placeholders are resolved separately for each item:

```yaml
global-lore:
  enabled: true
  lines:
  - '<gray>Type: <white>{tool_type}</white></gray>'
  - '<gray>Material: <white>{material}</white></gray>'
```

Set an enchant section's `enabled` value to `true` to apply its enchantments to
matching materials in that set. Material rules support exact materials,
wildcards, and built-in groups:

```yaml
materials:
- DIAMOND_PICKAXE
- '*_SHOVEL'
- AXES
- PICKAXES
- SHOVELS
```

Built-in groups are `PICKAXES`, `AXES`, `SHOVELS`, `HOES`, `SWORDS`, `HELMETS`,
`CHESTPLATES`, `LEGGINGS`, `BOOTS`, `ARMOR`, `TOOLS`, and `WEAPONS`.

SeaPack only creates missing custom item files and missing set item entries. If
you already customized an item, reload will read that config and will not
overwrite it. When new ItemsAdder items are added, replace the cache/contents,
run `/sitems reload`, and SeaPack adds only the new missing files or entries.

Older `customitems/sets/<set>/<item>.yml` files are not deleted automatically.
If those files contain an `id`, SeaPack can use them when creating the new
`customitems/sets/<set>.yml` file, but the set-level YAML is the active format.

## Reloading

After replacing the cache or contents folder, run:

```text
/sitems reload
```

Reload builds and validates a complete replacement registry before publishing it.
If validation fails or no current items are found, the previous working registry
remains active. Open SeaPack menus are closed after a successful reload so they
cannot give stale material or model-data mappings.

The command reports:

- Number of loaded items
- Number of armor definitions
- Number of scanned source folders
- Number of scanned ItemsAdder config files
- Number of discovered namespaces
- Number of custom item configs loaded and newly created
- Number of missing item entries added to existing set configs
- Number of placeable furniture definitions and newly generated entries

The armor definition count should be greater than zero when custom worn armor is
present. With the June 24, 2026 contents export, SeaPack should find about 110
config files and hundreds of armor mappings.

## Updating The Resource Pack

Whenever ItemsAdder content changes:

1. Build the new resource pack with ItemsAdder.
2. Update the pack used by ForcePack.
3. Replace `plugins/SeaPack/items_ids_cache.yml` with the newly generated cache.
4. Replace `plugins/SeaPack/contents` with the current ItemsAdder contents folder.
5. Run `/sitems reload` or restart the server.

The cache and contents folder must come from the same build as the resource pack.
Mixing versions can cause incorrect models or armor textures.

Do not delete `plugins/SeaPack/customitems` during normal updates. That folder
contains your custom display names, lore, and enchants.

### Importing From An Installed ItemsAdder

If ItemsAdder is installed in the same server's `plugins` folder, an operator can
import its current build files automatically:

```text
/sitems import
```

SeaPack searches the `plugins` folder for an `ItemsAdder` directory, copies
`ItemsAdder/contents`, and finds the item cache under `ItemsAdder/storage`. Both
`items_ids_cache.yml` and the singular `items_id_cache.yml` spelling are
recognized. The imported cache is stored under SeaPack's canonical
`items_ids_cache.yml` filename. SeaPack always prefers ItemsAdder's canonical
plural `items_ids_cache.yml`; the singular spelling is used only when the plural
file does not exist. The selected filename is reported when import finishes.

The copy runs asynchronously. SeaPack stages and validates the complete cache and
contents before replacing its previous files, so stale files from an older build
are removed. The previous import is retained under
`plugins/SeaPack/import-backup`. The import does not touch `customitems`,
`furniture.yml`, or any other SeaPack customization.

After the command finishes, run:

```text
/sitems reload
```

Import intentionally does not reload automatically, giving the administrator a
chance to confirm the copy completed first.

If an imported build has incorrect models, restore the previous import with:

```text
/sitems rollback
/sitems reload
```

SeaPack also recovers the retained backup automatically if the server stops in
the middle of replacing the active contents and cache.

## Item Definition Safety

When a current `contents` folder is present, SeaPack only lists cache IDs that
still have an enabled, non-template ItemsAdder item definition. Stale cache-only,
disabled, and template rows are excluded. Duplicate cache mappings are collapsed
to one deterministic item, preferring the material declared by the current
contents configuration.

Items using ItemsAdder's modern `graphics` or `item_model` format receive the
Minecraft `item_model` component in addition to their numeric CustomModelData.
Legacy resource items continue to use their material and CustomModelData.

Malformed `customitems` or `furniture.yml` files are logged and left byte-for-byte
untouched. Generated YAML updates are written through a temporary file and then
atomically replaced, reducing corruption risk during a server interruption.

## Placeable Furniture And Plushies

SeaPack automatically discovers ItemsAdder items that contain a
`behaviours.furniture` definition in the current `contents` folder. Source
definitions using `armor_stand`, `item_display`, or `item_frame` are all placed
as invisible armor stands by SeaPack. This also supports items that inherit the
furniture behaviour through `variant_of`.
No namespaces, item IDs, materials, or CustomModelData values need to be entered.

For armor-stand furniture such as plushies:

1. Get the item from `/sitems`.
2. Right-click an allowed floor, wall, or ceiling face while holding it.
3. SeaPack spawns a persistent invisible armor stand and puts the exact held item
   in its helmet slot.
4. Left-click the placed model to remove it and drop the stored item on the ground.

SeaPack does not inspect ItemsAdder's runtime state or use its API. Its only
ItemsAdder integration is reading or importing the generated source files.

When WorldGuard is installed, SeaPack checks WorldGuard at the furniture's stored
anchor only when a player attempts to place or break it. The check respects region
build rules, the `block-place` and `block-break` flags, region membership, and
WorldGuard bypasses. SeaPack does not scan regions or furniture in the background.
Without WorldGuard, furniture placement and breaking continue normally.

SeaPack imports the ItemsAdder hitbox dimensions and offsets and uses that same
rotated volume for view-ray break targeting and solid collision blocks. Models
whose visible geometry is offset from the invisible stand can therefore still be
broken by aiming at their configured hitbox. The fallback uses the player's
actual entity-interaction reach attribute.

SeaPack reads each furniture model's `display.head.rotation` and
`display.head.translation` and uses them as the initial `model-transform` values
in `furniture.yml`. Existing values are never overwritten. Placement uses the
YAML values, while the source transform is retained in memory so an edited value
replaces the model default instead of being applied twice.

Furniture survives server restarts because the armor stand is a persistent world
entity. SeaPack tags it with persistent data so it can be recognized after a
restart. SeaPack does not run a repeating background task and does not keep live
references to placed furniture.

SeaPack performs two bounded placement checks: an entity-and-light check after
two ticks and a light-only check after one second. They recover that placement
from an interrupted entity or block update and never repeat or scan unrelated furniture. The
light-only check requires the furniture to still exist, so breaking furniture
does not resurrect its light. Existing invisible light blocks are valid furniture
space, which also allows recovery from orphaned light blocks left by an
interrupted or older placement.

When the ItemsAdder furniture definition has `light_level`, SeaPack places an
invisible Minecraft light block in the model's space. That light block is removed
when the furniture is broken only if its level still matches the light SeaPack
created.

Running `/sitems reload` creates or updates:

```text
plugins/SeaPack/furniture.yml
```

Entries are generated from the current ItemsAdder contents and existing values
are never replaced. Newly discovered furniture gets a new entry, while existing
entries receive only newly supported missing keys. A generated entry resembles:

```yaml
items:
  'nogs_menagerie:nm_plushie_fairy_forest':
    enabled: true
    small: true
    gravity: false
    fixed-rotation: false
    rotation-snap: 45
    model-transform:
      rotation:
        x: 0.0
        y: 180.0
        z: 0.0
      translation:
        x: 0.0
        y: -29.75
        z: 7.0
    opposite-direction: false
    placeable-on:
      floor: false
      walls: true
      ceiling: false
    light-level: 0
    y-offset: 0.0
    drop-item: true
    entity: armor_stand
    solid: false
    hitbox:
      height: 1.0
      height-offset: 0.0
      length: 1.0
      length-offset: 0.0
      width: 1.0
      width-offset: 0.0
```

Use `y-offset` if a particular model sits slightly too high or low. Values are in
blocks, so try small changes such as `-0.1` or `0.1`. `light-level` accepts `0`
through `15`. Changes apply to future placements; furniture already in the world
is intentionally not rewritten on reload.

`placeable-on` follows the clicked block face. When `fixed-rotation` is `true`,
`rotation-snap` controls whether placement snaps to the nearest `45` or `90`
degrees. `model-transform` starts with the model's head rotation and translation;
those values can then be edited per item. Rotation uses degrees. Translation uses
the model's native units, where `16` is one block. `opposite-direction` remains a
boolean 180-degree shorthand. `solid: false` leaves the hitbox
non-colliding, while `solid: true` creates barrier blocks for the configured
hitbox. SeaPack stores their exact coordinates on the furniture entity and removes
only blocks that are still barriers when that furniture is broken. Hitbox
dimensions are clamped to ItemsAdder's 3-block limit and offsets to `-3` through
`3` to keep placement bounded.

## Armor Textures

`items_ids_cache.yml` only contains the material and custom model data used for
inventory and held-item models. It does not contain the information Minecraft
needs to render armor while it is worn.

SeaPack therefore scans the YAML files under `contents/*/configs` and applies the
current equipment asset or legacy armor color to the item. Normal inventory and
held-item textures continue to use the material and CustomModelData from
`items_ids_cache.yml`.

If the item has the correct inventory texture but appears as normal armor when
worn:

1. Confirm the current folder is at `plugins/SeaPack/contents`.
2. Confirm it contains namespace folders such as `sets`.
3. Run `/sitems reload`.
4. Check that the command reports more than `0 armor definitions`.
5. Confirm players are using the matching resource pack.

## Commands

| Command | Description |
| --- | --- |
| `/sitems` | Opens the item category menu |
| `/sitems <query>` | Searches for matching items |
| `/sitems reload` | Rediscovers and reloads the cache and contents |
| `/sitems import` | Imports `ItemsAdder/contents` and its generated item cache |
| `/sitems rollback` | Restores the contents and cache from before the last import |

## Permissions

| Permission | Description | Default |
| --- | --- | --- |
| `seapack.items.menu` | Open the menu, search, and receive items | Operators |
| `seapack.items.reload` | Reload SeaPack data | Operators |
| `seapack.items.import` | Import or roll back ItemsAdder source files | Operators |

Grant permissions with your permissions plugin if non-operators need access.
Placing and breaking SeaPack furniture require no SeaPack permission. When
WorldGuard is installed, those actions must also be allowed at the furniture's
anchor by the applicable WorldGuard region.

## Automatic Discovery

SeaPack automatically:

- Finds `items_ids_cache.yml` in `plugins/SeaPack`.
- Uses the newest `items_ids_cache*.yml` file if the standard name is absent.
- Can import the current contents and cache from `plugins/ItemsAdder` with `/sitems import`.
- Scans `plugins/SeaPack/contents`.
- Also recognizes `plugins/SeaPack/itemsadder-contents`.
- Discovers every namespace from the current ItemsAdder YAML files.
- Filters stale, disabled, and template cache entries using current contents.
- Applies modern ItemsAdder `graphics` and `item_model` components.
- Hides internal namespaces whose names begin with `_`.
- Loads worn-armor metadata without overriding normal CustomModelData models.
- Creates missing files and set item entries in `customitems` without replacing existing edits.
- Discovers ItemsAdder furniture behaviours and inherited variants.
- Seeds editable furniture transforms from imported model JSON.
- Creates missing `furniture.yml` entries without replacing existing edits.
- Copies the jar's current `README.md` into `plugins/SeaPack` on startup.

An existing `config.yml` can remain, but SeaPack ignores it.

## Troubleshooting

### The menu is empty

- Confirm `plugins/SeaPack/items_ids_cache.yml` exists.
- Check the server console for invalid material or missing file warnings.

### Reload reports zero armor definitions

- Confirm `plugins/SeaPack/contents` exists.
- Confirm YAML files exist under paths such as
  `plugins/SeaPack/contents/sets/configs`.
- Make sure the newest SeaPack jar is installed.
- Review the reported source and config-file counts.

### README.md was not created

- Install the newest SeaPack jar and restart the server.
- SeaPack refreshes `plugins/SeaPack/README.md` from the installed jar whenever
  it starts.

### Items have missing or incorrect textures

- Confirm ForcePack is sending the latest resource pack.
- Confirm the cache, contents folder, and resource pack came from the same build.
- Reconnect after replacing the resource pack so the client downloads it again.

### A plushie does not place

- Confirm its ItemsAdder item config contains `behaviours.furniture`, directly or
  through `variant_of`.
- Run `/sitems reload` and check that the furniture count is greater than zero.
- Check that the item has an enabled entry in `plugins/SeaPack/furniture.yml`.
- Place it against a face enabled under `placeable-on` in `furniture.yml`.

### A placed plushie is too high, low, or backwards

Edit that item's `model-transform` in `furniture.yml`. Change `rotation.y` by
`180` to flip a backwards model. Adjust translation to move it relative to its
generated model position, or use `y-offset` for a simple vertical adjustment.
Run `/sitems reload`, then place a new copy. Existing placed models are not
moved. To reseed only the model defaults, delete that item's `model-transform`
section and run `/sitems reload`; the rest of its settings remain untouched.

### A player cannot open the menu

Grant:

```text
seapack.items.menu
```

The menu permission defaults to server operators.

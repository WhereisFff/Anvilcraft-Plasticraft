---
navigation:
  title: "Model Editor and Import"
  icon: "anvilcraft:structure_disk"
---

# Model Editor and Import

## Editing Cubes

The chamber editor only speaks Cube: boxes stacked into a model. Cubes can be moved, resized, rotated and grouped around shared pivots, and a group shares visibility and lock state

At least one Cube is required before locking. A model outside the current forming range can only be edited, never locked. Zero-thickness planes stay in the model but add no casting volume

## Modeling Workspace

Click the expand button at the bottom right of the viewport, or press `Ctrl+Space`, to switch between the machine panel and the larger modeling workspace. The viewport sits in the center, transform tools on the left, and numeric properties and the hierarchical outliner on the right. The top toolbar provides new, duplicate, group, undo, redo, focus and return actions

Expanding the editor changes its layout; the model stays in the current chamber. Return to the machine panel to manage materials, production modes or Structure Disks

## Selection and Outliner

- Left-click a Cube to select it, `Ctrl`-click to add or remove it, `Shift`-click to select its parent group, or `Ctrl+Shift`-click to add or remove that parent group
- Hold `Shift` and drag a rectangle in the viewport to select several Cubes
- The outliner follows the group hierarchy, supports collapsed groups and name search, and provides visibility and lock controls on each row; within the outliner, `Ctrl`-click adds or removes a row and `Shift`-click selects a contiguous range
- `Ctrl+A` selects all and `Ctrl+Shift+A` inverts the selection; `Ctrl+D` duplicates the selection and `Ctrl+G` creates a group
- `F2` renames, `Delete` deletes the selection, `H` toggles visibility, and `L` toggles locking

Visibility controls whether an element appears in the viewport; locking controls whether it can be changed. Group state affects its descendants, and selecting a group allows its members to be transformed together

## Views and Tools

Drag empty viewport space with the left mouse button to orbit, drag with the right or middle button to pan, and scroll to zoom. A right-click opens the action menu. Transform handles change the model; dragging empty viewport space moves the camera

| Action | Shortcut |
| --- | --- |
| Move, resize, rotate | `V`, `S`, `R` |
| Move pivot, mirror | `P`, `M` |
| Focus selection, frame model | `F`, `Home` |
| Front, right, top | `1`, `3`, `7`, including the numeric keypad |
| Back, left, bottom | Hold `Ctrl` with the corresponding view key |
| Perspective view | `5`, including the numeric keypad |
| New Cube | `N` |
| Undo, redo | `Ctrl+Z`, `Ctrl+Shift+Z` or `Ctrl+Y` |
| Copy, cut, paste | `Ctrl+C`, `Ctrl+X`, `Ctrl+V` |

## Numeric Properties and Edits

The properties panel has separate X, Y and Z inputs for position, size, pivot and rotation. Position and pivot use the manufacturing origin as `(0,0,0)`, while size and rotation use the element’s local coordinates. Position, size and pivot use px, while rotation uses degrees; `16px` equals one block

When editing a number, `Enter` commits, `Esc` cancels, and `Tab` or `Shift+Tab` moves between fields. Inputs support addition, subtraction, multiplication, division and parentheses; for example, `8/2` sets the property to `4`. A leading `+`, `*`, `/` or `-=` applies an operation to each selected object’s own value, such as `+1`, `*2`, `/2` or `-=1`; entering `-2` directly sets an absolute negative value. Different selected values show a mixed-value indicator

Scroll over a numeric field to adjust it in steps. Position, size and pivot use `1px` by default, `0.1px` with `Shift`, and `10px` with `Ctrl`; rotation uses `15°`, `1°` and `45°` respectively. Transform-handle drags use the same snapping steps, and holding `Alt` temporarily disables snapping

Dragging a transform handle previews the shape immediately and releasing submits the edit. Moving a pivot leaves existing geometry in place; rotating several selected elements uses a shared selection center, and mirroring retains each element’s additive or subtractive role

Selected elements with different orientations cannot share a resize drag and should be adjusted separately. A transform is also rejected if nonuniform parent scaling would introduce an unsupported shear. Locked elements and descendants of locked groups cannot be transformed, and deleting a mixed selection preserves its locked members

Undo and redo apply to model-editing history; camera orbit, pan and zoom do not change the model

## Structure Disks

The editor's disk slot accepts only an AnvilCraft <ref item="anvilcraft:structure_disk"/>. Loading copies its molding model into the editor and keeps it editable; storing writes a copy of the current model

Molding models and native AnvilCraft structures use the same Structure Disk item, with one content type per disk. Replacing existing disk data with a model requires confirmation. When AnvilCraft writes a structure onto a disk, the native structure takes precedence over any remaining molding data

Sneak-right-click the air with a molding disk in either hand to clear that disk. Its shared model and any previously copied disks remain intact

## Shared model library

The world stores up to 512 shared `.json` models below `anvilcraftplasticraft/blueprints/`, while a Structure Disk keeps an embedded copy that loads independently. Shared models can be loaded, copied and renamed, and deleting a shared file does not affect a copy already on a disk or in a chamber

## Importing model files

Open the model folder from Model Management, drop in a Minecraft Java `.json` or Blockbench `.bbmodel` file no larger than 1MiB, then refresh the model list. Cubes, groups, pivots and supported rotations come across; textures, animations and display transforms are omitted, and non-Cube geometry that cannot be converted is rejected

An imported model outside the forming range is not scaled down to fit; its source dimensions are retained and it can be saved, loaded and edited, but it cannot be locked. A model that fits the range but sits outside the workspace is first offered a whole-model translation and imported after you confirm it

## Related

- [Cast and print](020_casting_and_printing.md)

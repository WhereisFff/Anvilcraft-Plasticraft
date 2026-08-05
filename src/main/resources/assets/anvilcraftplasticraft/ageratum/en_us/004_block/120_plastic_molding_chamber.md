---
navigation:
  title: "Plastic Molding Chamber"
  icon: "anvilcraftplasticraft:plastic_molding_chamber"
  position: 120
categories:
  - "anvilcraftplasticraft:production_blocks"
---

# Plastic Molding Chamber

## Modeling and locking

Placing a <ref item="anvilcraftplasticraft:plastic_molding_chamber"/> reserves a 3x3x3 forming region behind it. The editor represents this 48x48x48-unit workspace at 16 internal units per block. The editable projection has no collision. All 27 region blocks gain full collision atomically when clay filling actually begins, while the frozen model projection remains visible throughout the three clay layers.

Undo is available only when the current editing session has history. Copy and Cut require at least one selected cube, while Paste requires a cube in the clipboard. These buttons activate when the left mouse button is released over the same button; dragging away before release cancels the action. Delete All Cubes in the workspace context menu removes every cube as one undoable edit. If any cube is locked, the model remains unchanged.

A model needs at least one volumetric or zero-thickness cube before it can be locked. Locking freezes the current revision and calculates the clay requirement, but the whole batch does not need to fit in the slot at once. A player, item, or other entity in the forming region holds the machine at "region blocked" without consuming the first clay ball. It retries after the region is clear.

## Disks and shared blueprints

The GUI disk slot accepts AnvilCraft disks. Store to Disk works whether the current model is editable or locked. One click writes an empty disk; a disk with existing data first shows a title-bar warning and is cleared and replaced only after a second click. A successful store embeds the canonical blueprint and its SHA-256 hash while atomically writing a shared JSON copy under `<world>/anvilcraftplasticraft/blueprints/`. The embedded copy still loads when the shared file is missing or damaged.

The JSON management overlay starts closed whenever the GUI opens and shows up to six shared models per page. It renders one button per actual model and no empty model buttons. Search filters filenames, model names, and owners. Long lists scroll vertically, while long names scroll within their row. Selecting a row only selects that entry and writes it to the current disk; it never changes the chamber model directly. Without a disk, the selection remains for the current GUI session. Replacing different disk data shows both names and hashes and requires a second click on the same row.

Load Disk is the separate action that moves the disk blueprint into the editor, always unlocked and editable. Loading requires an editable machine with no molded clay and no melt in the forming batch. The 8 B staging tank may remain filled and is never changed. Replacing the current draft also requires confirmation.

The shared library holds at most `512` models. Shared files record the UUID and name of the player who saved them. Every player may view and load them; only the owner or an administrator with permission level 2 or higher may overwrite, rename, or delete one. Pins are stored separately per player and world. Copy creates a non-conflicting file with identical model content owned by the player who copied it. Deletion requires one click followed by a second Shift-click and does not affect copies already embedded in disks or loaded into chambers.

## Importing from the model folder

The same button at the bottom of JSON management is the import entry and remains available with no shared model selected. A normal Refresh Models click scans the directory once without opening a file manager. Shift-click opens the model folder without scanning or importing. In a local world it locates the authoritative world library above. On a remote server it uses a safe client directory and, before a Shift-open, exports the selected model there as a read-only copy that is not uploaded again. If the file manager cannot be opened, the absolute path is copied to the clipboard.

After placing a `.json` or `.bbmodel` file in that directory, use a normal Refresh Models click to read that scan's files and send them to the server. The GUI never scans in the background. Once the server confirms that a model is stored in the shared library, the client deletes the source only if its size and modification time still match the uploaded file; failed or replaced sources remain. A file is limited to `1 MiB` and `64` JSON nesting levels. Path traversal, symbolic links, other extensions, and files with no local `elements` that depend only on an unavailable external parent are rejected. Uploading the same owner, name, and model hash again does not create a duplicate.

Minecraft Java imports support `elements`, `from`, `to`, single-axis `rotation`, and `origin`. Local `elements` are imported directly and take precedence over `parent`; recognized vanilla full-cube parents expand only when local geometry is absent. Blockbench imports support cubes, groups, pivots, and rotations. Except when an exact `48 px` Blockbench span is fitted to the complete workspace, Java and Blockbench source coordinates both map to chamber display coordinates, so `(0,0,0)` remains numeric `(0,0,0)` after import. Zero-thickness planes remain zero-size cubes. UVs, textures, colours, animation, and display transforms are omitted. Meshes, locators, rotation rescaling, plugin-private elements, and other shapes that cannot be converted losslessly identify the unsupported item and reject the complete import.

Imported bounds may span at most `48 px` on each axis. A model outside the workspace but within that size is never scaled. The GUI first shows a suggested whole-model translation; another normal Refresh Models click confirms the import with that offset. A file change invalidates the pending confirmation.

## Power and clay mold

The chamber consumes AnvilCraft grid power at a fixed 256 kW working level. Its internal 160000000 FE buffer matches one super capacitor. Stored FE keeps the machine running after the grid disconnects. When FE runs out, clay animation and melt pumping pause in place while clay, fluids, progress, and mold collision remain intact.

The filter slot permanently accepts clay balls only and uses the chute-style ghost item, translucent red overlay, and red limit number. Scrolling over the slot changes its effective stack limit from 1 to 256, with a default of 256; the slot bypasses the clay ball's normal limit of 64. Lowering the limit never deletes existing items. If the current stack is above the new limit, automation may extract it but cannot insert more.

For model volume `V` in cubic pixels, each cycle requires:

`ceil((110592 - V) / 1024)` clay balls

Cavities also require clay, while zero-thickness cubes do not reduce the requirement. Filling takes 12 active game ticks. At gt 4, 8, and 12, the machine atomically consumes enough clay to reach one third, two thirds, and the full requirement, then renders one complete 3x3 layer. Missing clay or power stops before the next complete layer, with no partial deduction or interpolated jitter. The maximum requirement is 108 balls, so the default slot capacity can hold a whole batch; players may instead lower the limit and keep it supplied through automation. Manual unlock first returns molded clay to the filter slot, then drops any remainder in the forming region. A forming batch automatically returns to staging when all of it fits; only insufficient staging space requires extraction through the top first.

## Staging melt and forming batch

The top and bottom faces expose only the same fixed bidirectional fluid interface and no clay item capability. The four horizontal faces expose only the clay item capability and no fluid capability. The chamber's 8000 mB staging tank accepts compatible plastic melt in every machine state, including editing, no power, no clay, processing, and waiting. Right-click the GUI gauge with a matching plastic melt bucket on the menu cursor to empty it, or with an empty bucket to fill it; the player's main hand is not used. A mismatched fluid, colour, component set, or insufficient capacity changes neither the container nor the machine. The gauge tiles the tinted `16x16` fluid texture at its native height: each 4 B shows one complete tile and 1 B reveals only its bottom quarter.

After the mold is complete, the structure is valid, and power is available, up to 2000 mB moves from staging into an independent forming batch each gt. For model volume `V`, batch capacity is:

`M(V) = max(ceil(V / 4), 250) mB`

The two capacities do not overlap. A maximum solid model holds 27648 mB in its batch while staging still holds 8000 mB, for 35648 mB total. The first melt entering a batch fixes its fluid, material, colour, and component identity until the batch is empty. Extraction from either vertical face drains the unprocessed batch before staging.

Anvil processing requires at least 250 mB already inside the forming batch. Staging melt does not satisfy this threshold and is never moved into the batch instantly by an anvil strike.

## Giant Anvil processing and clay recovery

Build a 3x3 platform directly above the top layer of the forming region and land a Giant Anvil on that structure. Nine Crafting Tables perform a conversion and create a shape-preserving plastic entity inside the original 3x3x3 region. Eight outer Crafting Tables with a Space Overcompressor in the centre perform crafting and create a plastic item drop in the same region. A complete product retains each source cube, endpoint, and rotation instead of displaying the manufacturing cells as a stepped outline. The platform is derived from the chamber orientation and region anchor, so all four horizontal chamber directions use the same rule. Crafting Tables, the Space Overcompressor, the chamber, and its region blocks are never consumed.

A strike succeeds only when the structure is correct, the clay mold is complete, the chamber can maintain its 256 kW working level, and the forming batch contains at least 250 mB. The server first freezes and validates the model, batch melt, material, colour, output, and target region, then commits the product, clay recovery, and machine state together. A failed preflight consumes no melt or clay and creates no partial output. Duplicate delivery of one landing event cannot produce a second result.

The manufacturing conversion is fixed at `1 mB = 4` forming cells, so 250 mB pays for at most 1000 cells and 251 mB for at most 1004. The machine divides the paid cell count by the model's total cell count, then places one horizontal cut at that percentage between the model's lowest and highest Y coordinates. Source cubes below the cut retain their endpoints, rotations, and continuous slopes; cubes crossing it are clipped at the same height and receive a flat upper surface, while everything above it is removed. Cell order is stable accounting data and is never rendered as scattered pieces. A model of at most 1000 cells becomes complete at the 250 mB minimum. A purely zero-thickness model retains all surfaces after meeting that minimum; zero-thickness cubes in a mixed model follow the same height cut without creating caps.

A successful strike clears only the forming batch. The fluid identity, components, and exact mB amount in the 8 B staging tank stay unchanged. Products are created in the forming region rather than stored by the machine. Every clay ball actually committed to the mold drops back into that same region, merged into stacks of at most 64 where possible: batches that used 1, 64, or 108 clay balls return exactly 1, 64, or 108. Clay-breaking particles are visual and do not alter the recovered count.

## Production modes

- Continuous processing keeps the model and automatically prepares another mold after the region is clear and clay is supplied; every batch still needs a valid anvil strike
- Redstone control is the default and reacts once to a rising edge while unlocked; a steady high signal never retriggers
- Single processing locks one manually started batch and returns to editing after completion; redstone locking is ignored

Changing mode during a cycle only changes what happens after that cycle completes. It never destroys the current mold or refunds its resources. Jade shows only the grid bar, melt bar, red segmented FE bar, clay-ball icon and count, plus one State line containing the current pause reason. It adds no separate prose rows for exact FE, pumping, the two melt stores, or clay subtotals.

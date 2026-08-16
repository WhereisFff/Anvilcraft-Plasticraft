---
navigation:
  title: "Construction Blueprints and Structure Disks"
  icon: "anvilcraft:structure_disk"
items:
  - anvilcraft:structure_disk
---

# Construction Blueprints and Structure Disks

A structure disk can hold three kinds of content. A structure and a construction blueprint are the same vanilla NBT structure: importing a construction blueprint overwrites any structure already on the disk. The second tooltip line "Structure:" shows the blueprint name, and "Size" shows the real width, height and length. Vanilla says the disk can be used by a Smart Block Placer when that size is at most `5×5×5`. The hover preview spins this structure, and the placer reads the same data. A molding model still occupies the same "Structure:" line and rewrites size plus chamber checks. Structure content is stored as a canonical snapshot in the current world's structure library, with identical content deduplicated. The disk also writes a vanilla-readable `.nbt` for preview and the placer. A blueprint keeps the palette, blocks, block-entity data and entities. Import reports unknown blocks, unknown entities and damaged data instead of silently turning errors into air.

## Import

All three import entries write into the disk in hand:

- Right-click a vanilla structure block that already has a saved template
- Right-click a disk that already holds scanner data to convert it in place; conversion restores world-aligned coordinates from the scan facing and upside-down flag
- Sneak-right-click the disk to open the file import screen and upload files from the game directory `anvilcraftplasticraft/structures`

File import accepts three formats and produces the same canonical snapshot, so identical content gets the same hash: vanilla `.nbt`; Create blueprints, whose `.nbt` is vanilla structure format; and Litematica `.litematic`, including multi-region files, negative axes, compact bit palettes, block entities and entities. Overlapping regions keep the later write and warn. Unmappable extra fields are ignored and reported one by one. Files from older saves are upgraded first.

Limits: at most 512 blocks on one axis, 2,097,152 block entries (Litematica counts the union volume), 4,096 entity entries, and 8 MiB per upload. A disk that already holds a molding-chamber blueprint cannot also be a construction blueprint. Overwriting an already deployed disk removes that disk's previous world projection.

## Deploy session

Right-click an imported disk to enter a deploy session. A translucent projection follows the crosshair; right-click runs the current tool. The vanilla hotbar scroll is unchanged. Hold Ctrl and scroll to move across the seven tools, in the same direction as the hotbar. Hold Alt and scroll to adjust the current tool:

- Move anchor: right-click toggles follow-crosshair and lock; unlocked Alt-scroll changes height, locked Alt-scroll steps one block along the nearest axis
- Rotate: right-click turns 90° clockwise; Alt-scroll rotates 90° around the bounding-box center
- Mirror: right-click or Alt-scroll cycles none, left-right and front-back
- Previous / next layer: right-click steps one layer; Alt-scroll moves in the scroll direction and wraps back to the full view
- Confirm: submits the final position, rotation and mirror
- Cancel: removes the blueprint placed from this disk; aim at a leftover projection if the disk already holds another blueprint

Placed projections are visible to every player, and several non-overlapping blueprints can coexist. Confirmation requires the whole blueprint to stay within the current dimension's build height and horizontal world bounds. Hidden faces are culled by adjacency. Block entities, fluids and entities render as translucent originals and are not hidden by hologram blocks in front. Chests and minecarts keep only the camera-facing shell. Leaves keep their tint. Redstone dust sits on the top face and uses the blueprint power color; delivered dust does not cull the top face of its supporting block. Move, rotate and mirror ease over about `220 ms`. An unstarted blueprint keeps its cyan box; once construction starts, the box becomes a yellow-concrete and black alternating warning ring at the base, with a short matching translucent construction zone that fades upward. The boundary for the current state stays visible while the snapshot is downloading.

## Start and status

- Disk slot tint follows the disk through any menu: yellow means placed or paused, green means running
- Empty-handed right-click in a menu toggles start and stop, and no longer picks the disk up
- Each player may have only one running job; starting another pauses the old one. Allays that already picked up material fly back to the owner, insert the carry, then hover. Delivered fake blocks stay
- Unclaimed construction takes materials from the owner's inventory — or any needed item infinitely from a creative owner without consuming the inventory. After a disk enters a lounge slot, the lounge becomes the coordinator and the slot stays yellow until empty-handed right-click starts the job. Take and return use only the container below, and a creative player cannot bypass that chest; a creative crate below supplies any item infinitely. Each ordinary-block trip carries one item type up to a full stack, and rechecks the safe approach and enclosed cavity before every placement. If none of that batch's remaining targets is safe, the remainder is returned so interior work or another material can go first. Claimed discovery range is 128 blocks from the lounge to the target. Delivered work is still normal blocks, block-entity contents, bucket fluids, exact millibucket tanks, boats, spawn eggs, resin captures and plastic entities as construction projections. A delivered cell still looks like the finished block, including faces against remaining hologram cells, while the world cell stays air. Fences, walls and doors use finished adjacency for collision. Rails can be delivered into a cell that already has a minecart; the minecart entity waits until that cell's block is delivered. If another entity occupies a cell, the allay hovers and waits instead of pathfinding into it. Plastic entities stay as projections until commit, and the allay may enter the target cell when every neighbor is an undelivered place. Reach is 1 block, and take/place/break/collect happen at most once every 4 gt after it turns to face the target. Unclaimed allays leave the blueprint box before hovering; claimed bound allays dock at the lounge
- A live job first displaces declared fluids with real temporary fill and adds a one-block outer shell. Inner fill is placed strictly from the lowest Y layer upward; no upper layer is claimed until the lowest unresolved layer finishes. Demolition allays may then enter cleared cells reserved for later building and peel every existing block and inner fill from the outside inward. Projected building begins only after all demolition resolves. A bound builder docks as soon as the last seal resolves. A demolition target whose approach becomes unusable is released for reassignment immediately instead of making the allay hover above it. Demolition never writes a solid delivered projection. Shell fill stays until quiet commit writes real blocks, so water cannot flow back into projections
- After demolition, if the site box still has this job's marked drops and a loaded collector owned by the same player is in this dimension, within 128 blocks of the nearest mark, and not full, the job enters a bounded sweep. It returns to building after the marks are gone or every collector is full or out of range. Collection prefers magnet allays; a magnet docks after it finishes or unloads, and empty-hand collectors leave the blueprint box before hovering. Without a collector the drops stay in the world and building starts immediately
- The anchor can no longer move or rotate after an operation is delivered or leased, or material is in transit. A paused job with only pending planning may still move and replans every coordinate from its new anchor, rotation and mirror when restarted
- Cancel quietly commits delivered blocks and contents. Allays that already picked up material fly back and insert the carry. Undelivered cells stay as the world was. Real fill blocks already placed stay. Material or demolition shortage pauses by default, or continues incomplete after skip. Permission denial always pauses
- Block-entity contents are deducted slot by slot and written once on commit. Giant anvils and other large multiblocks consume one core item. Machines stay silent during construction. After commit, vanilla redstone dust is written with the blueprint look and power, and AnvilCraft redstone wire also writes the blueprint sides into the port overlay table
- Source liquids deduct full buckets and return empty buckets. Layered cauldrons and tank block entities extract exact millibuckets. Boats, spawn eggs, resin captures and plastic entities use adapters and real ingredients. Transient entries such as players, drops and experience orbs are skipped

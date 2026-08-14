---
navigation:
  title: "Construction Blueprints & Structure Disks"
  icon: "anvilcraft:structure_disk"
items:
  - anvilcraft:structure_disk
---

# Construction Blueprints & Structure Disks

A structure disk can hold three kinds of content, and construction blueprints are the same vanilla NBT structures as ordinary structures: importing a construction blueprint replaces any previous structure on the disk, the second tooltip line "Structure:" shows the blueprint name, "Size" shows the real bounds, AnvilCraft marks disks within `5×5×5` as placeable by the Smart Block Placer, the hover preview rotates that structure, and the placer reads the same payload. Molding models still use the same "Structure:" line with their own size and chamber check. Structure content is stored as a canonical snapshot in the current world's structure library, deduplicated by content, and a vanilla-readable `.nbt` copy is also written for preview and the placer. Blueprints keep the full palette, blocks, block entity data and entities; imports explicitly report unknown blocks, unknown entities and corrupted data instead of silently turning errors into air.

## Importing

Three import entries, all writing into the held structure disk:

- Right-click a vanilla structure block with a saved structure while holding a disk to copy that template
- Right-click a structure disk that carries scanner data to convert the scan in place; the conversion restores coordinates to world alignment using the recorded facing and upside-down flag, so the deployed result matches the scanned original
- Sneak-right-click the disk to open the file import screen, which reads files from anvilcraftplasticraft/structures in the game directory and uploads them in chunks

File import supports three formats that all produce the same canonical snapshot, so identical content yields identical hashes: vanilla structure .nbt; Create schematics, whose .nbt is the vanilla structure format and is read through the vanilla path; and Litematica .litematic with multiple regions, negative-size axes, tightly bit-packed palettes, block entities and entities, where the union of regions forms the blueprint bounds, overlapping regions are overridden by later ones with a warning, and unmappable extra fields are ignored and reported individually. Files from older game versions are upgraded through data fixers first.

Limits: at most 512 blocks per axis, 2,097,152 block entries (for Litematica counted over the region union volume), 4,096 entity entries and 8 MiB per uploaded file. A disk holding a molding chamber blueprint cannot double as a construction blueprint; importing over a disk that already has a placement automatically removes that previous projection from the world.

## Deployment session

Right-click with an imported disk to enter the deployment session. A translucent projection appears and follows the crosshair; right-click again to execute the current tool. Plain scrolling still changes the hotbar and is not captured by the session. Hold Ctrl and scroll to switch among the seven tools, in the same direction as the hotbar: up moves left, down moves right; hold Alt and scroll to adjust the current tool:

- Move anchor: right-click toggles between following the crosshair and locking in place; Alt+scroll changes the height offset while unlocked, or nudges one block along the nearest look axis once locked
- Rotate: right-click rotates 90 degrees clockwise; Alt+scroll rotates 90 degrees in the scroll direction around the bounding box centre
- Mirror: right-click or Alt+scroll cycles through none, left-right and front-back
- Layer up / Layer down: right-click steps through single layers; Alt+scroll steps by scroll direction, wrapping past the top or bottom back to full view
- Confirm placement: submits the final position, rotation and mirror to the server and records the job on the disk
- Cancel placement: removes the placement tied to the current disk; if the disk already holds a different blueprint, look at the leftover projection and execute cancel to delete it, or just exit the session when not aiming at one of your projections

Placed projections are visible to all players and multiple blueprints can coexist; hidden inner block faces are culled. Block entities such as signs, chests, pulse-generator dials and smart block placer arms, fluids such as lava, and entities such as minecarts, creepers or plastic entities are drawn as a translucent hologram of their real appearance and are not occluded by hologram blocks in front; chests and minecarts keep only the camera-facing outer silhouette with inner faces culled; foliage keeps its tint, and redstone dust lies on the block top and is coloured by the blueprint power level. During a deployment session, moving, rotating, or mirroring eases over about `220 ms` instead of snapping; the bounds use the same thick lines as selecting an entity with a high-viscosity resin bucket. A bounds outline is drawn even before the snapshot arrives so the placement is never completely invisible.

## Starting and status

- The slot background colour of the disk in any menu reflects job state: yellow means placed but not started or paused, green means construction is running; the colour follows the disk into any container
- Right-click a deployed disk in a menu with an empty cursor to toggle start and stop; the vanilla pickup interaction is overridden
- Each player can have at most one running task at a time; starting a new one automatically pauses the old; a drone that already picked up materials flies back to the owner and puts the in-transit items into the inventory, while delivered fake blocks stay
- A construction drone takes materials from the owner's inventory and, within a 128-block discovery range, delivers ordinary blocks as collidable construction projections; delivered cells look like the real block in the world (wood is opaque planks) while the world cell stays air; reach is 1 block and each delivery costs 2,560 FE; after the last delivery it flies out of the blueprint bounds before landing, and flies back to the owner when the owner is outside the site
- An active job first seals fluids inside declared cells and places a one-block temporary fill shell outside the region, then demolition drones peel existing blocks and interior fills from the outside in, and only then may projection building start; demolition only clears world blocks and does not write a delivered solid projection, so undelivered cells stay a translucent preview; shell fills stay until quiet commit writes real blocks so water cannot flow through projections
- After demolition, if the job box still has marked drops and a loaded owner collection drone in the same dimension can still accept cargo within 128 blocks of the nearest marked item, the job stays in a bounded debris sweep; it moves on to building once the marks are gone or every collector is full or out of range. Without a collection drone the drops stay in the world and building starts immediately, with no pause or skip
- After progress exists the anchor can no longer be moved or rotated
- Cancel quietly commits delivered blocks; a drone that already picked up materials flies back to the owner and puts the in-transit items into the inventory, and undelivered cells stay as they are in the world; already placed real fill blocks stay in place and are neither silently deleted nor demolished further; a material or missing-demolition shortage pauses by default, and switching to skip lets the rest continue then finishes incomplete; a permission denial always pauses
- Fluids, block-entity contents and entities in the blueprint target are still skipped and finish incomplete; fluids and solid blocks already in the world are sealed and demolished in this stage

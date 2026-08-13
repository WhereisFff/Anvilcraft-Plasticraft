---
navigation:
  title: "Construction Blueprints & Structure Disks"
  icon: "anvilcraft:structure_disk"
items:
  - anvilcraft:structure_disk
---

# Construction Blueprints & Structure Disks

A structure disk carries a reference to a construction blueprint: the disk stores only the content hash, name, size and source summary, while the structure content is stored as a canonical snapshot in the current world's structure library, deduplicated by content. Blueprints keep the full palette, blocks, block entity data and entities; imports explicitly report unknown blocks, unknown entities and corrupted data instead of silently turning errors into air.

## Importing

Three import entries, all writing into the held structure disk:

- Right-click a vanilla structure block with a saved structure while holding a disk to copy that template
- Right-click a structure disk that carries scanner data to convert the scan in place; the conversion restores coordinates to world alignment using the recorded facing and upside-down flag, so the deployed result matches the scanned original
- Sneak-right-click the disk to open the file import screen, which reads vanilla .nbt structure files from anvilcraftplasticraft/structures in the game directory and uploads them in chunks

Limits: at most 512 blocks per axis, 2,097,152 block entries, 4,096 entity entries and 8 MiB per uploaded file. A disk holding a molding chamber blueprint cannot double as a construction blueprint; a disk with a live deployment must be cancelled before importing over it.

## Deployment session

Right-click with an imported disk to enter the deployment session. A translucent projection appears and follows the crosshair; right-click again to execute the current tool, scroll to switch between the seven tools, and Shift+scroll to adjust layer view directly:

- Move anchor: toggles between following the crosshair and locking in place
- Rotate: rotates 90 degrees clockwise around the bounding box centre
- Mirror: cycles through none, left-right and front-back
- Layer up / Layer down: steps through single layers, wrapping past the top or bottom back to full view
- Confirm placement: submits the final position, rotation and mirror to the server and records the job on the disk
- Cancel placement: removes a deployed blueprint from the world, or just exits when nothing is deployed

Placed projections are visible to all players and multiple blueprints can coexist; blocks rendered purely by block entities appear as placeholder boxes with their particle texture, and entity entries are kept in data but not projected.

## Starting and status

- The slot background colour of the disk in any menu reflects job state: yellow means placed but not started, green means running; the colour follows the disk into any container
- Right-click a deployed disk in a menu with an empty cursor to toggle start and stop; the vanilla pickup interaction is overridden
- Each player can have at most one running task at a time; starting a new one automatically pauses the old
- This stage only deploys and toggles projections; drones are dispatched by the later task system

---
navigation:
  title: "Blueprint Deployment and Structure Disks"
  icon: "anvilcraft:structure_disk"
items:
  - anvilcraft:structure_disk
---

# Blueprint Deployment and Structure Disks

<row halign="center">
<item id="anvilcraft:structure_disk"/>
</row>

One structure disk can hold a construction blueprint or a plastic molding model. A construction blueprint keeps blocks, block-entity data, fluids and entities along with it; [Model Editing and Import](../004_molding/010_model_editor_and_import.md) covers the molding side

## Import

All three methods write to the Structure Disk in your hand:

- Right-click a saved vanilla structure block while holding the disk
- Right-click a disk containing scanner data to convert the scan into a blueprint
- Sneak-right-click the disk to import files from `anvilcraftplasticraft/structures`

File import happily takes vanilla or Create `.nbt` files and Litematica `.litematic` files, including multiple regions, block entities and entities. The limits are 512 blocks on one axis, 2,097,152 block entries, 4,096 entity entries and 8MiB per file

## Deployment tools

Right-click an imported disk and the projection follows your crosshair; right-click again executes the current tool. Plain scroll still changes the hotbar, `Ctrl+scroll` switches tools and `Alt+scroll` adjusts the tool:

| Tool | Operation |
| --- | --- |
| Move anchor | Right-click toggles follow or lock; adjust height while unlocked, or move one block along the nearest axis while locked |
| Rotate | Right-click or adjust the parameter to rotate clockwise by 90 degrees |
| Mirror | Cycle none, left-right and front-back mirrors |
| Previous/next layer | View one layer at a time; crossing an edge returns to the full view |
| Confirm | Submit position, rotation and mirror to create a construction job |
| Cancel | Remove this disk's deployed projection |

The whole projection must stay inside the dimension's buildable area and cannot overlap another blueprint. You can still readjust it after confirming, as long as the job has not started; once it does, the anchor is nailed down

See [Construction jobs](030_construction_jobs.md) for phases and resource handling

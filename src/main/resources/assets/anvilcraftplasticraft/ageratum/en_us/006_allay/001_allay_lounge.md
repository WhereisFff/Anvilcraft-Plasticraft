---
navigation:
  title: "Allay Lounge"
  icon: "anvilcraftplasticraft:allay_lounge"
categories:
  - "anvilcraftplasticraft:production_blocks"
items:
  - anvilcraftplasticraft:allay_lounge
---

# Allay Lounge

The Allay Lounge hosts hatted allays and later accepts a construction disk. It stores up to 16 allay records and 1 structure-disk slot. It has no worker-item slots, capacitor slot or internal FE. The bottom face is the only logistics side: lounge-bound allays take build materials from the container below, and both empty-hand and magnet collection unload there. Leftovers that do not fit are dropped on the ground beside the lounge; collected items must not stay on the allay. This stage has no crafting recipe.

## Power

- The lounge always requests 16 kW from its grid; powered means a working grid, not an internal tank
- Recall and docking only run while the grid is working
- Working allays themselves neither charge nor drain FE

## Top bay and recall

- The lounge block always keeps a full-cube collision
- The top bay is single-channel: one docking at a time; the allay vanishes on arrival and the bay stays busy for 20 gt, so at most one per second
- The recall button sends working allays within 16 blocks to the top bay; extras wait in a 4×4 formation 3 blocks above the roof at 1-block spacing, then stack upward after 16
- A full lounge never overwrites hosted records; waiters stay in formation
- Cutting power keeps hosted records and docking progress, then switches to the unpowered model; restoring power resumes from the same progress
- Breaking the lounge spawns every hosted and docking allay back into the world and drops the structure disk

## Shortage strategy

- The lounge screen can switch between pause-and-wait and skip; allays released from that lounge follow this setting
- World allays with no lounge stay on pause and no longer have their own settings screen

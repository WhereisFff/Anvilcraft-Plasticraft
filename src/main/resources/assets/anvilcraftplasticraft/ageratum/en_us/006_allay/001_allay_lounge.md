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

The Allay Lounge hosts hatted allays and accepts a construction disk. It stores up to 16 allay records and 1 structure-disk slot. It has no worker-item slots, capacitor slot or internal FE. The bottom face is the only logistics side: a claimed job takes and unloads only from the container below, and both empty-hand and magnet collection unload there. Leftovers that do not fit are dropped on the ground beside the lounge; collected items must not stay on the allay. Inserting a deployed disk claims and starts that job, and excludes unhosted construction, demolition and observation workers. This stage has no crafting recipe.

## Power

- The lounge always requests 16 kW from its grid; powered means a working grid, not an internal tank
- Recall and docking only run while the grid is working
- Working allays themselves neither charge nor drain FE

## Top bay and recall

- The lounge block always keeps a full-cube collision
- The top bay is single-channel for inbound and outbound: one allay at a time; after docking or launch the bay stays busy for 20 gt, so at most one per second
- The recall button sends working allays within 16 blocks to the top bay; extras wait in a 4×4 formation 3 blocks above the roof at 1-block spacing, then stack upward after 16
- A full lounge never overwrites hosted records; waiters stay in formation
- Cutting power keeps hosted records and docking progress, then switches to the unpowered model; restoring power resumes from the same progress
- Breaking the lounge spawns every hosted and docking allay back into the world and drops the structure disk

## Shortage strategy

- The lounge screen can switch between pause-and-wait and skip; allays released from that lounge follow this setting
- World allays with no lounge stay on pause and no longer have their own settings screen

## Disk claim and launch

- Inserting a deployed disk starts that job, pauses the owner's other jobs, and makes this lounge the sole coordinator
- The coordinator launches a matching profession only when there is real work; outbound uses the same 20 gt bay
- After a claim, unhosted construction, demolition and observation workers leave; an unhosted builder that already picked up material returns it to the owner first
- If the container below disappears, related allays hover and keep their lease and carry; work resumes from the current phase when the container returns
- While powered, the four horizontal sides show only already-reserved ledger items, not a hidden slot; demolition and observation show nothing, and collection uses its own unload path

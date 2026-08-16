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

The Allay Lounge hosts hatted allays and accepts a construction disk. It stores up to 16 allay records and 1 structure-disk slot. It has no worker-item slots, capacitor slot or internal FE, and it does not join a power grid or consume power. The bottom face is the only logistics side: a claimed job takes and unloads only from the container below, and both empty-hand and magnet collection unload there. Leftovers that do not fit are dropped on the ground beside the lounge; collected items must not stay on the allay. Inserting a deployed disk claims that job and keeps the slot yellow; empty-handed right-click turns it green and starts construction, and excludes unhosted construction, demolition and observation workers. A creative crate below supplies any blueprint item infinitely, like taking from a creative inventory, and does not consume the crate filter. This stage has no crafting recipe.

## Top bay and recall

- The lounge block always keeps a full-cube collision
- The top bay is single-channel for inbound and outbound: one allay at a time; after docking or launch the bay stays busy for 20 gt, so at most one per second
- The recall button selects only working allays owned by the player who pressed it, within 16 blocks and up to the lounge's remaining capacity. Manual recalls and automatic returns are stably ordered by their distance to the top center when they enter the queue, then by UUID; live movement does not reorder them. Only the queue head uses the top center; the others keep fixed slots on a four-sided ring 3 blocks above the roof, at radius 3 and 1.2-block spacing, stacking 1 block upward after every 16 slots
- When the head docks, only the next allay moves to the center; other waiters do not shift slots. A full lounge never overwrites hosted records
- Breaking the lounge spawns every hosted and docking allay back into the world and drops the structure disk

## Shortage strategy

- The lounge screen can switch between pause-and-wait and skip; allays released from that lounge follow this setting
- World allays with no lounge stay on pause and no longer have their own settings screen

## Disk claim and launch

- Inserting a deployed disk only makes this lounge the sole coordinator and does not start the job; empty-handed right-click turns the disk from yellow to green and starts it, pausing the owner's other jobs
- A creative crate below supplies any blueprint item infinitely without consuming the crate; a normal container still deducts stock
- The coordinator launches a matching profession only when the current phase and layer have a claimable operation; outbound uses the same 20 gt bay. A job takes at most 64 allays, and another launch happens only while a parallel safe frontier remains. Upper fluid-fill layers blocked by the unfinished lowest layer do not trigger extra launches. The roof center and fixed ring slots are queued as time slots
- After a claim, unhosted construction, demolition and observation workers leave; an unhosted builder that already picked up material returns it to the owner first
- If the container below disappears, related allays hover and keep their lease and carry; work resumes from the current phase when the container returns
- The four horizontal sides show only already-reserved ledger items, not a hidden slot; demolition and observation show nothing, and collection uses its own unload path
- Item collection launches a magnet allay first. After evacuation and unloading, a bound allay docks whenever it has no work it can claim and holds no lease, carried material or collected items, even if the current phase can still use its profession

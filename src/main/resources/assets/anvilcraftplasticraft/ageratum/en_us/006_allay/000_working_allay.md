---
navigation:
  title: "Working Allay"
  icon: "minecraft:allay_spawn_egg"
categories:
  - tools
---

# Working Allay

Print an Allay Hard Hat in the Plastic Molding Chamber, then right-click a vanilla allay while holding that hat. The allay becomes a working allay. Sneak-right-click removes the hat and turns it back into a vanilla allay, keeping its UUID, position, custom name and held item. The hat cannot be removed while hosted carry or collected items are still present.

A hard hat may be at most `11×11 px` across and `16 px` tall, and needs the 3D printing component above the chamber. Empty models, models wider than `11×11 px`, or models taller than `16 px` are rejected; they do not have to sit in the workspace center. Worn hats keep their modeled size, with the horizontal center aligned to the crown center and the bottom face first aligned to the crown then sunk `1 px` into the head so the hat sits on rather than above it.

## Empty-handed generalist

A hatted allay with an empty main hand both builds and collects nearby drops:

- It takes materials from the owner and places blueprint blocks; while placing, it holds the block it is delivering. A creative owner is never charged, and any blueprint item can be taken infinitely
- It walks up to a drop, picks up exactly 1 item, and has no nine-slot bag; it then flies back to insert into the owner's inventory or the container under its lounge, and drops leftovers on the ground beside that target if it is full
- Empty-hand reach is 1 block, and it cannot break blocks

## Held items

A special main-hand item replaces the empty-hand kit:

- <ref item="anvilcraft:crab_claw"/>: construction reach becomes 4 blocks for long-range placing, but it cannot pick up drops; a block taken from the owner is held in the downward-opening pincers
- <ref item="minecraft:stonecutter"/>: can only smash like an anvil hitting a stonecutter; it cannot build or pick up drops
- <ref item="anvilcraft:magnet"/>: inhales drops in a wide radius like a player using a handheld magnet once, and has a nine-slot temporary bag, but it cannot build; it must empty that bag on unload, and leftovers that fit neither the owner nor the lounge are dropped on the ground beside the target
- <ref item="minecraft:spyglass"/>: observation; it only wanders in this stage, 3×3 chunk loading comes later

## Flight and collision

- With no construction task it uses vanilla allay wander AI and flight animation, and is not locked to a 5×5×5 cube
- While picking up, placing, demolishing, collecting or docking it uses construction pathing and does not wander off the job
- Take, place, break, collect and unload happen at most once every `4 gt`; once in reach it turns to face the player or target before acting, instead of snapping the action instantly
- Its hitbox matches a vanilla allay at 0.35 × 0.6 blocks; it has no hard collision, so players cannot stand on it, but they can shove it or lead it
- Construction, demolition and collection all discover targets within 128 blocks
- After the last delivery or sweep the allay leaves the blueprint box, then returns to vanilla wandering; if the owner is outside the site it flies back to them
- Working allays themselves use no FE and never request grid power

## Construction, demolition and collection

- Construction takes items from the owner's inventory and delivers normal blocks, block-entity contents, bucket fluids, exact millibucket tanks, boats, spawn eggs, resin captures and plastic entities as construction projections. Creative owners are not charged and can supply any needed item infinitely
- A delivered projection is still air in the world cell, but it looks like the finished block, including faces against remaining hologram cells, and has real collision
- Demolition only clears the world block and never writes a solid delivered projection; bedrock and other negative-hardness blocks skip the matching place op
- Missing demolition follows the lounge shortage strategy: pause and wait, or skip breakable obstacles. World allays with no lounge stay on pause
- Magnet free-mode collection rescans any item entity within 16 blocks after every inhale, ignores experience orbs and does not wear the magnet; the nine slots are only temporary storage, so a full bag stops inhaling and immediately unloads into the owner's inventory or the container under its lounge, and leftovers that fit neither are dropped on the ground beside that target
- Empty-hand collection must reach 1 block to pick one item, then flies back to the owner's inventory or the container under its lounge and drops leftovers on the ground beside that target if it is full
- Task mode only claims drops marked for this job; without a collector, demolition does not block building and the drops stay in the world
- Pausing a job stops new leases; an allay that already picked up material flies back to the owner, inserts the carry, then returns to vanilla wandering

## Settings

- Working allays no longer have their own GUI, and cannot set pause or skip by themselves
- Only lounge-bound allays follow that lounge's shortage strategy
- Sneak-right-click a hatted allay to remove the hat; the hat stays on while hosted carry or collected items are still present

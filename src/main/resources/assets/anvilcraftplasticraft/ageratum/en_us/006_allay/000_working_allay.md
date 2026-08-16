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

- An unclaimed job takes materials from the owner and places blueprint blocks. Each ordinary-block trip carries one item type, up to that item's full stack, and delivers it block by block. A creative owner is never charged, and any blueprint item can be taken infinitely. After a lounge claim, take and return use only the container below; a normal chest deducts stock, and a creative crate supplies any item infinitely
- It walks up to a drop, picks up exactly 1 item, and has no nine-slot bag; it then flies back to insert into the owner's inventory or the container under its lounge, and drops leftovers on the ground beside that target if it is full
- Empty-hand reach is 1 block; it cannot break blocks, and it cannot place giant anvils or plastic entities wider than 1 block or taller than 2 blocks

## Held items

A special main-hand item replaces the empty-hand kit:

- <ref item="anvilcraft:crab_claw"/>: construction reach becomes 4 blocks so it can grip giant anvils and large plastic entities, but it cannot pick up drops; a block taken from the owner is held in the downward-opening pincers
- <ref item="minecraft:stonecutter"/>: can only smash like an anvil hitting a stonecutter; it cannot build or pick up drops
- <ref item="anvilcraft:magnet"/>: inhales drops in a wide radius like a player using a handheld magnet once, and has a nine-slot temporary bag, but it cannot build; it must empty that bag on unload, and leftovers that fit neither the owner nor the lounge are dropped on the ground beside the target
- <ref item="minecraft:spyglass"/>: observation; it only wanders in this stage, 3×3 chunk loading comes later

## Flight and collision

- With no construction task it uses vanilla allay wander AI and flight animation, and is not locked to a 5×5×5 cube
- While picking up, placing, demolishing, collecting or docking it uses construction pathing and does not wander off the job. Flight goes around delivered fake blocks and seals from outside a cavity. Approach cells, evacuation cells and docking slots stay exclusive only to prevent duplicate endpoint claims; no reservation becomes a pathfinding wall, and narrow-corridor reservations are only yielding hints. A docking allay yields to an allay that is still collecting materials or working so its return route cannot interrupt the current delivery. Rails, grass, open fence gates and other empty-collision states remain passable. With no room to sidestep inside a one-block opening, working allays continue straight and pass through each other because they have no hard collision. If a block appears en route, another allay pushes it off the route, or it makes no progress toward its waypoint for `20 gt`, it reprioritizes a route from its current position instead of shaking or freezing against an edge
- Take, place, break, collect and unload happen at most once every `4 gt`; once in reach it turns to face the player or target before acting, instead of snapping the action instantly
- Its hitbox matches a vanilla allay at 0.35 × 0.6 blocks; it has no hard collision, so players cannot stand on it, but they can shove it or lead it
- Construction, demolition and collection all discover targets within 128 blocks
- After the last delivery or sweep the allay leaves the blueprint box; with no lounge it returns to vanilla wandering, or flies to the owner if they are outside the site. After evacuation and unloading, a lounge-bound allay docks whenever it has no work it can claim and holds no lease, carried material or collected items, even if the current phase can still use its profession

## Construction, demolition and collection

- Unclaimed construction takes items from the owner's inventory; creative owners are not charged and can supply any needed item infinitely. After a lounge claim, take and return use only the container below, and a creative player cannot bypass that chest. A creative crate below supplies any item infinitely, like taking from a creative inventory. Ordinary PLACE blocks are batched by item and components, with at most one full stack of one item per trip. Every placement recalculates its safe approach and enclosed cavity; when no remaining matching block is safe, the allay ends that batch and returns the remainder so interior work or another material can go first. Block-entity contents, fluids, entities, and materials that produce a returned item remain separate transactions. Delivered work is still normal blocks, block-entity contents, bucket fluids, exact millibucket tanks, boats, spawn eggs, resin captures and plastic entities as construction projections. A double chest costs two chests and both halves are delivered together; a door's upper half rides with the lower half. Cancel writes a leftover LEFT/RIGHT chest as a single chest instead of a half-width double chest. Rails can be delivered into a cell that already has a minecart. If another entity occupies a cell, the allay hovers and waits instead of pathfinding into it
- If a claimed operation loses its safe work position after later closure or route changes and no safe alternative remains, the allay immediately releases it for reassignment while keeping carried material instead of flying above the target and waiting. Temporary world-state or entity-occupancy blocks still follow the existing blocked-wait flow and do not trigger this immediate release
- A delivered projection is still air in the world cell, but it looks like the finished block, including faces against remaining hologram cells, and has real collision
- Demolition may enter already-cleared blueprint cells and keep peeling inward; cells reserved for later building do not block it. Building begins only after every breakable block and inner temporary fill is cleared. Demolition only clears the world block and never writes a solid delivered projection; bedrock and other negative-hardness blocks skip the matching place op
- Missing demolition follows the lounge shortage strategy: pause and wait, or skip breakable obstacles. World allays with no lounge stay on pause
- Magnet free-mode collection rescans any item entity within 16 blocks after every inhale, ignores experience orbs and does not wear the magnet; the nine slots are only temporary storage, so a full bag stops inhaling and immediately unloads into the owner's inventory or the container under its lounge, and leftovers that fit neither are dropped on the ground beside that target
- Empty-hand collection must reach 1 block to pick one item, then flies back to the owner's inventory or the container under its lounge and drops leftovers on the ground beside that target if it is full
- Task mode only claims drops marked for this job and prefers magnet allays; a collector that confirms it cannot reach a drop releases that lease for another allay, and without usable collection capability demolition leaves the drops in the world while building continues
- Pausing a job stops new leases. Unclaimed allays that already picked up material fly back to the owner; claimed jobs insert in-transit items into the container below or drop them beside the lounge, then bound allays dock

## Settings

- Working allays no longer have their own GUI, and cannot set pause or skip by themselves
- Only lounge-bound allays follow that lounge's shortage strategy
- Sneak-right-click a hatted allay to remove the hat; the hat stays on while hosted carry or collected items are still present

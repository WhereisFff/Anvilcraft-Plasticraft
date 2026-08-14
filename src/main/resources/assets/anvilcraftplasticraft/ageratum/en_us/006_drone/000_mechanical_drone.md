---
navigation:
  title: "Mechanical Drone"
  icon: "anvilcraftplasticraft:construction_drone"
categories:
  - tools
items:
  - anvilcraftplasticraft:drone
  - anvilcraftplasticraft:construction_drone
  - anvilcraftplasticraft:demolition_drone
  - anvilcraftplasticraft:collection_drone
  - anvilcraftplasticraft:observation_drone
---

# Mechanical Drone

<recipe id="anvilcraftplasticraft:construction_drone"/>
<recipe id="anvilcraftplasticraft:demolition_drone"/>
<recipe id="anvilcraftplasticraft:collection_drone"/>
<recipe id="anvilcraftplasticraft:observation_drone"/>

A mechanical drone is assembled from two plastic propellers, an <ref item="anvilcraft:ionocraft"/>, a <ref item="anvilcraft:magnetoelectric_core"/>, a <ref item="anvilcraft:processor"/>, a <ref item="anvilcraft:capacitor"/> and one tool. The four recipes differ only in the tool of the bottom-left slot: a <ref item="anvilcraft:crab_claw"/> yields the Construction Drone, a <ref item="minecraft:stonecutter"/> the Demolition Drone, an <ref item="anvilcraft:magnet"/> the Collection Drone, and a <ref item="minecraft:spyglass"/> the Observation Drone. All four drones are variants of the same mechanical entity carrying different tool attachments; the body, both propellers and the attachment always render as the full three-dimensional model in the inventory, in hand, as dropped items and in item frames.

The creative inventory shows a single toolless drone fitted with two default white propellers; right-click that entry to open the same 4x4 picker overlay used by the sixteen-color plastics and take out the toolless drone or any of the four tool variants.

## Propellers

The two propellers in the top corners must be "propeller" type products made in the Plastic Molding Chamber. On assembly the complete item data of both propellers is stored in the drone; each keeps its own player-made model, color and material and they are never merged into an averaged look. Both the entity and the item form render the two propellers as-is. Whenever either propeller carries a material trait, the whole drone gains that trait; once heat-resistant plastic is added, a single heat-resistant propeller will make the entire drone fireproof.

## Placement and Recovery

- Right-click a block face while holding a drone item to place the drone entity at the clicked position; entities do not snap to the block grid, and up to 8 drones can be stacked inside one block space
- The collision box of a drone entity is fixed at 0.5 × 0.5 × 0.5 blocks; players can stand on drones and push them around, and a pushed drone never passes through blocks or other entities
- Sneak and right-click a drone with any anvil hammer to recover it as its item variant; tool, both propellers, energy, settings and owner data survive every round trip between item and entity
- Attacking a drone drops its item with all data preserved; creative attacks remove it directly

## Energy and Flight

- Every drone stores energy up to the current <ref item="anvilcraft:capacitor"/> capacity of 8,000,000 FE
- Cost model: total = 256 FE × airborne gt + 256 FE × actual flight distance in blocks + 2,560 FE × instant actions; distance follows the real server-side trajectory, and a landed drone pays no hover cost
- Drones charge automatically inside any working power grid; each drone below full charge requests only 8 kW and converts it with the current power efficiency (800 FE/gt by default); hovering, moving and instant actions always drain internal FE first
- Without a job, regular drones wait on the ground; an observation drone with charge takes off and hovers with the bottom of its collision box 4 blocks above the nearest stable, non-fluid collision surface below, returning to the ground if blocked above
- A construction drone discovers work when the nearest executable target is within 128 blocks and has a path, with a 1-block reach; it takes items from the owner's inventory, flies to an approach cell, delivers a construction projection or places a temporary fluid-seal block, then returns to the player; after the last delivery it flies out of the blueprint bounds before landing instead of stopping on the site, and flies back to the owner when the owner is outside the site; pickup and returns do not count as instant actions, and each delivery or seal costs 2,560 FE
- A demolition drone also discovers work within 128 blocks of the nearest smashable target and has a 1-block reach; on arrival it instantly breaks the block with ordinary anvil-on-stonecutter semantics, costs 2,560 FE, and never picks up drops; unbreakable blocks such as bedrock are permanent obstacles whose matching build cells are skipped without stalling the demolition phase; if no demolition drone is available the shortage strategy either pauses or skips the remaining breakable obstacles
- A collection drone in free mode rescans its current 16-block radius after every inhale, has a 1-block reach, costs 2,560 FE per item entity, ignores experience orbs, and does not consume magnet durability; when all nine cargo slots are full it lands and stops inhaling; if the owner walks within 16 blocks it unloads into the owner's inventory with leftover stacks staying on the drone; in a demolition or debris job it only claims drops marked for that job, leaving unmarked nearby items for free-mode drones, players, or hoppers; missing collection drones never block construction, so leftover drops stay in the world and building continues
- Delivered construction projections stay as air with no block entity, but they look like the real block in the world (wood is opaque planks) and provide real collision so players can stand on them; undelivered holograms can be walked through; empty-collision targets such as redstone dust create no fake collision
- Pausing a job stops dispatch; a drone that already picked up materials flies back to the owner, puts the in-transit items into the inventory, then lands, while delivered fake blocks stay; cancelling also makes in-transit drones fly back to return items, then quietly commits delivered blocks and leaves undelivered cells as they are in the world
- An observation drone always keeps a safe-landing reserve of 20,480 FE and lands as soon as the reserve is reached; it will not take off below the 40,960 FE threshold
- A drone whose energy cannot cover a job quote plus the safe-landing reserve refuses the job

## Settings Screen

- Right-click a drone entity, or sneak and right-click while holding a drone item, to open the same settings screen; both forms read and write the same data, and settings survive recovery
- The screen shows the installed tool, owner, flight state, wait reason and energy; construction drones also show hosted carry; the energy bar shares the Plastic Molding Chamber layout
- Construction and demolition drones can switch the shortage strategy between pause-and-wait (default) and skip
- Collection drones additionally show their nine-slot cargo; observation drones show their chunk loading state

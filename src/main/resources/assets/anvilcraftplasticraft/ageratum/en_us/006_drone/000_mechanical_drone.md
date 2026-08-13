---
navigation:
  title: "Mechanical Drone"
  icon: "anvilcraftplasticraft:construction_drone"
categories:
  - tools
items:
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

## Propellers

The two propellers in the top corners must be "propeller" type products made in the Plastic Molding Chamber. On assembly the complete item data of both propellers is stored in the drone; each keeps its own player-made model, color and material and they are never merged into an averaged look. Both the entity and the item form render the two propellers as-is. Whenever either propeller carries a material trait, the whole drone gains that trait; once heat-resistant plastic is added, a single heat-resistant propeller will make the entire drone fireproof.

## Placement and Recovery

- Right-click a block face while holding a drone item to place the drone entity at the clicked position; entities do not snap to the block grid, and up to 8 drones can be stacked inside one block space
- The collision box of a drone entity is fixed at 0.5 × 0.5 × 0.5 blocks; players can stand on drones and push them around, and a pushed drone never passes through blocks or other entities
- Sneak and right-click a drone with any anvil hammer to recover it as its item variant; tool, both propellers, energy, settings and owner data survive every round trip between item and entity
- Attacking a drone drops its item with all data preserved; creative attacks remove it directly

Energy, flight and job capabilities of drones are unlocked in later stages.

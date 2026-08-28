---
navigation:
  title: "High-Viscosity Resin"
  icon: "anvilcraftplasticraft:high_viscosity_resin_block"
items:
  - anvilcraftplasticraft:liquid_high_viscosity_resin_bucket
  - anvilcraftplasticraft:high_viscosity_resin_block
---

# High-Viscosity Resin

## Liquid High-Viscosity Resin

Fast cooking in a cauldron or Fish Tank holding one bucket of water gives you 1000mB of Liquid High-Viscosity Resin per batch

<recipe id="anvilcraftplasticraft:fast_cooking/liquid_high_viscosity_resin"/>

- It flows very slowly: one block every 40gt, and never more than two blocks from its source
- Step in and non-player entities are immobilized outright, while you can still move at cobweb speed
- It does not evaporate, and buckets, Fish Tanks, fluid pipes, and Hardened Resin Cauldrons can all carry it

## Resin adhesive

Use a Liquid High-Viscosity Resin Bucket on an entity to select it — the selection may even include a falling Giant Anvil as a complete 3x3x3 structure. Then use it on a block or another entity to bond them, and the route color tells you up front whether the bond will take:

| Color | Direct distance to target | Result |
| --- | ---: | --- |
| Green | At most 12 blocks | Can bond |
| Yellow | More than 12 and at most 16 blocks | Can bond |
| Red | No valid route or more than 16 blocks | Cannot bond |

Move more than 20 blocks from the selected entity and the selection clears itself

With no entity selected, releasing use within 0.5 seconds still operates an interactive block normally, while holding use leaves a patch of exposed adhesive; non-interactive blocks receive adhesive immediately. An entity touching that face becomes stuck, and placing a block against it bonds the adjacent blocks together

- Pistons and Sliding Rails move a bonded block group as one
- An unanchored bonded entity group flies off together under knockback; once anchored to a block, the whole group rebounds around the bond
- Splash or lingering Invisibility potions permanently hide adhesive within 4 horizontal blocks and 2 vertical blocks of the impact — invisible, but still sticky

## High-Viscosity Resin Block

Time-warp a full bucket of Liquid High-Viscosity Resin to obtain a High-Viscosity Resin Block

<recipe id="anvilcraftplasticraft:time_warp/high_viscosity_resin_block"/>

- It keeps all of the Resin Block behavior: bouncing, slowing, mob capture, spawner capture, and time-warp recipes
- Captured mobs have no size limit, although hostile mobs must still have Weakness
- It adheres to adjacent blocks like a Slime Block; a complete group connected by High-Viscosity Resin counts as only one block against the piston push limit

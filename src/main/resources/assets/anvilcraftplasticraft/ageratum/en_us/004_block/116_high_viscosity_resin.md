---
navigation:
  title: "High-Viscosity Resin"
  icon: "anvilcraftplasticraft:high_viscosity_resin_block"
  position: 116
categories:
  - "anvilcraftplasticraft:production_blocks"
---

# High-Viscosity Resin

## Liquid High-Viscosity Resin

Place four resin, four slime balls, and one lime powder in a cauldron or fish tank holding a full bucket of water, then use fast cooking to transform it into 1000 mB of <ref item="anvilcraftplasticraft:liquid_high_viscosity_resin_bucket"/>.

- In the world it advances one block every 40 game ticks and travels at most two blocks from its source.
- Non-player entities are immobilized. Players can still move at cobweb speed.
- It does not evaporate and can be moved with buckets, fish tanks, fluid pipes, and hardened resin cauldrons.

## Adhesive Bucket

- Right-click an entity to select it, then right-click a block or another entity to bond them. Falling giant anvils are supported as complete 3x3x3 structures.
- While the server is searching, a moving white dashed line is shown only as a search indicator; it changes to a colored solid line when the result arrives. For a bondable target, that solid line is the collision-free route calculated by the server, and the white transit trail after confirmation reuses it. Green means the direct distance is at most 12 blocks, yellow means more than 12 but at most 16, and red means the current target cannot be bonded. Confirmation is blocked beyond 16 blocks, and the selection disconnects beyond 20.
- With no entity selected, release within 0.5 seconds to use an interactive block normally. Hold longer to place exposed adhesive; non-interactive blocks receive adhesive immediately.
- An entity touching exposed adhesive bonds to that face. Placing a block in front of the adhesive bonds both blocks.
- An unanchored bonded entity group moves as one under knockback. If any member is bonded to a block, the whole group rebounds together around that attachment.
- Pistons and sliding rails move bonded block groups together. Ordinary falling blocks do not time out while bonded, but still land as blocks and retain their bond.

## High-Viscosity Resin Block

Time-warp a full bucket of liquid high-viscosity resin in a cauldron or fish tank to obtain <ref item="anvilcraftplasticraft:high_viscosity_resin_block"/>.

- It retains resin-block bouncing, slowing, mob and spawner capture, dispenser capture, resentment, time-warp, and resin shock-base behavior.
- Captured mobs have no size limit, although hostile mobs still require Weakness.
- It adheres like a slime block and can join the moving list when an adjacent ordinary block is pushed.
- A complete group connected by high-viscosity resin consumes only one piston push budget.

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
- Route calculation starts from the entity's position when selected. Movement of up to 2 blocks does not restart an unfinished search; on confirmation, the selected group returns to that recorded position before following the route. If it moves more than 2 blocks, or remains displaced while a search is still unfinished for 2 seconds, calculation restarts from its current position.
- While the server is searching, a moving white dashed line is shown only as a search indicator; it changes to a colored solid line when the result arrives. For a bondable target, that solid line is the collision-free route calculated by the server, and the white transit trail after confirmation reuses it. Every collision check includes each member's real model and the physical collision shapes of mounted tray components. Green means the direct distance is at most 12 blocks, yellow means more than 12 but at most 16, and red means the current target cannot be bonded. Confirmation is blocked beyond 16 blocks, and the selection disconnects beyond 20. The endpoint aligns the center of the complete model collision-bounding face in the selected direction with the target block face and keeps the two faces flush; neither the first outermost Cube nor the entity center is used as the alignment anchor. Once the server result arrives, the selected group is drawn there using every member's real model and mounted tray components: a valid endpoint uses the standard pale-blue plastic overlay, while a rejected endpoint uses pale red. The translucent white checker pattern on the selected face uses fixed `4 px` cells tiled across its complete real area rather than stretching a fixed cell count.
- With no entity selected, release within 0.5 seconds to use an interactive block normally. Hold longer to place exposed adhesive; non-interactive blocks receive adhesive immediately.
- An entity touching exposed adhesive bonds to that face. Uncovered parts of the same face can still catch later entities. Placing a block in front of the adhesive, or letting a falling 3x3x3 giant anvil scrape an uncovered part of that face, bonds both blocks and holds them even if another entity is already stuck there.
- An unanchored bonded entity group moves as one under knockback. If any member is bonded to a block, the whole group rebounds together around that attachment.
- Pistons and sliding rails move bonded block groups together. Blocks a piston would normally break, such as levers, buttons, and torches, move with the group and stay intact. A plastic entity glued to a sliding rail becomes a block and then ignores that rail, so the rail does not carry it as cargo. Pistons can still move the rail and the glued block together. Ordinary falling blocks do not time out while bonded, but still land as blocks and retain their bond. If an entity in transit is removed or becomes a block, its transit state, route, and endpoint rendering end immediately. Plastic blockification still keeps the entity for one render handoff tick, but no transit trail remains during that tick.
- Splash or lingering Invisibility potions permanently hide adhesive within four horizontal blocks and two vertical blocks of the impact point. Hidden patches, stretched adhesive during rebound or rotation, and adhesive particle effects are no longer rendered, while every bond keeps functioning.

## High-Viscosity Resin Block

Time-warp a full bucket of liquid high-viscosity resin in a cauldron or fish tank to obtain <ref item="anvilcraftplasticraft:high_viscosity_resin_block"/>.

- It retains resin-block bouncing, slowing, mob and spawner capture, dispenser capture, resentment, time-warp, and resin shock-base behavior.
- Captured mobs have no size limit, although hostile mobs still require Weakness.
- It adheres like a slime block and can join the moving list when an adjacent ordinary block is pushed.
- A complete group connected by high-viscosity resin consumes only one piston push budget.

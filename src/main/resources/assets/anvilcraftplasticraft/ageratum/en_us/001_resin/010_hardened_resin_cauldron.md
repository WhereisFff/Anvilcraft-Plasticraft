---
navigation:
  title: "Hardened Resin Cauldron"
  icon: "anvilcraftplasticraft:hardend_resin_cauldron"
items:
  - anvilcraftplasticraft:hardend_resin_cauldron
---

# Hardened Resin Cauldron

<row halign="center">
<recipe id="anvilcraftplasticraft:hardend_resin_cauldron"/>
<recipe id="anvilcraftplasticraft:magnetic_hardend_resin_cauldron"/>
</row>

A cauldron you can carry away. Like a resin anvil it is an entity affected by gravity and buoyancy, and it attaches to blocks in 24 orientations

- 8 input slots, 8 output slots, and up to 1B of fluid, lava included
- Pipe Heads, Pumps, and Control Valves connect to all six faces
- Use it with an empty hand to take items back, or with a fluid container to insert or extract fluid
- Sneak-use it with any Anvil Hammer to collect the cauldron together with its items, though fluid does not come along
- A non-magnetic cauldron pours the moment its opening faces sideways or downward: partial fluid is discarded, a full tank creates a source when the target accepts it, and otherwise it just empties. A magnetized cauldron never pours on its own
- Catalysis and cauldron recipes require the opening to face up
- When facing up, catalysis, plasma jets, and every falling-anvil cauldron recipe search from the center and every covered part of the physical bottom face along current gravity for one block. A valid processing block below the center takes priority, then the remaining covered bottom cells are tried; that is why a small cauldron can share a cell with a campfire, and a cauldron taller than one block still reads the block beside its actual bottom
- Striking a cauldron with an Anvil Hammer processes only the one you actually hit. A falling anvil instead processes every cauldron whose collision box actually enters its landing cell, never cauldrons in neighboring cells
- Non-placeable fluids such as milk are still emptied, they simply leave no block behind
- In Creative mode, sneak-use a Magnet or a magnet-mode multitool to magnetize the cauldron

<warning>
A Hardened Resin Cauldron can store lava normally, but its body is not fire-resistant. Touching world lava still destroys the cauldron and its contents
</warning>

Plastic cauldrons are a first-class product type in the Plastic Molding Chamber, taking their shape and capacity from your own model while behaving exactly like a Hardened Resin Cauldron, and every plastic material can store lava normally. A model with a large enough opening upgrades to a Large Plastic Cauldron. See [Plastic Chests, Tanks and Cauldrons](../003_plastic/020_plastic_containers.md)

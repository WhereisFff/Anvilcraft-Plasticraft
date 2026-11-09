---
navigation:
  title: "Plastic Melts"
  icon: "anvilcraftplasticraft:universal_plastic_melt_bucket"
items:
  - anvilcraftplasticraft:plastic_oil_bucket
  - anvilcraftplasticraft:universal_plastic_melt_bucket
  - anvilcraftplasticraft:clear_plastic_melt_bucket
  - anvilcraftplasticraft:engineering_plastic_melt_bucket
  - anvilcraftplasticraft:heat_resistant_plastic_melt_bucket
  - anvilcraftplasticraft:elastic_plastic_melt_bucket
---

# Plastic Melts

<row halign="center">
<item id="anvilcraftplasticraft:plastic_oil_bucket"/>
<item id="anvilcraftplasticraft:universal_plastic_melt_bucket"/>
<item id="anvilcraftplasticraft:clear_plastic_melt_bucket"/>
<item id="anvilcraftplasticraft:engineering_plastic_melt_bucket"/>
<item id="anvilcraftplasticraft:heat_resistant_plastic_melt_bucket"/>
<item id="anvilcraftplasticraft:elastic_plastic_melt_bucket"/>
</row>

Melts come from environmental catalysis: drop a catalyst into the fluid, give it the right surroundings, and it changes on its own. JEI's Plastic Melt Catalysis category displays the five routes below. Look up recipes for the fluid or its bucket, or uses for a catalyst, to find the matching routes. Royal and frost materials have separate entries to show their different conditions and efficiencies. Catalysts are not consumed; the displayed 1000mB is an equal-volume conversion example. Hover over the bottom of the page for batch rules and branch priorities

The JEI page renders the input and output as fluid blocks, with valid heat sources or cold blocks cycling directly against the underside of the input. The catalyst sits below the models, with its efficiency on the right. Heat sources use states that actually provide heat; hover over them to see the block name, required conditions and heat value. The frost-metal route to Engineering Plastic Melt needs no environment block below

| Input | Catalyst | Environment | Output |
| --- | --- | --- | --- |
| Plastic Oil | Royal steel or frost metal | Heated from directly below | Universal Plastic Melt |
| Plastic Oil | Royal glass or frost glass | Heated from directly below | Clear Plastic Melt |
| Plastic Oil | Any resin item or its crafting derivatives | Cold directly below | Elastic Plastic Melt |
| Universal Plastic Melt | Royal steel or frost metal | Royal steel needs cold directly below | Engineering Plastic Melt |
| Universal Plastic Melt | Ember metal | A heat source directly below | Heat-Resistant Plastic Melt |

Plenty of vessels support open catalysis: Plastic Oil Cauldrons, Universal Plastic Melt Cauldrons, Fish Tanks, Large Cauldrons, upward-facing Hardened Resin and plastic cauldrons, and even fluid source blocks out in the world

Ordinary block containers and fluid sources read the condition directly beneath their container cell. An upward-facing Hardened Resin or plastic cauldron instead searches from the center and every covered part of its physical bottom face along current gravity for one block. A valid processing block below the center takes priority, then the remaining covered bottom cells are tried. That is why a small cauldron can share a cell with a campfire, while a cauldron taller than one block still reads the block outside its actual bottom

## Catalyst count

Catalysis counts types, not stacks — a whole chest of the same item is no faster. Royal steel, royal glass and ember metal contribute at full efficiency; frost metal and frost glass contribute at half. Adding more types does speed things up, but with diminishing returns. The good news is that catalysts are never consumed

For royal, ember or resin materials, one type gives a 0.25 multiplier and eight types give 0.5, with the ceiling at 0.55. Frost materials use half of the corresponding multiplier

For paired royal and frost materials, including glass, let their distinct type counts be `R` and `F`. Their mixed multiplier is `M(R) + 0.5 * (M(R + F) - M(R))`, where `M(n) = min(0.55, 0.25 + log2(n) / 12)` and `M(0) = 0`

## Plastic Oil catalysis

Plastic Oil has heated and cold processing routes. When heated, hotter sources make more progress. Royal steel and frost metal produce Universal Plastic Melt, while royal glass or frost glass takes priority for Clear Plastic Melt. With cold below and a resin catalyst present, the Elastic Plastic route takes priority

Clear Plastic Melt supports all 16 dye colors, and that color carries through molding, cooling and buckets. The trade-off is that it stops there — it cannot continue into the metal Engineering Plastic branch

## Elastic Plastic Melt

Resin, Hardened Resin, Resin Blocks, High-Viscosity Resin Buckets, High-Viscosity Resin Blocks, Resin Anvils, Hardened Resin Anvils, Hardened Resin Cauldrons, Catalytic Press Lids and Resin Anvil Hammers all catalyze this route. Items made from them by crafting or smithing count recursively, including processors and machines crafted with processors. Reusable crafting tools and ordinary metals recovered by smelting or crushing do not spread resin identity

Put a resin catalyst in an ordinary cauldron, fish tank, large cauldron, plastic cauldron or world Plastic Oil source, then provide cold below. An upward-facing Hardened Resin Cauldron already counts as one resin catalyst: fill it with Plastic Oil and provide cold underneath, with no extra resin item needed

Cold follows the Engineering Plastic rules and includes water, ice, packed ice, blue ice, snow blocks, powder snow, snow layers and other accepted coolant blocks or blocks whose items qualify as cold. Neither the cold block nor the resin catalyst is consumed. Plastic Oil becomes an equal volume of melt at a 1:1 ratio while preserving color

Open catalysis with one distinct resin item takes 400gt (20 seconds), eight take 200gt (10 seconds), and maximum efficiency takes 182gt (about 9.1 seconds). Extra copies of one item do not speed it up. A sealed [Catalytic Press Lid](020_catalytic_press_lid.md) supplies a resin catalytic surface and completes the cold route at full speed in 100gt (5 seconds). Elastic Melt accepts all 16 dyes and does not continue into Engineering or Heat-Resistant Plastic

## Engineering Plastic Melt

- Royal steel is picky: it only contributes while a cold block sits directly below the melt. One type takes 400gt, eight take 200gt
- Frost metal is not picky at all, needing neither cooling nor heat, but it runs at half efficiency. One type takes 800gt, eight take 400gt

## Heat-Resistant Plastic Melt

The heat-resistant branch needs at least one ember-metal type plus a heat source below. More heat and more distinct ember-metal types make it faster, again with diminishing returns from extra types

When both the engineering and heat-resistant conditions are met, the heat-resistant branch takes priority

## Batches and dyeing

A Large Cauldron uses the average actual heat output of the nine blocks in the 3x3 area directly beneath its floor and can process a full fluid layer at once. The engineering and elastic branches are exceptions: they read cold only from the center block

All five Plastic Melts can be dyed, and catalysis preserves the melt's amount and color. Clear Plastic products look like stained glass, and each color tints beacon beams passing through it exactly like the matching stained glass. Elastic Plastic instead has a translucent shell and core like a Resin Block and retains Resin Anvil bouncing

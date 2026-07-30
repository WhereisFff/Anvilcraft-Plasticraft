---
navigation:
  title: "Universal Plastic Melt and Royal-Steel/Frost-Metal Catalysis"
  icon: "anvilcraftplasticraft:universal_plastic_melt_bucket"
items:
  - anvilcraftplasticraft:plastic_oil_bucket
  - anvilcraftplasticraft:universal_plastic_melt_bucket
  - anvilcraftplasticraft:catalytic_press_lid
---

# Universal Plastic Melt and Royal-Steel/Frost-Metal Catalysis

Converting plastic oil into universal plastic melt is an environmental reaction, not a recipe. The reaction advances automatically when plastic oil, a royal-steel or frost-metal product, and a valid heat source are in contact, so JEI has no separate catalytic-pressing recipe.

## Open catalysis

Place an item in the `anvilcraftplasticraft:royal_steel_items` or `anvilcraftplasticraft:frost_metal_items` tag into plastic oil and put a heat source directly against the underside of its carrier. These carriers are supported:

- a four-level cauldron of plastic oil;
- a Fish Tank;
- a Large Cauldron;
- an upward-facing Hardened Resin Cauldron;
- a plastic-oil source block in the world.

Royal steel and frost metal are not consumed. Speed counts distinct item IDs within each material, so multiple copies of one item still count as one catalyst. For `n` royal-steel types, the open-reaction multiplier is `M(n) = 0.25 + log2(n) / 12`, capped at 0.55: one royal-steel item runs at one quarter of sealed-lid speed, while eight distinct items run at exactly one half.

The same number of frost-metal types runs at exactly half the corresponding royal-steel multiplier: one type runs at 12.5% of sealed-lid speed and eight types run at 25%. Frost metal retains royal-steel magic, but its low temperature offsets part of the external heat. When the materials are mixed, the multiplier is `M(R) + 0.5 * (M(R + F) - M(R))`, where `R` and `F` are their distinct royal-steel and frost-metal type counts. Each frost-metal type therefore supplies half the gain of an additional royal-steel type and never reduces the existing royal-steel speed.

Valid heat sources include everything recognized by a Heat Collector, plus active burning and electric heaters. Each source retains its actual heat output; mixed sources are not treated as copies of one selected source.

## Large Cauldrons

A Large Cauldron reads the 3×3 area directly below its floor. Its final heat output is the sum of the nine actual outputs divided by 9. Nine identical sources match a Fish Tank over that source; one source runs at one ninth of that rate; mixed sources contribute their individual outputs. One completed batch converts the entire plastic-oil layer, allowing up to 64 B to be processed at once.

## Catalytic Press Lid

<recipe id="anvilcraftplasticraft:catalytic_press_lid"/>

The Catalytic Press Lid supplies its own royal-steel catalytic surface. Bond it directly above a compatible vessel with high-viscosity resin and heat the vessel from below. No loose royal-steel or frost-metal item is required, and the sealed reaction runs at full speed. Once the reaction finishes, strike the arm with a falling anvil. Melt leaves through a vessel outlet or connected pipe network first; if neither route can accept it, the pressure ruptures the vessel and launches the lid.

If the vessel outlet points directly at a full vanilla water cauldron, pressing consumes exactly `1000 mB` of universal plastic melt and the full cauldron of water, then drops 16 granules with the melt's colour at the cauldron. Less than `1000 mB`, a partially filled water cauldron, or a failed transaction consumes neither resource; ordinary outlet and pipe handling remains unchanged for every other target.

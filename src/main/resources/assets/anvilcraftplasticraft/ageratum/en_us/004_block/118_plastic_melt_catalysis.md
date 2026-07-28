---
navigation:
  title: "Universal Plastic Melt and Royal-Steel Catalysis"
  icon: "anvilcraftplasticraft:universal_plastic_melt_bucket"
items:
  - anvilcraftplasticraft:plastic_oil_bucket
  - anvilcraftplasticraft:universal_plastic_melt_bucket
  - anvilcraftplasticraft:catalytic_press_lid
---

# Universal Plastic Melt and Royal-Steel Catalysis

Converting plastic oil into universal plastic melt is an environmental reaction, not a recipe. The reaction advances automatically when plastic oil, a royal-steel product, and a valid heat source are in contact, so JEI has no separate catalytic-pressing recipe.

## Open catalysis

Place an item in the `anvilcraftplasticraft:royal_steel_items` tag into plastic oil and put a heat source directly against the underside of its carrier. These carriers are supported:

- a four-level cauldron of plastic oil;
- a Fish Tank;
- a Large Cauldron;
- an upward-facing Hardened Resin Cauldron;
- a plastic-oil source block in the world.

Royal steel is not consumed. Speed counts distinct item IDs, so multiple copies of one item still count as one catalyst. The open-reaction multiplier is `0.25 + log2(distinct items) / 12`, capped at 0.55: one royal-steel item runs at one quarter of sealed-lid speed, while eight distinct items run at exactly one half.

Valid heat sources include everything recognized by a Heat Collector, plus active burning and electric heaters. Each source retains its actual heat output; mixed sources are not treated as copies of one selected source.

## Large Cauldrons

A Large Cauldron reads the 3×3 area directly below its floor. Its final heat output is the sum of the nine actual outputs divided by 9. Nine identical sources match a Fish Tank over that source; one source runs at one ninth of that rate; mixed sources contribute their individual outputs. One completed batch converts the entire plastic-oil layer, allowing up to 64 B to be processed at once.

## Catalytic Press Lid

<recipe id="anvilcraftplasticraft:catalytic_press_lid"/>

The Catalytic Press Lid supplies its own royal-steel catalytic surface. Bond it directly above a compatible vessel with high-viscosity resin and heat the vessel from below. No loose royal-steel item is required, and the sealed reaction runs at full speed. Once the reaction finishes, strike the arm with a falling anvil. Melt leaves through a vessel outlet or connected pipe network first; if neither route can accept it, the pressure ruptures the vessel and launches the lid.

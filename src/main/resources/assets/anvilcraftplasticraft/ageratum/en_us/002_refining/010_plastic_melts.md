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
---

# Plastic Melts

<row halign="center">
<item id="anvilcraftplasticraft:plastic_oil_bucket"/>
<item id="anvilcraftplasticraft:universal_plastic_melt_bucket"/>
<item id="anvilcraftplasticraft:clear_plastic_melt_bucket"/>
<item id="anvilcraftplasticraft:engineering_plastic_melt_bucket"/>
<item id="anvilcraftplasticraft:heat_resistant_plastic_melt_bucket"/>
</row>

Plastic melts are produced by environmental catalysis through four routes:

| Input | Catalyst | Environment | Output |
| --- | --- | --- | --- |
| Plastic Oil | Royal steel or frost metal | Heated from directly below | Universal Plastic Melt |
| Plastic Oil | Royal glass or frost glass | Heated from directly below | Clear Plastic Melt |
| Universal Plastic Melt | Royal steel or frost metal | Royal steel needs cold directly below | Engineering Plastic Melt |
| Universal Plastic Melt | Ember metal | A heat source directly below | Heat-Resistant Plastic Melt |

Plastic Oil Cauldrons, Universal Plastic Melt Cauldrons, Fish Tanks, Large Cauldrons, upward-facing Hardened Resin Cauldrons and fluid source blocks in the world all support open catalysis

## Catalyst count

Open catalysis counts distinct item types. Extra copies of the same item do not increase speed. Royal steel, royal glass and ember metal contribute at full efficiency; frost metal and frost glass contribute at half efficiency. Adding more types gives diminishing returns, and catalysts are not consumed

For royal or ember materials, one type gives a 0.25 multiplier and eight types give 0.5; the multiplier is capped at 0.55. Frost materials use half of the corresponding multiplier

For paired royal and frost materials, including glass, let their distinct type counts be `R` and `F`. Their mixed multiplier is `M(R) + 0.5 * (M(R + F) - M(R))`, where `M(n) = min(0.55, 0.25 + log2(n) / 12)` and `M(0) = 0`

## Plastic Oil catalysis

Plastic Oil must be heated from directly below and progresses with the heat source's actual output. Royal steel and frost metal produce Universal Plastic Melt. If royal glass or frost glass is also present, the clear branch takes priority

Clear Plastic Melt supports all 16 dye colors; its color is retained through molding, cooling and buckets, but it cannot continue into the metal Engineering Plastic branch

## Engineering Plastic Melt

- Royal steel contributes only while a cold block is directly below the melt; one type takes 400gt and eight take 200gt
- Frost metal needs neither cooling nor heat but runs at half efficiency; one type takes 800gt and eight take 400gt

## Heat-Resistant Plastic Melt

The heat-resistant branch needs at least one ember-metal type and a heat source below. More heat and more distinct ember-metal types make it faster, with diminishing returns from extra types

When both the engineering and heat-resistant conditions are met, the heat-resistant branch takes priority

## Batches and dyeing

A Large Cauldron uses the average actual heat output of the nine blocks in the 3x3 area directly beneath its floor and can process a full fluid layer at once. The engineering branch reads cold only from the center block

All four Plastic Melts can be dyed. Catalysis preserves the melt's amount and color, and Clear Plastic products use a stained-glass appearance. Each color tints beacon beams like the matching stained glass

---
navigation:
  title: "Condenser Tower"
  icon: "anvilcraftplasticraft:condenser_tower"
items:
  - anvilcraftplasticraft:condenser_tower
  - anvilcraftplasticraft:high_heat_fuel_bucket
  - anvilcraftplasticraft:plastic_oil_bucket
  - anvilcraftplasticraft:crude_oil_acid_bucket
---

# Condenser Tower

Targeting any tower part highlights the precise model outline of the entire 3x3x3 module, while the crosshair can still pass through model gaps. Hits belong to the actual targeted cell, keeping the four lower fluid ports distinct

<row halign="center">
<recipe id="anvilcraftplasticraft:multiblock/condenser_tower"/>
<recipe id="anvilcraftplasticraft:multiblock_conversion/condenser_tower"/>
</row>

One module requires 4 High-Viscosity Resin Blocks, 4 Straight Pipes, 8 Cut Brass Pillars, 8 Glass blocks, and 2 Copper Trapdoors, arranged from bottom to top

- Bottom: resin blocks in the corners, outward-facing horizontal pipes at the four edge centers, and a bottom-half copper trapdoor in the center
- Middle: vertical brass pillars in the corners, glass at the four edge centers, and air in the center
- Top: vertical brass pillars in the corners, glass at the four edge centers, and a top-half copper trapdoor in the center

Both trapdoors must be closed and face north, and neither the pipes nor the trapdoors may be waterlogged. The whole structure can be rotated horizontally. Pipe end caps and check valves are optional

Place a 3x3 layer of crafting tables directly above the structure and drop a Giant Anvil onto it to convert the structure into a tower in place. Replace the center crafting table with a Space Overcompressor to consume the structure and produce one Condenser Tower item instead

One Condenser Tower module takes up 3x3x3 blocks and stacks straight onto a Large Cauldron. Every layer comes with its own 64B condensate tank, 64B gas buffer, and four output-only ports

A tower stack supports up to five contiguous layers. Crude-oil separation uses the first three, water and experience use only the first, and anything you stack above that is just scenery

## Vaporization rate

- Each normal jet beneath a Large Cauldron vaporizes 5mB of the top fluid per gt, and multiple jets simply add their rates together
- High-Heat Fuel upgrades a jet to an enhanced jet that burns through 50mB per gt, at the cost of 10mB of High-Heat Fuel at the same time
- A High-Heat Fuel Cauldron takes one 250mB layer at a time and extends the enhanced jet by 50gt; other ignited fluid containers supply fuel continuously

Worth mentioning: High-Heat Fuel is ignitable itself, and its fire deals twice the damage of ordinary fuel fire

## Crude-oil separation

In JEI, hover over a High-Heat Fuel Bucket, Plastic Oil Bucket, or Crude Oil Essence Bucket and press the recipe key (R by default) to view its condensation recipe. The page shows the actual fluid amount processed per operation

Gaseous crude oil rises through the tower on its own, and the first three layers separate each 50mB batch exactly:

| Layer | Input | Output |
| --- | ---: | ---: |
| First | 10mB gaseous crude oil | 10mB <ref item="anvilcraftplasticraft:high_heat_fuel_bucket"/> |
| Second | 30mB gaseous crude oil | 30mB <ref item="anvilcraftplasticraft:plastic_oil_bucket"/> |
| Third | 10mB gaseous crude oil | 10mB <ref item="anvilcraftplasticraft:crude_oil_acid_bucket"/> |

Do not throw away that third-layer Crude Oil Essence — it is a multiplier: 1B Crude Oil Essence plus 1B High-Heat Fuel produces 3B High-Heat Fuel, or 3B Plastic Oil when Plastic Oil is used instead

<row halign="center">
<recipe id="anvilcraftplasticraft:fluid_mixing/high_heat_fuel_enrichment"/>
<recipe id="anvilcraftplasticraft:fluid_mixing/plastic_oil_enrichment"/>
</row>

## Water and experience

The first layer handles these two jobs on the side:

| Input | Output |
| ---: | ---: |
| 50mB gaseous water | 50mB water |
| 50mB gaseous experience | 50mB experience fluid |

Gaseous experience nobody caught drifts out, and players or employed adult villagers within three blocks horizontally and three blocks above an open outlet can absorb it. Without a tower, every open Large Cauldron outlet emits it; with a tower, only the highest layer's open center outlet does, so standing underneath the tower gets you nothing. You gain 1 experience point per 10mB, while a villager advances from novice to master after absorbing 64B; multiple targets split the gas equally

Escaping water vapor helpfully extinguishes flames, campfires, candles, and burning entities in the same area. Crude-oil vapor does the opposite: it gathers into a temporary cloud that can be ignited

## Vapor backpressure

While the top of the tower is open, excess vapor simply escapes and nothing happens. Seal the highest layer's top-center outlet with a full block, though, and backpressure kicks in as soon as any layer has both a full condensate tank and a full gas buffer, stopping vaporization for the complete line

Without a Condenser Tower, covering all 3x3 outlets above a Large Cauldron does the same thing. Backpressure also removes and extinguishes active jets, so after clearing the obstruction and draining the tower you still have to rebuild the jets to resume processing

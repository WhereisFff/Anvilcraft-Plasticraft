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

<row halign="center">
<recipe id="anvilcraftplasticraft:multiblock/condenser_tower"/>
<recipe id="anvilcraftplasticraft:multiblock_conversion/condenser_tower"/>
</row>

Each Condenser Tower module occupies 3x3x3 blocks and can stack directly above a Large Cauldron. Every layer has its own 64B condensate tank, 64B gas buffer, and four output-only ports

A tower stack supports up to five contiguous layers. Crude-oil separation uses the first three; water and experience use only the first

## Vaporization rate

- Each normal jet beneath a Large Cauldron vaporizes 5mB of the top fluid per gt; multiple jets add their rates
- High-Heat Fuel creates an enhanced jet that vaporizes 50mB per gt while consuming 10mB of High-Heat Fuel
- A High-Heat Fuel Cauldron consumes one 250mB layer at a time and extends the enhanced jet by 50gt; other ignited fluid containers supply fuel continuously

High-Heat Fuel is ignitable, and its fire deals twice the damage of ordinary fuel fire

## Crude-oil separation

Gaseous crude oil rises through the tower. The first three layers exactly separate each 50mB batch:

| Layer | Input | Output |
| --- | ---: | ---: |
| First | 10mB gaseous crude oil | 10mB <ref item="anvilcraftplasticraft:high_heat_fuel_bucket"/> |
| Second | 30mB gaseous crude oil | 30mB <ref item="anvilcraftplasticraft:plastic_oil_bucket"/> |
| Third | 10mB gaseous crude oil | 10mB <ref item="anvilcraftplasticraft:crude_oil_acid_bucket"/> |

Crude Oil Essence also supports fluid mixing: 1B Crude Oil Essence plus 1B High-Heat Fuel produces 3B High-Heat Fuel, or 3B Plastic Oil when Plastic Oil is used instead

<row halign="center">
<recipe id="anvilcraftplasticraft:fluid_mixing/high_heat_fuel_enrichment"/>
<recipe id="anvilcraftplasticraft:fluid_mixing/plastic_oil_enrichment"/>
</row>

## Water and experience

The first tower layer supports these condensations:

| Input | Output |
| ---: | ---: |
| 50mB gaseous water | 50mB water |
| 50mB gaseous experience | 50mB experience fluid |

Escaping gaseous experience can be absorbed by players or employed adult villagers within three blocks horizontally and three blocks above an open outlet. Without a tower, each open Large Cauldron outlet emits it; with a tower, only the highest layer's open center outlet does, and gas cannot be absorbed through the tower from below. A player gains 1 experience point per 10mB, while a villager advances from novice to master after absorbing 64B; multiple targets split the gas equally

Escaping water vapor extinguishes flames, campfires, candles, and burning entities in the same area. Crude-oil vapor forms a temporary cloud that can be ignited

## Vapor backpressure

Excess vapor escapes while the top of the tower is open. Sealing the top-center outlet of the highest layer with a full block causes backpressure once any layer has both a full condensate tank and a full gas buffer, stopping vaporization for the complete line

Without a Condenser Tower, covering all 3x3 outlets above a Large Cauldron causes the same backpressure. Backpressure removes and extinguishes active jets; after removing the obstruction and draining the tower, rebuild the jets to resume processing

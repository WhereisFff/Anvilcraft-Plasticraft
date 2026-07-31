---
navigation:
  title: "Condenser Towers and Enhanced Jets"
  icon: "anvilcraftplasticraft:condenser_tower"
  position: 117
categories:
  - "anvilcraftplasticraft:production_blocks"
---

# Condenser Towers and Enhanced Jets

## Building a condenser stack

Each <ref item="anvilcraftplasticraft:condenser_tower"/> module occupies a 3x3x3 area and stacks directly above a Large Cauldron. Every layer has a separate 64 B condensate tank, a 64 B gas buffer, and four output-only ports. Both the Anvil Hammer overlay and Jade show its current contents and capacity.

When normal plasma jets touch the underside of a Large Cauldron, each jet vaporizes 5 mB of the top fluid per game tick. Multiple jets add their rates together. Without a condenser, or while the top of the stack remains open, vapor that cannot be collected vents into the air.

## Fractionating crude oil

Gaseous crude oil rises through the condenser stack. The first three layers fully separate each 50 mB batch:

| Layer | Input | Output |
| --- | --- | --- |
| First | 10 mB gaseous crude oil | 10 mB <ref item="anvilcraftplasticraft:high_heat_fuel_bucket"/> |
| Second | 30 mB gaseous crude oil | 30 mB <ref item="anvilcraftplasticraft:plastic_oil_bucket"/> |
| Third | 10 mB gaseous crude oil | 10 mB <ref item="anvilcraftplasticraft:crude_oil_acid_bucket"/> |

A normal jet creates only 5 mB of gaseous oil per game tick. The first layer buffers the first 5 mB, then condenses one 10 mB batch after the next game tick. An enhanced jet creates 50 mB per game tick and saturates the three-layer process. The fourth productive layer is reserved for future recipes that need a taller stack.

## Gaseous experience

Experience fluid vaporizes into gaseous experience at a 1:1 ratio and produces slowly rising experience particles above the liquid. The first condenser layer converts each 50 mB of gaseous experience back into 50 mB of experience fluid. It fills the condensate tank first. After that tank reaches 64 B, gaseous experience continues filling the separate 64 B gas buffer. Vapor escapes from an open tower top only after both buffers are full.

Without a condenser, an adult employed villager or player can absorb escaping gaseous experience within three blocks horizontally and up to three blocks above any actual open outlet of the Large Cauldron. With a condenser installed, the range is measured from the actual open center outlet above the highest layer; vapor cannot be absorbed through the tower from below. A player gains one experience point per 10 mB, twice the yield of the corresponding experience fluid. A villager reaches master after absorbing a total of 64 B. Multiple eligible entities split each release equally with ordinary rounding; for example, three entities receive 33 mB each from a 100 mB release.

## Escaping vapor

Outlet-particle intensity follows the amount that actually escapes after condensation. These particles are sparser and disappear sooner than vapor rising inside the cauldron. Water vapor extinguishes flames, campfires, candles, and burning entities within three blocks horizontally and up to three blocks above an outlet.

Gaseous oil forms a short-lived ignitable cloud over the same area. Use flint and steel or a fire charge on a nearby block, or throw a torch or high-temperature block into the cloud, to ignite a blue high-heat-fuel flame. The flame disappears after venting stops, and water vapor in range extinguishes it.

## Enhanced plasma jets

Use high-heat fuel instead of crude oil as plasma-jet fuel to create an enhanced jet with blue particles. Each enhanced jet vaporizes 50 mB per game tick while consuming 10 mB of high-heat fuel per game tick from a fluid-capable fuel cauldron.

A vanilla level-based cauldron must hold a full 1000 mB of high-heat fuel. The jet consumes 250 mB at a time and adds 50 game ticks to its remaining duration. Full fluid containers such as fish tanks and hardened resin cauldrons instead supply fuel continuously each game tick.

## Vapor backpressure

A condenser stack is open by default, allowing excess vapor to vent from its top. Placing a full block directly above the top-center of the highest module seals the entire stack. Once a module's 64 B condensate tank and 64 B gas buffer are both full, backpressure stops further vaporization and visible vapor accumulates above the cauldron. Backpressured experience fluid uses suspended experience particles.

Without a condenser stack, covering all nine blocks of the Large Cauldron's 3x3 outlet has the same effect. Remove the obstruction or drain the full condenser module to resume processing.

---
navigation:
  title: "Catalytic Press Lid"
  icon: "anvilcraftplasticraft:catalytic_press_lid"
items:
  - anvilcraftplasticraft:catalytic_press_lid
---

# Catalytic Press Lid

<recipe id="anvilcraftplasticraft:catalytic_press_lid"/>

Bond a Catalytic Press Lid directly above a compatible vessel with High-Viscosity Resin and you get a sealed royal-steel catalytic surface. From then on the Plastic Oil to Universal Plastic route and the chilled Universal Plastic to Engineering Plastic route both run at full speed, with no royal-steel item in the vessel at all

- The clear route does not benefit: it still needs royal glass or frost glass inside the vessel and uses the glass catalysts' open multiplier
- The heat-resistant branch counts distinct ember-metal items in the vessel inventory and loose items inside it, and the heat source is still mandatory
- Ordinary block vessels read heat and cold directly below. Upward-facing Hardened Resin and plastic cauldrons instead search from the center and every covered part of their physical bottom face along current gravity for one block. A valid processing block below the center takes priority, then the remaining covered bottom cells are tried
- How much heat, whether cold is needed, and which branch wins all follow [Plastic Melts](010_plastic_melts.md)

After the reaction finishes, strike the press arm with a falling anvil. The melt first tries to leave through the vessel outlet or connected pipe network; if it has nowhere to go, pressure ruptures the vessel and launches the lid along with it

## Making granules

Point the vessel outlet at a full vanilla water cauldron. Pressing consumes exactly 1000mB of any plastic melt and the full cauldron of water, then drops 16 granules matching the melt's material and color

If the melt or water requirement is not met, or pressing cannot complete, neither resource is consumed — so feel free to try again

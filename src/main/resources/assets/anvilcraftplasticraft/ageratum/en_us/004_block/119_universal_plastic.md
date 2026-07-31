---
navigation:
  title: "Universal Plastic"
  icon: "anvilcraftplasticraft:universal_plastic"
items:
  - anvilcraftplasticraft:universal_plastic
  - anvilcraftplasticraft:universal_plastic_granule
---

# Universal Plastic

A universal-plastic-melt fluid block placed in the world always solidifies in place as one universal plastic block, regardless of which world mechanism caused the change. Rain, adjacent water, and blocks in `anvilcraftplasticraft:plastic_melt_coolants` are current examples. The product keeps the melt's colour and the world conversion never also drops granules.

The block is `16 px` wide, `16 px` long, and `14 px` high. It has no menu, inventory, tank, fluid capability, or special storage. It retains the common plastic-product behaviour instead: gravity, buoyancy, dynamic attraction, pushing, sliding-rail transport, magnetisation, adhesive bonding, persistence, clicked-face placement, hammer quick-use and six-face rotation, and colour-preserving recovery. Hammer rotation uses the centre of the actual outline bounds, so flipping between upright and upside-down does not shift the block because of its missing `2 px`; a target orientation that would overlap a block or entity renders pale red and is not applied on release. Its texture is generated from the common plastic base and the selected 16-level colour palette. Base greys map by absolute brightness across `0..255`, so a base that uses only a bright range is not forcibly darkened. Resource reloads regenerate it from the complete edited base pattern, preserving the border around every exposed face.

For gravity-affected blocks such as sand and anvils, the `14 px` top is not a block-grid-aligned landing surface: a block that is already falling shatters into its corresponding drop on impact. A gravity-affected block placed directly one cell above universal plastic remains a block until the logical cell below is completely unoccupied by the plastic entity.

## Plastic granules

Granules are deliberately limited to recipe or container processing. Each of these routes produces 16 granules with the melt's colour per `1000 mB`:

- the existing solid-liquid cooling recipe with a cold item;
- a Catalytic Press Lid outlet aimed at a full vanilla water cauldron.

These routes do not change the rule for a melt fluid block in the world. Each novice AnvilCraft Jeweler offer randomly requests one of the 16 colours, buying 8 granules of that colour for 2 emeralds, up to 16 trades per offer.

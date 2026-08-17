---
navigation:
  title: "Production Modes, Platform and Logistics"
  icon: "anvilcraftplasticraft:plastic_molding_chamber"
---

# Production Modes, Platform and Logistics

## Casting platform

For Casting, build a 3x3 platform directly above the top layer of the 3x3x3 forming region and let the giant anvil land on it:

- With nine <ref item="minecraft:crafting_table"/> blocks, the impact creates a plastic entity that keeps the model shape
- With eight crafting tables and <ref item="anvilcraft:space_overcompressor"/> in the center, the impact creates a plastic product item

The platform, chamber and giant anvil are not consumed. 3D Printing needs no platform and ignores impacts

## Three production modes

- **Continuous**: keeps the model after each success. Casting prepares clay again after the region clears and waits for another impact; Printing pumps and prints the next complete batch automatically
- **Redstone control**: the default; while unlocked, one redstone rising edge starts one cycle. A sustained signal does not repeat, and the next cycle waits for another edge
- **Single**: manually locks one cycle, then returns to editing after the product completes and ignores redstone. Casting still needs an impact; Printing completes automatically when its batch is full

The 3D Printing Component selects the forming method independently of these production modes. Changing a mode affects the next cycle after the current one finishes

## Inputs and outputs

The chamber's top and bottom expose one bidirectional fluid interface. The four horizontal faces accept clay items but not fluids. The 3D Printing Component above has separate storage

External melt enters staging first, then moves into the casting batch or printing component when the model, structure, lock and power are ready

A successful Casting cycle consumes only its forming batch and returns the mold's clay to the filter slot; any overflow drops into the forming region. Unlocking manually also returns molded clay to the filter slot first. 3D Printing never reserves or consumes clay

An entity in the region, missing clay, missing melt or missing power pauses the cycle without consuming additional resources. Once the condition is cleared, processing resumes

## Related

- [Plastic Molding](index.md)
- [Molding Chamber](000_molding_chamber.md)

---
navigation:
  title: "Production Modes, Platform and Logistics"
  icon: "anvilcraftplasticraft:plastic_molding_chamber"
---

# Production Modes, Platform and Logistics

## Casting platform

Casting works by impact. Build a 3x3 platform flush above the top layer of the 3x3x3 forming region, then let the giant anvil land on it. What sits in the platform's center decides whether you get an entity or an item:

- With nine <ref item="minecraft:crafting_table"/> blocks, the impact creates a plastic entity that keeps the model shape
- With eight crafting tables and <ref item="anvilcraft:space_overcompressor"/> in the center, the impact creates a plastic product item

The platform, chamber and giant anvil are never consumed, so hammer away. 3D Printing needs no platform and ignores impacts entirely

## Three production modes

- **Continuous**: keeps the model after each success. Casting prepares clay again once the region clears and waits for your next impact; Printing pumps and prints the next complete batch automatically
- **Redstone control**: the default; while unlocked, one redstone rising edge starts one cycle. A sustained signal does not repeat, and the next cycle waits for another edge
- **Single**: manually locks one cycle, then returns to editing after the product completes and ignores redstone. Casting still needs your impact; Printing completes automatically when its batch is full

The 3D Printing Component selects the forming method independently of these production modes. Change a mode mid-cycle and the change only applies to the next cycle

## Inputs and outputs

The chamber's top and bottom expose one bidirectional fluid interface, which is where staged melt goes in and comes out. The four horizontal faces accept clay items but not fluids. The 3D Printing Component above has separate storage

External melt enters staging first, then moves into the casting batch or printing component once the model, structure, lock and power are all ready

A successful Casting cycle consumes only its forming batch and returns the mold's clay to the filter slot; any overflow drops into the forming region. Unlocking manually also returns molded clay to the filter slot first. 3D Printing never reserves or consumes clay

A player, dropped item or other entity in the region parks the cycle in a blocked state rather than burning resources, and it resumes on its own once the region is clear. Missing clay, melt or power behaves the same way, and completed progress is kept either way

## Related

- [Plastic Molding](index.md)
- [Molding Chamber](000_molding_chamber.md)

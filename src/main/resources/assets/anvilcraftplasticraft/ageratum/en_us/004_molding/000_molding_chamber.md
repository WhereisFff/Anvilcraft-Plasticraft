---
navigation:
  title: "Molding Chamber"
  icon: "anvilcraftplasticraft:plastic_molding_chamber"
items:
  - anvilcraftplasticraft:plastic_molding_chamber
---

# Plastic Molding Chamber

<recipe id="anvilcraftplasticraft:plastic_molding_chamber"/>

## Forming region

Placing a <ref item="anvilcraftplasticraft:plastic_molding_chamber"/> reserves a 3x3x3 forming region behind the block. Model coordinates use pixels: the ordinary workspace is 48x48x48px, with 16px per world block. A model may contain volumetric Cubes and zero-thickness planes

Casting accepts an outer span of at most 48px on every axis. 3D Printing accepts only a model inside the 32x32x32px printing region, with 8px margins on the X/Z edges and a 1px bottom margin. An oversized model can still be loaded and edited, but cannot be locked for processing

## Starting a cycle

Add at least one Cube in the editor, then lock the model. Locking keeps the model and forming method fixed for that cycle and determines the method from the model and the structure above the chamber. An invalid model or structure does not consume clay, melt or power

Without a 3D Printing Component, an ordinary model uses Casting. Installing <ref item="anvilcraftplasticraft:plastic_3d_printing_component"/> exactly one block above the chamber changes every model to 3D Printing. The method is fixed when the cycle is locked and cannot switch midway

If the model does not meet the selected product type, place <ref item="anvilcraft:multiphase_transcendium"/> in the resource slot to force the chamber to process it as that type instead of rejecting the cycle. It cannot bypass the Casting or Printing size limit, and one is consumed per cycle only when the type actually needs overriding

## Related

- [Edit, import and save models](010_model_editor_and_import.md)
- [Cast and print](020_casting_and_printing.md)
- [Production modes, platform and logistics](030_production_modes.md)

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

Place a <ref item="anvilcraftplasticraft:plastic_molding_chamber"/> and it reserves a 3x3x3 forming region behind the block — that is where your product shows up. Model coordinates use pixels: the ordinary workspace is 48x48x48px, with 16px per world block. A model may contain volumetric Cubes and zero-thickness planes

The two forming methods have different size gates. Casting accepts an outer span of at most 48px on every axis; 3D Printing is stricter and only accepts a model inside the 32x32x32px printing region, with 8px margins on the X/Z edges and a 1px bottom margin. An oversized model can still be loaded and edited, it just cannot be locked for processing

## Starting a cycle

Add at least one Cube in the editor, then lock the model. The moment you lock, the model and forming method for that cycle are settled, and the method is determined from the model and the structure above the chamber. If the model, material or structure falls short, the cycle does not waste clay, melt or power

Without a 3D Printing Component, an ordinary model uses Casting. Install <ref item="anvilcraftplasticraft:plastic_3d_printing_component"/> exactly one block above the chamber and every model switches to 3D Printing. The method is fixed when the cycle is locked and cannot switch midway

If the model does not meet the selected product type, there is a way out: place <ref item="anvilcraft:multiphase_transcendium"/> in the resource slot and the chamber processes it as that type instead of rejecting the cycle. It cannot bypass the Casting or Printing size limit, and one is consumed per cycle only when the type actually needs overriding

## Related

- [Edit, import and save models](010_model_editor_and_import.md)
- [Cast and print](020_casting_and_printing.md)
- [Production modes, platform and logistics](030_production_modes.md)

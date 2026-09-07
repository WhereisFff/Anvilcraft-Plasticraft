---
navigation:
  title: "Casting and 3D Printing"
  icon: "anvilcraftplasticraft:plastic_3d_printing_component"
items:
  - anvilcraftplasticraft:plastic_3d_printing_component
---

# Casting and 3D Printing

<row halign="center">
<item id="anvilcraftplasticraft:plastic_3d_printing_component"/>
</row>

## Power

The chamber uses AnvilCraft grid power, draws a fixed 256kW while working and stores up to 160,000,000FE. A grid disconnect does not stop it right away — it keeps burning its stored FE, and when the FE runs out the current step pauses without losing the model, clay, melt or progress

The recipe accepts a charged or empty Super Capacitor, and the chamber inherits its energy. Its resource slot also takes a charged Capacitor or Super Capacitor for 8,000,000FE or 160,000,000FE respectively and hands the empty item back. A capacitor is consumed only when the chamber has room for its full charge, so nothing goes to waste

## Melt and batch size

The chamber's staging tank holds 8B, or 8000mB, and accepts universal, engineering, clear and heat-resistant plastic melt. The resource slot can empty a bucket of plastic melt and return the empty bucket. Note that the casting batch and printing component have their own separate storage and do not share this capacity. Once the structure and cycle are ready, the chamber pumps at most 250mB/gt

The arithmetic is easy: one complete product needs 1mB of melt for every 4 cubic pixels, rounded up, with a minimum batch of 250mB

The first melt entering a batch fixes its fluid, color and material until the storage is empty. The printing component holds 8192mB; it only starts once the full amount for one product has been pumped in, then consumes at most 1mB/gt while printing

## Casting clay

Casting fills the mold with clay first: one clay ball for every 1024 empty cubic pixels in the forming space, rounded up. The ordinary 48x48x48px workspace needs at most 108 clay balls. Cavities need clay too, and zero-thickness Cubes will not save you any

After locking, clay filling takes 12gt and completes the 3x3 mold in three layers. If the next layer lacks clay or power, the chamber stops before it and takes no partial layer

## Short batches and complete printing

Running low on melt still ships something, just half of it. Casting can start with 250mB in the batch: the available melt forms 4 cubic pixels per mB from the model's lowest Y upward, the paid lower section keeps its original shape, and the upper section is omitted. A model of at most 1000 cubic pixels is therefore complete at the minimum 250mB

3D Printing never makes a partial product. It first pumps the complete amount into the component, then prints the complete model pixel by pixel

## Printing component and high precision

The printing component uses its model cubes for outlines and precise selection, including the screen and eyes shown while powered. The crosshair can pass through gaps between its cubes

Hold a <ref item="anvilcraftplasticraft:plastic_3d_printing_component"/> and right-click any face of a chamber without one to install it exactly one block above. No Shift is needed, and this click does not open the GUI. If the space above is obstructed, installation fails without consuming the item. Installing it selects 3D Printing; right-clicking the component also opens the chamber. Without it, high-precision types cannot be locked at all

Allay Hard Hats are a high-precision type and must be printed; their shape limits are listed in [Trays and Allay Hard Hats](../003_plastic/040_plastic_components.md)

Printing creates a complete plastic entity in the forming region — no clay, no 3x3 platform, no giant anvil needed. Casting's platform and output rules are in [Production modes, platform and logistics](030_production_modes.md)

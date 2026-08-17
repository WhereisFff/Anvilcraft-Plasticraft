---
navigation:
  title: "Plastic Chests and Tanks"
  icon: "anvilcraftplasticraft:universal_plastic"
---

# Plastic Chests and Tanks

Both types require one or more sealed, empty cavities. The shell must close each cavity in all six directions, and cavities must make up at least half of the model's total volume. A zero-thickness face may seal a cavity without adding solid volume

Several sealed cavities combine their capacity. Interior decoration, geometry entering a cavity or an open side invalidates the type

## Chest

- The cavity must contain at least 64 cubic pixels
- Every 64 cubic pixels of cavity volume provides one item slot; 64 gives 1 slot and 1728 gives 27 slots
- Hoppers, chutes and other item logistics can insert and extract items; there is no extra menu
- Items and their slots survive pickup and placement

## Tank

- The cavity must contain at least 171 cubic pixels
- Every 171 cubic pixels of cavity volume provides 1B of capacity; 171 gives 1B and 2744 gives 16B
- Pipes and handheld fluid containers can insert or extract fluids; multiple fluids share the total capacity
- Universal, engineering and clear plastic tanks shatter as soon as lava enters; every stored fluid is discarded and no lava source is left in the world
- Only heat-resistant plastic tanks can store lava
- The liquid surface follows the current gravity direction; moving or recovering the tank preserves its capacity and the order in which fluids are extracted

## Type override

When <ref item="anvilcraft:multiphase_transcendium"/> forces a Chest or Tank type, capacity is calculated from the model's complete outer bounding volume instead of its actual cavity. A forced tank still accepts and outputs fluids normally, but does not render its internal fluid or surface; its contents remain accessible through containers, Jade and the Anvil Hammer overlay

Hardness, transparency and fire resistance follow [Material Comparison](000_material_comparison.md). See [Plastic Molding](../004_molding/index.md) for model limits and production

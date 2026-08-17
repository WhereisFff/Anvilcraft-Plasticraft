---
navigation:
  title: "Model Editor and Import"
  icon: "anvilcraft:structure_disk"
---

# Model Editor and Import

## Editing Cubes

The chamber editor builds models from Cubes. Cubes can be moved, resized, rotated and grouped around shared pivots; groups also share visibility and lock state

At least one Cube is required before locking. A model outside the current forming range can only be edited; zero-thickness planes remain in the model but add no casting volume

## Structure Disks

The editor's disk slot accepts only an AnvilCraft <ref item="anvilcraft:structure_disk"/>. Loading copies its molding model into the editor and keeps it editable; storing writes a copy of the current model. Molding and construction blueprints use the same disk item but cannot coexist, and a construction projection cannot be processed directly as a molding model

## Shared model library

The world stores up to 512 shared `.json` models below `anvilcraftplasticraft/blueprints/`; a Structure Disk also keeps an embedded copy that loads independently. Shared models can be loaded, copied and renamed. Deleting a shared file does not affect a copy already on a disk or in a chamber

## Importing model files

Open the model folder from Model Management, add a Minecraft Java `.json` or Blockbench `.bbmodel` file no larger than 1MiB, then refresh the model list. Cubes, groups, pivots and supported rotations are imported; textures, animations and display transforms are omitted, while non-Cube geometry is rejected

An imported model outside the forming range is not scaled; its source dimensions are retained and it can be saved, loaded and edited, but it cannot be locked. A model that fits the range but sits outside the workspace is first offered a whole-model translation and imported after confirmation

## Related

- [Cast and print](020_casting_and_printing.md)
- [Blueprint deployment and construction imports](../005_construction/020_blueprint_deployment.md)

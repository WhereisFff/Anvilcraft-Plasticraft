---
navigation:
  title: "Model Editor and Import"
  icon: "anvilcraft:structure_disk"
---

# Model Editor and Import

## Editing Cubes

The chamber editor only speaks Cube: boxes stacked into a model. Cubes can be moved, resized, rotated and grouped around shared pivots, and a group shares visibility and lock state

At least one Cube is required before locking. A model outside the current forming range can only be edited, never locked. Zero-thickness planes stay in the model but add no casting volume

## Structure Disks

The editor's disk slot accepts only an AnvilCraft <ref item="anvilcraft:structure_disk"/>. Loading copies its molding model into the editor and keeps it editable; storing writes a copy of the current model

Molding models and construction blueprints share the same disk item, but one disk cannot hold both, and a construction projection cannot be processed directly as a molding model

## Shared model library

The world stores up to 512 shared `.json` models below `anvilcraftplasticraft/blueprints/`, while a Structure Disk keeps an embedded copy that loads independently. Shared models can be loaded, copied and renamed, and deleting a shared file does not affect a copy already on a disk or in a chamber

## Importing model files

Open the model folder from Model Management, drop in a Minecraft Java `.json` or Blockbench `.bbmodel` file no larger than 1MiB, then refresh the model list. Cubes, groups, pivots and supported rotations come across; textures, animations and display transforms are omitted, and non-Cube geometry that cannot be converted is rejected

An imported model outside the forming range is not scaled down to fit; its source dimensions are retained and it can be saved, loaded and edited, but it cannot be locked. A model that fits the range but sits outside the workspace is first offered a whole-model translation and imported after you confirm it

## Related

- [Cast and print](020_casting_and_printing.md)
- [Blueprint deployment and construction imports](../005_construction/020_blueprint_deployment.md)

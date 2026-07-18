# Agent and Porting Notes

This repository currently maintains the Minecraft 1.21.1 implementation only.
AnvilCraft is expected to move toward 26.1 later, so new code should keep the
porting boundary explicit.

## 1.21-to-26.1 conventions

- Keep game-facing logic in small common classes and isolate NeoForge/client
  registration in `init` and `client` packages.
- Prefer vanilla and AnvilCraft public APIs (`Entity`, `AnvilMenu`,
  `ItemCombinerScreen`, `ResourceLocation`, `Holder`, and data builders) over
  mappings-specific helpers or reflection.
- Keep registry names, resource paths, serialized NBT keys, recipe IDs, and
  network payload order stable. Add compatibility readers before changing a
  saved-data format.
- Use `ResourceLocation.fromNamespaceAndPath` and registry holders instead of
  constructing registry objects from strings at runtime.
- Keep entity behavior independent from renderer code. The plastic anvil entity
  owns collision, movement, placement state, interaction, and drops; the
  renderer only consumes its synchronized display state.
- Keep optional integrations in separate classes loaded by JEI/Jade/Ageratum.
  The common entry point must not directly initialize optional client APIs.
- Treat generated resources as outputs. Change the Registrum/DataGen source,
  run `runData`, and review the generated diff instead of hand-editing generated
  JSON.
- Avoid broad mixins. Add one only when the 1.21 and 26.1 public APIs cannot
  express the behavior, and document the target class and expected port.
- Keep constants for menu payloads and texture locations in local classes so a
  future port changes one boundary instead of gameplay code.

When the 26.1 port begins, preserve the 1.21 source in a branch and port one
vertical slice at a time: registrations, entity/menu behavior, client screen,
data generation, then integrations and manual pages.

# Agent and Porting Notes

This repository currently maintains the Minecraft 1.21.1 implementation only.
AnvilCraft is expected to move toward 26.1 later, so new code should keep the
porting boundary explicit.

## Hard prohibition: Minecraft client and UI automation

- Never invoke the `computer-use` skill or any desktop/UI automation tool for
  this repository.
- Never launch, focus, reload, inspect, send input to, or terminate a Minecraft
  client, including a client that the user already has running.
- Rendering changes must be validated statically and with non-client build or
  test tasks only. The user performs all in-game visual validation.

## 1.21-to-26.1 conventions

- Keep game-facing logic in small common classes and isolate NeoForge/client
  registration in `init` and `client` packages.
- Follow AnvilCraft's registry split: block registrations and tags live in
  `init.block`, entity registrations in `init.entity`, item groups and tags in
  `init.item`, and menu registrations in the `init` root. Keep reusable entity
  mechanics in `entity.physics` and in-world recipe bridges in `recipe`.
- Keep `neoforge.mods.toml` in `src/main/resources/META-INF` and expand its
  properties through `processResources`; do not add a separate `templates`
  source tree for mod metadata.
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

## Client and rendering validation

- Never use Computer Use, desktop automation, or other UI-control tooling for
  this project.
- Do not launch the Minecraft client to inspect rendering. Review renderer,
  model, and asset code statically; the user will report visual discrepancies
  that require follow-up.
- Validate changes with compilation, builds, dedicated-server/GameTest runs,
  and direct inspection of generated run arguments and packaged resources.

## 注释语言

- 新增或修改的代码注释必须使用中文；API 标识符、类名、方法名、资源 ID 和协议关键字可保留原文。

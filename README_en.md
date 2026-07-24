# Anvilcraft: Plasticraft

[简体中文](README.md)

**Anvilcraft: Plasticraft** is an in-development NeoForge add-on for
[AnvilCraft](https://github.com/Anvil-Dev/AnvilCraft).

Its main purpose is not to make AnvilCraft's progression line longer. Plasticraft uses
plastic as a medium for broader, more flexible interactions: every item and block should
reward small player experiments, with readable responses to pushing, collision, bonding,
wrapping, heating, and anvil impacts.

Material properties become gameplay. Resin anvils bounce, high-viscosity resin can bond
almost anything to a block or another entity, and future plastic shells will wrap many
kinds of items, blocks, and entities. Material tiers organize how these abilities are
unlocked; they are not meant to be a longer processing chain for its own sake.

## Current Focus

- Movable resin and hardened-resin anvils affected by gravity, collisions, buoyancy, and magnetism.
- Six-direction placement, continuous pushing, and the vanilla anvil workflow on hardened-resin anvils.
- High-viscosity resin buckets select and move entities, preserve ordinary block interactions on a short press, and place exposed adhesive after a 0.5-second hold or immediately on non-interactive blocks.
- Exposed adhesive bonds touching entities and newly placed blocks; pistons and sliding rails move the resulting block group together.
- Bonding support for 3x3x3 falling giant anvils; ordinary falling blocks no longer time out while bonded, but still land as blocks without losing the bond.
- Hardened-resin cauldrons, condenser towers, plasma-jet processing, and JEI/Jade/Ageratum integration.

The planned material families are resin, general and functional plastics, composite
plastics, and transfinite plastics. Each tier should open new sideways branches for
existing AnvilCraft systems rather than merely extending the main line.

See the [Chinese material and content design document](docs/plastic-materials-and-process-design.zh_cn.md)
for the current design details. Development uses JDK 21 and the Gradle Wrapper; run
`./gradlew build` (or `.\gradlew.bat build` on Windows) to build the mod.

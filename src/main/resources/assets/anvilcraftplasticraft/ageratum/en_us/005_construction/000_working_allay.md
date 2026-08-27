---
navigation:
  title: "Working Allays"
  icon: "anvilcraftplasticraft:allay_lounge"
---

# Working Allays

Print an [Allay Hard Hat](../003_plastic/040_plastic_components.md) from any plastic, then right-click an allay while holding it. The hat's material determines its demolition and fire-resistance abilities

Sneak-right-click removes the hat and restores the vanilla allay. It cannot be removed while the allay is carrying a block for placement or collected items

## Main-hand tools

| Main hand | Ability |
| --- | --- |
| Empty | Builds and collects nearby drops; reach is 1 block and it picks up one drop at a time |
| <ref item="anvilcraft:crab_claw"/> | Build reach 4 blocks and carry Giant Anvils or large plastic products, but does not collect drops |
| <ref item="minecraft:stonecutter"/> | Demolishes target blocks, but does not build or collect; the hat material changes the drops |
| <ref item="anvilcraft:magnet"/> | Collects any drops within 16 blocks with a nine-slot temporary inventory, but does not build |
| <ref item="minecraft:spyglass"/> | Observes the site and keeps its own chunk plus the eight horizontal neighbours — 9 chunks — entity-ticking, without building, demolishing or collecting |

## Observation and chunk loading

A hard-hatted allay holding a spyglass becomes an observation allay. Centred on its own chunk it keeps a horizontal 3×3 block of 9 full-height chunks entity-ticking, with no height limit: underground, surface and sky targets share one coverage as long as their X/Z falls inside those 9 chunks, and the tenth chunk is not loaded by that observer

- Loading only applies while the allay still wears a hat, holds the spyglass in its main hand and actually exists in that dimension; removing the hat, swapping the spyglass out or leaving the world revokes it immediately
- Blueprints themselves and builder, demolition and collector allays never load chunks. They can only work inside chunks already kept entity-ticking by a player, an observation allay or another loading source
- When an observer crosses a chunk border it establishes the new 9-chunk coverage before releasing the old one, so ticking never stops; dependent workers do not cross the frontier before the new coverage exists
- Coverage applies to the current dimension only, never across dimensions; overlapping coverages merge, so a shared chunk costs nothing extra
- An observer without a job returns to vanilla wandering; on an observation task it parks at any collision-free safe position the scheduler picks

Spyglass allays hosted inside a lounge provide the same coverage — see [Allay Lounge](010_allay_lounge.md)

## Work rules

- A working allay may join jobs owned by its owner or a current FTB Teams teammate; without FTB Teams, only its owner is authorized
- Taking, placing, breaking, collecting and unloading happen at most once every 4gt; the allay searches for work targets within 128 blocks
- Without a lounge, builders take materials from the job owner's inventory
- Free-collected items return to the allay owner first, or to a current online teammate when that owner is outside the dimension; lounge-bound allays return them to the lounge
- Team membership is checked again before dispatch, world interaction and unloading; a former teammate cannot keep using old authorization
- An Allay without a job returns to vanilla wandering

See [Construction jobs](030_construction_jobs.md) for the work order and cancellation

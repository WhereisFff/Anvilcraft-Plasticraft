---
navigation:
  title: "Working Allays"
  icon: "anvilcraftplasticraft:allay_lounge"
---

# Working Allays

Print an [Allay Hard Hat](../003_plastic/040_plastic_components.md) from any plastic, then right-click an allay while holding it and it is on the job. The hat's material also settles its demolition and fire-resistance abilities

To retire one, sneak-right-click to remove the hat and it goes back to vanilla allay life. The hat cannot come off while the allay is still carrying a block for placement or collected items, so let it unload first

## Main-hand tools

What an allay does depends entirely on what it is holding:

| Main hand | Ability |
| --- | --- |
| Empty | Builds and collects nearby drops; reach is 1 block and it picks up one drop at a time |
| <ref item="anvilcraft:crab_claw"/> | Build reach 4 blocks and carry Giant Anvils or large plastic products, but does not collect drops |
| <ref item="minecraft:stonecutter"/> | Demolishes target blocks, but does not build or collect; the hat material changes the drops |
| <ref item="anvilcraft:magnet"/> | Collects drops within 16 blocks except ordinary stock in its own supply cell, with a nine-slot temporary inventory, but does not build |
| <ref item="minecraft:spyglass"/> | Observes the site and keeps its own chunk plus the eight horizontal neighbours — 9 chunks — entity-ticking, without building, demolishing or collecting |
| <ref item="minecraft:flint_and_steel"/> | Reach 1 block; only ignites blueprint fire, soul fire and Nether portals, without carrying materials, building, sealing, demolishing or collecting |

Tools given directly by a player are fixed equipment: the allay keeps its specialization. Empty-handed workers on a lounge job can borrow useful tools from the warehouse below: crab claws for building, stonecutters for demolition, magnets for collection, spyglasses for chunk loading and flint and steel for ignition

Automatic equipment fills current shortages while retaining useful workers. Observation gets enough workers for missing coverage windows; collection prefers 1 magnet worker and ignition assigns 1 dedicated flint-and-steel worker. Separate workers carry materials and build the structure. Workers already on site finish handling carried materials and collected items, then fly back to the warehouse to return their old tool and take the next one. Exchanges never happen remotely. Tools can come from a container or item drops within the single block below the lounge. Creative Crates supply every tool indefinitely without a sample item. When ordinary sources lack a suitable tool, workers use their existing abilities or wait

Borrowed tools keep their names, enchantments and existing damage. When construction finishes or is cancelled, automatic workers return their tools and become empty-handed; player-equipped workers keep theirs. If the warehouse cannot accept an old tool, the worker keeps it pending return instead of destroying it

## Blueprint ignition

Fire and Nether portal cells do not consume block items. A dedicated worker holding flint and steel must deliver the ignition operation. It never carries building blocks at the same time; workers carrying construction materials or collected items refuse player-given flint and steel until they unload. Each fire costs 1 tool use; one connected portal in the same plane also costs only 1 use. Durability follows vanilla enchantment rules, and a worker whose borrowed tool breaks can borrow another

Ignition takes effect after the final structure is committed. Fire and soul fire require suitable support. Nether portals require a complete valid frame with an interior 2–21 blocks wide and 3–21 blocks high, and the entire blueprint portal must have been delivered. Portals activate only in the Overworld and Nether. Missing frames, unsupported fire and unsupported dimensions produce an incomplete construction result instead of invalid portal blocks

## Observation and chunk loading

The one with the spyglass is the special one — it is the power supply for the whole site. A hard-hatted allay holding a spyglass becomes an observation allay. Centred on its own chunk it keeps a horizontal 3×3 block of 9 full-height chunks entity-ticking, with no height limit: underground, surface and sky targets share one coverage as long as their X/Z falls inside those 9 chunks, and the tenth chunk is none of its business

- Loading only applies while the allay still wears a hat, holds the spyglass in its main hand and actually exists in that dimension; removing the hat, swapping the spyglass out or leaving the world revokes it immediately
- Blueprints themselves and builder, demolition and collector allays never load chunks. They can only work inside chunks already kept entity-ticking by a player, an observation allay or another loading source
- When an observer crosses a chunk border it establishes the new 9-chunk coverage before releasing the old one, so ticking never stops; dependent workers do not cross the frontier before the new coverage exists
- Coverage applies to the current dimension only, never across dimensions; overlapping coverages merge, so a shared chunk costs nothing extra
- An observer without a job returns to vanilla wandering; on an observation task it parks at any collision-free safe position the scheduler picks

Spyglass allays hosted inside a lounge provide the same coverage — see [Allay Lounge](010_allay_lounge.md)

## Work rules

- A working allay answers to its owner and to that owner's current FTB Teams teammates; without FTB Teams, only its owner is authorized
- Taking, placing, breaking, collecting and unloading happen at most once every 4gt, and it searches for work targets within 128 blocks
- Without a lounge, builders take materials straight from the job owner's inventory
- Free-collected items return to the allay owner first, or to a current online teammate when that owner is outside the dimension; lounge-bound allays return them to the lounge
- Team membership is rechecked before dispatch, world interaction and unloading, so a former teammate cannot keep using old authorization
- An Allay without a job returns to vanilla wandering and gets to play again

See [Construction jobs](030_construction_jobs.md) for the work order and what cancellation does

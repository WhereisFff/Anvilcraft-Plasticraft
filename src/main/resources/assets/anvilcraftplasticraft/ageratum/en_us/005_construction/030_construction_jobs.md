---
navigation:
  title: "Construction Jobs"
  icon: "anvilcraftplasticraft:allay_lounge"
---

# Construction Jobs

Once a blueprint starts, allays prepare the site, clear obstacles, collect drops and place the blueprint in order; if a new real block differs from the blueprint during building, demolition happens before construction resumes

## Phases

1. **Seal**: displace fluids inside the blueprint with temporary blocks
2. **Demolish**: clear blocks and temporary fill inside the blueprint's range; bedrock and other unbreakable blocks remain obstacles. By default even blocks on cells the blueprint leaves blank are demolished; a lounge can restrict this to cells that overlap a blueprint block
3. **Collect**: recover drops produced by this job; without a collection tool, building is not blocked
4. **Build**: place blocks, fluids and entities in material batches, and restore block-entity contents. Targets are handed out from the first 24 pending operations that share the queue head's layer, ordered by distance to that allay, so a ring or row is built around instead of placing one block and flying to the opposite side; across layers the lower layer is still finished before the one above

Signs and hanging signs are never placed with their text already on them: an allay first puts down a blank face, then carries dye, a glow ink sac and honeycomb up to write, color, glow and wax it, with the front and back worked separately. Writing itself costs nothing, coloring one face costs one dye of that color, making one face glow costs one glow ink sac, and waxing costs one honeycomb; only the steps that were actually carried out show up on the finished sign, while anything short on materials or skipped stays blank

Placed contents appear first as collidable projections, then become actual blocks, fluids and entities when the job finishes or is cancelled. If another entity occupies the target position or the allay cannot approach safely, it waits. When the same target is confirmed unreachable 4 times in a row — a full delivery round stuck while neither in tool reach nor in an occupancy or world wait, or no usable approach position at all — the carried material is returned to the lounge or the owner and that location backs off for 10 seconds; the rest of the site keeps building, and afterwards any allay may re-supply it and pick a new approach. An unreachable position is never counted as built and never ends the job: while every remaining position is backing off, the job reports "Allays cannot reach a construction position and will retry later" and stays in the build phase. The counter resets as soon as the target is within reach again

During the construction phases, real block changes in explicitly declared blueprint cells are watched. A mismatching block pauses building and adds a demolition operation; its drops keep the task marker and are handled by the normal collection ledger. An exact target-state block satisfies the cell directly. `CLEAR_AREA` reacts in explicit blank cells, while `KEEP_BLANK` leaves new blocks there but still reacts in blueprint entity cells. The commit phase keeps its final forced-overwrite rule

## Loading coverage

A job only dispatches work into chunks that are entity-ticking. Players, observation allays and any other loading source all count, and a chunk that already ticks is reused without spending an observer

- Before dispatch the target's chunk column is checked. A position that is not ticking is skipped for this round and waits for the frontier to reach it; the remaining positions keep building
- When the next batch of targets enters an uncovered chunk the scheduler inserts an observation task: an observer flies over and establishes the 9-chunk window first, and only then do the other roles enter. One job holds at most 4 windows at a time, so further work areas queue for the frontier
- The coordinating lounge's own column must tick as well, otherwise its inventory cannot be read and nothing can dock or launch
- A window is released as soon as it no longer covers any pending operation, and the observer moves to the next window, returns to its lounge or resumes wandering; while a gap is still waiting to be taken, a freshly launched observer holds position until it is assigned instead of turning straight back into the lounge and wasting the outbound bay
- Too few observers never fails the job: the scheduler shrinks the parallel frontier, reorders operations that have not started, has idle workers convoy along with the observer, and if necessary lets one observer shuttle its window between two work areas
- With no observer available and no other loading source for the next batch, the job reports "Construction is waiting for a spyglass allay to keep the work area ticking" and stops in the observer wait, while allays carrying materials rest at their lounge. It replans the moment any loading source appears or an observer becomes available, with no need to restart the job

## Limits and resources

- The blueprint owner and current FTB Teams teammates can manage the job and share working allays; without FTB Teams, only the owner is authorized
- An unclaimed job takes from its owner's inventory; a creative owner is not charged
- After a lounge claims the job, its container and shortage settings control material supply; see [Allay Lounge](010_allay_lounge.md)
- A player can run only one job at a time, and a job uses at most 64 allays

## Cancellation and recovery

Cancellation does not roll back progress: delivered blocks, contents, fluids and entities remain, materials currently being carried are returned, locations not yet built remain unchanged and already placed temporary fill remains

Permission denial during planning, sealing, demolition, delivery or commit enters a permission wait that Skip and forced overwrite cannot bypass. Work resumes from the same ledger after permission returns. If the lounge container disappears, its allays wait with the job and continue when the container returns

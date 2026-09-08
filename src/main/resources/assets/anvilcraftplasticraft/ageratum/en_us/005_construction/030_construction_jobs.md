---
navigation:
  title: "Construction Jobs"
  icon: "anvilcraftplasticraft:allay_lounge"
---

# Construction Jobs

Once a blueprint starts, the allays work in order: prepare the site, clear obstacles, collect drops, then place the blueprint cell by cell. If a new real block that differs from the blueprint shows up mid-build, the job schedules its demolition before carrying on

## Phases

1. **Seal**: displace fluids inside the blueprint with temporary blocks
2. **Demolish**: clear blocks and temporary fill inside the blueprint's range. Bedrock and other unbreakable blocks remain obstacles. By default even blocks on cells the blueprint leaves blank are demolished; a lounge can restrict this to cells that overlap a blueprint block
3. **Collect**: recover drops produced by this job. Having no collection tool on hand does not block building
4. **Build**: place blocks, fluids and entities in material batches, and restore block-entity contents along the way

Step 4's dispatch order is worth a note: targets are handed out from the first 24 pending operations that share the queue head's layer, ordered by distance to that allay. So a ring or a row gets built around, instead of placing one block and flying clear across the site. Layers stay strict, though — the lower layer is finished before the one above

Signs and hanging signs are never placed with their text already on them. An allay first puts down a blank face, then carries dye, a glow ink sac and honeycomb up to write, color, glow and wax it, with the front and back worked separately. Writing itself costs nothing; coloring one face costs one dye of that color, making one face glow costs one glow ink sac, and waxing costs one honeycomb. Only the steps that were actually carried out show up on the finished sign, while anything short on materials or skipped stays blank

Placed contents appear first as collidable projections, and only become actual blocks, fluids and entities when the job finishes or is cancelled. If another entity occupies the target position or the allay cannot approach safely, it waits

A fully built structure reports "Construction finished", including complete multiblock machines. An incomplete result is reported only when construction contents were skipped or could not be restored

<tip>
When the same target is confirmed unreachable 4 times in a row — a full delivery round stuck while neither in tool reach nor in an occupancy or world wait, or no usable approach position at all — the carried material is returned to the lounge or the owner and that location backs off for 10 seconds. The rest of the site keeps building, and afterwards any allay may re-supply it and pick a new approach. An unreachable position is never counted as built and never ends the job: while every remaining position is backing off, the job reports "Allays cannot reach a construction position and will retry later" and stays in the build phase. The counter resets as soon as the target is within reach again
</tip>

Throughout the construction phases, real block changes in explicitly declared blueprint cells are watched. A mismatching block pauses building and adds a demolition operation; its drops keep the task marker and are handled by the normal collection ledger. An exact target-state block satisfies the cell directly. `CLEAR_AREA` reacts in explicit blank cells, while `KEEP_BLANK` leaves new blocks there but still reacts in blueprint entity cells. The commit phase keeps its final forced-overwrite rule

## Loading coverage

A job only dispatches work into chunks that are entity-ticking. Players, observation allays and any other loading source all count, and a chunk that already ticks is reused without spending an observer

- Before dispatch the target's chunk column is checked. A position that is not ticking is skipped for this round and waits for the frontier to reach it; the remaining positions keep building
- When the next batch of targets enters an uncovered chunk the scheduler inserts an observation task: an observer flies over and establishes the 9-chunk window first, and only then do the other roles enter. One job holds at most 4 windows at a time, so further work areas queue for the frontier
- The coordinating lounge's own column must tick as well, otherwise its inventory cannot be read and nothing can dock or launch
- A window is released as soon as it no longer covers any pending operation, and the observer moves to the next window, returns to its lounge or resumes wandering. While a gap is still waiting to be taken, a freshly launched observer holds position until it is assigned instead of turning straight back into the lounge and wasting the outbound bay
- Too few observers never fails the job: the scheduler shrinks the parallel frontier, reorders operations that have not started, has idle workers convoy along with the observer, and if necessary lets one observer shuttle its window between two work areas
- With no observer available and no other loading source for the next batch, the job reports "Construction is waiting for a spyglass allay to keep the work area ticking" and stops in the observer wait, while allays carrying materials rest at their lounge. It replans the moment any loading source appears or an observer becomes available, with no need to restart the job

## Limits and resources

- The blueprint owner and current FTB Teams teammates can manage the job and share working allays; without FTB Teams, only the owner is authorized
- An unclaimed job takes from its owner's inventory; a creative owner is not charged
- After a lounge claims the job, its container and shortage settings control material supply; see [Allay Lounge](010_allay_lounge.md)
- A player can run only one job at a time, and a job uses at most 64 allays

## Very large structures

Huge blueprints are handled by spreading writes and syncing across ticks, not by lowering the limits:

- The quiet commit writes at most 4096 cells per tick. The whole structure is written region by region behind the projection and published in a single final step, so it still looks instantaneous while the server never takes one enormous write
- Delivered projections sync per 16×16×16 section, at most 16 sections per tick, nearest player first. Sections nobody can see are not sent at all and are filled in with the chunk when you walk over
- Each section's payload is compressed with a section-local palette and a sparse bitset, so a section of one repeated block carries that state only once
- Files whose declared size is invalid or too large are rejected at import time instead of being allocated first. The limits are listed in [Blueprint Deployment and Structure Disks](020_blueprint_deployment.md)

## Cancellation and recovery

Cancellation does not roll back progress, so keep that in mind: delivered blocks, contents, fluids and entities all stay put, materials currently being carried are returned, locations not yet built remain unchanged, and temporary fill already placed is not cleared for you

Permission denial during planning, sealing, demolition, delivery or commit enters a permission wait that Skip and forced overwrite cannot bypass. Work resumes from the same ledger after permission returns. If the lounge container disappears, its allays hold onto the job and wait, then continue when the container returns

## Server restarts

The job itself, the material ledger, sealing and demolition progress, delivered projections, lounge docking, observation tickets and the commit log all live in the save file. After a restart the job resumes from that data, recovering exactly once:

- Temporary fill already placed and cells already smashed stay as they are: nothing is replayed and nothing is conjured back
- In-transit material is still recorded in the escrow ledger, so a reloaded allay keeps delivering against the original entry — never duplicated, never dropped as an ownerless item
- A restart mid-commit continues from the saved region cursor and passes its publish point only once, so no part of the structure is written twice
- Observation allays' nine-column chunk tickets are re-issued from the world-level loading index, and any ticket outside that index is trimmed so no ownerless loaded area is left behind. Which window belongs to whom is planned afresh rather than inherited from before the restart

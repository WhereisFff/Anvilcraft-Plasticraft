---
navigation:
  title: "Construction Jobs"
  icon: "anvilcraftplasticraft:allay_lounge"
---

# Construction Jobs

Once a blueprint starts, allays prepare the site, clear obstacles, collect drops and place the blueprint in order

## Phases

1. **Seal**: displace fluids inside the blueprint with temporary blocks
2. **Demolish**: clear blocks and temporary fill that conflict with the blueprint; bedrock and other unbreakable blocks remain obstacles
3. **Collect**: recover drops produced by this job; without a collection tool, building is not blocked
4. **Build**: place blocks, fluids and entities in material batches, and restore block-entity contents

Placed contents appear first as collidable projections, then become actual blocks, fluids and entities when the job finishes or is cancelled. If another entity occupies the target position or the allay cannot approach safely, it waits

## Limits and resources

- The blueprint owner and current FTB Teams teammates can manage the job and share working allays; without FTB Teams, only the owner is authorized
- An unclaimed job takes from its owner's inventory; a creative owner is not charged
- After a lounge claims the job, its container and shortage settings control material supply; see [Allay Lounge](010_allay_lounge.md)
- A player can run only one job at a time, and a job uses at most 64 allays

## Cancellation and recovery

Cancellation does not roll back progress: delivered blocks, contents, fluids and entities remain, materials currently being carried are returned, locations not yet built remain unchanged and already placed temporary fill remains

Permission denial during planning, sealing, demolition, delivery or commit enters a permission wait that Skip and forced overwrite cannot bypass. Work resumes from the same ledger after permission returns. If the lounge container disappears, its allays wait with the job and continue when the container returns

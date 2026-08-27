---
navigation:
  title: "Allay Lounge"
  icon: "anvilcraftplasticraft:allay_lounge"
items:
  - anvilcraftplasticraft:allay_lounge
---

# Allay Lounge

<row halign="center">
<item id="anvilcraftplasticraft:allay_lounge"/>
</row>

An Allay Lounge hosts up to 16 hatted allays and has one structure-disk slot. It belongs to the player who placed it and can be operated by that owner or a current FTB Teams teammate; without FTB Teams, only the owner is authorized. It needs no power; after a job is claimed, workers take materials from and unload collected items into the container below the lounge

## Recall and capacity

- The recall button finds working allays owned by the lounge owner or a current teammate within 16 blocks
- The top bay serves both docking and launch, one allay at a time; each trip occupies it for 20gt, or about one allay per second
- Breaking the lounge releases hosted and docking allays into the world and drops the structure disk

## Chunk loading

As soon as at least one of the 16 hosted records is a hatted allay holding a spyglass, the lounge keeps a horizontal 3×3 block of 9 full-height chunks entity-ticking around its own chunk. Hosting several observers does not widen the range; eligibility depends only on the hosted records still being valid and never on power, and a spyglass in an ordinary chest or a player inventory loads nothing

- When the last eligible observer launches, its own 9-chunk coverage is established before the lounge coverage is released, and docking performs the reverse handover; ticking never stops for the lounge, the allay or the workers following it
- Inventory access, job coordination, docking, launching and remote transfer all require the lounge's own column to be entity-ticking. A lounge that hosts an observer loads itself; otherwise another observer must cover it first, and only then does it launch builder, demolition or collector allays
- Coverage records live in a world-level index. On server restart the chunk tickets are restored from that index first, then validated once the entities and block entities have loaded; invalid records drop their tickets immediately

## Remote transfer

Lounges in the same dimension can form a transfer chain: any two neighbouring lounges must be within 128 blocks, and distant allays travel the chain hop by hop to join a job, then return along the same chain to their home lounge when the job ends, without lingering at the job lounge. The scheduler borrows only when it is expected to shorten the job's completion time; it holds back when local workers are already sufficient or the job is nearly finished, and it never drains a lounge that is itself running another job.

A borrowed allay is a guest at the job lounge: it uses the container below the job lounge to take materials and unload, but it never docks into the job lounge, never queues above it and never takes one of its 16 hosted slots. Every intermediate lounge on the chain still docks and launches normally, so only the final hop changes. That keeps the job lounge's 20gt bay entirely for its own allays — a distant lounge with dozens of workers can never block the local ones from launching. As soon as a guest has no work left to claim it starts flying home instead of idling at the job lounge.

## Claiming a job

- Putting a deployed structure disk in the slot claims the job; empty-hand right-clicking the disk starts or pauses it
- A lounge can claim only a job owned by its owner or a current teammate; after that relationship ends, it no longer takes materials, unloads or launches allays
- A yellow slot means the job is deployed or paused; green means running. A player can run only one job at a time
- The lounge launches more allays only while the current phase has available work positions
- A normal container below supplies inventory; a creative crate supplies any blueprint item infinitely

## Shortage and demolition strategy

When materials or a suitable demolition allay are missing, the screen lets you choose between Pause and Skip. Skipping lets the job continue but may leave the final structure incomplete

## Blank-cell clearance strategy

A pair of buttons on the right of the screen decides what happens to blank cells inside the blueprint's range; exactly one is active:

- **Demolish the whole blueprint area** (default): every world block inside the blueprint's declared range is demolished, including cells where the blueprint places nothing, so only the blueprint's own contents remain when the job finishes
- **Keep blocks on cells the blueprint leaves blank**: only cells that overlap a block the blueprint actually places are demolished; blocks on blank cells are left exactly as they are

In both modes the temporary fill used to displace fluids is still cleared — the job placed those blocks itself. Doors, beds, pistons, multiblocks and other composite blocks are judged as a whole through their core: if the core cell overlaps a blueprint block the whole assembly is removed, so no half door is left behind

During building, a new real block in an explicitly declared cell that differs from the target pauses construction and schedules a demolition allay; its drops keep the task marker and follow the normal collection flow. A block that exactly matches the target satisfies that cell, while undeclared sparse gaps are ignored

The setting is read once when the job is first planned, and only while the lounge has claimed that job. Put the disk in the slot to claim the job, pick the button, then start it. An already planned job does not change its demolition ledger when the button is switched: pausing and restarting keeps the old ledger, so cancel and restart the job, or re-deploy the blueprint while nothing has been delivered yet

See [Construction jobs](030_construction_jobs.md) for the task itself

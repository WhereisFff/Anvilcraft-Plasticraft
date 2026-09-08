---
navigation:
  title: "Allay Lounge"
  icon: "anvilcraftplasticraft:allay_lounge"
items:
  - anvilcraftplasticraft:allay_lounge
---

# Allay Lounge

Selection includes the body, status indicator and both hatches. Their outlines and selectable positions follow the opening animation, and the crosshair can pass through gaps between model cubes

The lounge is the site's logistics hub: it hosts up to 16 hatted allays and has one structure-disk slot. It belongs to the player who placed it and can be operated by that owner or a current FTB Teams teammate; without FTB Teams, only the owner is authorized. It needs no power. Once a job is claimed, workers take materials from the container below the lounge and unload collected items back into it

<row halign="center">
<recipe id="anvilcraftplasticraft:allay_lounge"/>
</row>

## Recall and capacity

- The recall button on the stone just outside the window's upper-right corner activates as soon as the left mouse button is pressed, looks within 16 blocks and only counts working allays owned by the lounge owner or a current teammate
- The top bay serves automatic docking and launch, one allay at a time; each trip occupies it for 20gt, which works out to about one allay per second. Manual left-click releases bypass this limit and never occupy or reset the bay
- The two hatch panels open over 9gt when an allay approaches the top bay or a trip starts; they stay open during continuous traffic, then wait 40gt after the last activity before closing over 12gt
- Breaking the lounge packs hosted and docking allay data into the lounge item, including the structure disk

The 4×4 display on the right has 16 hosting spaces; empty spaces stay empty. Hosted allays keep flapping their wings and hovering, with their actual hat shapes, held tools and carried items visible. Hold the left mouse button inside a space and drag horizontally to rotate that allay independently in the drag direction, at 3° per GUI pixel. After release, rotation continues and gradually slows to a stop within about one second. Dragging never launches an allay; a stationary left-click releases the selected allay immediately, with no wait between clicks. The hover tooltip shows the allay's custom name, or its hat's name if unnamed, followed by “Tool: item name” or “Tool: None” when empty-handed. Both lines are white; the third line reads “Left-click to release”

The structure-disk slot sits on the left side of the chalkboard. The four sticky notes on its right form two groups: Pause above Skip in the inner column, and the two blank-cell clearance strategies in the rightmost column. The two buttons in each group touch vertically and exactly one is selected; a gap separates the groups. Strategy buttons activate only when the left mouse button is released over the original button; releasing outside cancels the action. Selected buttons show the hovered appearance under the pointer and return to their latched appearance when the pointer leaves. Both recall and strategy buttons have normal, hovered and held appearances, with no timed frame cycling while held. The player inventory uses the slots already drawn into the lower part of the background

The side trays display reserved building materials in north, east, south, west order. When all four are occupied, further displays wait for a free tray and refill it as soon as its item is collected. Items slide outward over 4gt; one allay's material batch shares one tray. Both block and ordinary item displays are twice their previous size. These animations add no delay to the 20gt bay cycle and consume no extra materials

The four indicator lights show the disk job's state: green while running; blue with no job, before starting, or after completion; red while paused, failed, or waiting for materials, an observer, a demolition allay, permission, or the material source. They turn green again when work resumes. Each light has a bright center and a faint halo matching its status color; this is a visual cue and does not illuminate the surroundings

## Chunk loading

As soon as at least one of the 16 hosted records is a hatted allay holding a spyglass, the lounge itself keeps a horizontal 3×3 block of 9 full-height chunks entity-ticking around its own chunk. Hosting several observers does not widen the range; eligibility depends only on the hosted records still being valid and never on power. Worth noting: a spyglass in an ordinary chest or a player inventory loads nothing at all

- When the last eligible observer launches, its own 9-chunk coverage is established before the lounge coverage is released, and docking performs the reverse handover; ticking never stops for the lounge, the allay or the workers following it
- Inventory access, job coordination, docking, launching and remote transfer all require the lounge's own column to be entity-ticking. A lounge that hosts an observer loads itself; otherwise another observer must cover it first, and only then does it launch builder, demolition or collector allays
- Coverage records live in a world-level index. On server restart the chunk tickets are restored from that index first, then validated once the entities and block entities have loaded; invalid records drop their tickets immediately

## Remote transfer

Lounges in the same dimension can form a transfer chain, with any two neighbouring lounges within 128 blocks. Distant allays travel the chain hop by hop to join a job, then return along the same chain to their home lounge when the job ends, rather than lingering at the job lounge

The scheduler is restrained about this: it borrows only when doing so is expected to shorten the job's completion time, holds back when local workers are already sufficient or the job is nearly finished, and never drains a lounge that is itself running another job

A borrowed allay is a guest at the job lounge — it uses the container below the job lounge to take materials and unload, but it never docks into the job lounge, never queues above it and never takes one of its 16 hosted slots. Every intermediate lounge on the chain still docks and launches normally, so only the final hop changes. The payoff is practical: the job lounge's 20gt bay stays entirely for its own allays, so a distant lounge sending dozens of workers can never block the local ones from launching. As soon as a guest has no work left to claim it starts flying home instead of idling on site

## Claiming a job

- Putting a deployed structure disk in the slot only claims the job, it does not start it; empty-hand right-clicking the disk is what starts or pauses it
- A lounge can claim only a job owned by its owner or a current teammate; once that relationship ends it stops taking materials, unloading and launching allays
- Read the slot color for status: yellow means the job is deployed or paused, green means running. A player can run only one job at a time
- The lounge launches more allays only while the current phase has available work positions
- A normal container below supplies inventory; a creative crate supplies any blueprint item infinitely

The supply area is the single 1×1×1 block immediately below the lounge, using each drop’s position; neighbouring drops are excluded. Containers are used first, and multiple drops in the same block can supply any remaining items once their pickup delay expires. Availability checks consume nothing, and extraction rechecks that each drop still exists within the supply area. Without a container, returned materials and tools are dropped back into this cell. The lounge’s collectors leave ordinary stock in this supply cell alone to avoid repeatedly picking it up and unloading it; marked construction debris can still be collected and settled. An empty supply cell remains available, so consuming the last stack does not interrupt materials already in transit

## Shortage and demolition strategy

The warehouse below also supplies automatic equipment. Empty-handed workers borrow crab claws, stonecutters, magnets, spyglasses, flint and steel or fire charges according to current demand, and return unused items after work. Player-given tools stay fixed. Creative Crates satisfy every supply request indefinitely, including construction items, fluids, sealing blocks and all tools, without matching samples or filters. See [main-hand tools and blueprint ignition](000_working_allay.md)

When materials or a suitable demolition allay are missing, the screen lets you choose between Pause and Skip. Skipping lets the job continue, but may leave the final structure a few blocks short

## Blank-cell clearance strategy

The rightmost pair of vertically stacked sticky-note buttons on the left chalkboard decides what happens to blank cells inside the blueprint's range; exactly one is active:

- **Demolish the whole blueprint area** (default): every world block inside the blueprint's declared range is demolished, including cells where the blueprint places nothing, so only the blueprint's own contents remain when the job finishes
- **Keep blocks on cells the blueprint leaves blank**: only cells that overlap a block the blueprint actually places are demolished; blocks on blank cells are left exactly as they are

In both modes the temporary fill used to displace fluids is still cleared — the job placed those blocks itself. Doors, beds, pistons, multiblocks and other composite blocks are judged as a whole through their core: if the core cell overlaps a blueprint block the whole assembly is removed, so you never get half a door

During building, a new real block in an explicitly declared cell that differs from the target pauses construction and schedules a demolition allay; its drops keep the task marker and follow the normal collection flow. A block that exactly matches the target satisfies that cell, while undeclared sparse gaps are ignored

<warning>
The setting is read once when the job is first planned, and only while the lounge has claimed that job. The order matters: put the disk in the slot to claim the job, pick the button, then start it. An already planned job does not change its demolition ledger when the button is switched — pausing and restarting keeps the old ledger, so cancel and restart the job, or re-deploy the blueprint while nothing has been delivered yet
</warning>

See [Construction jobs](030_construction_jobs.md) for the task itself

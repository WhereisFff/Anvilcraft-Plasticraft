---
navigation:
  title: "Plastic Crates, Tanks and Cauldrons"
  icon: "anvilcraftplasticraft:universal_plastic"
---

# Plastic Crates, Tanks and Cauldrons

What a model turns into comes down to what kind of cavity it encloses. Crates and tanks want a genuinely sealed one: the shell must close each cavity in all six directions, and cavities must make up at least half of the model's total volume. A zero-thickness face may seal a cavity without adding solid volume

Several sealed cavities combine their capacity. Working against you, interior decoration, geometry poking into a cavity or an open side will invalidate the type on the spot

Cauldrons play by a different rule. They are open-top vessels to begin with, so they only need a closed bottom and closed side walls — neither sealed nor bound by that 50% cavity ratio

<tip>
An inward-facing volume cube can act as either wall or cavity in all three checks: when it wholly or partly fills a valid cavity enclosed by the surrounding wall, its occupied portion counts as cavity rather than interior decoration; when it cannot create or extend a valid cavity, it stays wall
</tip>

## Crate

- The cavity must contain at least 1 cubic pixel
- Every cubic pixel of cavity volume provides 2 storage units; the default model has 1600 cubic pixels of cavity and 3200 storage units
- Items stacking to 64 use 1 unit each, items stacking to 16 use 4 units each, and unstackable items use 64 units each
- Right-click the entity or bonded block to open the AnvilCraft crate interface for search, sorting, bulk transfers and crafting unlocks. Hoppers and chutes share the same inventory
- Matching items merge by type. Contents and crafting unlocks survive pickup, placement and world reloads

## Tank

- The cavity must contain at least 171 cubic pixels
- Every 171 cubic pixels of cavity volume provides 1B of capacity: 171 gives 1B and 2744 gives 16B
- Pipes and handheld fluid containers can insert or extract fluids, and multiple fluids share that one total capacity
- Every plastic tank can store lava normally; only heat-resistant plastic remains immune to fire damage
- The liquid surface follows the current gravity direction, and moving or recovering the tank preserves its capacity and the order in which fluids are extracted

## Cauldron

A cauldron may be round, square or any odd shape as long as it abstracts to a walled hollow prism with only its top face removed: the bottom and all side walls stay complete, the top forms an open mouth, and every other boundary is closed. Uneven wall heights are fine

- The top opening must cover at least 144 square pixels, the equivalent of 12x12; the opening is counted as pixel columns whose cavity is exposed upward, not as a bounding rectangle
- The cavity must be at least 8 pixels deep overall, which means the shortest wall stands at least 8 pixels tall
- The vanilla cauldron cavity of 12x12x12 = 1728 cubic pixels is the 1B baseline; capacity is `max(1, cavity volume / 1728)` rounded down
- Capacity is always a whole number of B and never leaves a mB remainder: 1727, 1728 and 3455 cubic pixels all give 1B, 3456 jumps to 2B, and 5184 gives 3B
- It has 8 input slots and 8 output slots, and holds a single fluid at a time
- It behaves exactly like a <ref item="anvilcraftplasticraft:hardend_resin_cauldron"/>: it can be ignited, processes falling-anvil recipes, connects to pipes and fluid networks, absorbs touching items and ejects them through its outlet, and takes part in oil catalysis and plasma jets
- Use any Anvil Hammer on a side to toggle its outlet there; use it through the top opening to choose the side you are facing. The outlet uses the fish-tank shape and scales its cross-section and through-wall length from the cavity and actual wall thickness
- A plain anvil is enough to trigger falling-anvil recipes, so no need to haul a Giant Anvil over; catalysis and cauldron recipes still need the opening to face up
- When facing up, catalysis, plasma jets, and every falling-anvil cauldron recipe search from the center and every covered part of the physical bottom face along current gravity for one block. A valid processing block below the center takes priority, then the remaining covered bottom cells are tried; that is why a small cauldron can share a cell with a campfire, and a cauldron taller than one block still reads the block beside its actual bottom
- Striking a cauldron with an Anvil Hammer processes only the one you actually hit. A falling anvil instead processes every cauldron whose collision box actually enters its landing cell, never cauldrons in neighboring cells
- Every plastic cauldron can store lava normally; only heat-resistant plastic remains immune to fire damage
- Plastic Melt inside a cauldron slows entities that fall into it, just like the melt fluid block

When filled with ordinary oil and ignited, molded cauldrons use AnvilCraft’s Fish Tank fuel behavior: each 1mB extends the jet by 24gt by default, with consumption and duration following AnvilCraft’s configuration. Enhanced jets still consume 10mB of High-Heat Fuel per jet per gt

## Large Plastic Cauldron

A cauldron whose top opening covers **more than** 1600 square pixels is automatically upgraded to a Large Plastic Cauldron. This is an upgrade rather than a downgrade, and capacity is no longer derived from the cavity

- It has 8 fluid layers, each holding 64B by default for 512B total and up to 8 different fluids; layer capacity follows AnvilCraft’s Large Cauldron configuration
- It has 8 input slots and 32 output slots; input slots stack 9 times the vanilla limit by default, which is 576 per slot for ordinary items; the multiplier follows AnvilCraft’s configuration
- Each anvil impact processes at most 9 groups by default, following AnvilCraft’s configured processing limit. Each group has one normal stack of the selected ingredient as its consumption budget; default ordinary stackable items allow up to 9×64 items, subject to each recipe’s consumption amount
- Inputs are processed first, with recipe priorities reconsidered after every group. Active processing blocks defer item compression until other recipes have been attempted. Outputs present before the impact may then be reprocessed; newly produced outputs are excluded from that impact
- Supports AnvilCraft fluid mixing and liquid enchantment reactions. Remaining groups try fluid-only recipes when item processing has not changed the fluids. Mixing and liquid enchantment preserve their ingredients and fluids if outputs do not fit
- The model's horizontal bounds form a 3×3 grid: the center selects outputs and the surrounding eight cells select inputs. This grid rotates with the model. Empty-hand clicks on the inner floor retrieve all items from the selected input cell, or all outputs from the center; the first stack goes into the main hand and the rest into the inventory
- Main-hand items enter through the inner floor or the upper third of the walls and rim, preferring the closest input cell. Identical items with identical components share one input slot. The middle walls and outer bottom do not accept ordinary held items
- Automation inserts through the opening without extracting inputs. Upper and middle side ports extract their corresponding input cell; the bottom and lowest side ports only extract outputs. Entity side ports select the center of that side, while bonded blocks use the connected part's position
- Filling merges matching fluids and rejects new types after all eight layers are used. Bottom ports drain from the bottom and move inserted fluid to the bottom; other ports drain from the top. Buckets and bottles select a layer by click height and may search lower layers for matching fluid. Failed container interactions never insert the container as an ingredient
- Ignition and plasma jets use the top layer; covering it with a nonflammable fluid extinguishes it. Recipes can use any matching layer and produce several fluids. A Menger Sponge used on a wall clears every layer
- Upright pots absorb fluid sources across their opening. Lava damage, plastic melt slowing and extinguishing depend on the layer actually touched; extinguishing a noncreative entity consumes 250mB of the corresponding fluid
- Lava repairs damaged input items carrying Fire Reforging by 10 durability per tick. A lit normal campfire below deals 1 damage per hit, or 2 for a soul campfire; sneaking avoids this campfire damage. Entities touching the wall of an upright large pot can climb it
- Like ordinary plastic cauldrons, every material can store lava normally
- For a forced type without a cavity, upward-facing model surfaces replace the inner floor for the same nine-cell interactions
- There is no demonstration blueprint for it; you have to model one yourself

## Type override

When a model does not qualify but you want the type anyway, <ref item="anvilcraft:multiphase_transcendium"/> can force a Crate, Tank or Cauldron. The price is that capacity is calculated from the model's complete outer bounding volume instead of its actual cavity. A forced fluid container still accepts and outputs fluids normally, it just does not render its internal fluid or surface; to see what is inside, use containers, Jade or the Anvil Hammer overlay

A forced cauldron also measures its opening from the horizontal projection of that bounding box — so a model with a large enough bounding box lands on a Large Plastic Cauldron in one step

Hardness, transparency and fire resistance follow [Material Comparison](000_material_comparison.md). See [Plastic Molding](../004_molding/index.md) for model limits and production

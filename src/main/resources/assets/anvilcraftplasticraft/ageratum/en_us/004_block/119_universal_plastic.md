---
navigation:
  title: "Universal Plastic"
  icon: "anvilcraftplasticraft:universal_plastic"
items:
  - anvilcraftplasticraft:universal_plastic
  - anvilcraftplasticraft:universal_plastic_granule
---

# Universal Plastic

A universal-plastic-melt fluid block placed in the world always solidifies in place as one universal plastic block, regardless of which world mechanism caused the change. Rain, adjacent water, and blocks in `anvilcraftplasticraft:plastic_melt_coolants` are current examples. The product keeps the melt's colour and the world conversion never also drops granules.

The block is `16 px` wide, `16 px` long, and `14 px` high. It has no menu, inventory, tank, fluid capability, or special storage. It retains the common plastic-product behaviour instead: gravity, buoyancy, dynamic attraction, pushing, sliding-rail transport, magnetisation, adhesive bonding, persistence, clicked-face placement, hammer quick-use and six-face rotation, and colour-preserving recovery. Hammer rotation uses the centre of the actual outline bounds, so flipping between upright and upside-down does not shift the block because of its missing `2 px`; a target orientation that would overlap a block or entity renders pale red and is not applied on release. Its texture is generated from the common plastic base and the selected 16-level colour palette. Base greys map by absolute brightness across `0..255`, so a base that uses only a bright range is not forcibly darkened. Resource reloads regenerate it from the complete edited base pattern, preserving the border around every exposed face.

For gravity-affected blocks such as sand and anvils, the `14 px` top is not a block-grid-aligned landing surface: a block that is already falling shatters into its corresponding drop on impact. A gravity-affected block placed directly one cell above universal plastic remains a block until the logical cell below is completely unoccupied by the plastic entity.

## Molded products

Normal products made by a Plastic Molding Chamber reuse the same universal plastic item and entity IDs, but their item data stores the actual formed shape, model hash, melt material and colour, and stable pivot. Every source cube retains its original endpoints and rotation, so continuous slopes are not refitted into steps from the `1 px` manufacturing cells. When melt is short, the whole model is cut horizontally at the paid forming percentage: the cut has a flat top while every remaining sloped side keeps its original angle. The inventory model, world entity, and bonded block state all derive from the same actual surface data and share one cached result from Plasticraft's public procedural-texture API. Inventory rendering normalises the longest visible surface-bounds axis to the same footprint as an ordinary universal plastic block and centres the actual surface; this does not change world size. A world entity samples block and sky light at the centre and six outer faces of its real bounds, so a small or offset model does not turn black on an opaque floor because its declared origin lies elsewhere. Hammer recovery, chunk reloads, and cross-dimensional travel do not reduce the product to the legacy block shape, and holding the item adds no placement preview.

A molded entity has 24 discrete orientations: six attachment faces, each with four quarter-turns in its plane. Entity persistence and network synchronisation preserve both parts, while the saved manufacturing pivot keeps rendering, interaction outline, and physical collision aligned. Item placement follows the existing universal plastic block rules. Breaking, hammer-recovering, or pick-blocking an entity in any rotation returns the same model and material in the default item orientation.

Each complete or horizontally cut volumetric source cube becomes one continuous convex collision body. A dynamic entity uses those bodies directly, so a sloped cube keeps sloped physics and sloped F3+B edges instead of becoming many small boxes. A bonded block registers that same set of real convex bodies as SAT obstacles, preserving slopes for the final movement clipping of players, mobs, dropped items, and plastic entities. It also exposes a coarse `VoxelShape` made from one bounding box per body, but that shape is only for vanilla block APIs, broad-phase enumeration, and selection candidates; it neither overrides the real collision nor returns to per-pixel fitting. A zero-thickness cube renders on both sides and has a very thin interaction-only outline, so it can be selected and recovered but cannot support, block, or push an entity. Shapes up to the full 3x3x3 workspace retain the common plastic physics for gravity, buoyancy, dynamic attraction, pushing, sliding rails, magnetisation, adhesive bonding, persistence, and recovery.

Entity movement, short support probes, stepping, carrying, and side pushing share these bodies and the same continuous SAT contact. Players, mobs, dropped items, and other supported entities follow one rule: movement over a slope stays on its real surface, and sneaking stops only at its real support edge. A plastic product supported by another entity receives its carried displacement during that supporter's current move instead of catching up on a later entity tick. Movement normal to and away from the product does not drag it along, but a gravity component in the same request does not cancel tangential carrying. Pushing a rotated cube transfers only the component aimed along the real contact normal, while the pusher retains movement along the face. If any product in a push chain is blocked, the pusher is clipped to the distance the whole chain can complete. Server-side player movement validation uses the same real convex bodies, so it does not mistake a compatibility bounding-box edge for a surface that can be stood on.

An unspecified product, an explicitly normal product, and any future product downgraded to normal after partial forming all use the tooltip "An ordinary piece of plastic". Every universal plastic item also shows its exact three-axis size in blocks with at most four necessary decimal places. The ordinary `16x16x14 px` block displays `Size: 1 x 0.875 x 1 blocks`; a molded product uses its exact visible surface bounds.

## Carry synchronisation and collision outlines

When a plastic product is genuinely supported by an entity's head or another face, it receives the tangential displacement during that carrier's current move. The client keeps ordered, unconfirmed movement segments per X, Y, and Z axis, so an immediate reversal does not cancel the two steps before the server confirms them. Snapshots consume the path in order; an upside-down hardened-resin cauldron therefore stays on a contacting rim or inner ceiling in the same tick and cannot be interpolated into the player's pose. Support, side pushing, and ordinary entities still use the same continuous convex bodies, while empty space inside the cauldron is not support.

With F3+B enabled, convex plastic collision is drawn as the real edge set of the union of every source cube. Shared faces, coplanar seams, and collinear segments are merged; concave openings, overlap boundaries, rotated cubes, and continuous slopes remain visible. The debug outline never falls back to a compatibility `VoxelShape` or one total bounding box; that compatibility shape remains limited to vanilla APIs and broad-phase queries.

## Plastic granules

Granules are deliberately limited to recipe or container processing. Each of these routes produces 16 granules with the melt's colour per `1000 mB`:

- the existing solid-liquid cooling recipe with a cold item;
- a Catalytic Press Lid outlet aimed at a full vanilla water cauldron.

These routes do not change the rule for a melt fluid block in the world. Each novice AnvilCraft Jeweler offer randomly requests one of the 16 colours, buying 8 granules of that colour for 2 emeralds, up to 16 trades per offer.

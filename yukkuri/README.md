# Yukkuri

Yukkuri is the public large-cauldron vaporization runtime bundled with AnvilCraft: Plasticraft.

Addons register a `VaporizationSource` to describe how a large cauldron is heated and expose an
`IVaporConsumer` through `YukkuriCapabilities.VAPOR_CONSUMER` on the block directly above the cauldron.
Consumers are open by default: vapor they cannot accept escapes into the atmosphere, and a missing consumer also
allows vaporization to continue. A consumer that represents a sealed connection can override `sealsOutlet` so its
remaining capacity applies backpressure before the cauldron is drained.

Yukkuri depends only on NeoForge, Minecraft, and AnvilCraft. It never references Plasticraft content.

## Depending on Yukkuri

Use the published API as a compile-time dependency. A mod that keeps a legacy fallback should declare Yukkuri as an
optional NeoForge dependency and load its integration only when `ModList` contains `yukkuri`.

```groovy
compileOnly "dev.anvilcraft.plasticraft:yukkuri:0.1.0"
```

Plasticraft embeds the same artifact with NeoForge Jar-in-Jar, so players do not install a second file when they use
the integration through Plasticraft.

## Registering a consumer

Register `YukkuriCapabilities.VAPOR_CONSUMER` for the engine block or block entity during
`RegisterCapabilitiesEvent`. The queried side is `Direction.DOWN`, and the provider must be located on the first
block directly above the large cauldron's top-center part. Its `IVaporConsumer` must honor `VaporAction.SIMULATE`
without changing state and return the number of millibuckets accepted. Override `sealsOutlet` only when the physical
connection is airtight and must stop vaporization whenever the consumer is full; otherwise unaccepted vapor is vented.
For an open consumer, simulation can receive the complete vapor offer while execution receives only the amount that
simulation accepted.

## Registering a source

Register one `VaporizationSource` from mod initialization:

```java
VaporizationSources.register(MyVaporizationSource.INSTANCE);
```

`createOffer` discovers the source structure and returns an exact fluid-input/vapor-output pair without changing the
world. `commit` consumes source-specific fuel or power after the cauldron input has been drained. Sources are checked
by descending priority and then by resource-location ID; the first source that completes a transaction wins the tick.

package dev.anvilcraft.plasticraft.molding.blueprint;

import dev.anvilcraft.plasticraft.block.PlasticMoldingMachineState;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.dubhe.anvilcraft.item.DiskItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** 成型舱、磁盘槽与共享文件之间的服务端事务。 */
public final class MoldingBlueprintService {
    private MoldingBlueprintService() {
    }

    public static OperationResult storeModel(
        ServerPlayer player,
        PlasticMoldingChamberBlockEntity chamber,
        UUID sessionId,
        long baseRevision,
        String expectedDiskToken,
        boolean overwriteConfirmed
    ) throws BlueprintException {
        requireSession(chamber, player, sessionId, baseRevision);
        ItemStack currentDisk = requireDisk(chamber);
        verifyDisk(currentDisk, expectedDiskToken, overwriteConfirmed);

        MinecraftServer server = player.getServer();
        if (server == null) throw failure("server_unavailable", "Server is unavailable");
        long time = System.currentTimeMillis();
        MoldingBlueprint blueprint = MoldingBlueprint.create(
            chamber.model(),
            player.getUUID(),
            player.getGameProfile().getName(),
            time
        );
        ItemStack replacement = MoldingBlueprintDisk.writeCopy(currentDisk, blueprint);
        MoldingBlueprintLibrary.StoredBlueprint stored = MoldingBlueprintLibrary.saveNew(server, blueprint);
        try {
            chamber.inventory().setItem(PlasticMoldingChamberBlockEntity.DISK_SLOT, replacement);
            chamber.inventory().setChanged();
        } catch (RuntimeException exception) {
            MoldingBlueprintLibrary.rollbackNew(server, stored);
            throw new BlueprintException("disk_write_failed", exception.getMessage(), exception);
        }
        return new OperationResult(stored.fileId(), stored.blueprint().fileRevision());
    }

    public static OperationResult writeSharedModelToDisk(
        ServerPlayer player,
        PlasticMoldingChamberBlockEntity chamber,
        UUID sessionId,
        long baseRevision,
        String fileId,
        long fileRevision,
        String expectedDiskToken,
        boolean overwriteConfirmed
    ) throws BlueprintException {
        requireSession(chamber, player, sessionId, baseRevision);
        ItemStack currentDisk = requireDisk(chamber);
        MinecraftServer server = player.getServer();
        if (server == null) throw failure("server_unavailable", "Server is unavailable");
        MoldingBlueprint blueprint = MoldingBlueprintLibrary.read(server, fileId, fileRevision);
        boolean alreadyStored = MoldingBlueprintDisk.read(currentDisk)
            .filter(existing -> existing.name().equals(blueprint.name())
                && existing.modelHash().equals(blueprint.modelHash()))
            .isPresent();
        verifyDisk(currentDisk, expectedDiskToken, overwriteConfirmed || alreadyStored);
        ItemStack replacement = MoldingBlueprintDisk.writeCopy(currentDisk, blueprint);
        chamber.inventory().setItem(PlasticMoldingChamberBlockEntity.DISK_SLOT, replacement);
        chamber.inventory().setChanged();
        return new OperationResult(fileId, blueprint.fileRevision());
    }

    public static OperationResult loadDiskModel(
        ServerPlayer player,
        PlasticMoldingChamberBlockEntity chamber,
        UUID sessionId,
        long baseRevision,
        String expectedDiskToken,
        boolean draftOverwriteConfirmed
    ) throws BlueprintException {
        requireSession(chamber, player, sessionId, baseRevision);
        ItemStack currentDisk = requireDisk(chamber);
        if (!MoldingBlueprintDisk.stateToken(currentDisk).equals(expectedDiskToken)) {
            throw failure("stale_disk", "The disk changed before the operation completed");
        }
        if (chamber.machineState() != PlasticMoldingMachineState.EDITABLE) {
            throw failure("not_editable", "Unlock the chamber before loading a disk");
        }
        if (chamber.moldedClayBalls() != 0) throw failure("reserved_clay", "Return reserved clay before loading a disk");
        if (chamber.batchFluidAmount() != 0) {
            throw failure("drain_batch_first", "Drain the molding region before loading a disk");
        }
        MoldingBlueprint blueprint = MoldingBlueprintDisk.read(currentDisk)
            .orElseThrow(() -> failure("invalid_disk_blueprint", "The disk does not contain a valid molding blueprint"));
        boolean hasDraft = !chamber.model().isEmpty();
        if (hasDraft && !draftOverwriteConfirmed) {
            throw failure("confirm_model_overwrite", "Loading this disk will replace the current draft");
        }
        PlasticMoldingChamberBlockEntity.MachineOutcome outcome = chamber.replaceEditableModel(
            blueprint.model(),
            baseRevision
        );
        if (!outcome.accepted()) throw failure(outcome.reason(), outcome.reason());
        return new OperationResult("", blueprint.fileRevision());
    }

    private static void requireSession(
        PlasticMoldingChamberBlockEntity chamber,
        ServerPlayer player,
        UUID sessionId,
        long baseRevision
    ) throws BlueprintException {
        PlasticMoldingChamberBlockEntity.MachineOutcome outcome = chamber.validateBlueprintSession(
            player,
            sessionId,
            baseRevision
        );
        if (!outcome.accepted()) throw failure(outcome.reason(), outcome.reason());
    }

    private static ItemStack requireDisk(PlasticMoldingChamberBlockEntity chamber) throws BlueprintException {
        ItemStack disk = chamber.inventory().getItem(PlasticMoldingChamberBlockEntity.DISK_SLOT);
        if (!(disk.getItem() instanceof DiskItem)) throw failure("missing_disk", "Place an AnvilCraft disk in the slot");
        return disk;
    }

    private static void verifyDisk(
        ItemStack disk,
        String expectedDiskToken,
        boolean overwriteConfirmed
    ) throws BlueprintException {
        String currentToken = MoldingBlueprintDisk.stateToken(disk);
        if (!currentToken.equals(expectedDiskToken)) {
            throw failure("stale_disk", "The disk changed before the operation completed");
        }
        if (!currentToken.isEmpty() && !overwriteConfirmed) {
            throw failure("confirm_disk_overwrite", "The disk already contains data");
        }
    }

    private static BlueprintException failure(String reason, String detail) {
        return new BlueprintException(reason, detail);
    }

    public record OperationResult(String selectedFileId, long fileRevision) {
    }
}

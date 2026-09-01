package dev.anvilcraft.plasticraft.molding.session;

import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;

import java.util.UUID;

/** 打开或重建编辑器时由服务端确认的完整会话快照。 */
public record MoldingSessionSnapshot(
    UUID sessionId,
    boolean writable,
    String writerName,
    long revision,
    EditableMoldingModel model
) {
}

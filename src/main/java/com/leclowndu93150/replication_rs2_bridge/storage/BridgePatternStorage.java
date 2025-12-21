package com.leclowndu93150.replication_rs2_bridge.storage;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public class BridgePatternStorage {

    @SuppressWarnings("DataFlowIssue")
    public static BridgePatternRepository get(Level level) {
        ServerLevel serverLevel = level.getServer().getLevel(Level.OVERWORLD);
        return serverLevel.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(
                BridgePatternRepository::new,
                BridgePatternRepository::new,
                null
            ),
            BridgePatternRepository.NAME
        );
    }

    @SuppressWarnings("DataFlowIssue")
    public static BridgeTaskSnapshotRepository getTaskSnapshots(Level level) {
        ServerLevel serverLevel = level.getServer().getLevel(Level.OVERWORLD);
        return serverLevel.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(
                BridgeTaskSnapshotRepository::new,
                BridgeTaskSnapshotRepository::new,
                null
            ),
            BridgeTaskSnapshotRepository.NAME
        );
    }

    @SuppressWarnings("DataFlowIssue")
    public static BridgeTaskHandlerRepository getTaskHandler(Level level) {
        ServerLevel serverLevel = level.getServer().getLevel(Level.OVERWORLD);
        return serverLevel.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(
                BridgeTaskHandlerRepository::new,
                BridgeTaskHandlerRepository::new,
                null
            ),
            BridgeTaskHandlerRepository.NAME
        );
    }
}

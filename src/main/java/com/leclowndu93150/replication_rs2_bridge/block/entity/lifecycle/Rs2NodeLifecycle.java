package com.leclowndu93150.replication_rs2_bridge.block.entity.lifecycle;

import org.slf4j.Logger;

import com.leclowndu93150.replication_rs2_bridge.block.entity.RepRS2BridgeBlockEntity;
import com.refinedmods.refinedstorage.common.api.support.network.NetworkNodeContainerProvider;

import net.minecraft.world.level.Level;

/**
 * Handles the lifecycle of the RS2 network node container for the bridge block.
 */
public final class Rs2NodeLifecycle {
    private static final long MAX_RETRY_DELAY = 200L;
    private static final int CONNECTION_CHECK_INTERVAL = 20; // Check every second (20 ticks)

    private final RepRS2BridgeBlockEntity owner;
    private final NetworkNodeContainerProvider containerProvider;
    private final Logger logger;
    private Rs2LifecycleState state = Rs2LifecycleState.IDLE;
    private long retryAtTick = -1L;
    private int attempts;
    private boolean initializationInFlight;
    private boolean rsNodeAttached;
    private int connectionCheckTicks = 0;

    public Rs2NodeLifecycle(final RepRS2BridgeBlockEntity owner,
                            final NetworkNodeContainerProvider containerProvider,
                            final Logger logger) {
        this.owner = owner;
        this.containerProvider = containerProvider;
        this.logger = logger;
    }

    public void requestInitialization(final String reason) {
        if (canAttemptInitialization()) {
            beginInitialization(reason);
        }
    }

    public void resetAfterDataLoad() {
        initializationInFlight = false;
        attempts = 0;
        retryAtTick = -1L;
        rsNodeAttached = false;
        if (state != Rs2LifecycleState.REMOVED) {
            state = Rs2LifecycleState.IDLE;
        }
    }

    public void tick() {
        final Level level = owner.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        // Handle retry logic for WAITING_RETRY state
        if (state == Rs2LifecycleState.WAITING_RETRY) {
            if (retryAtTick >= 0 && level.getGameTime() >= retryAtTick) {
                beginInitialization("retry");
            }
            return;
        }

        // Periodic connection check every second when in READY state
        if (state == Rs2LifecycleState.READY) {
            connectionCheckTicks++;
            if (connectionCheckTicks >= CONNECTION_CHECK_INTERVAL) {
                connectionCheckTicks = 0;
                checkConnectionAndReconnect();
            }
        }
    }

    private void checkConnectionAndReconnect() {
        if (RepRS2BridgeBlockEntity.isWorldUnloading()) {
            return;
        }
        final var networkNode = owner.getBridgeNetworkNode();
        if (networkNode == null) {
            logger.warn("Bridge: Network node is null, triggering reconnection");
            triggerReconnect("null_network_node");
            return;
        }
        final var network = networkNode.getNetwork();
        if (network == null) {
            logger.warn("Bridge: Disconnected from RS2 network, triggering reconnection");
            triggerReconnect("null_network");
        }
    }

    private void triggerReconnect(final String reason) {
        state = Rs2LifecycleState.IDLE;
        rsNodeAttached = false;
        beginInitialization(reason);
    }

    public void shutdown(final String reason, final boolean allowRestart) {
        initializationInFlight = false;
        attempts = 0;
        retryAtTick = -1L;
        if (state == Rs2LifecycleState.REMOVED && !allowRestart) {
            return;
        }
        state = Rs2LifecycleState.REMOVING;
        final Rs2LifecycleState targetState = allowRestart ? Rs2LifecycleState.IDLE : Rs2LifecycleState.REMOVED;
        final Level level = owner.getLevel();
        if (level != null && !level.isClientSide() && rsNodeAttached) {
            try {
                containerProvider.remove(level);
            } catch (Exception e) {
                logger.warn("Bridge: Failed to remove RS2 node during {}: {}", reason, e.getMessage());
            } finally {
                rsNodeAttached = false;
            }
        }
        state = targetState;
    }

    public boolean isRemoved() {
        return state == Rs2LifecycleState.REMOVED;
    }

    private boolean canAttemptInitialization() {
        if (RepRS2BridgeBlockEntity.isWorldUnloading()) {
            return false;
        }
        final Level level = owner.getLevel();
        if (level == null || level.isClientSide()) {
            return false;
        }
        if (isRemoved() || state == Rs2LifecycleState.REMOVING) {
            return false;
        }
        if (initializationInFlight) {
            return false;
        }
        if (state == Rs2LifecycleState.READY) {
            return false;
        }
        if (state == Rs2LifecycleState.WAITING_RETRY && retryAtTick >= 0 && level.getGameTime() < retryAtTick) {
            return false;
        }
        return true;
    }

    private void beginInitialization(final String reason) {
        final Level level = owner.getLevel();
        if (level == null || level.isClientSide() || isRemoved()) {
            return;
        }
        initializationInFlight = true;
        state = Rs2LifecycleState.INITIALIZING;
        attempts++;
        try {
            containerProvider.initialize(level, () -> handleInitializationCallback(reason));
        } catch (Exception e) {
            handleInitializationFailure(reason, e);
        }
    }

    private void handleInitializationCallback(final String reason) {
        initializationInFlight = false;
        final Level level = owner.getLevel();
        if (level == null || level.isClientSide() || isRemoved()
            || RepRS2BridgeBlockEntity.isWorldUnloading()
            || state != Rs2LifecycleState.INITIALIZING) {
            return;
        }
        try {
            owner.onRsNodeInitializedFromLifecycle();
            state = Rs2LifecycleState.READY;
            retryAtTick = -1L;
            attempts = 0;
            rsNodeAttached = true;
        } catch (Exception e) {
            handleInitializationFailure(reason, e);
        }
    }

    private void handleInitializationFailure(final String reason, final Exception exception) {
        logger.error("Bridge: RS2 node initialization failed ({}): {}", reason, exception.getMessage(), exception);
        state = Rs2LifecycleState.WAITING_RETRY;
        initializationInFlight = false;
        rsNodeAttached = false;
        final Level level = owner.getLevel();
        if (level != null && !level.isClientSide()) {
            final long backoff = 20L * Math.max(1, Math.min(attempts, 6));
            retryAtTick = level.getGameTime() + Math.min(MAX_RETRY_DELAY, backoff);
        } else {
            retryAtTick = -1L;
        }
    }

    private enum Rs2LifecycleState {
        IDLE,
        INITIALIZING,
        READY,
        WAITING_RETRY,
        REMOVING,
        REMOVED
    }
}

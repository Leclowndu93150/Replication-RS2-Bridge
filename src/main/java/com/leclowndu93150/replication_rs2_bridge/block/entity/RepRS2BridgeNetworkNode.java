package com.leclowndu93150.replication_rs2_bridge.block.entity;

import com.buuz135.replication.api.IMatterType;
import com.leclowndu93150.replication_rs2_bridge.Config;
import com.mojang.logging.LogUtils;
import com.refinedmods.refinedstorage.api.autocrafting.Pattern;
import com.refinedmods.refinedstorage.api.autocrafting.PatternBuilder;
import com.refinedmods.refinedstorage.api.autocrafting.PatternType;
import com.refinedmods.refinedstorage.api.autocrafting.status.TaskStatus;
import com.refinedmods.refinedstorage.api.autocrafting.task.ExternalPatternSink;
import com.refinedmods.refinedstorage.api.autocrafting.task.StepBehavior;
import com.refinedmods.refinedstorage.api.autocrafting.task.Task;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskId;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskListener;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent;
import com.refinedmods.refinedstorage.api.network.autocrafting.ParentContainer;
import com.refinedmods.refinedstorage.api.network.autocrafting.PatternProvider;
import com.refinedmods.refinedstorage.api.network.impl.autocrafting.TaskContainer;
import com.refinedmods.refinedstorage.api.network.impl.node.AbstractNetworkNode;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.network.storage.StorageProvider;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Storage;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * RS2 network node that exposes Replication storage and patterns to the Refined Storage autocrafting system.
 */
public class RepRS2BridgeNetworkNode extends AbstractNetworkNode
    implements StorageProvider, PatternProvider, TaskListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final RepRS2BridgeBlockEntity blockEntity;
    private final long energyUsage;
    private final TaskContainer tasks = new TaskContainer(this);
    private final Set<ParentContainer> parents = new HashSet<>();
    private final Map<PatternSignature, ReplicationPatternInstance> patternsBySignature = new HashMap<>();
    private final Map<Pattern, ReplicationPatternInstance> patternsByPattern = new HashMap<>();

    private StepBehavior stepBehavior = StepBehavior.DEFAULT;
    private int priority;

    public RepRS2BridgeNetworkNode(final RepRS2BridgeBlockEntity blockEntity, final long energyUsage) {
        this.blockEntity = blockEntity;
        this.energyUsage = energyUsage;
    }

    @Override
    public long getEnergyUsage() {
        return energyUsage;
    }

    @Override
    public void setNetwork(@Nullable final Network newNetwork) {
        final Network previous = this.network;
        if (previous != null && previous != newNetwork) {
            tasks.detachAll(previous);
            final StorageNetworkComponent storage = previous.getComponent(StorageNetworkComponent.class);
            if (storage != null) {
                storage.removeSource(getStorage());
            }
        }
        super.setNetwork(newNetwork);
        if (newNetwork != null) {
            tasks.attachAll(newNetwork);
            if (isActive()) {
                final StorageNetworkComponent storage = newNetwork.getComponent(StorageNetworkComponent.class);
                if (storage != null) {
                    storage.addSource(getStorage());
                }
            }
        }
    }

    @Override
    public void doWork() {
        super.doWork();
        if (network != null) {
            tasks.step(network, stepBehavior, this);
        }
    }

    @Override
    protected void onActiveChanged(final boolean newActive) {
        super.onActiveChanged(newActive);
        if (Config.enableDebugLogging) {
            LOGGER.debug("Bridge node active state changed: {}", newActive);
        }
        if (network != null) {
            final StorageNetworkComponent storage = network.getComponent(StorageNetworkComponent.class);
            if (storage != null) {
                if (newActive) {
                    storage.addSource(getStorage());
                } else {
                    storage.removeSource(getStorage());
                }
            }
        }
        blockEntity.onNetworkActivityChanged(newActive);
    }

    @Override
    public Storage getStorage() {
        return blockEntity.getMatterStorage();
    }

    public void setPriority(final int priority) {
        if (this.priority == priority) {
            return;
        }
        this.priority = priority;
        parents.forEach(parent -> patternsByPattern.keySet().forEach(pattern -> parent.update(pattern, priority)));
    }

    public void setStepBehavior(final StepBehavior behavior) {
        this.stepBehavior = behavior;
    }

    public void updatePatterns(final List<ReplicationPatternTemplate> templates) {
        final Map<PatternSignature, ReplicationPatternInstance> nextBySignature = new HashMap<>();
        final Map<Pattern, ReplicationPatternInstance> nextByPattern = new HashMap<>();
        final List<Pattern> added = new ArrayList<>();
        final List<Pattern> removed = new ArrayList<>();

        for (ReplicationPatternTemplate template : templates) {
            final ReplicationPatternInstance existing = patternsBySignature.get(template.signature());
            if (existing != null) {
                final ReplicationPatternInstance updated = new ReplicationPatternInstance(existing.pattern(), template);
                nextBySignature.put(template.signature(), updated);
                nextByPattern.put(existing.pattern(), updated);
            } else {
                final Pattern pattern = createPattern(template);
                if (pattern == null) {
                    continue;
                }
                final ReplicationPatternInstance instance = new ReplicationPatternInstance(pattern, template);
                nextBySignature.put(template.signature(), instance);
                nextByPattern.put(pattern, instance);
                added.add(pattern);
            }
        }

        for (Pattern pattern : patternsByPattern.keySet()) {
            if (!nextByPattern.containsKey(pattern)) {
                removed.add(pattern);
            }
        }

        patternsBySignature.clear();
        patternsBySignature.putAll(nextBySignature);
        patternsByPattern.clear();
        patternsByPattern.putAll(nextByPattern);

        removed.forEach(pattern -> parents.forEach(parent -> parent.remove(this, pattern)));
        added.forEach(pattern -> parents.forEach(parent -> parent.add(this, pattern, priority)));
    }

    private Pattern createPattern(final ReplicationPatternTemplate template) {
        final PatternBuilder builder = PatternBuilder.pattern(PatternType.EXTERNAL);
        for (Map.Entry<IMatterType, Long> entry : template.matterCost().entrySet()) {
            final Item item = blockEntity.getItemForMatterType(entry.getKey());
            if (item == null) {
                LOGGER.warn("Skipping pattern for {} due to missing matter item {}", template.outputStack(), entry.getKey().getName());
                return null;
            }
            builder.ingredient(new ItemResource(item), entry.getValue());
        }
        final ItemStack output = template.outputStack();
        builder.output(ItemResource.ofItemStack(output), output.getCount());
        return builder.build();
    }

    @Override
    public void onAddedIntoContainer(final ParentContainer parentContainer) {
        parents.add(parentContainer);
        tasks.onAddedIntoContainer(parentContainer);
        patternsByPattern.keySet().forEach(pattern -> parentContainer.add(this, pattern, priority));
    }

    @Override
    public void onRemovedFromContainer(final ParentContainer parentContainer) {
        patternsByPattern.keySet().forEach(pattern -> parentContainer.remove(this, pattern));
        tasks.onRemovedFromContainer(parentContainer);
        parents.remove(parentContainer);
    }

    @Override
    public boolean contains(final AutocraftingNetworkComponent component) {
        return false;
    }

    @Override
    public void addTask(final Task task) {
        tasks.add(task, network);
        parents.forEach(parent -> parent.taskAdded(this, task));
    }

    @Override
    public void cancelTask(final TaskId taskId) {
        tasks.cancel(taskId);
    }

    @Override
    public List<TaskStatus> getTaskStatuses() {
        return tasks.getStatuses();
    }

    @Override
    public long getAmount(final ResourceKey resource) {
        return tasks.getAmount(resource);
    }

    @Override
    public void receivedExternalIteration() {
        blockEntity.handleExternalIteration();
    }

    @Override
    public void receivedExternalIteration(final Pattern pattern) {
        if (network == null) {
            return;
        }
        final AutocraftingNetworkComponent autocrafting = network.getComponent(AutocraftingNetworkComponent.class);
        if (autocrafting == null) {
            return;
        }
        final PatternProvider provider = autocrafting.getProviderByPattern(pattern);
        if (provider != null) {
            provider.receivedExternalIteration();
        }
    }

    @Override
    public ExternalPatternSink.Result accept(final Pattern pattern,
                                             final Collection<ResourceAmount> resources,
                                             final Action action) {
        final ReplicationPatternInstance instance = patternsByPattern.get(pattern);
        if (instance == null) {
            return ExternalPatternSink.Result.SKIPPED;
        }
        return blockEntity.handlePatternRequest(instance.template(), resources, action);
    }

    public ReplicationPatternTemplate getTemplate(final Pattern pattern) {
        final ReplicationPatternInstance instance = patternsByPattern.get(pattern);
        return instance != null ? instance.template() : null;
    }

    private record ReplicationPatternInstance(Pattern pattern, ReplicationPatternTemplate template) {
    }
}

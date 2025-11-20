package com.leclowndu93150.replication_rs2_bridge.block.entity.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.network.MatterNetwork;
import com.buuz135.replication.ReplicationRegistry;
import com.leclowndu93150.replication_rs2_bridge.block.entity.RepRS2BridgeBlockEntity;
import com.leclowndu93150.replication_rs2_bridge.block.entity.task.ReplicationTaskHandler;
import com.leclowndu93150.replication_rs2_bridge.component.MatterComponent;
import com.leclowndu93150.replication_rs2_bridge.component.ModDataComponents;
import com.leclowndu93150.replication_rs2_bridge.item.ModItems;
import com.leclowndu93150.replication_rs2_bridge.item.UniversalMatterItem;
import com.leclowndu93150.replication_rs2_bridge.util.MatterTypeUtil;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.Storage;
import com.refinedmods.refinedstorage.api.storage.composite.CompositeAwareChild;
import com.refinedmods.refinedstorage.api.storage.composite.ParentComposite;
import com.refinedmods.refinedstorage.common.api.storage.PlayerActor;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Storage wrapper that mirrors the replication matter inventory into the RS2 network.
 */
public final class MatterItemsStorage implements Storage, CompositeAwareChild {
    private final RepRS2BridgeBlockEntity owner;
    private final ReplicationTaskHandler taskHandler;
    private ParentComposite parentComposite;
    private final Map<ResourceKey, Long> cachedAmounts = new HashMap<>();

    public MatterItemsStorage(final RepRS2BridgeBlockEntity owner, final ReplicationTaskHandler taskHandler) {
        this.owner = owner;
        this.taskHandler = taskHandler;
    }

    @Override
    public long insert(final ResourceKey resource, final long amount, final Action action, final Actor actor) {
        return 0;
    }

    @Override
    public long extract(final ResourceKey resource, final long amount, final Action action, final Actor actor) {
        if (!owner.isBridgeInitialized()) {
            return 0;
        }
        if (actor instanceof PlayerActor) {
            return 0;
        }
        if (resource instanceof ItemResource itemResource) {
            final Item item = itemResource.item();
            if (item == ModItems.UNIVERSAL_MATTER.get()) {
                final MatterNetwork network = owner.getNetwork();
                if (network != null) {
                    final ItemStack stack = itemResource.toItemStack(1);
                    final MatterComponent component = stack.get(ModDataComponents.MATTER.get());
                    if (component != null) {
                        final IMatterType matterType = MatterTypeUtil.getMatterTypeFromComponent(component);
                        if (matterType != null) {
                            final long available = network.calculateMatterAmount(matterType);
                            return Math.min(amount, available);
                        }
                    }
                }
            }
        }
        return 0;
    }

    public long extractVirtual(final Item item, final long amount) {
        return item == ModItems.UNIVERSAL_MATTER.get() ? amount : 0;
    }

    @Override
    public Collection<ResourceAmount> getAll() {
        if (!owner.isBridgeInitialized()) {
            return List.of();
        }
        final MatterNetwork network = owner.getNetwork();
        if (network == null) {
            return List.of();
        }

        final List<ResourceAmount> amounts = new ArrayList<>();
        final List<IMatterType> matterTypes = new ArrayList<>(
            ReplicationRegistry.MATTER_TYPES_REGISTRY.stream().toList()
        );
        final Map<IMatterType, Long> totalAllocated = taskHandler.summarizeAllocatedMatter();

        for (IMatterType matterType : matterTypes) {
            final long amount = network.calculateMatterAmount(matterType);
            final long allocated = totalAllocated.getOrDefault(matterType, 0L);
            final long available = amount - allocated;
            if (available > 0) {
                final ItemStack matterStack = UniversalMatterItem.createMatterStack(matterType, 1);
                if (!matterStack.isEmpty()) {
                    final ItemResource resource = ItemResource.ofItemStack(matterStack);
                    amounts.add(new ResourceAmount(resource, available));
                }
            }
        }
        return amounts;
    }

    @Override
    public long getStored() {
        return getAll().stream().mapToLong(ResourceAmount::amount).sum();
    }

    @Override
    public void onAddedIntoComposite(final ParentComposite parentComposite) {
        this.parentComposite = parentComposite;
        cachedAmounts.clear();
        for (ResourceAmount amount : getAll()) {
            cachedAmounts.put(amount.resource(), amount.amount());
        }
    }

    @Override
    public void onRemovedFromComposite(final ParentComposite parentComposite) {
        this.parentComposite = null;
        cachedAmounts.clear();
    }

    @Override
    public Amount compositeInsert(final ResourceKey resource, final long amount, final Action action, final Actor actor) {
        return Amount.ZERO;
    }

    @Override
    public Amount compositeExtract(final ResourceKey resource, final long amount, final Action action, final Actor actor) {
        final long extracted = extract(resource, amount, action, actor);
        if (extracted == 0) {
            return Amount.ZERO;
        }
        return new Amount(extracted, extracted);
    }

    public void refreshCache() {
        if (parentComposite == null || owner.getLevel() == null || owner.getLevel().isClientSide()) {
            return;
        }
        final Map<ResourceKey, Long> latest = new HashMap<>();
        for (ResourceAmount amount : getAll()) {
            latest.put(amount.resource(), amount.amount());
        }
        for (Map.Entry<ResourceKey, Long> entry : latest.entrySet()) {
            final long previous = cachedAmounts.getOrDefault(entry.getKey(), 0L);
            final long delta = entry.getValue() - previous;
            if (delta > 0) {
                parentComposite.addToCache(entry.getKey(), delta);
            } else if (delta < 0) {
                parentComposite.removeFromCache(entry.getKey(), -delta);
            }
        }
        for (ResourceKey previousKey : new HashSet<>(cachedAmounts.keySet())) {
            if (!latest.containsKey(previousKey)) {
                final long previous = cachedAmounts.get(previousKey);
                if (previous > 0) {
                    parentComposite.removeFromCache(previousKey, previous);
                }
            }
        }
        cachedAmounts.clear();
        cachedAmounts.putAll(latest);
    }
}

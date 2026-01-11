package com.leclowndu93150.replication_rs2_bridge.mixin;

import com.leclowndu93150.replication_rs2_bridge.item.ModItems;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskImpl;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.resource.list.MutableResourceList;
import com.refinedmods.refinedstorage.api.storage.root.RootStorage;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Prevents RS2 from refunding virtual matter items when replication tasks are cancelled or completed.
 */
@Mixin(value = TaskImpl.class, remap = false)
public abstract class TaskImplMixin {
    @Shadow
    private MutableResourceList internalStorage;

    @Inject(
        method = "returnInternalStorageAndTryCompleteTask",
        at = @At("HEAD")
    )
    private void repRs2Bridge$discardVirtualMatter(final RootStorage rootStorage,
                                                   final CallbackInfoReturnable<Boolean> cir) {
        final List<ResourceKey> keys = new ArrayList<>(internalStorage.getAll());
        for (ResourceKey key : keys) {
            if (key instanceof ItemResource itemResource
                && itemResource.item() == ModItems.UNIVERSAL_MATTER.get()) {
                final long amount = internalStorage.get(key);
                if (amount > 0) {
                    internalStorage.remove(key, amount);
                }
            }
        }
    }
}

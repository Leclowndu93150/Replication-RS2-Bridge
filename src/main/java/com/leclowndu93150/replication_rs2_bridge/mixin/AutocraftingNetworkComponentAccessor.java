package com.leclowndu93150.replication_rs2_bridge.mixin;

import com.refinedmods.refinedstorage.api.autocrafting.Pattern;
import com.refinedmods.refinedstorage.api.autocrafting.PatternRepositoryImpl;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskId;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskPlan;
import com.refinedmods.refinedstorage.api.network.autocrafting.PatternProvider;
import com.refinedmods.refinedstorage.api.network.impl.autocrafting.AutocraftingNetworkComponentImpl;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.refinedmods.refinedstorage.api.storage.root.RootStorage;
import java.util.Map;
import java.util.function.Supplier;

@Mixin(value = AutocraftingNetworkComponentImpl.class, remap = false)
public interface AutocraftingNetworkComponentAccessor {
    @Accessor("patternRepository")
    PatternRepositoryImpl repRs2Bridge$getPatternRepository();

    @Accessor("providerByPattern")
    Map<Pattern, PatternProvider> repRs2Bridge$getProviderByPattern();

    @Accessor("rootStorageProvider")
    Supplier<RootStorage> repRs2Bridge$getRootStorageProvider();

    @Invoker("addTask")
    TaskId repRs2Bridge$invokeAddTask(ResourceKey resource, long amount, Actor actor, TaskPlan plan, boolean notify);
}

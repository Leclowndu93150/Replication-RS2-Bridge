package com.leclowndu93150.replication_rs2_bridge.mixin;

import com.leclowndu93150.replication_rs2_bridge.block.entity.RepRS2BridgeNetworkNode;
import com.refinedmods.refinedstorage.api.autocrafting.Pattern;
import com.refinedmods.refinedstorage.api.autocrafting.calculation.CancellationToken;
import com.refinedmods.refinedstorage.api.autocrafting.calculation.CraftingCalculator;
import com.refinedmods.refinedstorage.api.autocrafting.calculation.CraftingCalculatorImpl;
import com.refinedmods.refinedstorage.api.autocrafting.craftability.IsCraftableCraftingCalculatorListener;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskPlan;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskPlanCraftingCalculatorListener;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskId;
import com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent;
import com.refinedmods.refinedstorage.api.network.impl.autocrafting.AutocraftingNetworkComponentImpl;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Optional;

/**
 * Make RS2 issue a single Task for replication bridge patterns instead of splitting into many tasks.
 */
@Mixin(value = AutocraftingNetworkComponentImpl.class, remap = false)
public abstract class AutocraftingNetworkComponentMixin implements AutocraftingNetworkComponent {

    @Inject(method = "ensureTaskForCraftableAmount", at = @At("HEAD"), cancellable = true)
    private void repRs2Bridge$aggregateReplicationTask(final ResourceKey resource,
        final Actor actor,
        final long amount,
        final CraftingCalculator calculator,
        final CancellationToken cancellationToken,
        final CallbackInfoReturnable<EnsureResult> cir) {
        final AutocraftingNetworkComponentAccessor accessor =
            (AutocraftingNetworkComponentAccessor) (Object) this;
        if (!isReplicationPattern(resource, accessor)) {
            return;
        }
        // Compute craftable amount once and create a single Task for it.
        final long craftable = IsCraftableCraftingCalculatorListener
            .binarySearchMaxAmount(calculator, resource, cancellationToken);
        final long correctedAmount = Math.min(craftable, amount);
        if (correctedAmount <= 0) {
            cir.setReturnValue(EnsureResult.MISSING_RESOURCES);
            return;
        }
        final CraftingCalculator singleCalc = new CraftingCalculatorImpl(
            accessor.repRs2Bridge$getPatternRepository(),
            accessor.repRs2Bridge$getRootStorageProvider().get()
        );
        final Optional<TaskPlan> planOpt = TaskPlanCraftingCalculatorListener
            .calculatePlan(singleCalc, resource, correctedAmount, cancellationToken);
        if (planOpt.isEmpty()) {
            cir.setReturnValue(EnsureResult.MISSING_RESOURCES);
            return;
        }
        final TaskId taskId = ((AutocraftingNetworkComponentAccessor) (Object) this)
            .repRs2Bridge$invokeAddTask(resource, correctedAmount, actor, planOpt.get(), false);
        cir.setReturnValue(taskId != null ? EnsureResult.TASK_CREATED : EnsureResult.MISSING_RESOURCES);
    }

    private boolean isReplicationPattern(final ResourceKey resource,
                                         final AutocraftingNetworkComponentAccessor accessor) {
        for (Pattern pattern : accessor.repRs2Bridge$getPatternRepository().getByOutput(resource)) {
            final Map<Pattern, ?> providers = accessor.repRs2Bridge$getProviderByPattern();
            if (providers.get(pattern) instanceof RepRS2BridgeNetworkNode) {
                return true;
            }
        }
        return false;
    }
}

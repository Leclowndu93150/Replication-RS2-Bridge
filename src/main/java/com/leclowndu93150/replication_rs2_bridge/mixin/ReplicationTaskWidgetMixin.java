package com.leclowndu93150.replication_rs2_bridge.mixin;

import com.buuz135.replication.api.task.IReplicationTask;
import com.buuz135.replication.client.gui.ReplicationTaskWidget;
import com.leclowndu93150.replication_rs2_bridge.block.entity.RepRS2BridgeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(value = ReplicationTaskWidget.TaskDisplay.class, remap = false)
public class ReplicationTaskWidgetMixin {

    @Shadow
    @Final
    private IReplicationTask task;

    @Inject(method = "render", at = @At("HEAD"))
    private void hideAndDisableCancelButtonForRS2Tasks(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY, float v, CallbackInfo ci) {
        BlockPos source = task.getSource();
        if (source != null) {
            Level level = Minecraft.getInstance().level;
            if (level != null && level.getBlockEntity(source) instanceof RepRS2BridgeBlockEntity) {
                try {
                    Field cancelButtonField = this.getClass().getDeclaredField("cancelButton");
                    cancelButtonField.setAccessible(true);
                    Object cancelButton = cancelButtonField.get(this);
                    if (cancelButton instanceof AbstractWidget widget) {
                        widget.active = false;
                        widget.visible = false;
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }
}

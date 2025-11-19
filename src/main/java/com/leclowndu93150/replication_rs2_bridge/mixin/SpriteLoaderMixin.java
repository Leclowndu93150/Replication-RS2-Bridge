package com.leclowndu93150.replication_rs2_bridge.mixin;

import com.leclowndu93150.replication_rs2_bridge.util.MatterTypeInfo;
import com.leclowndu93150.replication_rs2_bridge.util.MatterTypeUtil;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

@Mixin(SpriteLoader.class)
public abstract class SpriteLoaderMixin {

    @Shadow
    @Final
    private ResourceLocation location;

    @Shadow
    public abstract SpriteLoader.Preparations stitch(List<SpriteContents> contents, int mipLevel, Executor executor);

    @Inject(
            method = "loadAndStitch(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/ResourceLocation;ILjava/util/concurrent/Executor;Ljava/util/Collection;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void rep_rs2_bridge$loadMatterSprites(ResourceManager resourceManager, ResourceLocation atlasSource, int mipLevel, Executor executor, Collection<MetadataSectionSerializer<?>> serializers, CallbackInfoReturnable<CompletableFuture<SpriteLoader.Preparations>> cir) {
        if (!this.location.equals(InventoryMenu.BLOCK_ATLAS)) {
            return;
        }

        SpriteResourceLoader spriteResourceLoader = SpriteResourceLoader.create(serializers);
        CompletableFuture<SpriteLoader.Preparations> future = CompletableFuture
                .supplyAsync(() -> {
                    List<Function<SpriteResourceLoader, SpriteContents>> factories = SpriteSourceList.load(resourceManager, atlasSource).list(resourceManager);
                    List<Function<SpriteResourceLoader, SpriteContents>> mutable = new ArrayList<>(factories);
                    rep_rs2_bridge$appendMatterFactories(mutable, resourceManager);
                    return mutable;
                }, executor)
                .thenCompose(factories -> SpriteLoader.runSpriteSuppliers(spriteResourceLoader, factories, executor))
                .thenApply(contents -> this.stitch(contents, mipLevel, executor));

        cir.setReturnValue(future);
    }

    @Unique
    private void rep_rs2_bridge$appendMatterFactories(List<Function<SpriteResourceLoader, SpriteContents>> factories, ResourceManager resourceManager) {
        for (MatterTypeInfo info : MatterTypeUtil.getAllMatters().values()) {
            factories.add(loader -> rep_rs2_bridge$loadMatterSprite(loader, info, resourceManager));
        }
    }

    @Unique
    private SpriteContents rep_rs2_bridge$loadMatterSprite(SpriteResourceLoader loader, MatterTypeInfo info, ResourceManager resourceManager) {
        ResourceLocation spriteId = info.texture();
        ResourceLocation texturePath = ResourceLocation.fromNamespaceAndPath(
                spriteId.getNamespace(),
                "textures/" + spriteId.getPath() + ".png"
        );

        var resourceOpt = resourceManager.getResource(texturePath);
        var resource = resourceOpt.orElse(null);
        if (resource == null) {
            return null;
        }

        SpriteContents contents = loader.loadSprite(spriteId, resource);
        if (contents != null && info.matterType() != null) {
            float[] color = info.matterType().getColor().get();
            rep_rs2_bridge$applyTint(contents.getOriginalImage(), color);
        }
        return contents;
    }

    @Unique
    private void rep_rs2_bridge$applyTint(NativeImage image, float[] color) {
        if (color == null || color.length < 3) {
            return;
        }

        final float r = clampColor(color[0]);
        final float g = clampColor(color[1]);
        final float b = clampColor(color[2]);

        int height = image.getHeight();
        int width = image.getWidth();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getPixelRGBA(x, y);
                int a = (pixel >>> 24) & 0xFF;
                int rr = (int) Math.min(255, ((pixel >>> 16) & 0xFF) * r);
                int gg = (int) Math.min(255, ((pixel >>> 8) & 0xFF) * g);
                int bb = (int) Math.min(255, (pixel & 0xFF) * b);

                // convert to ABGR as used by NativeImage
                int tinted = (a << 24) | (bb << 16) | (gg << 8) | rr;
                image.setPixelRGBA(x, y, tinted);
            }
        }
    }

    @Unique
    private float clampColor(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}

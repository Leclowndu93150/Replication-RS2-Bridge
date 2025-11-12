package com.leclowndu93150.replication_rs2_bridge.client.model;

import com.google.common.collect.Maps;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.leclowndu93150.replication_rs2_bridge.ReplicationRSBridge;
import com.leclowndu93150.replication_rs2_bridge.component.MatterComponent;
import com.leclowndu93150.replication_rs2_bridge.component.ModDataComponents;
import com.leclowndu93150.replication_rs2_bridge.util.MatterTypeUtil;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.model.IModelBuilder;
import net.neoforged.neoforge.client.model.geometry.*;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Map;
import java.util.function.Function;

public record UniversalMatterItemModel(String defaultMatterType) implements IUnbakedGeometry<UniversalMatterItemModel> {

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides) {
        ResourceLocation textureLoc = resolveTexture(defaultMatterType);
        TextureAtlasSprite sprite = spriteGetter.apply(new Material(InventoryMenu.BLOCK_ATLAS, textureLoc));
        
        var itemContext = StandaloneGeometryBakingContext.builder(context)
                .withGui3d(false)
                .withUseBlockLight(false)
                .build(ResourceLocation.fromNamespaceAndPath(ReplicationRSBridge.MODID, "universal_matter_override"));
        
        var renderTypes = new RenderTypeGroup(RenderType.translucent(), 
                NeoForgeRenderTypes.ITEM_UNSORTED_TRANSLUCENT.get());
        
        var unbaked = UnbakedGeometryHelper.createUnbakedItemElements(0, sprite);
        var quads = UnbakedGeometryHelper.bakeElements(unbaked, material -> sprite, modelState);
        var model = IModelBuilder.of(itemContext.useAmbientOcclusion(), itemContext.useBlockLight(), 
                itemContext.isGui3d(), context.getTransforms(), 
                new MatterOverrideHandler(overrides, baker, itemContext), 
                sprite, renderTypes);
        quads.forEach(model::addUnculledFace);
        return model.build();
    }

    private ResourceLocation resolveTexture(String matterTypeName) {
        var info = MatterTypeUtil.getMatterInfo(matterTypeName);
        if (info != null) {
            return info.texture();
        }
        return ResourceLocation.fromNamespaceAndPath("replication", "gui/mattertypes/" + matterTypeName.toLowerCase());
    }

    public static class Loader implements IGeometryLoader<UniversalMatterItemModel> {
        @Override
        public UniversalMatterItemModel read(JsonObject json, JsonDeserializationContext context) {
            String defaultType = json.has("default_matter") ? json.get("default_matter").getAsString() : "empty";
            return new UniversalMatterItemModel(defaultType);
        }
    }

    private static class MatterOverrideHandler extends ItemOverrides {
        private final Map<String, BakedModel> cache = Maps.newHashMap();
        private final ItemOverrides nested;
        private final ModelBaker baker;
        private final IGeometryBakingContext owner;

        private MatterOverrideHandler(ItemOverrides nested, ModelBaker baker, IGeometryBakingContext owner) {
            this.nested = nested;
            this.baker = baker;
            this.owner = owner;
        }

        @Override
        @Nullable
        @ParametersAreNonnullByDefault
        public BakedModel resolve(BakedModel originalModel, ItemStack stack, @Nullable ClientLevel level, 
                                   @Nullable LivingEntity entity, int seed) {
            BakedModel overridden = nested.resolve(originalModel, stack, level, entity, seed);
            if (overridden != originalModel || level == null) {
                return overridden;
            }
            
            final MatterComponent component = stack.get(ModDataComponents.MATTER.get());
            if (component == null) {
                return originalModel;
            }
            
            final String matterTypeName = component.matterTypeName();
            final String cacheKey = "matter:" + matterTypeName;
            
            if (!cache.containsKey(cacheKey)) {
                UniversalMatterItemModel unbaked = new UniversalMatterItemModel(matterTypeName);
                BakedModel bakedModel = unbaked.bake(owner, baker, Material::sprite, BlockModelRotation.X0_Y0, this);
                cache.put(cacheKey, bakedModel);
                return bakedModel;
            }
            
            return cache.get(cacheKey);
        }
    }
}

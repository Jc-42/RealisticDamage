package com.github.Jc42.realisticdamage.mixins;

import com.github.Jc42.realisticdamage.PainCapabilityProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ArrowLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(ArrowLayer.class)
public abstract class CustomArrowPincushionRotation {


    @Inject(
            method = "renderStuckItem",
            at = @At("HEAD"),
            cancellable = true
    )
    private void customArrowRotation(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, Entity entity, float x, float y, float z, float partialTicks, CallbackInfo ci) {

        entity.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {
            ArrayList<double[]> arrows = pain.getLodgedArrowPositions();

            float f = Mth.sqrt(x * x + z * z);
            Arrow arrow = new Arrow(entity.level(), entity.getX(), entity.getY(), entity.getZ());

            if (arrows != null) {
                for(int i = 0; i < arrows.size(); ++i) {
                    if(arrows.get(i)[0] == x && arrows.get(i)[1] == y && arrows.get(i)[2] == z) {
                        arrow.setXRot((float)arrows.get(i)[3]);
                        arrow.setYRot((float)arrows.get(i)[4]);

                        arrow.yRotO = arrow.getYRot();
                        arrow.xRotO = arrow.getXRot();
                        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
                        dispatcher.render(arrow, 0.0D, 0.0D, 0.0D, 0.0F, partialTicks, poseStack, bufferSource, packedLight);

                        ci.cancel();
                    }
                }

            }
        });
    }
}
package com.github.Jc42.realisticdamage.mixins;

import com.github.Jc42.realisticdamage.PainCapabilityProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.StuckInBodyLayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(StuckInBodyLayer.class)
public abstract class CustomArrowPincushion<T extends LivingEntity, M extends PlayerModel<T>> extends RenderLayer<T, M> {

    protected CustomArrowPincushion(RenderLayerParent<T, M> pRenderer) {
        super(pRenderer);
    }

    // Make it private to match StuckInBodyLayer
    @Shadow
    protected abstract void renderStuckItem(PoseStack p_117566_, MultiBufferSource p_117567_,
                                            int p_117568_, Entity p_117569_, float p_117570_, float p_117571_, float p_117572_, float p_117573_);

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void customArrowRender(PoseStack poseStack, MultiBufferSource buffer, int packedLight, LivingEntity entity, float num1, float num2, float partialTick, float num3, float num4, float num5, CallbackInfo ci) {

        // Only run if the implementation ArrowLayer on the player is calling this
        if (this.getClass().getName().contains("ArrowLayer") && entity instanceof Player) {
            int i = entity.getArrowCount();


            entity.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {
                ArrayList<double[]> arrows = pain.getLodgedArrowPositions();


                RandomSource randomsource = RandomSource.create(entity.getId());
                if (i > 0) {
                    for (int j = 0; j < i; ++j) {
                        poseStack.pushPose();
                        ModelPart modelpart = this.getParentModel().getRandomModelPart(randomsource);
                        ModelPart.Cube modelpart$cube = modelpart.getRandomCube(randomsource);
                        modelpart.translateAndRotate(poseStack);

                        //region Override position
                        float x = randomsource.nextFloat();
                        float y = randomsource.nextFloat();
                        float z = randomsource.nextFloat();

                        if (arrows != null && j < arrows.size()) {
                            x = (float)arrows.get(j)[0];
                            y = (float)arrows.get(j)[1];
                            z = (float)arrows.get(j)[2];
                        }
                        //endregion

                        float f3 = Mth.lerp(x, modelpart$cube.minX, modelpart$cube.maxX) / 16.0F;
                        float f4 = Mth.lerp(y, modelpart$cube.minY, modelpart$cube.maxY) / 16.0F;
                        float f5 = Mth.lerp(z, modelpart$cube.minZ, modelpart$cube.maxZ) / 16.0F;
                        poseStack.translate(f3, f4, f5);
                        x = -1.0F * (x * 2.0F - 1.0F);
                        y = -1.0F * (y * 2.0F - 1.0F);
                        z = -1.0F * (z * 2.0F - 1.0F);
                        this.renderStuckItem(poseStack, buffer, packedLight, entity, x, y, z, partialTick);
                        poseStack.popPose();
                    }

                }
                ci.cancel();
            });
        }
    }
}
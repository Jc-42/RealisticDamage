package com.github.Jc42.realisticdamage;

import net.minecraftforge.eventbus.api.SubscribeEvent;import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PainOverlayHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PainOverlayHandler.class);
    public static final ResourceLocation OVERLAY = new ResourceLocation(RealisticDamage.MODID, "textures/gui/pain_bar.png");

    private static void renderPain(ForgeGui forgeGui, GuiGraphics guiGraphics, float partialTicks, int width, int height)
    {
        Minecraft minecraft = Minecraft.getInstance();


        Player player = minecraft.player;


        if (player != null) {
            player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {
                float chronicPainLevel = pain.getChronicPainLevel();
                float adrenalineLevel = pain.getAdrenalineLevel();
                if (minecraft.gameMode != null && minecraft.gameMode.getPlayerMode().isSurvival()) {
                    drawPain(guiGraphics, width, height, adrenalineLevel, chronicPainLevel);
                    forgeGui.rightHeight += 10;
                }
            });
        }
    }

    private static void drawPain(GuiGraphics gui, int width, int height, float adrenalineLevel, float chronicPainLevel)
    {
        int left = width / 2 + 91;
        int top = height - ((ForgeGui)Minecraft.getInstance().gui).rightHeight;


        int startX = left - 90;
        int startY = top + 4;

        int adrenalineWidth = adrenalineLevel > 10 ? 90 : (int)(((double)adrenalineLevel / 10) * 90);
        int chronicWidth = (int)(((double)chronicPainLevel / 100) * 90);
        int backgroundWidth = 90 - chronicWidth;

        //LOGGER.debug("fW: {}, bW:{}, pL:{}",adrenalineWidth, backgroundWidth, adrenalineLevel);
        //                                      --                                                  --          --
        //Image, xPosToRender, yPosToRender, zPos(blit offset), leftXOfImage, topYOfImage, widthOfImage, heightOfImage, widthToRender, heightToRender
        //Image, xPosToRender, yPosToRender, leftXOfImage, topYOfImage, widthToRender, heightToRender
        //Render Empty
        gui.blit(OVERLAY, startX + chronicWidth, startY, 0 + chronicWidth, 0, backgroundWidth, 5);

        //Render Filled Chronic
        gui.blit(OVERLAY, startX, startY, 0, 5,  chronicWidth, 5);

        //Render Filled Adrenaline
        gui.blit(OVERLAY, startX , startY, 0,10, adrenalineWidth, 5);


    }


    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    private static class OverlayRegister
    {
        @SubscribeEvent
        public static void registerOverlays(RegisterGuiOverlaysEvent event)
        {
            event.registerBelow(VanillaGuiOverlay.AIR_LEVEL.id(), "pain_level", (forgeGui, guiGraphics, partialTick, screenWidth, screenHeight) ->
            {
                Minecraft minecraft = Minecraft.getInstance();
                if (!minecraft.options.hideGui && forgeGui.shouldDrawSurvivalElements())
                {
                    forgeGui.setupOverlayRenderState(true, false);
                    renderPain(forgeGui, guiGraphics, partialTick, screenWidth, screenHeight);
                }
            });
        }
    }
}

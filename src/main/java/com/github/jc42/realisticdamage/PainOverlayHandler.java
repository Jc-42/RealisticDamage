package com.github.jc42.realisticdamage;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PainOverlayHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PainOverlayHandler.class);
    public static final Identifier OVERLAY = Identifier.fromNamespaceAndPath(RealisticDamage.MODID, "textures/gui/pain_bar.png");

    private static void renderPain(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, int width, int height)
    {
        Minecraft minecraft = Minecraft.getInstance();

        Player player = minecraft.player;

        if (player != null) {
            PainCapability pain = player.getData(RealisticDamage.PAIN);

            float chronicPainLevel = pain.getChronicPainLevel();
            float adrenalineLevel = pain.getAdrenalineLevel();
            if (minecraft.gameMode != null && minecraft.gameMode.getPlayerMode().isSurvival()) {
                drawPain(guiGraphics, minecraft.gui.hud, width, height, adrenalineLevel, chronicPainLevel);
            }
        }
    }

    private static void drawPain(GuiGraphicsExtractor gui, Hud hud, int width, int height, float adrenalineLevel, float chronicPainLevel)
    {
        int left = width / 2 + 91;
        // Draw below whatever right-side rows (food, vehicle health, air) already claimed this frame,
        // then claim a row of our own so anything registered above us stacks further down.
        int top = height - hud.rightHeight;
        hud.rightHeight += 10;

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
        gui.blit(RenderPipelines.GUI_TEXTURED, PainOverlayHandler.OVERLAY, startX + chronicWidth, startY, 0 + chronicWidth, 0, backgroundWidth, 5, backgroundWidth, 5, 256, 256);

        //Render Filled Chronic
        gui.blit(RenderPipelines.GUI_TEXTURED, PainOverlayHandler.OVERLAY, startX, startY, 0, 5, chronicWidth, 5, chronicWidth, 5, 256, 256);

        //Render Filled Adrenaline
        gui.blit(RenderPipelines.GUI_TEXTURED, PainOverlayHandler.OVERLAY, startX, startY, 0, 10, adrenalineWidth, 5, adrenalineWidth, 5, 256, 256);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiLayersEvent event)
    {
        event.registerAbove(
                VanillaGuiLayers.AIR_LEVEL,
                Identifier.fromNamespaceAndPath(RealisticDamage.MODID, "pain_level"),
                (guiGraphics, deltaTracker) -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (!minecraft.gameRenderer.gameRenderState().guiRenderState.isHudHidden && minecraft.gameMode != null && minecraft.gameMode.canHurtPlayer() && minecraft.getCameraEntity() instanceof Player) {
                        int width = guiGraphics.guiWidth();
                        int height = guiGraphics.guiHeight();
                        PainOverlayHandler.renderPain(guiGraphics, deltaTracker, width, height);
                    }
                }
        );
    }

}
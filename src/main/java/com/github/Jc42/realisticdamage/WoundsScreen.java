package com.github.Jc42.realisticdamage;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public class WoundsScreen extends Screen {
    private static final ResourceLocation WOUNDS_LOCATION = new ResourceLocation(RealisticDamage.MODID, "textures/gui/wound_inventory.png");
    private final int imageWidth = 176;
    private final int imageHeight = 166;
    private Player player;

    public WoundsScreen(Player player) {
        super(Component.literal("Wounds"));
        this.player = player;
    }

    @Override
    protected void init() {
        super.init();

        int x = (this.width - this.imageWidth) / 2 + 4;
        int y = (this.height - this.imageHeight) / 2 - 19;

        this.addRenderableWidget(Button.builder(
                        Component.literal("<-"),
                        btn -> this.getMinecraft().setScreen(new InventoryScreen(this.getMinecraft().player)))
                .pos(x, y)
                .size(20, 20)
                .build()
        );
    }


    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Render the background
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        gui.blit(WOUNDS_LOCATION, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // Example of rendering wound information
//        String woundText = "Active Wounds:";
//        gui.drawString(this.font, Component.literal(woundText), x + 10, y + 20, 4210752);

        // Demo line for wound display
        player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(painCap -> {
            int screenX = (this.width - this.imageWidth) / 2;
            int screenY = (this.height - this.imageHeight) / 2;

            for(Wound wound : painCap.getWounds()) {
                // Draw a red circle for each wound
                int size = 3;
                
                // Adjust color based on severity
                int color = switch(wound.getSeverity()) {
                    case 1 -> 0xFF00FF00; // Green
                    case 2 -> 0xFFFFFF00; // Yellow
                    case 3 -> 0xFFFF0000; // Red
                    default -> 0xFFFFFFFF; // White
                };

                // Draw filled circle
                gui.fill(
                        screenX + wound.getPosX() - size/2,
                        screenY + wound.getPosY() - size/2,
                        screenX + wound.getPosX() + size/2,
                        screenY + wound.getPosY() + size/2,
                        color
                );

                // Optional: Draw tooltip on hover
                if(mouseX >= screenX + wound.getPosX() - size/2 &&
                        mouseX <= screenX + wound.getPosX() + size/2 &&
                        mouseY >= screenY + wound.getPosY() - size/2 &&
                        mouseY <= screenY + wound.getPosY() + size/2) {

                    String tooltip = String.format("%s - %s (Time Remaining: ~%ds)",
                            wound.getType(),
                            wound.getBodyPart(),
                            (int)(wound.getTicksRemaining() / 20.0f));
                    gui.renderTooltip(this.font, Component.literal(tooltip), mouseX, mouseY);
                }
            }
        });

        super.render(gui, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public int getGuiLeft() {
        return 0;
    }

    public int getGuiTop() {
        return 0;
    }
}
package com.github.jc42.realisticdamage;

import com.github.jc42.realisticdamage.item.Bandage;
import com.github.jc42.realisticdamage.network.ApplyBandagePacket;
import com.github.jc42.realisticdamage.network.PacketHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class WoundsScreen extends Screen {
    private static final Identifier WOUNDS_LOCATION = Identifier.fromNamespaceAndPath(RealisticDamage.MODID, "textures/gui/wound_inventory.png");
    private final int imageWidth = 176;
    private final int imageHeight = 166;
    private Player player;
    private ItemStack bandageStack;

    public WoundsScreen(Player player) {
        this(player, ItemStack.EMPTY);
    }

    public WoundsScreen(Player player, ItemStack bandageStack) {
        super(Component.literal("Wounds"));
        this.player = player;
        this.bandageStack = bandageStack;
    }

    @Override
    protected void init() {
        super.init();

        int x = (this.width - this.imageWidth) / 2 + 4;
        int y = (this.height - this.imageHeight) / 2 - 19;

        this.addRenderableWidget(Button.builder(
                        Component.literal("<-"),
                        btn -> this.getMinecraft().gui.setScreen(new InventoryScreen(this.getMinecraft().player)))
                .pos(x, y)
                .size(20, 20)
                .build()
        );
    }


    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {

        super.extractRenderState(gui, mouseX, mouseY, partialTick);
        // Manual dimmed background (renderBackground is not reliably available anymore)
        gui.fill(0, 0, this.width, this.height, 0xC0101010);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Render the background
        gui.blit(RenderPipelines.GUI_TEXTURED, WOUNDS_LOCATION, x, y, 0, 0, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight, 256, 256);

        // Example of rendering wound information
//        String woundText = "Active Wounds:";
//        gui.drawString(this.font, Component.literal(woundText), x + 10, y + 20, 4210752);

        // Demo line for wound display
        PainCapability painCap = player.getData(RealisticDamage.PAIN);
        int screenX = (this.width - this.imageWidth) / 2;
        int screenY = (this.height - this.imageHeight) / 2;

        for(Wound wound : painCap.getWounds()) {
            // Draw a red circle for each wound
            int size = 3;

            // Adjust color based on severity
            int color = switch(wound.getSeverity()) {
                case 0 -> 0xFFFFFFFF; // White
                case 1 -> 0xFF00FF00; // Green
                case 2 -> 0xFFFFFF00; // Yellow
                case 3 -> 0xFFFF0000; // Red
                default -> 0xFF800080;  // Purple
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

                String tooltip = String.format("%s - %s (Healed: ~%.1f%%)",
                        wound.getType(),
                        wound.getBodyPart(),
                        (1 - (float)wound.getTicksRemaining() / wound.SEVERITY_TICKS[wound.getSeverity()]) * 100.0F);
                gui.setTooltipForNextFrame(Component.literal(tooltip), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!bandageStack.isEmpty() && bandageStack.getItem() instanceof Bandage) {
            PainCapability painCap = player.getData(RealisticDamage.PAIN);
            int screenX = (this.width - this.imageWidth) / 2;
            int screenY = (this.height - this.imageHeight) / 2;
            int size = 3;

            for (int i = 0; i < painCap.getWounds().size(); i++) {
                Wound wound = painCap.getWounds().get(i);
                if (event.x() >= screenX + wound.getPosX() - size / 2 &&
                        event.x() <= screenX + wound.getPosX() + size / 2 &&
                        event.y() >= screenY + wound.getPosY() - size / 2 &&
                        event.y() <= screenY + wound.getPosY() + size / 2) {
                    PacketHandler.Client.sendToServer(new ApplyBandagePacket(i));
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
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
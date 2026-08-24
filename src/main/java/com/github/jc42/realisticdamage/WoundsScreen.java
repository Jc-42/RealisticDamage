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

import java.util.List;

public class WoundsScreen extends Screen {
    private static final Identifier WOUNDS_LOCATION = Identifier.fromNamespaceAndPath(RealisticDamage.MODID, "textures/gui/wound_inventory.png");
    private static final int MARKER_SIZE = 3;
    private static final int MARKER_HIT_RADIUS = 4;
    private final int imageWidth = 176;
    private final int imageHeight = 166;
    private Player player;
    private ItemStack bandageStack;

    //Index of the wound awaiting a replace-bandage decision, or -1 if no prompt is open
    private int confirmWoundIndex = -1;
    private boolean confirmCanReplace = false;
    private Button confirmPrimaryButton;
    private Button confirmSecondaryButton;

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
        gui.fill(0, 0, this.width, this.height, 0xC0101010);

        int screenX = (this.width - this.imageWidth) / 2;
        int screenY = (this.height - this.imageHeight) / 2;

        gui.blit(RenderPipelines.GUI_TEXTURED, WOUNDS_LOCATION, screenX, screenY, 0, 0, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight, 256, 256);

        PainCapability painCap = player.getData(RealisticDamage.PAIN);

        for(Wound wound : painCap.getWounds()) {
            int markerX = screenX + wound.getPosX();
            int markerY = screenY + wound.getPosY();
            boolean hovered = isWoundHovered(wound, mouseX, mouseY, screenX, screenY);

            int color = switch(wound.getSeverity()) {
                case 0 -> 0xFFFFFFFF;
                case 1 -> 0xFF00FF00;
                case 2 -> 0xFFFFFF00;
                case 3 -> 0xFFFF0000;
                default -> 0xFF800080;
            };

            if(hovered) {
                gui.fill(
                        markerX - MARKER_HIT_RADIUS,
                        markerY - MARKER_HIT_RADIUS,
                        markerX + MARKER_HIT_RADIUS,
                        markerY + MARKER_HIT_RADIUS,
                        0x80FFFFFF
                );
            }

            gui.fill(
                    markerX - MARKER_SIZE / 2,
                    markerY - MARKER_SIZE / 2,
                    markerX + MARKER_SIZE / 2,
                    markerY + MARKER_SIZE / 2,
                    color
            );

            if(hovered) {
                Component tooltipHeader = Component.literal(toTitleCase(wound.getBodyPart()));

                Component tooltipWound = Component.literal(String.format("%s (Healed: ~%.1f%%)",
                        wound.getName(),
                        (1 - (float)wound.getTicksRemaining() / wound.SEVERITY_TICKS[wound.getSeverity()]) * 100.0F));

                Component tooltipBandage = wound.getAppliedBandage() == null
                        ? Component.literal("No Bandage")
                        : Component.translatable(wound.getAppliedBandage().getDescriptionId());

                List<Component> tooltipLines = new java.util.ArrayList<>(List.of(tooltipHeader, tooltipWound, tooltipBandage));

                //Preview what clicking will do with the bandage currently held, so upgrades/replacements aren't a surprise
                if (!bandageStack.isEmpty() && bandageStack.getItem() instanceof Bandage heldBandage) {
                    Bandage existingBandage = wound.getAppliedBandage();
                    if (existingBandage == null) {
                        tooltipLines.add(Component.literal("Apply this bandage").withStyle(net.minecraft.ChatFormatting.GREEN));
                    } else if (heldBandage.getTier() > existingBandage.getTier()) {
                        tooltipLines.add(Component.literal("Replace with this bandage (current applied bandage will be lost)").withStyle(net.minecraft.ChatFormatting.AQUA));
                    } else {
                        tooltipLines.add(Component.literal("Must be a higher tier bandage to replace").withStyle(net.minecraft.ChatFormatting.RED));
                    }
                }

                gui.setComponentTooltipForNextFrame(this.font, tooltipLines, mouseX, mouseY);
            }
        }

        if (confirmWoundIndex != -1) {
            int boxWidth = 200;
            int boxHeight = 60;
            int boxX = (this.width - boxWidth) / 2;
            int boxY = this.height / 2 - boxHeight / 2;

            gui.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xF0202020);

            Component message = confirmCanReplace
                    ? Component.literal("Replace the current bandage on this wound?")
                    : Component.literal("Needs a higher tier bandage to replace this one.");

            gui.textWithWordWrap(this.font, message, boxX + 10, boxY + 8, boxWidth - 20, 0xFFFFFFFF);
        }
    }

    private void openBandageConfirm(int woundIndex, boolean canReplace) {
        closeBandageConfirm();
        this.confirmWoundIndex = woundIndex;
        this.confirmCanReplace = canReplace;

        int centerX = this.width / 2;
        int buttonY = this.height / 2 + 16;

        if (canReplace) {
            this.confirmPrimaryButton = this.addRenderableWidget(Button.builder(
                    Component.literal("Yes"),
                    btn -> {
                        PacketHandler.Client.sendToServer(new ApplyBandagePacket(this.confirmWoundIndex));
                        closeBandageConfirm();
                    }).pos(centerX - 84, buttonY).size(80, 20).build());
            this.confirmSecondaryButton = this.addRenderableWidget(Button.builder(
                    Component.literal("No"),
                    btn -> closeBandageConfirm()).pos(centerX + 4, buttonY).size(80, 20).build());
        } else {
            this.confirmPrimaryButton = this.addRenderableWidget(Button.builder(
                    Component.literal("OK"),
                    btn -> closeBandageConfirm()).pos(centerX - 40, buttonY).size(80, 20).build());
        }
    }

    private void closeBandageConfirm() {
        if (this.confirmPrimaryButton != null) {
            this.removeWidget(this.confirmPrimaryButton);
            this.confirmPrimaryButton = null;
        }
        if (this.confirmSecondaryButton != null) {
            this.removeWidget(this.confirmSecondaryButton);
            this.confirmSecondaryButton = null;
        }
        this.confirmWoundIndex = -1;
    }

    private boolean isWoundHovered(Wound wound, int mouseX, int mouseY, int screenX, int screenY) {
        int markerX = screenX + wound.getPosX();
        int markerY = screenY + wound.getPosY();
        return mouseX >= markerX - MARKER_HIT_RADIUS && mouseX <= markerX + MARKER_HIT_RADIUS &&
                mouseY >= markerY - MARKER_HIT_RADIUS && mouseY <= markerY + MARKER_HIT_RADIUS;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        //While a replace prompt is open, only let the Yes/No/OK buttons (handled by super) respond
        if (confirmWoundIndex == -1 && !bandageStack.isEmpty() && bandageStack.getItem() instanceof Bandage heldBandage) {
            PainCapability painCap = player.getData(RealisticDamage.PAIN);
            int screenX = (this.width - this.imageWidth) / 2;
            int screenY = (this.height - this.imageHeight) / 2;

            for (int i = 0; i < painCap.getWounds().size(); i++) {
                Wound wound = painCap.getWounds().get(i);
                if (isWoundHovered(wound, (int) event.x(), (int) event.y(), screenX, screenY)) {
                    Bandage existingBandage = wound.getAppliedBandage();
                    if (existingBandage == null) {
                        PacketHandler.Client.sendToServer(new ApplyBandagePacket(i));
                    } else {
                        openBandageConfirm(i, heldBandage.getTier() > existingBandage.getTier());
                    }
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

    public String toTitleCase(String s){
        String[] words = s.split(" ");
        for(int i = 0; i < words.length; i++){
            words[i] = words[i].substring(0, 1).toUpperCase() + words[i].substring(1);
        }
        return String.join(" ", words);
    }
}
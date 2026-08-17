package com.github.jc42.realisticdamage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.lwjgl.glfw.GLFW;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = RealisticDamage.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = RealisticDamage.MODID, value = Dist.CLIENT)
public class RealisticDamageClient {
    public RealisticDamageClient(IEventBus modEventBus, ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(PainOverlayHandler::registerOverlays);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        RealisticDamage.LOGGER.info("HELLO FROM CLIENT SETUP");
        RealisticDamage.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    //Un-press the sprint key when the player shouldn't sprint.
    //This IS required in addition to the server side code
    //as Minecraft REALLY doesn't want you to stop the sprint event
    //Also un-press the jump key
    @SubscribeEvent
    public static void onClientTickEvent(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player != null) {
            PainCapability pain = mc.player.getData(RealisticDamage.PAIN);

            if (pain.getChronicPainLevel() >= 40 && pain.getAdrenalineLevel() == 0) {
                mc.options.keySprint.setDown(false);
                mc.player.setSprinting(false);
            }
            //Disable jumping if the player has jumped recently, or they are over 90 pain
            if ((System.currentTimeMillis() - RealisticDamage.lastJumpTime < RealisticDamage.jumpCooldown || pain.getChronicPainLevel() > 90) && pain.getAdrenalineLevel() == 0) {
                mc.options.keyJump.setDown(false);
                mc.player.setJumping(false);
            }
        }
    }

    //Reset releasedMovementKey when the player hits the ground and set the key packet to handled
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            if (mc.player.onGround()) {
                RealisticDamage.releasedMovementKeyMidair = false;
                RealisticDamage.keyPacketHandled = true;
            }
        }
    }

    //Determine if the player let go of movement midair
    //Needed to tell if we should resume the movement when the player lands
    //Otherwise the player will keep moving even if they let go
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            if (event.getKey() == minecraft.options.keyUp.getKey().getValue()) {
                if (!minecraft.player.onGround() && event.getAction() == GLFW.GLFW_PRESS) {
                    RealisticDamage.releasedMovementKeyMidair = false;
                }
                if (!minecraft.player.onGround() && event.getAction() == GLFW.GLFW_RELEASE) {
                    RealisticDamage.releasedMovementKeyMidair = true;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();

        if (screen instanceof InventoryScreen inventoryScreen) {
            int x = inventoryScreen.getLeftPos() + 4;
            int y = inventoryScreen.getTopPos() - 19;

            //TODO Make this happen! we need to change the event or something
//                if (inventoryScreen.getRecipeBookComponent().isActive()) {
//                    x += 58;
//                }

            for (var widget : event.getScreen().children()) {
                if (widget instanceof Button button && button.getX() + button.getWidth() > x && button.getX() < x + 20 && button.getY() + button.getHeight() > y && button.getY() < y + 20) {
                    x += 22;
                }
            }

            event.addListener(Button.builder(
                            Component.literal("W"),
                            btn -> Minecraft.getInstance().gui.setScreen(new WoundsScreen(Minecraft.getInstance().player)))
                    .pos(x, y)
                    .size(20, 20)
                    .build()
            );
        }
    }
}
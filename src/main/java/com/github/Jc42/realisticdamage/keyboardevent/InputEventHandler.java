//package com.github.Jc42.realisticdamage.keyboardevent;
//
//import net.minecraft.client.player.Input;
//import net.minecraft.network.chat.Component;
//import net.minecraft.world.entity.player.Player;
//import net.minecraftforge.client.event.MovementInputUpdateEvent;
//import net.minecraftforge.common.MinecraftForge;
//import net.minecraftforge.eventbus.api.SubscribeEvent;
//
//public class InputEventHandler {
//
//    @SubscribeEvent
//    public void onMovementInput(MovementInputUpdateEvent event) {
//        if (event.getEntity() instanceof Player) {
//            Player player = (Player) event.getEntity();
//            Input input = event.getInput();
//
//            PlayerMovementInputEvent customEvent = new PlayerMovementInputEvent(player, input);
//            MinecraftForge.EVENT_BUS.post(customEvent);
//
//            if (customEvent.isCanceled()) {
//                // Cancel movement by setting all input fields to false/zero
//                //player.sendSystemMessage(Component.literal("!!! move vector before " + input.getMoveVector().x + " " + input.getMoveVector().y));
//                input.up = false;
//                input.down = false;
//                input.left = false;
//                input.right = false;
//                input.forwardImpulse = 0;
//                input.leftImpulse = 0;
//                input.jumping = false;
//                player.sendSystemMessage(Component.literal("!!! speed " + player.getDeltaMovement()));
//
////                input.shiftKeyDown = false;
//            }
//        }
//    }
//}

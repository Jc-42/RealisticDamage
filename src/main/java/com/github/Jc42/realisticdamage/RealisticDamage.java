package com.github.Jc42.realisticdamage;

import com.github.Jc42.realisticdamage.network.CPainLevelPacket;
import com.github.Jc42.realisticdamage.network.CStopKeyPacket;
import com.github.Jc42.realisticdamage.network.PacketHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import net.minecraftforge.client.event.InputEvent;


import java.util.*;


// The value here should match an entry in the META-INF/mods.toml file
@Mod(RealisticDamage.MODID)
@Mod.EventBusSubscriber(modid = RealisticDamage.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RealisticDamage
{

    private static final UUID PAIN_MOVEMENT_SPEED_MODIFIER_ID = UUID.fromString("457fdf9f-c0d0-42be-8e8e-6d4bcb488de5");
    private static final UUID PAIN_ATTACK_SPEED_MODIFIER_ID = UUID.fromString("27d34546-5822-4fc2-b8ce-58c248410213");
    public static boolean keyPacketHandled = true;
    public static boolean releasedMovementKeyMidair = false;
    public static final String MODID = "realisticdamage";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static long lastActionTime = -1;
    private static long lastUseTime = -1;
    private static long lastJumpTime = -1;

    private static boolean cancelAttack = false;
    private static boolean cancelUse = false;

    //In milliseconds
    private static final long ACTION_COOLDOWN = 1000;
    private static float jumpCooldown = 0;

    //region Config Variables
        //max = the value at which this effect will be when end pain level is reached
        //min = the value at which this effect will be at start after which it will increase linearly until end is reached
        //start = the pain level that will trigger this effect
        //end = the pain level after which the effect will remain constant OR increase to infinity(in the case of jump/sprint)

        private static float maxJumpCooldown = 5000;
        private static float minJumpCooldown = 500;
        private static float startJumpCooldown = 30;
        private static float endJumpCooldown = 90;
    //endregion

    //Constructor
    public RealisticDamage()
    {

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    //Register packet handler
    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");
        event.enqueueWork(PacketHandler::register);
    }


    //region Make pain persistent and TODO apply respawn pain

    //Copy pain data from old player to cloned player, e.g. respawning, switching dimensions, etc
    @SubscribeEvent
    public static void onPlayerCloned(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            //TODO implement respawn pain to stop players commiting suicide to avoid pain debuffs
            return;
        }

        event.getOriginal().getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(oldStore -> {
            event.getEntity().getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(newStore -> {
                newStore.deserializeNBT(oldStore.serializeNBT());
            });
        });
    }

    //Serializes the player's pain to be saved when the player saves the game
    @SubscribeEvent
    public static void onPlayerSave(PlayerEvent.SaveToFile event) {
        event.getEntity().getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(store -> {
            CompoundTag tag = event.getEntity().getPersistentData();
            tag.put(RealisticDamage.MODID + "_pain", store.serializeNBT());
        });
    }

    //Deserializes the player's pain to be added to the pain capability
    @SubscribeEvent
    public static void onPlayerLoad(PlayerEvent.LoadFromFile event) {
        event.getEntity().getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(store -> {
            CompoundTag tag = event.getEntity().getPersistentData();
            if (tag.contains(RealisticDamage.MODID + "_pain")) {
                store.deserializeNBT(tag.getCompound(RealisticDamage.MODID + "_pain"));
            }
        });
    }
    //endregion

    //Update pain level and send pain packet
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {
                pain.addChronicPain(event.getAmount() * 4);

                if(pain.getChronicPainLevel() < 0) pain.setChronicPainLevel(0);
                if(pain.getChronicPainLevel() > 100) pain.setChronicPainLevel(100);

                //player.sendSystemMessage(Component.literal("You have been hurt!: " + event.getAmount() + ", Chronic Pain: " + pain.getChronicPainLevel() + ", Adrenaline : " + pain.getAdrenalineLevel()));

                if (player instanceof ServerPlayer) {
                    PacketHandler.sendToPlayer(new CPainLevelPacket(pain.getChronicPainLevel(), pain.getAdrenalineLevel()), () -> (ServerPlayer) player);
                }
            });
        }
    }



    //Lower pain level with packets and lower player speed. Also stop player from sprinting and moving midair
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = event.getServer();
        if (event.phase == TickEvent.Phase.START) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {
                    if(pain.getChronicPainLevel() > 0) {
                        pain.addChronicPain(-.05f); //Chronic pain lowers by 1 per second
                        if(pain.getChronicPainLevel() < 0) pain.setChronicPainLevel(0);
                        if(pain.getChronicPainLevel() > 100) pain.setChronicPainLevel(100);
                    }

                    if(player.isCreative()){
                        pain.setChronicPainLevel(0);
                    }

                    updateModifiers(player, pain);

                    //Objects.requireNonNull(player.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(Math.max(VANILLA_BASE_SPEED - (pain.getChronicPainLevel() * .00111111), 0));

                    //Lower attach speed such that at 90 pain attack speed = 0
                    //Objects.requireNonNull(player.getAttribute(Attributes.ATTACK_SPEED)).setBaseValue(Math.max(VANILLA_BASE_SPEED - (pain.getChronicPainLevel() * .00111111), 0));
                    //player.getAttribute()
                    PacketHandler.sendToPlayer(new CPainLevelPacket(pain.getChronicPainLevel(), pain.getAdrenalineLevel()), () -> (ServerPlayer) player);
                });
            }

        }

        // Stop player from sprinting after 40 pain
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {
                if(pain.getChronicPainLevel() >= 40) {
                   if(!player.onGround() && keyPacketHandled){
                       PacketHandler.sendToPlayer(new CStopKeyPacket("sprint movement"), () -> (ServerPlayer) player);
                   }
                   else {
                       PacketHandler.sendToPlayer(new CStopKeyPacket("sprint"), () -> (ServerPlayer) player);
                   }
                   player.setSprinting(false);
               }
                long currentTime = System.currentTimeMillis();
                if(currentTime - lastJumpTime < jumpCooldown){
                    PacketHandler.sendToPlayer(new CStopKeyPacket("jump"), () -> (ServerPlayer) player);
                    player.setJumping(false);
                }
            });

        }
    }

//    @SubscribeEvent
//    public void onPlayerAttack(LivingAttackEvent event) {
//        long currentTime = System.currentTimeMillis();
//        Objects.requireNonNull(event.getSource().getEntity()).sendSystemMessage(Component.literal("Attack " + (currentTime - lastAttackTime)));
//        if(currentTime - lastAttackTime < ATTACK_COOLDOWN){
//            event.setCanceled(true);
//            Objects.requireNonNull(event.getEntity()).sendSystemMessage(Component.literal("Cancel Attack"));
//        }
//        else{
//            lastAttackTime = currentTime;
//            Objects.requireNonNull(event.getEntity()).sendSystemMessage(Component.literal("Allow Attack"));
//        }
//    }

    //Used to stop the event from being allowed and then canceled since this event fires multiple time per click
    private static boolean allowedLastAction = false;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // Check if the player is holding a block
        if (!(event.getItemStack().getItem() instanceof BlockItem)) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        if (currentTime - lastActionTime < ACTION_COOLDOWN && !allowedLastAction) {
            event.setCanceled(true);
            event.setUseItem(Event.Result.DENY);

        } else {
            event.setCanceled(false);
            event.setUseItem(Event.Result.ALLOW);

            if(allowedLastAction) {
                allowedLastAction = false;
            }
            else{
                allowedLastAction = true;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        //Reset the action time here because sometimes the RClickBlock code says allowed but then minecraft denys it
        lastActionTime = System.currentTimeMillis();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) //Handle this last
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            lastJumpTime = System.currentTimeMillis();
        }
    }


//    @SubscribeEvent
//    public void onUseItem(PlayerInteractEvent.RightClickItem event) {
//        long currentTime = System.currentTimeMillis();
//        if(currentTime - lastAttackTime < ATTACK_COOLDOWN){
//            event.setCanceled(true);
//        }
//        else{
//            lastAttackTime = currentTime;
//        }
//    }

    //Remove the sprint attribute, may be redundant
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {

            AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            UUID SPRINT_SPEED_BOOST_ID = UUID.fromString("662a6b8d-da3e-4c1c-8813-96ea6097278d");

            if (movementSpeed != null) {
                // Check if the player has high chronic pain
                player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {
                    if (pain.getChronicPainLevel() >= 40) {
                        // Remove the sprinting speed modifier
                        movementSpeed.removeModifier(SPRINT_SPEED_BOOST_ID);
                    }
                });
            }
        }
    }

    //Client code
    @Mod.EventBusSubscriber(modid = RealisticDamage.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientModEventBusEvents {

        //Un-press the sprint key when the player shouldn't sprint.
        //This IS required in addition to the server side code
        //as Minecraft REALLY doesn't want you to stop the sprint event
        @SubscribeEvent
        @OnlyIn(Dist.CLIENT)
        public static void onClientTickEvent(TickEvent.ClientTickEvent event) {
            Minecraft mc = Minecraft.getInstance();

            if (mc.player != null) {
                mc.player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {
                    if (pain.getChronicPainLevel() >= 40) {
                        mc.options.keySprint.setDown(false);
                        mc.player.setSprinting(false);
                    }
                    if(System.currentTimeMillis() - lastJumpTime < jumpCooldown) {
                        mc.options.keyJump.setDown(false);
                        mc.player.setJumping(false);
                    }
                });
            }
        }


        @SubscribeEvent
        @OnlyIn(Dist.CLIENT)
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.START) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    if (mc.player.onGround()) {
                        releasedMovementKeyMidair = false;
                        keyPacketHandled = true;
                    }
                }
            }
        }

        //Determine if the player let go of movement midair
        //Needed to tell if we should resume the movement when the player lands
        //Otherwise the player will keep moving even if they let go
        @SubscribeEvent (priority = EventPriority.HIGHEST)
        @OnlyIn(Dist.CLIENT)
        public static void onKeyInput(InputEvent.Key event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                if (event.getKey() == minecraft.options.keyUp.getKey().getValue()) {
                    if (!minecraft.player.onGround() && event.getAction() == GLFW.GLFW_PRESS) {
                        releasedMovementKeyMidair = false;
                    }
                    if (!minecraft.player.onGround() && event.getAction() == GLFW.GLFW_RELEASE) {
                        releasedMovementKeyMidair = true;
                    }
                }
            }
        }

    }

    //Attach the pain capability and add the movement modifier
    @SubscribeEvent
    public static void onAttachCapabilitiesPlayer(AttachCapabilitiesEvent<Entity> attachEvent) {
        if (attachEvent.getObject() instanceof Player) {
            if (!attachEvent.getObject().getCapability(PainCapabilityProvider.PAIN_CAPABILITY).isPresent()) {
                attachEvent.addCapability(new ResourceLocation(MODID, "pain"), new PainCapabilityProvider());
            }
        }
    }

    //Register the pain capability
    @Mod.EventBusSubscriber(modid = RealisticDamage.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEventBusEvents {
        @SubscribeEvent
        public static void registerCapabilities(RegisterCapabilitiesEvent event) {
            event.register(IPainCapability.class);
            LOGGER.debug("Registering pain capabilities");
        }
    }

    //Add the player attributes when the player joins the world
    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Player player) {
            player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {
                AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
                AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);

                if (movementSpeed != null) {

                    if (movementSpeed.getModifier(PAIN_MOVEMENT_SPEED_MODIFIER_ID) == null) {
                        AttributeModifier speedModifier = new AttributeModifier(
                                PAIN_MOVEMENT_SPEED_MODIFIER_ID,
                                "Chronic Pain Speed Reduction",
                                0, // Start with no effect
                                AttributeModifier.Operation.MULTIPLY_TOTAL
                        );
                        movementSpeed.addPermanentModifier(speedModifier);
                    }
                }

                if(attackSpeed != null){
                    if (attackSpeed.getModifier(PAIN_ATTACK_SPEED_MODIFIER_ID) == null) {
                        AttributeModifier speedModifier = new AttributeModifier(
                                PAIN_ATTACK_SPEED_MODIFIER_ID,
                                "Chronic Pain Attack Speed Reduction",
                                0, // Start with no effect
                                AttributeModifier.Operation.MULTIPLY_TOTAL
                        );
                        attackSpeed.addPermanentModifier(speedModifier);
                    }
                }

                updateModifiers(player, pain);
            });
        }
    }

    //Update the players modifiers such as speed, attack speed, jump cooldown, action cooldown, etc.
    private static void updateModifiers(Player player, IPainCapability pain) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);

        if (movementSpeed != null) {
            AttributeModifier existingModifier = movementSpeed.getModifier(PAIN_MOVEMENT_SPEED_MODIFIER_ID);
            if (existingModifier != null) {

                //Lower speed such that 90 pain = 0 speed
                double modifierValue = -Math.min(.0111111 * pain.getChronicPainLevel(), 1);

                // If the value has changed, remove the old modifier and add a new one with the updated value
                if (existingModifier.getAmount() != modifierValue) {
                    movementSpeed.removeModifier(PAIN_MOVEMENT_SPEED_MODIFIER_ID);
                    AttributeModifier updatedModifier = new AttributeModifier(
                            PAIN_MOVEMENT_SPEED_MODIFIER_ID,
                            "Chronic Pain Speed Reduction",
                            modifierValue,
                            AttributeModifier.Operation.MULTIPLY_TOTAL
                    );
                    movementSpeed.addPermanentModifier(updatedModifier);
                }
            }
        }

        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);

        if (attackSpeed != null) {

            AttributeModifier existingModifier = attackSpeed.getModifier(PAIN_ATTACK_SPEED_MODIFIER_ID);
            if (existingModifier != null) {

                //Lower speed such that 90 pain = 0 speed
                double modifierValue = -Math.min(.0111111 * pain.getChronicPainLevel(), 1);

                // If the value has changed, remove the old modifier and add a new one with the updated value
                if (existingModifier.getAmount() != modifierValue) {
                    attackSpeed.removeModifier(PAIN_ATTACK_SPEED_MODIFIER_ID);
                    AttributeModifier updatedModifier = new AttributeModifier(
                            PAIN_ATTACK_SPEED_MODIFIER_ID,
                            "Chronic Pain Attack Speed Reduction",
                            modifierValue,
                            AttributeModifier.Operation.MULTIPLY_TOTAL
                    );
                    attackSpeed.addPermanentModifier(updatedModifier);
                }
            }
        }

        //Set the jump Cooldown
        jumpCooldown = pain.getChronicPainLevel() < startJumpCooldown ? 0 : Math.max(Math.min((((minJumpCooldown - maxJumpCooldown) / (startJumpCooldown - endJumpCooldown)) * (pain.getChronicPainLevel() - endJumpCooldown)) + maxJumpCooldown, maxJumpCooldown), minJumpCooldown);
        player.sendSystemMessage(Component.literal("jumpCooldown: " + (float)Math.round(jumpCooldown * 100) / 100));
    }


}

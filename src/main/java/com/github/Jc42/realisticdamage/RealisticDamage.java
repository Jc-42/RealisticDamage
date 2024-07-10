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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import net.minecraftforge.client.event.InputEvent;


import javax.swing.text.JTextComponent;
import java.time.chrono.MinguoEra;
import java.util.*;


// The value here should match an entry in the META-INF/mods.toml file
@Mod(RealisticDamage.MODID)
@Mod.EventBusSubscriber(modid = RealisticDamage.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RealisticDamage
{

    public static final double VANILLA_BASE_SPEED = 0.10000000149011612;
    public static boolean keyPacketHandled = true;
    public static boolean releasedMovementKeyMidair = false;
    public static final String MODID = "realisticdamage";
    private static final Logger LOGGER = LogUtils.getLogger();

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
                pain.addChronicPain(event.getAmount() * 2);

                if(pain.getChronicPainLevel() < 0) pain.setChronicPainLevel(0);
                if(pain.getChronicPainLevel() > 100) pain.setChronicPainLevel(100);

                player.sendSystemMessage(Component.literal("You have been hurt!: " + event.getAmount() + ", Chronic Pain: " + pain.getChronicPainLevel() + ", Adrenaline : " + pain.getAdrenalineLevel()));

                if (player instanceof ServerPlayer) {
                    PacketHandler.sendToPlayer(new CPainLevelPacket(pain.getChronicPainLevel(), pain.getAdrenalineLevel()), () -> (ServerPlayer) player);
                }
            });
        }
    }


    //Lower pain level with packets and player speed. Also stop player from sprinting and moving midair
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = event.getServer();
        if (event.phase == TickEvent.Phase.START) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {
                    if(pain.getChronicPainLevel() > 0) {
//                        pain.addChronicPain(-.05f); //Chronic pain lowers by 1 per second
                        if(pain.getChronicPainLevel() < 0) pain.setChronicPainLevel(0);
                        if(pain.getChronicPainLevel() > 100) pain.setChronicPainLevel(100);
                    }

                    if(player.isCreative()){
                        pain.setChronicPainLevel(0);
                    }

                    //TODO add an attribute modifier instead of setting directly
                    //Lower player speed such that at 90 pain speed = 0
                    Objects.requireNonNull(player.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(Math.max(VANILLA_BASE_SPEED - (pain.getChronicPainLevel() * .00111111), 0));

                    PacketHandler.sendToPlayer(new CPainLevelPacket(pain.getChronicPainLevel(), pain.getAdrenalineLevel()), () -> (ServerPlayer) player);
                });
            }

        }

        // Stop player from sprinting after 20 pain
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {
                if(pain.getChronicPainLevel() >= 20) {
                   if(!player.onGround() && keyPacketHandled){
                       PacketHandler.sendToPlayer(new CStopKeyPacket("sprint movement"), () -> (ServerPlayer) player);
                   }
                   else {
                       PacketHandler.sendToPlayer(new CStopKeyPacket("sprint"), () -> (ServerPlayer) player);
                   }
                   player.setSprinting(false);
               }
            });

        }
    }


    //Remove the sprint attribute, may be redundant
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {

            AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            UUID SPRINT_SPEED_BOOST_ID = UUID.fromString("662a6b8d-da3e-4c1c-8813-96ea6097278d");

            if (movementSpeed != null) {
                // Check if the player has high chronic pain
                player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {
                    if (pain.getChronicPainLevel() >= 20) {
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

        //Stop player from sprinting after 20 pain
        @SubscribeEvent
        @OnlyIn(Dist.CLIENT)
        public static void onClientTickEvent(TickEvent.ClientTickEvent event) {
            Minecraft mc = Minecraft.getInstance();

            if (mc.player != null) {
                mc.player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {
                    if(pain.getChronicPainLevel() >= 20) {
                        mc.options.keySprint.setDown(false);
                    }
                });
            }
        }

        //Un-press the sprint key when the player shouldn't sprint.
        //This IS required in addition to the server side code
        //as Minecraft REALLY doesn't want you to stop the sprint event
        @SubscribeEvent
        @OnlyIn(Dist.CLIENT)
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.START) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    if(mc.player.onGround()){
                        releasedMovementKeyMidair = false;
                        keyPacketHandled = true;
                    }
                    mc.player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {

                        if (pain.getChronicPainLevel() >= 20) {
                            mc.options.keySprint.setDown(false);
                            mc.player.setSprinting(false);
                        }
                    });
                }
            }
        }

        //Determine if the player let go of movement midair
        //Needed to tell if we should resume the movement when the player lands
        //Otherwise the player will keep moving even if they let go
        @SubscribeEvent
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

    //Attach the pain capability
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
}

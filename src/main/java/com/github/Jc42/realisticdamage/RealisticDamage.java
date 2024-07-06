package com.github.Jc42.realisticdamage;

import com.github.Jc42.realisticdamage.keyboardevent.InputEventHandler;
import com.github.Jc42.realisticdamage.network.CPainLevelPacket;
import com.github.Jc42.realisticdamage.network.PacketHandler;
import com.github.Jc42.realisticdamage.keyboardevent.PlayerMovementInputEvent;
import com.mojang.logging.LogUtils;
import net.minecraft.client.player.Input;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;


// The value here should match an entry in the META-INF/mods.toml file
@Mod(RealisticDamage.MODID)
@Mod.EventBusSubscriber(modid = RealisticDamage.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RealisticDamage
{

    public static final double VANILLA_BASE_SPEED = 0.10000000149011612;

    // Define mod id in a common place for everything to reference
    public static final String MODID = "realisticdamage";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public RealisticDamage()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

//        // Register the Deferred Register to the mod event bus so blocks get registered
//        BLOCKS.register(modEventBus);
//        // Register the Deferred Register to the mod event bus so items get registered
//        ITEMS.register(modEventBus);
//        // Register the Deferred Register to the mod event bus so tabs get registered
//        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register the event handler
        MinecraftForge.EVENT_BUS.register(new InputEventHandler());

//        // Register the item to a creative tab
//        modEventBus.addListener(this::addCreative);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");
        event.enqueueWork(PacketHandler::register);


//        if (Config.logDirtBlock)
//            LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));
//
//        LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);
//
//        Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
    }

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



    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            MinecraftServer server = event.getServer();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {
//                    if(pain.getAdrenalineLevel() > 0) {
//                        pain.addAdrenaline(-.15f); //Adrenaline lowers by 3 per second
//                        if(pain.getAdrenalineLevel() < 0) pain.setAdrenalineLevel(0);
//                        if(pain.getAdrenalineLevel() > 100) pain.setAdrenalineLevel(100);
//                    }
                    if(pain.getChronicPainLevel() > 0) {
//                        pain.addChronicPain(-.05f); //Chronic pain lowers by 1 per second
                        if(pain.getChronicPainLevel() < 0) pain.setChronicPainLevel(0);
                        if(pain.getChronicPainLevel() > 100) pain.setChronicPainLevel(100);
                    }

                    if(player.isCreative()){
                        pain.setChronicPainLevel(0);
                    }

                    Objects.requireNonNull(player.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(Math.max(VANILLA_BASE_SPEED - (pain.getChronicPainLevel() * .00111111), 0)); //Lower player speed such that at 90 pain speed = 0

                    PacketHandler.sendToPlayer(new CPainLevelPacket(pain.getChronicPainLevel(), pain.getAdrenalineLevel()), () -> (ServerPlayer) player);
                });
            }

        }
    }




    @SubscribeEvent
    public void onPlayerMovementInput(PlayerMovementInputEvent event) {
        Player player = event.getPlayer();
        Input input = event.getInput();

        // Prevent movement input if the player is in midair and is going faster than base speed
        // Used to stop the player from jumping to move faster when in pain
        player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {
            if(!player.onGround()) {
                double horizontalSpeed = Math.sqrt(Math.pow(player.getDeltaMovement().x, 2) + Math.pow(player.getDeltaMovement().z, 2));
                double speedLossScale = Math.min((Math.max(pain.getChronicPainLevel() - 20, 0)) / 30, 1); // Used to scale the speed loss from midair such that at <=20 pain there is no effect and >=50 there is no speed bonus
                LOGGER.debug("!!! speed scale : " + speedLossScale);
                if (horizontalSpeed * speedLossScale > Objects.requireNonNull(player.getAttribute(Attributes.MOVEMENT_SPEED)).getBaseValue()) {
                    event.setCanceled(true);
                    LOGGER.debug("!!! canceled");

                    // Stop movement under a certain value
                    // This is a hacky way to prevent the little amount of movement that still occurs
                    // even after canceling which I cant figure out the source of

                    if (Math.abs(player.getDeltaMovement().x) < .03) {
                        Vec3 velocity = player.getDeltaMovement();
                        velocity = new Vec3(-player.getDeltaMovement().x * .8, velocity.y, velocity.z);
                        player.setDeltaMovement(velocity);
                    }
                    if (Math.abs(player.getDeltaMovement().z) < .03) {
                        Vec3 velocity = player.getDeltaMovement();
                        velocity = new Vec3(velocity.x, velocity.y, -player.getDeltaMovement().z * .8);
                        player.setDeltaMovement(velocity);
                    }
                } else {
                    event.setCanceled(false);
                }
            }

        });
    }

    //Disable jump boosting
    @SubscribeEvent
    public static void onPlayerJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {
                if (pain.getChronicPainLevel() >= 20) {
                    // Disable sprinting
                    player.setSprinting(false);

                    // If on server side, sync to client
                    if (player instanceof ServerPlayer) {
                        ServerPlayer serverPlayer = (ServerPlayer) player;
                        serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(serverPlayer));
                    }
                }
            });
        }
    }

    @SubscribeEvent
    public static void onAttachCapabilitiesPlayer(AttachCapabilitiesEvent<Entity> attachEvent) {
        if (attachEvent.getObject() instanceof Player) {
            if (!attachEvent.getObject().getCapability(PainCapabilityProvider.PAIN_CAPABILITY).isPresent()) {
                LOGGER.debug("Pain Attached");
                attachEvent.addCapability(new ResourceLocation(MODID, "pain"), new PainCapabilityProvider());
            }
        }
    }


    @Mod.EventBusSubscriber(modid = RealisticDamage.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEventBusEvents {
        @SubscribeEvent
        public static void registerCapabilities(RegisterCapabilitiesEvent event) {
            event.register(IPainCapability.class);
            LOGGER.debug("Registering pain capabilities");
        }
    }

    @Mod.EventBusSubscriber(modid = RealisticDamage.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEventBusEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Client-side initialization
        }
    }




//    // Create a Deferred Register to hold Blocks which will all be registered under the "examplemod" namespace
//    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
//    // Create a Deferred Register to hold Items which will all be registered under the "examplemod" namespace
//    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
//    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "examplemod" namespace
//    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
//
//    // Creates a new Block with the id "examplemod:example_block", combining the namespace and path
//    public static final RegistryObject<Block> EXAMPLE_BLOCK = BLOCKS.register("example_block", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)));
//    // Creates a new BlockItem with the id "examplemod:example_block", combining the namespace and path
//    public static final RegistryObject<Item> EXAMPLE_BLOCK_ITEM = ITEMS.register("example_block", () -> new BlockItem(EXAMPLE_BLOCK.get(), new Item.Properties()));
//
//    // Creates a new food item with the id "examplemod:example_id", nutrition 1 and saturation 2
//    public static final RegistryObject<Item> EXAMPLE_ITEM = ITEMS.register("example_item", () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
//            .alwaysEat().nutrition(1).saturationMod(2f).build())));
//
//    // Creates a creative tab with the id "examplemod:example_tab" for the example item, that is placed after the combat tab
//    public static final RegistryObject<CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
//            .withTabsBefore(CreativeModeTabs.COMBAT)
//            .icon(() -> EXAMPLE_ITEM.get().getDefaultInstance())
//            .displayItems((parameters, output) -> {
//                output.accept(EXAMPLE_ITEM.get()); // Add the example item to the tab. For your own tabs, this method is preferred over the event
//            }).build());

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
//        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS)
//            event.accept(EXAMPLE_BLOCK_ITEM);
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
        }
    }
}

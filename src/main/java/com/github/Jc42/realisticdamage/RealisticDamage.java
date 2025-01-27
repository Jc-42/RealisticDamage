package com.github.Jc42.realisticdamage;

import com.github.Jc42.realisticdamage.network.CPainLevelPacket;
import com.github.Jc42.realisticdamage.network.CStopKeyPacket;
import com.github.Jc42.realisticdamage.network.PacketHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import net.minecraftforge.client.event.InputEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.util.*;


//The value here should match an entry in the META-INF/mods.toml file
@Mod(RealisticDamage.MODID)
@Mod.EventBusSubscriber(modid = RealisticDamage.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RealisticDamage {

    private static final UUID PAIN_MOVEMENT_SPEED_MODIFIER_ID = UUID.fromString("457fdf9f-c0d0-42be-8e8e-6d4bcb488de5");
    private static final UUID PAIN_ATTACK_SPEED_MODIFIER_ID = UUID.fromString("27d34546-5822-4fc2-b8ce-58c248410213");
    private static final UUID PAIN_MINING_SPEED_MODIFIER_ID = UUID.fromString("96b1deb1-fcce-423f-9b30-82752bca7736");
    public static boolean keyPacketHandled = true;
    public static boolean releasedMovementKeyMidair = false;
    public static final String MODID = "realisticdamage";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static long lastJumpTime = -1;
    private static long lastAdrenalineRushTime = -1;

    private static boolean lastAdrenalineRushReset = false;
    //In milliseconds
    private static final long ACTION_COOLDOWN = 1000;
    private static float jumpCooldown = 0;

    private static final Map<String, String> mobWoundTypes = new HashMap<>();

    //region Config Variables
    private static long adrenalineRushCooldown = 60000;

    //max = the value at which this effect will be when end pain level is reached
    //min = the value at which this effect will be at start after which it will increase linearly until end is reached
    //start = the pain level that will trigger this effect
    //end = the pain level after which the effect will remain constant OR increase to infinity(in the case of jump/sprint)


    private static float maxJumpCooldown = 5000;
    private static float minJumpCooldown = 700;
    private static float startJumpCooldown = 30; //Before which it is 0
    private static float endJumpCooldown = 90; //After which you cannot jump

    private static float maxMovementSpeedScale = 1;
    private static float minMovementSpeedScale = 0;
    private static float startMovementSpeedScale = 0;
    private static float endMovementSpeedScale = 90;

    private static float maxAttackSpeedScale = 1;
    private static float minAttackSpeedScale = 0;
    private static float startAttackSpeedScale = 0;
    private static float endAttackSpeedScale = 90;

    private static float maxMiningSpeedScale = 1;
    private static float minMiningSpeedScale = 0;
    private static float startMiningSpeedScale = 0;
    private static float endMiningSpeedScale = 80; //After which you cannot mine

    private static float startNauseaEffect = 60; //Above which nausea is applied
    //endregion

    //Constructor
    public RealisticDamage() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    //Register packet handler
    private void commonSetup(final FMLCommonSetupEvent event) {
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
            event.register(PainCapability.class);
            LOGGER.debug("Registering pain capabilities");
        }
    }

    //Add the player attributes when the player joins the world
    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Player player) {
            player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {
                AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);

                if (movementSpeed != null) {

                    if (movementSpeed.getModifier(PAIN_MOVEMENT_SPEED_MODIFIER_ID) == null) {
                        AttributeModifier speedModifier = new AttributeModifier(
                                PAIN_MOVEMENT_SPEED_MODIFIER_ID,
                                "Chronic Pain Speed Reduction",
                                0, //Start with no effect
                                AttributeModifier.Operation.MULTIPLY_TOTAL
                        );
                        movementSpeed.addPermanentModifier(speedModifier);
                    }
                }

                updateModifiers(player, pain);
            });
        }
    }

    //endregion

    //Update pain level and send pain packet
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {
                DamageSource damageSource = event.getSource();
                Entity directEntity = damageSource.getDirectEntity();
                String[] damageType = classifyDamage(damageSource, directEntity, player);
                //TODO figure out dammage types
                //player.sendSystemMessage(Component.literal("SOURCE: " + damageSource + " " + damageSource.));

                if (directEntity instanceof Arrow) {
                    Arrow arrow = (Arrow) directEntity;

                    //region Test arrow lodging


                    //endregion
                    String hitBodyPart = detectHitBodyPart(player, arrow);

                    double[] position = {arrow.position().x, arrow.position().y, arrow.position().z, arrow.getXRot(), arrow.getYRot()};

                    pain.getLodgedArrowPositions().add(position);

                    //TODO head code sets it to right arm?
                    //TODO it never triggers the left arm or the left leg

                    //TODO remove this later and specify which leg / arm
                    if(hitBodyPart.equals("arm") || hitBodyPart.equals("leg")) hitBodyPart = "left " + hitBodyPart;

                    //TODO make it so that the severity is based on the amount of damage
                    //TODO make it so the player doesnt receive any damage for the hit, besides the amount of bleed of the wound
                    pain.addWound(new Wound("Puncture", 2, hitBodyPart));

                    player.sendSystemMessage(Component.literal("Player hit in the " + hitBodyPart + " " + event.getAmount()));
                }
                else{
                    if(!damageType[0].equals("vanilla")) {
                        float fractionLost = event.getAmount() / player.getMaxHealth();
                        int severity = fractionLost >= .6 ? 3 : (fractionLost >= .4 ? 2 : (fractionLost >= .25 ? 1 : 0));
                        if(damageType[0].equals("blunt")){
                            if(severity == 3) damageType[0] = "laceration";
                            else damageType[0] = "hematoma";
                        }
                        String bodyPart = damageType.length > 1 ? getWoundLocation(new ArrayList<>(Arrays.asList(Arrays.copyOfRange(damageType, 1, damageType.length)))) : getWoundLocation(null);
                        pain.addWound(new Wound(damageType[0], severity, bodyPart));

                        event.setCanceled(true);
                    }
                }

                player.sendSystemMessage(Component.literal("" + pain.getChronicPainLevel()));
                if (System.currentTimeMillis() - lastAdrenalineRushTime > adrenalineRushCooldown && pain.getAdrenalineLevel() == 0) {
                    if (pain.getChronicPainLevel() >= 30) {
                        pain.setAdrenalineLevel(50 + ((pain.getChronicPainLevel() - 30) / 70) * 50);
                        lastAdrenalineRushReset = false;
                    }
                }

                if (pain.getAdrenalineLevel() < 0) pain.setAdrenalineLevel(0);

                if (pain.getAdrenalineLevel() > 100) pain.setAdrenalineLevel(100);
                if (player instanceof ServerPlayer) {
                    PacketHandler.sendToPlayer(new CPainLevelPacket(pain.getAdrenalineLevel(), pain.getWounds()), () -> (ServerPlayer) player);
                }
            });
        }
    }

    private static String[] classifyDamage(DamageSource source, Entity directEntity, Player player) {
        //TODO add fracture and hematoma
        //TODO magic damage, wither damage
        //TODO make lava more than just a burn
        if (source.is(DamageTypes.IN_FIRE) ||
                source.is(DamageTypes.ON_FIRE) ||
                source.is(DamageTypes.LAVA) ||
                source.is(DamageTypes.HOT_FLOOR) ||
                source.is(DamageTypes.LIGHTNING_BOLT) ||
                source.is(DamageTypes.DRAGON_BREATH) ||
                source.is(DamageTypes.FREEZE) ||
                source.is(DamageTypes.FIREWORKS) ||
                source.is(DamageTypes.FIREBALL) ||
                source.is(DamageTypes.UNATTRIBUTED_FIREBALL) ||
                source.is(DamageTypes.WITHER_SKULL) ||
                source.is(DamageTypes.EXPLOSION) ||
                source.is(DamageTypes.PLAYER_EXPLOSION)) {
            if(source.is(DamageTypes.IN_FIRE)){
                return new String[]{"burn", "left foot" , "right foot", "left leg", "right leg"};
            }
            if(source.is(DamageTypes.HOT_FLOOR)){
                return new String[]{"burn", "left foot", "right foot"};
            }

            if (source.is(DamageTypes.LAVA)) {
                List<String> affectedParts = new ArrayList<>();
                affectedParts.add("burn");  //Always add burn as first element

                //Get the lava height - start at player's feet and look up for lava blocks
                BlockPos pos = player.blockPosition();
                Level world = player.level();
                double lavaHeight = 0;

                //Search up to player height for highest lava block
                for (int y = 0; y <= player.getBbHeight(); y++) {
                    BlockPos checkPos = pos.above(y);
                    if (world.getBlockState(checkPos).getBlock() instanceof LiquidBlock) {
                        lavaHeight = checkPos.getY() + 1; //Add 1 because lava fills the block
                    }
                }

                double entityY = player.getY();
                double entityHeight = player.getBbHeight();

                //Feet level (0-10% of height)
                if (entityY <= lavaHeight) {
                    affectedParts.add("left foot");
                    affectedParts.add("right foot");
                }

                //Legs level (10-45% of height)
                if (entityY + (entityHeight * 0.45) <= lavaHeight) {
                    affectedParts.add("left leg");
                    affectedParts.add("right leg");
                }

                //Torso level (45-75% of height)
                if (entityY + (entityHeight * 0.75) <= lavaHeight) {
                    affectedParts.add("torso");
                    affectedParts.add("left arm");
                    affectedParts.add("right arm");
                }

                //Head level (75-100% of height)
                if (player.getEyeY() <= lavaHeight) {
                    affectedParts.add("head");
                    affectedParts.add("face");
                }

                return affectedParts.toArray(new String[0]);
            }
            return new String[]{"burn"};
        }

        if (source.is(DamageTypes.PLAYER_ATTACK)) {
            Player attacker = (Player) directEntity;
            ItemStack weapon = attacker.getMainHandItem();
            if (weapon.getItem() instanceof SwordItem ||
                    weapon.getItem() instanceof AxeItem ||
                    weapon.getItem() instanceof HoeItem) {
                return new String[]{"laceration"};
            }
            else if (weapon.getItem() instanceof PickaxeItem){
                return new String[]{"puncture"};
            }
            else{
                return new String[]{"blunt"}; //TODO If this is tier 3+ make it a laceration, otherwise make it a hematoma
            }
        }

        if(source.is(DamageTypes.MOB_ATTACK) ||
                source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)){
            for (ItemStack itemStack : directEntity.getHandSlots()){;
                if (!itemStack.isEmpty()){
                    if(itemStack.getItem() instanceof SwordItem ||
                    itemStack.getItem() instanceof AxeItem ||
                    itemStack.getItem() instanceof HoeItem){
                        return new String[]{"laceration"};
                    }
                    else if (itemStack.getItem() instanceof PickaxeItem){
                        return new String[]{"puncture"};
                    }
                    else{
                        return new String[]{"blunt"};
                    }
                }
                else{
                    //In the form namespace:entity_name
                    String mobName = ForgeRegistries.ENTITY_TYPES.getKey(directEntity.getType()).toString();
                    if(mobWoundTypes.containsKey(mobName)){
                        String type = mobWoundTypes.get(mobName);
                        if(Wound.validWoundType(type)){
                            return new String[]{mobWoundTypes.get(mobName)};
                        }
                        else{
                            return new String[]{"laceration"};
                        }
                    }
                    return new String[]{"blunt"}; //TODO If this is tier 3+ make it a laceration, otherwise make it a hematoma
                }
            }
        }

        //TODO probably doesn't work and doesn't really need to but it'd be kinda cool
        if (source.is(DamageTypes.THORNS)) {
            if (directEntity != null) {
                //Get the last damage source we used against them
                DamageSource lastDamageSource = ((LivingEntity)directEntity).getLastDamageSource();
                if (lastDamageSource != null) {
                    return classifyDamage(lastDamageSource, lastDamageSource.getDirectEntity(), player);
                }
            }
            return new String[]{"blunt"};
        }

        if (source.is(DamageTypes.MOB_PROJECTILE)) {
            if (directEntity instanceof LlamaSpit) {
                return new String[]{"hematoma"};
            }

            if (directEntity instanceof ShulkerBullet) {
                return new String[]{"blunt"};
            }

            if (directEntity instanceof DragonFireball) {
                return new String[]{"burn"};
            }

            if (directEntity instanceof WitherSkull) {
                return new String[]{"burn"};
            }

            if (directEntity instanceof SmallFireball) {
                return new String[]{"burn"};
            }

            if (directEntity instanceof LargeFireball) {
                return new String[]{"burn"};
            }

            return new String[]{"vanilla"};

        }

        if (directEntity instanceof Arrow ||
                directEntity instanceof ThrownTrident ||
                source.is(DamageTypes.ARROW) ||
                source.is(DamageTypes.TRIDENT) ||
                source.is(DamageTypes.FALLING_STALACTITE) ||
                source.is(DamageTypes.STALAGMITE) ||
                source.is(DamageTypes.STING)) {
            return new String[]{"puncture"};
        }

        if (source.is(DamageTypes.FALL) ||
                source.is(DamageTypes.FLY_INTO_WALL) ||
                source.is(DamageTypes.FALLING_ANVIL) ||
                source.is(DamageTypes.FALLING_BLOCK)) {
            if(source.is(DamageTypes.FALL)){
                Random r = new Random();
                return new String[]{"fracture", "left foot", "right foot", "left leg", "right leg"};

            }
            if(source.is(DamageTypes.FALLING_BLOCK) || source.is(DamageTypes.FALLING_ANVIL)){
                return new String[]{"fracture", "head"};
            }
            return new String[]{"fracture"};
        }

        if (source.is(DamageTypes.SWEET_BERRY_BUSH) ||
                source.is(DamageTypes.CACTUS)) {
            if (source.is(DamageTypes.SWEET_BERRY_BUSH)){
                return new String[]{"abrasion", "left foot", "right foot", "left leg", "right leg"};
            }

            BlockPos playerPos = player.blockPosition();
            Level world = player.level();

            //Check if there's a cactus directly below
            if (world.getBlockState(playerPos.below()).getBlock() instanceof CactusBlock) {
                return new String[]{"abrasion", "left foot", "right foot"};
            }

            return new String[]{"abrasion"};
        }

        if(source.is(DamageTypes.IN_WALL) ||
                source.is(DamageTypes.CRAMMING) ||
                source.is(DamageTypes.DROWN)){
            return new String[]{"vanilla"};
        }

        if(source.is(DamageTypes.STARVE) ||
                source.is(DamageTypes.FELL_OUT_OF_WORLD) ||
                source.is(DamageTypes.GENERIC) ||
                source.is(DamageTypes.MAGIC) ||
                source.is(DamageTypes.WITHER) ||
                source.is(DamageTypes.BAD_RESPAWN_POINT) ||
                source.is(DamageTypes.OUTSIDE_BORDER) ||
                source.is(DamageTypes.GENERIC_KILL) ||
                source.is(DamageTypes.INDIRECT_MAGIC)){
            return new String[]{"vanilla"}; //Handled the same as without the mod
        }


        return new String[]{"vanilla"}; //Unknown damage type
    }

    private static String getWoundLocation(ArrayList<String> include) {
        final String[] BODY_PARTS = {"head", "chest", "left arm", "right arm", "left leg", "right leg", "left foot", "right foot"};
        final int[] WEIGHTS = {10, 30, 15, 15, 12, 12, 3, 3};
        Random r = new Random();
        int maxScore = Integer.MIN_VALUE;
        String selectedPart = "";

        for (int i = 0; i < BODY_PARTS.length; i++) {
            if(include != null && !include.contains(BODY_PARTS[i])) continue;
            int score = r.nextInt(WEIGHTS[i] + 1); //Value from 0 to the weight
            if (score > maxScore) {
                maxScore = score;
                selectedPart = BODY_PARTS[i];
            }
        }

        return selectedPart;
    }

    private static String detectHitBodyPart(Player player, Arrow arrow) {
        Vec3 arrowPos = arrow.position();
        Vec3 playerPos = player.position();

        double relativeX = arrowPos.x - playerPos.x;
        double relativeY = arrowPos.y - playerPos.y;
        double relativeZ = arrowPos.z - playerPos.z;

        //Simple hit detection based on relative position
        player.sendSystemMessage(Component.literal("Relative X,Y" + relativeX + "," + relativeY + "," + relativeZ));

        if (relativeY > player.getBbHeight() * 0.8) {
            return "head";
        } else if (relativeY > player.getBbHeight() * 0.4) {
            if (relativeX > 0.3 || relativeZ > 0.3) {
                return "right arm";
            }
            else if(relativeX < 0.3 || relativeZ < 0.3) {
                return "left arm";
            }
            else{
                return "chest";
            }
        } else {
            if (relativeX > 0.3 || relativeZ > 0.3) {
                return "right leg";
            }
            else if(relativeX < 0.3 || relativeZ < 0.3) {
                return "left leg";
            }
        }

        return "chest";
    }

    //Lower pain level with packets and update player modifiers.
    //Also stop player from sprinting and moving midair
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = event.getServer();
        if (event.phase == TickEvent.Phase.START) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {
                    if (pain.getChronicPainLevel() > 0) {
                        if (pain.getAdrenalineLevel() == 0) {
                            for(int i = 0; i < pain.getWounds().size(); i++){

                                //TODO replace with tickWounds
                                if(pain.getWounds().get(i).tick() <= 0) pain.getWounds().remove(i--);
                                player.setHealth(player.getHealth() - pain.getBleedLevel() / 10000);
                            }
                        }
                    }

                    if (pain.getAdrenalineLevel() > 0) {
                        pain.addAdrenaline(-.05f * 5); //Adrenaline pain lowers by 6 per second
                        if (pain.getAdrenalineLevel() < 0) pain.setAdrenalineLevel(0);
                        if (pain.getAdrenalineLevel() > 100) pain.setAdrenalineLevel(100);
                    } else if (!lastAdrenalineRushReset) {
                        //Set cooldown once the player has no adrenaline
                        lastAdrenalineRushTime = System.currentTimeMillis();
                        lastAdrenalineRushReset = true;
                    }

                    if (player.isCreative()) {
                        pain.getWounds().clear();
                        pain.setAdrenalineLevel(0);
                        lastAdrenalineRushTime = 0;
                    }

                    updateModifiers(player, pain);
                    PacketHandler.sendToPlayer(new CPainLevelPacket(pain.getAdrenalineLevel(), pain.getWounds()), () -> (ServerPlayer) player);
                });
            }

        }

        //Stop player from sprinting after 40 pain
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {
                if (pain.getAdrenalineLevel() == 0) {
                    if (pain.getChronicPainLevel() >= 40) {
                        if (!player.onGround() && keyPacketHandled) {
                            PacketHandler.sendToPlayer(new CStopKeyPacket("sprint movement"), () -> (ServerPlayer) player);
                        } else {
                            PacketHandler.sendToPlayer(new CStopKeyPacket("sprint"), () -> (ServerPlayer) player);
                        }
                        player.setSprinting(false);
                    }
                    long currentTime = System.currentTimeMillis();
                    if ((currentTime - lastJumpTime < jumpCooldown || pain.getChronicPainLevel() >= 90) && !player.isInFluidType() && pain.getAdrenalineLevel() == 0) {
                        PacketHandler.sendToPlayer(new CStopKeyPacket("jump"), () -> (ServerPlayer) player);
                        player.setJumping(false);
                    }
                }
            });

        }
    }

    //Create the mob wound config text file if it doesn't already exist, then load it
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        File configFile = new File(event.getServer().getServerDirectory(), "mod_wounds_config.txt");

        if (!configFile.exists()) {
            try {
                //Create a default file if it doesn't exist
                Files.write(configFile.toPath(), "minecraft:zombie laceration\nminecraft:spider puncture\n".getBytes());
            } catch (Exception e) {
                return;
            }
        }

        //Read the file into the mobWoundTypes map
        loadMobConfig(configFile);
    }

    //Read the mob wound config file
    private static void loadMobConfig(File configFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length == 2) {
                    String mob = parts[0];
                    String woundType = parts[1];
                    mobWoundTypes.put(mob, woundType);
                }
            }
        } catch (Exception e) {
            return;
        }
    }


    //region Apply and manage pain effects


    //Used to stop the event from being allowed and then canceled since this event fires multiple time per click
    private static boolean allowedLastAction = false;

    //Stop the player from placing blocks too often
    //TODO make this only happen when the player has adrenaline
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        event.getEntity().getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {

            //Check if the player is holding a block
            if (!(event.getItemStack().getItem() instanceof BlockItem)) {
                return;
            }

            long currentTime = System.currentTimeMillis();

            if (pain.getAdrenalineLevel() != 0) {
                event.setCanceled(true);
                event.setUseItem(Event.Result.DENY);
            }
        });
    }

    //Set the lastJumpTime
    @SubscribeEvent(priority = EventPriority.LOWEST) //Handle this last
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            lastJumpTime = System.currentTimeMillis();
        }
    }

    //Remove the sprint attribute, may be redundant
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {

            AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            UUID SPRINT_SPEED_BOOST_ID = UUID.fromString("662a6b8d-da3e-4c1c-8813-96ea6097278d");

            if (movementSpeed != null) {
                //Check if the player has high chronic pain
                player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {
                    if (pain.getChronicPainLevel() >= 40 && pain.getAdrenalineLevel() == 0) {
                        //Remove the sprinting speed modifier
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
        //Also un-press the jump key
        @SubscribeEvent
        @OnlyIn(Dist.CLIENT)
        public static void onClientTickEvent(TickEvent.ClientTickEvent event) {
            Minecraft mc = Minecraft.getInstance();

            if (mc.player != null) {
                mc.player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {


                    if (pain.getChronicPainLevel() >= 40 && pain.getAdrenalineLevel() == 0) {
                        mc.options.keySprint.setDown(false);
                        mc.player.setSprinting(false);
                    }
                    //Disable jumping if the player has jumped recently, or they are over 90 pain
                    if ((System.currentTimeMillis() - lastJumpTime < jumpCooldown || pain.getChronicPainLevel() > 90) && pain.getAdrenalineLevel() == 0) {
                        mc.options.keyJump.setDown(false);
                        mc.player.setJumping(false);
                    }

                });
            }
        }

        //Reset releasedMovementKey when the player hits the ground and set the key packet to handled
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
        @SubscribeEvent(priority = EventPriority.HIGHEST)
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

        @SubscribeEvent
        public static void onScreenInit(ScreenEvent.Init.Post event) {
            if (event.getScreen() instanceof InventoryScreen && !(event.getScreen() instanceof WoundsScreen)) {
                InventoryScreen screen = (InventoryScreen) event.getScreen();
                int x = screen.getGuiLeft();
                int y = screen.getGuiTop();

                event.addListener(Button.builder(
                                Component.literal("W"),
                                button -> screen.getMinecraft().setScreen(new WoundsScreen(screen.getMinecraft().player)))
                        .pos(x + 176, y)
                        .size(20, 20)
                        .build()
                );
            }
        }
    }

    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        //Modify the break speed for both client and server side, may be unnecessary
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {
                //Lower speed such that 90 pain = 0 speed
                double miningSpeedScale = Math.max(Math.min((((maxMiningSpeedScale - minMiningSpeedScale) / (startMiningSpeedScale - endMiningSpeedScale)) * (pain.getChronicPainLevel() - endMiningSpeedScale)) + minMiningSpeedScale, maxMiningSpeedScale), minMiningSpeedScale);
                if (pain.getAdrenalineLevel() != 0) miningSpeedScale = 1;
                event.setNewSpeed(event.getOriginalSpeed() * (float) miningSpeedScale);
            });
        } else if (event.getEntity() instanceof Player) {
            Player player = event.getEntity();
            player.getCapability(PainCapabilityProvider.PAIN_CAPABILITY).ifPresent(pain -> {
                //Lower speed such that 90 pain = 0 speed
                double miningSpeedScale = Math.max(Math.min((((maxMiningSpeedScale - minMiningSpeedScale) / (startMiningSpeedScale - endMiningSpeedScale)) * (pain.getChronicPainLevel() - endMiningSpeedScale)) + minMiningSpeedScale, maxMiningSpeedScale), minMiningSpeedScale);
                if (pain.getAdrenalineLevel() != 0) miningSpeedScale = 1;
                event.setNewSpeed(event.getOriginalSpeed() * (float) miningSpeedScale);
            });
        }
    }

    //Update the players modifiers such as speed, attack speed, jump cooldown, action cooldown, etc.
    private static void updateModifiers(Player player, PainCapability pain) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);

        if (movementSpeed != null) {
            AttributeModifier existingModifier = movementSpeed.getModifier(PAIN_MOVEMENT_SPEED_MODIFIER_ID);
            if (existingModifier != null) {

                //Lower speed such that 90 pain = 0 speed
                double movementSpeedScale = Math.max(Math.min((((maxMovementSpeedScale - minMovementSpeedScale) / (startMovementSpeedScale - endMovementSpeedScale)) * (pain.getChronicPainLevel() - endMovementSpeedScale)) + minMovementSpeedScale, maxMovementSpeedScale), minMovementSpeedScale);
                movementSpeedScale -= 1; //Reduce it by 1 as Minecraft takes our values and adds 1 to it
                //If the value has changed, remove the old modifier and add a new one with the updated value
                if (pain.getAdrenalineLevel() != 0) movementSpeedScale = 0.6; //1.6 times
                if (existingModifier.getAmount() != movementSpeedScale) {
                    movementSpeed.removeModifier(PAIN_MOVEMENT_SPEED_MODIFIER_ID);
                    AttributeModifier updatedModifier = new AttributeModifier(
                            PAIN_MOVEMENT_SPEED_MODIFIER_ID,
                            "Chronic Pain Speed Reduction",
                            movementSpeedScale,
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

                //Lower attack speed such that 90 pain = 0 speed
                double attackSpeedScale = Math.max(Math.min((((maxAttackSpeedScale - minAttackSpeedScale) / (startAttackSpeedScale - endAttackSpeedScale)) * (pain.getChronicPainLevel() - endAttackSpeedScale)) + minAttackSpeedScale, maxAttackSpeedScale), minAttackSpeedScale);
                attackSpeedScale -= 1; //Reduce it by 1 as Minecraft takes our values and adds 1 to it
                if (pain.getAdrenalineLevel() != 0) attackSpeedScale = 7; //8 times
                //If the value has changed, remove the old modifier and add a new one with the updated value
                if (existingModifier.getAmount() != attackSpeedScale) {
                    attackSpeed.removeModifier(PAIN_ATTACK_SPEED_MODIFIER_ID);
                    AttributeModifier updatedModifier = new AttributeModifier(
                            PAIN_ATTACK_SPEED_MODIFIER_ID,
                            "Chronic Pain Attack Speed Reduction",
                            attackSpeedScale,
                            AttributeModifier.Operation.MULTIPLY_TOTAL
                    );
                    attackSpeed.addPermanentModifier(updatedModifier);
                }
            }
        }


        //Set the jump Cooldown
        if (!player.isInFluidType()) {
            jumpCooldown = pain.getChronicPainLevel() < startJumpCooldown ? 0 : Math.max(Math.min((((minJumpCooldown - maxJumpCooldown) / (startJumpCooldown - endJumpCooldown)) * (pain.getChronicPainLevel() - endJumpCooldown)) + maxJumpCooldown, maxJumpCooldown), minJumpCooldown);
        }

        //Set Nausea Effect
        if(pain.getChronicPainLevel() >= startNauseaEffect && pain.getAdrenalineLevel() == 0){
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20 * 5, 0, false, false, false));
        }
    }
    //endregion


}

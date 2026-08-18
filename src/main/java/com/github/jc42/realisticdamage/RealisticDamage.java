package com.github.jc42.realisticdamage;

import com.github.jc42.realisticdamage.network.PainLevelPacket;
import com.github.jc42.realisticdamage.network.StopKeyPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.TriState;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.*;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Supplier;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(RealisticDamage.MODID)
public class RealisticDamage {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "realisticdamage";

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MODID);

    public static final Supplier<AttachmentType<PainCapability>> PAIN = ATTACHMENT_TYPES.register(
            "pain",
            () -> AttachmentType.serializable(PainCapability::new).copyOnDeath().build()
    );

    private static final Identifier PAIN_MOVEMENT_SPEED_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(MODID, "pain_movement_speed");

    private static final Identifier PAIN_ATTACK_SPEED_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(MODID, "pain_attack_speed");

    private static final Identifier PAIN_MINING_SPEED_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(MODID, "pain_mining_speed");
    public static boolean keyPacketHandled = true;
    public static boolean releasedMovementKeyMidair = false;
    // Directly reference a slf4j logger
    static final Logger LOGGER = LogUtils.getLogger();

    static long lastJumpTime = -1;
    private static long lastAdrenalineRushTime = -1;

    private static boolean lastAdrenalineRushReset = false;
    //In milliseconds
    private static final long ACTION_COOLDOWN = 1000;
    static float jumpCooldown = 0;

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

    // Create a Deferred Register to hold Blocks which will all be registered under the "realisticdamage" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "realisticdamage" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "realisticdamage" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creates a new Block with the id "realisticdamage:example_block", combining the namespace and path
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", p -> p.mapColor(MapColor.STONE));
    // Creates a new BlockItem with the id "realisticdamage:example_block", combining the namespace and path
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);

    // Creates a new food item with the id "realisticdamage:example_id", nutrition 1 and saturation 2
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", p -> p.food(new FoodProperties.Builder()
            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));

    // Creates a creative tab with the id "realisticdamage:example_tab" for the example item, that is placed after the combat tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.realisticdamage")) //The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> EXAMPLE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(EXAMPLE_ITEM.get());// Add the example item to the tab. For your own tabs, this method is preferred over the event
            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public RealisticDamage(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);


        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (RealisticDamage) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(RealisticDamage.class);

        ATTACHMENT_TYPES.register(modEventBus);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(EXAMPLE_BLOCK_ITEM);
        }
    }

    //region Make pain persistent and TODO apply respawn pain

    //Copy pain data when respawning
    @SubscribeEvent
    public static void onPlayerCloned(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            //TODO implement respawn pain to stop players commiting suicide to avoid pain debuffs
            return;
        }

        event.getEntity().setData(RealisticDamage.PAIN, event.getOriginal().getData(RealisticDamage.PAIN));
    }

    //Add the player attributes when the player joins the world
    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Player player) {
            PainCapability pain = player.getData(RealisticDamage.PAIN);

            AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);

            if (movementSpeed != null) {

                if (movementSpeed.getModifier(PAIN_MOVEMENT_SPEED_MODIFIER_ID) == null) {
                    AttributeModifier speedModifier = new AttributeModifier(
                            PAIN_MOVEMENT_SPEED_MODIFIER_ID,
                            0, //Start with no effect
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                    );
                    movementSpeed.addPermanentModifier(speedModifier);
                }
            }

            updateModifiers(player, pain);
        }
    }

    //endregion

    private static float calculateDamageReduction(Player player, LivingDamageEvent.Pre event, EquipmentSlot slot) {
        ItemStack armor = player.getItemBySlot(slot);
        float newDamage = event.getOriginalDamage();
        DamageContainer container = event.getContainer();

        if(!armor.isEmpty()) {
            DamageSource source = container.getSource();

            newDamage = container.getNewDamage()
                    + container.getReduction(DamageContainer.Reduction.ARMOR)
                    + container.getReduction(DamageContainer.Reduction.MOB_EFFECTS)
                    + container.getReduction(DamageContainer.Reduction.ENCHANTMENTS);

            if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
                AttributeInstance armorInst = new AttributeInstance(Attributes.ARMOR, i -> {});
                AttributeInstance toughInst = new AttributeInstance(Attributes.ARMOR_TOUGHNESS, i -> {});
                Map<Identifier, AttributeModifier> armorMods = new LinkedHashMap<>();
                Map<Identifier, AttributeModifier> toughMods = new LinkedHashMap<>();
                armor.forEachModifier(slot, (attr, mod) -> {
                    if (attr.equals(Attributes.ARMOR)) armorMods.put(mod.id(), mod);
                    else if (attr.equals(Attributes.ARMOR_TOUGHNESS)) toughMods.put(mod.id(), mod);
                });
                armorMods.values().forEach(armorInst::addTransientModifier);
                toughMods.values().forEach(toughInst::addTransientModifier);

                newDamage = CombatRules.getDamageAfterAbsorb(player, newDamage, source,
                        Mth.floor(armorInst.getValue()), (float) toughInst.getValue());
            }

            if (newDamage > 0.0F
                    && !source.is(DamageTypeTags.BYPASSES_EFFECTS)
                    && !source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
                    && player.level() instanceof ServerLevel serverLevel) {
                MutableFloat protection = new MutableFloat(0.0F);
                EnchantmentHelper.runIterationOnItem(armor, slot, player, (ench, lvl, item) ->
                        ench.value().modifyDamageProtection(serverLevel, lvl, item.itemStack(), player, source, protection));
                if (protection.floatValue() > 0.0F) {
                    newDamage = CombatRules.getDamageAfterMagicAbsorb(newDamage, protection.floatValue());
                }
            }
        }
        return newDamage;
    }

    //Update pain level and send pain packet
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDamagePre(LivingDamageEvent.Pre event) {
        //TODO when wearing armor on the hit body part the amount of health the armor prevents scales a percentage chance to turn any wound type into a blunt
        //TODO use #bypasses_armor
        //TODO when considering the amount of health lost for calcs, only look at armor reduction for the body part hit. So if hit on chest look at the damage they would recieve if wearing only chestplate (if chestplate is on)
        //TODO make bleed go head > chest > legs > arms > feet
        if (event.getEntity() instanceof Player player) {

            PainCapability pain = player.getData(RealisticDamage.PAIN);

            DamageSource damageSource = event.getContainer().getSource();
            Entity directEntity = damageSource.getDirectEntity();
            String[] damageType = classifyDamage(damageSource, directEntity, player);
            String bodyPart = getWoundLocation(damageType, directEntity, player);


            float newDamage = switch (bodyPart) {
                case "head" -> calculateDamageReduction(player, event, EquipmentSlot.HEAD);
                case "chest", "left arm", "right arm" -> calculateDamageReduction(player, event, EquipmentSlot.CHEST);
                case "left leg", "right leg" -> calculateDamageReduction(player, event, EquipmentSlot.LEGS);
                case "left foot", "right foot" -> calculateDamageReduction(player, event, EquipmentSlot.FEET);
                default -> event.getContainer().getNewDamage();
            };

            player.sendSystemMessage(Component.literal("Original Damage: " + event.getOriginalDamage() + " New Damage: " + event.getNewDamage() + " Container New: " + event.getContainer().getNewDamage() + " Custom New: " + newDamage));

            float fractionLost = newDamage / player.getMaxHealth();
            int severity = fractionLost >= .40 ? 3 : (fractionLost >= .20 ? 2 : (fractionLost >= .10 ? 1 : 0));

            if (directEntity instanceof Arrow arrow) {

                //region Test arrow lodging


                //endregion

                double[] position = {arrow.position().x, arrow.position().y, arrow.position().z, arrow.getXRot(), arrow.getYRot()};

                pain.getLodgedArrowPositions().add(position);

                //TODO head code sets it to right arm?
                //TODO it never triggers the left arm or the left leg

                //TODO make it so that the severity is based on the amount of damage
                pain.addWound(new Wound("Puncture", severity, bodyPart));

            } else {
                if (!damageType[0].equals("vanilla")) {
                    if (damageType[0].equals("blunt")) {
                        if (severity == 3) damageType[0] = "laceration";
                        else damageType[0] = "hematoma";
                    }
                    pain.addWound(new Wound(damageType[0], severity, bodyPart));
                }
            }

            if (System.currentTimeMillis() - lastAdrenalineRushTime > adrenalineRushCooldown && pain.getAdrenalineLevel() == 0) {
                if (pain.getChronicPainLevel() >= 30) {
                    pain.setAdrenalineLevel(50 + ((pain.getChronicPainLevel() - 30) / 70) * 50);
                    lastAdrenalineRushReset = false;
                }
            }

            if (pain.getAdrenalineLevel() < 0) pain.setAdrenalineLevel(0);

            if (pain.getAdrenalineLevel() > 100) pain.setAdrenalineLevel(100);
            if (player instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayer(serverPlayer, new PainLevelPacket(pain.getAdrenalineLevel(), pain.getWounds()));
            }

            //Cancel damage
            event.setNewDamage(0f);
        }
    }

    private static String[] classifyDamage(DamageSource source, Entity directEntity, Player player) {
        //TODO make blunt have chance to add a fracture too
        //TODO make lava more than just a burn
        if (source.is(DamageTypes.IN_FIRE) ||
                source.is(DamageTypes.LAVA) ||
                source.is(DamageTypes.ON_FIRE) ||
                source.is(DamageTypes.HOT_FLOOR) ||
                source.is(DamageTypes.SULFUR_CUBE_HOT) ||
                source.is(DamageTypes.CAMPFIRE) ||
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
            if(source.is(DamageTypes.HOT_FLOOR) ||
                source.is(DamageTypes.CAMPFIRE)){
                return new String[]{"burn", "left foot", "right foot"};
            }
            if (source.is(DamageTypes.SULFUR_CUBE_HOT)) {
                Entity cube = directEntity != null ? directEntity : source.getEntity();

                if (cube == null) return new String[]{"burn", "left foot", "right foot"};

                AABB victim = player.getBoundingBox();
                AABB hazard = cube.getBoundingBox();
                double lo = Math.max(victim.minY, hazard.minY);
                double hi = Math.min(victim.maxY, hazard.maxY);

                if (hi <= lo) return new String[]{"burn", "left foot", "right foot"};

                double height = victim.getYsize();
                float low = (float) ((lo - victim.minY) / height);
                float high = (float) ((hi - victim.minY) / height);

                List<String> parts = new ArrayList<>();
                parts.add("burn");
                if (low < 0.08f) { parts.add("left foot"); parts.add("right foot"); }
                if (high > 0.05f && low < 0.30f) { parts.add("left leg"); parts.add("right leg"); }
                if (high > 0.30f && low < 0.75f) { parts.add("chest"); }
                if (high > 0.35f && low < 0.70f) { parts.add("left arm"); parts.add("right arm"); }
                if (high > 0.85f) { parts.add("head"); }
                return parts.toArray(new String[0]);
            }

            if (source.is(DamageTypes.LAVA)) {
                List<String> affectedParts = new ArrayList<>();
                affectedParts.add("burn");  //Always add burn as first element
                //Get the lava height - start at player's feet and look up for lava blocks
                Level world = player.level();
                AABB box = player.getBoundingBox();
                double entityY = player.getY();
                double entityHeight = player.getBbHeight();
                double lavaHeight = Double.NEGATIVE_INFINITY;
                BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();
                //Search up to player height for highest lava block
                for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
                    for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                        for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY); y++) {
                            checkPos.set(x, y, z);
                            FluidState fluidState = world.getFluidState(checkPos);
                            if (!fluidState.is(FluidTags.LAVA)) break;
                            lavaHeight = Math.max(lavaHeight, y + fluidState.getHeight(world, checkPos));
                        }
                    }
                }
                double lavaDepth = (lavaHeight - entityY) / entityHeight;
                //Feet level (0-9.4% of height)
                if (lavaDepth >= 0.023) {
                    affectedParts.add("left foot");
                    affectedParts.add("right foot");
                }
                //Legs level (9.4-37.5% of height)
                if (lavaDepth >= 0.164) {
                    affectedParts.add("left leg");
                    affectedParts.add("right leg");
                }
                //Chest level (37.5-75% of height)
                if (lavaDepth >= 0.469) {
                    affectedParts.add("chest");
                    affectedParts.add("left arm");
                    affectedParts.add("right arm");
                }
                //Head level (75-100% of height)
                if (lavaDepth >= 0.813) {
                    affectedParts.add("head");
                }
                return affectedParts.toArray(new String[0]);
            }
            return new String[]{"burn"};
        }

        if (source.is(DamageTypes.PLAYER_ATTACK)) {
            Player attacker = (Player) directEntity;
            ItemStack weapon = attacker.getMainHandItem();

            //TODO Add Mace and Spear
            if (weapon.is(ItemTags.SWORDS) || weapon.is(ItemTags.AXES) || weapon.is(ItemTags.HOES)) {
                return new String[]{"laceration"};
            }
            else if (weapon.is(ItemTags.PICKAXES) || weapon.is(ItemTags.SPEARS)) {
                return new String[]{"puncture"};
            }
            else {
                //Includes Maces, Shovels, etc
                return new String[]{"blunt"}; // TODO: If this is tier 3+ make it a laceration, otherwise make it a hematoma
            }
        }

        if (source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)) {
            if (directEntity instanceof LivingEntity livingAttacker) {
                ItemStack itemStack = livingAttacker.getWeaponItem();
                if (!itemStack.isEmpty()) {
                    if (itemStack.is(ItemTags.SWORDS) || itemStack.is(ItemTags.AXES) || itemStack.is(ItemTags.HOES)) {
                        return new String[]{"laceration"};
                    }
                    else if (itemStack.is(ItemTags.PICKAXES) || itemStack.is(ItemTags.SPEARS)) {
                        return new String[]{"puncture"};
                    }
                    else{
                        //Includes Maces, Shovels, etc
                        return new String[]{"blunt"};
                    }
                }
                else{
                    //In the form namespace:entity_name
                    String mobName = BuiltInRegistries.ENTITY_TYPE.getKey(directEntity.getType()).toString();
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

        if (source.is(DamageTypes.MACE_SMASH)){
            return new String[]{"blunt"};
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

        if (source.is(DamageTypes.SPIT) || source.is(DamageTypes.WIND_CHARGE)){
            return new String[]{"hematoma"};
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
                source.is(DamageTypes.STING) ||
                source.is(DamageTypes.SPEAR)) {
            if (source.is(DamageTypes.FALLING_STALACTITE)) return new String[]{"puncture", "head"};
            else return new String[]{"puncture"};
        }

        if (source.is(DamageTypes.FALL) ||
                source.is(DamageTypes.FLY_INTO_WALL) ||
                source.is(DamageTypes.FALLING_ANVIL) ||
                source.is(DamageTypes.FALLING_BLOCK)) {
            if(source.is(DamageTypes.FALL)){
                return new String[]{"fracture", "left foot", "right foot", "left leg", "right leg"};

            }
            if(source.is(DamageTypes.FALLING_BLOCK) ||
                    source.is(DamageTypes.FALLING_ANVIL) ||
                    source.is(DamageTypes.FLY_INTO_WALL)){
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
                source.is(DamageTypes.DROWN) ||
                source.is(DamageTypes.STARVE) ||
                source.is(DamageTypes.ENDER_PEARL) ||
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

        // TODO THROWN is unknown so will end up here (also SONIC_BOOM)
        return new String[]{"vanilla"}; //Unknown damage type
    }
    private static String getWoundLocation(String[] damageType, Entity directEntity, Player player) {
        if(directEntity instanceof Arrow arrow){
            return detectHitBodyPart(player, arrow);
        }

        final String[] BODY_PARTS = {"head", "chest", "left arm", "right arm", "left leg", "right leg", "left foot", "right foot"};
        final int[] WEIGHTS = {10, 30, 15, 15, 12, 12, 3, 3};
        Random r = new Random();
        int maxScore = Integer.MIN_VALUE;
        String selectedPart = "";

        for (int i = 0; i < BODY_PARTS.length; i++) {
            if(damageType.length > 1 && !Arrays.asList(damageType).contains(BODY_PARTS[i])) continue;
            int score = r.nextInt(WEIGHTS[i] + 1); //Value from 0 to the weight
            if (score > maxScore) {
                maxScore = score;
                selectedPart = BODY_PARTS[i];
            }
        }

        return selectedPart;
    }

    private static String detectHitBodyPart(Player player, AbstractArrow arrow) {
        String[] parts = {"head", "chest", "right arm", "left arm", "right leg", "left leg"};
        double[][] regions = {
                {-0.25, 1.36, -0.25, 0.25, 1.86, 0.25},
                {-0.25, 0.68, -0.13, 0.25, 1.36, 0.13},
                {0.25, 0.66, -0.13, 0.52, 1.38, 0.13},
                {-0.52, 0.66, -0.13, -0.25, 1.38, 0.13},
                {0.0, 0.0, -0.13, 0.25, 0.68, 0.13},
                {-0.25, 0.0, -0.13, 0.0, 0.68, 0.13}
        };
        AABB hitbox = player.getBoundingBox();
        double width = Math.max(hitbox.getXsize(), 0.05);
        double height = Math.max(hitbox.getYsize(), 0.05);
        boolean prone = height < width * 1.2;
        double horizontalScale = width / 0.6;
        double verticalScale = prone ? horizontalScale : height / 1.8;

        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        if (prone) {
            double pitch = Math.toRadians(player.getXRot());
            double yaw = Math.toRadians(player.getYRot());
            double cosPitch = Math.cos(pitch);
            Vec3 look = new Vec3(-Math.sin(yaw) * cosPitch, -Math.sin(pitch), Math.cos(yaw) * cosPitch);
            if (look.lengthSqr() > 1.0E-8) {
                up = look.normalize();
            }
        }

        double bodyYaw = Math.toRadians(player.yBodyRot);
        Vec3 right = new Vec3(-Math.cos(bodyYaw), 0.0, -Math.sin(bodyYaw));
        right = right.subtract(up.scale(right.dot(up)));
        if (right.lengthSqr() < 1.0E-8) {
            right = new Vec3(up.z, 0.0, -up.x);
        }
        if (right.lengthSqr() < 1.0E-8) {
            right = new Vec3(1.0, 0.0, 0.0);
        }
        right = right.normalize();
        Vec3 front = up.cross(right).normalize();

        Vec3 origin = prone
                ? player.getEyePosition().subtract(up.scale(1.62 * verticalScale))
                : new Vec3((hitbox.minX + hitbox.maxX) * 0.5, hitbox.minY, (hitbox.minZ + hitbox.maxZ) * 0.5);

        Vec3 motion = arrow.getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-8) {
            motion = origin.add(up.scale(0.9 * verticalScale)).subtract(arrow.position());
        }
        if (motion.lengthSqr() < 1.0E-8) {
            motion = new Vec3(0.0, -1.0, 0.0);
        }

        Vec3 direction = motion.normalize();
        Vec3 offset = arrow.position().subtract(direction.scale(4.0)).subtract(origin);
        double[] start = {offset.dot(right), offset.dot(up), offset.dot(front)};
        double[] slope = {direction.dot(right), direction.dot(up), direction.dot(front)};
        double reach = 5.5 + motion.length();

        int hit = -1;
        double bestEntry = Double.MAX_VALUE;
        for (int i = 0; i < regions.length; i++) {
            double near = Double.NEGATIVE_INFINITY;
            double far = Double.POSITIVE_INFINITY;
            boolean miss = false;
            for (int axis = 0; axis < 3 && !miss; axis++) {
                double scale = axis == 1 ? verticalScale : horizontalScale;
                double low = regions[i][axis] * scale;
                double high = regions[i][axis + 3] * scale;
                if (Math.abs(slope[axis]) < 1.0E-9) {
                    miss = start[axis] < low || start[axis] > high;
                    continue;
                }
                double first = (low - start[axis]) / slope[axis];
                double second = (high - start[axis]) / slope[axis];
                if (first > second) {
                    double swap = first;
                    first = second;
                    second = swap;
                }
                near = Math.max(near, first);
                far = Math.min(far, second);
                miss = near > far;
            }
            if (miss || far < 0.0) {
                continue;
            }
            double entry = Math.max(near, 0.0);
            if (entry <= reach && entry < bestEntry) {
                bestEntry = entry;
                hit = i;
            }
        }
        if (hit >= 0) {
            return parts[hit];
        }

        double travel = Math.max(0.0, Math.min(reach, -start[0] * slope[0]
                + (0.9 * verticalScale - start[1]) * slope[1] - start[2] * slope[2]));
        double[] closest = {start[0] + slope[0] * travel, start[1] + slope[1] * travel, start[2] + slope[2] * travel};

        int nearest = 1;
        double nearestDistance = Double.MAX_VALUE;
        for (int i = 0; i < regions.length; i++) {
            double total = 0.0;
            for (int axis = 0; axis < 3; axis++) {
                double scale = axis == 1 ? verticalScale : horizontalScale;
                double gap = Math.max(Math.max(regions[i][axis] * scale - closest[axis], 0.0),
                        closest[axis] - regions[i][axis + 3] * scale);
                total += gap * gap;
            }
            if (total < nearestDistance) {
                nearestDistance = total;
                nearest = i;
            }
        }
        return parts[nearest];
    }

    //Lower pain level with packets and update player modifiers.
    //Also stop player from sprinting and moving midair
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        MinecraftServer server = event.getServer();
        boolean bleedTick = server.getTickCount() % 20 == 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PainCapability pain = player.getData(RealisticDamage.PAIN);

            if (pain.getChronicPainLevel() > 0 && pain.getAdrenalineLevel() == 0) {
                //TODO replace with tickWounds
                pain.getWounds().removeIf(wound -> wound.tick() <= 0);
                if (bleedTick && pain.getBleedLevel() > 0 && player.level() instanceof ServerLevel serverLevel) {
                    //500.0F to make
                    player.setHealth(Math.max(0.0F, player.getHealth() - pain.getBleedLevel() ));
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
            PacketDistributor.sendToPlayer(player, new PainLevelPacket(pain.getAdrenalineLevel(), pain.getWounds()));

            //Stop player from sprinting after 40 pain
            if (pain.getAdrenalineLevel() == 0) {
                if (pain.getChronicPainLevel() >= 40) {
                    if (!player.onGround() && keyPacketHandled) {
                        PacketDistributor.sendToPlayer(player, new StopKeyPacket("sprint movement"));
                    } else {
                        PacketDistributor.sendToPlayer(player, new StopKeyPacket("sprint"));
                    }
                    player.setSprinting(false);
                }
                long currentTime = System.currentTimeMillis();
                //TODO statics are per-server, not per-player.
                if ((currentTime - lastJumpTime < jumpCooldown || pain.getChronicPainLevel() >= 90) && !player.isInFluidType()) {
                    PacketDistributor.sendToPlayer(player, new StopKeyPacket("jump"));
                    player.setJumping(false);
                }
            }
        }
    }

    //Create the mob wound config text file if it doesn't already exist, then load it
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        File configFile = new File(event.getServer().getServerDirectory().toFile(), "mob_wounds_config.txt");

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
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        PainCapability pain =  event.getEntity().getData(RealisticDamage.PAIN);

        //Check if the player is holding a block
        if (!(event.getItemStack().getItem() instanceof BlockItem)) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        if (pain.getAdrenalineLevel() != 0) {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
        }
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
    public static void onLivingTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {

            AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            Identifier SPRINT_SPEED_BOOST_ID = Identifier.fromNamespaceAndPath("realisticdamage", "sprint_speed_boost");

            if (movementSpeed != null) {
                //Check if the player has high chronic pain
                PainCapability pain = player.getData(RealisticDamage.PAIN);
                if (pain.getChronicPainLevel() >= 40 && pain.getAdrenalineLevel() == 0) {
                    //Remove the sprinting speed modifier
                    AttributeModifier modifier = movementSpeed.getModifier(SPRINT_SPEED_BOOST_ID);
                    if (modifier != null) {
                        movementSpeed.removeModifier(modifier);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        //Modify the break speed for both client and server side, may be unnecessary
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            PainCapability pain = player.getData(RealisticDamage.PAIN);
            //Lower speed such that 90 pain = 0 speed
            double miningSpeedScale = Math.max(Math.min((((maxMiningSpeedScale - minMiningSpeedScale) / (startMiningSpeedScale - endMiningSpeedScale)) * (pain.getChronicPainLevel() - endMiningSpeedScale)) + minMiningSpeedScale, maxMiningSpeedScale), minMiningSpeedScale);
            if (pain.getAdrenalineLevel() != 0) miningSpeedScale = 1;
            event.setNewSpeed(event.getOriginalSpeed() * (float) miningSpeedScale);
        } else if (event.getEntity() instanceof Player) {
            Player player = event.getEntity();
            PainCapability pain = player.getData(RealisticDamage.PAIN);
            //Lower speed such that 90 pain = 0 speed
            double miningSpeedScale = Math.max(Math.min((((maxMiningSpeedScale - minMiningSpeedScale) / (startMiningSpeedScale - endMiningSpeedScale)) * (pain.getChronicPainLevel() - endMiningSpeedScale)) + minMiningSpeedScale, maxMiningSpeedScale), minMiningSpeedScale);
            if (pain.getAdrenalineLevel() != 0) miningSpeedScale = 1;
            event.setNewSpeed(event.getOriginalSpeed() * (float) miningSpeedScale);
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
                if (existingModifier.amount() != movementSpeedScale) {
                    movementSpeed.removeModifier(PAIN_MOVEMENT_SPEED_MODIFIER_ID);
                    AttributeModifier updatedModifier = new AttributeModifier(
                            PAIN_MOVEMENT_SPEED_MODIFIER_ID,
                            movementSpeedScale,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
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
                if (pain.getAdrenalineLevel() != 0) attackSpeedScale = 999; //1000 times
                //If the value has changed, remove the old modifier and add a new one with the updated value
                if (existingModifier.amount() != attackSpeedScale) {
                    attackSpeed.removeModifier(PAIN_ATTACK_SPEED_MODIFIER_ID);
                    AttributeModifier updatedModifier = new AttributeModifier(
                            PAIN_ATTACK_SPEED_MODIFIER_ID,
                            attackSpeedScale,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
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
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20 * 5, 0, false, false, false));
        }
    }
    //endregion
}

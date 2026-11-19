package com.cleannrooster.rpg_minibosses.test;

import com.cleannrooster.rpg_minibosses.entity.ArchmageFireEntity;
import com.cleannrooster.rpg_minibosses.entity.ArtilleristEntity;
import com.cleannrooster.rpg_minibosses.entity.JuggernautEntity;
import com.cleannrooster.rpg_minibosses.entity.MinibossEntity;
import com.cleannrooster.rpg_minibosses.entity.RPGMinibossesEntities;
import com.cleannrooster.rpg_minibosses.entity.TemplarEntity;
import com.cleannrooster.rpg_minibosses.loadout.HeldAbilities;
import com.cleannrooster.rpg_minibosses.loadout.Loadout;
import com.cleannrooster.rpg_minibosses.loadout.LoadoutEntry;
import com.cleannrooster.rpg_minibosses.loadout.Loadouts;
import com.cleannrooster.rpg_minibosses.loadout.SpellMotion;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.spell_power.api.SpellSchools;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellContainer;
import net.spell_engine.internals.SpellContainerHelper;
import net.spell_engine.internals.arrow.ArrowExtension;
import net.minecraft.util.math.random.Random;
import net.sweenus.simplyswords.api.AwakeningApi;
import net.sweenus.simplyswords.world.WeaponAbilityCooldownManager;

import java.util.List;
import java.util.function.Consumer;

public class LoadoutTests implements FabricGameTest {
    private static final int MERCENARY_WINDUP = 20;

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestException(message);
        }
    }

    private static Loadout loadout(String json) {
        try {
            return Loadout.fromJson(JsonParser.parseString(json).getAsJsonObject());
        } catch (Exception e) {
            throw new GameTestException("bad loadout json: " + e.getMessage());
        }
    }

    private static MobEntity frozen(TestContext context, EntityType<? extends MobEntity> type, BlockPos pos) {
        MobEntity mob = context.spawnEntity(type, pos);
        mob.setAiDisabled(true);
        mob.setNoGravity(true);
        return mob;
    }

    private static MobEntity fighter(TestContext context, ItemStack weapon, double distance) {
        MobEntity mob = frozen(context, EntityType.VINDICATOR, new BlockPos(1, 2, 1));
        MobEntity target = frozen(context, EntityType.IRON_GOLEM, new BlockPos(1, 2, 1));
        target.refreshPositionAndAngles(mob.getX() + distance, mob.getY(), mob.getZ(), 0, 0);
        mob.equipStack(EquipmentSlot.MAINHAND, weapon);
        mob.setTarget(target);
        return mob;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void defaultLoadoutsLoad(TestContext context) {
        Loadout templar = Loadouts.get(RPGMinibossesEntities.TEMPLAR_ENTITY_ENTRY.entityType());
        check(templar != null, "templar loadout missing");
        for (int i = 0; i < 30; i++) {
            LoadoutEntry entry = templar.roll(EquipmentSlot.MAINHAND, context.getWorld().getRandom());
            check(entry != null && entry.available(), "templar rolled an entry with nothing in it");
            check(!entry.create(context.getWorld().getRandom()).isEmpty(), "templar rolled an empty hand");
        }
        check(templar.roll(EquipmentSlot.OFFHAND, context.getWorld().getRandom()) == null, "templar offhand should be empty");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void entryParsing(TestContext context) {
        Loadout loadout = loadout("""
                {"mainhand": [
                  {"item": "simplyswords:shadowsting", "awakening": 3, "drop_chance": 0.5, "ability": {"cooldown": 40, "max_range": 4}},
                  {"item": "minecraft:stick", "weight": 0}
                ], "offhand": [
                  {"tag": "minecraft:swords"}
                ]}""");
        for (int i = 0; i < 50; i++) {
            LoadoutEntry rolled = loadout.roll(EquipmentSlot.MAINHAND, context.getWorld().getRandom());
            check(rolled != null && rolled.item() != Items.STICK, "weight 0 entry got rolled");
        }
        LoadoutEntry sword = loadout.mainhand().get(0);
        ItemStack stack = sword.create(context.getWorld().getRandom());
        check(AwakeningApi.getLevel(stack) == 3, "awakening should be 3, got " + AwakeningApi.getLevel(stack));
        check(sword.dropChance() == 0.5F, "drop chance not parsed");
        check(sword.ability().cooldown() == 40 && sword.ability().maxRange() == 4, "ability settings not parsed");
        check(loadout.find(EquipmentSlot.MAINHAND, stack) == sword, "find should match the rolled stack");

        LoadoutEntry full = loadout("{\"mainhand\":[{\"item\":\"simplyswords:shadowsting\"}]}").mainhand().get(0);
        check(AwakeningApi.getLevel(full.create(context.getWorld().getRandom())) == 8, "default awakening should be full");

        ItemStack fromTag = loadout.offhand().get(0).create(context.getWorld().getRandom());
        check(fromTag.isIn(net.minecraft.registry.tag.ItemTags.SWORDS), "tag entry should give a sword, got " + fromTag);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void templarEquipsFromLoadout(TestContext context) {
        TemplarEntity templar = context.spawnEntity(RPGMinibossesEntities.TEMPLAR_ENTITY_ENTRY.entityType(), new BlockPos(1, 2, 1));
        context.waitAndRun(5, () -> {
            check(!templar.getMainHandStack().isEmpty(), "templar holds nothing");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void swordIsLockedWithoutTrigger(TestContext context) {
        ServerWorld world = context.getWorld();
        MobEntity mob = frozen(context, EntityType.VINDICATOR, new BlockPos(1, 2, 1));
        ItemStack stack = new ItemStack(Registries.ITEM.get(new Identifier("simplyswords", "shadowsting")));
        AwakeningApi.setLevel(stack, 8);
        mob.equipStack(EquipmentSlot.MAINHAND, stack);
        new HeldAbilities(mob).tick(world, null);
        check(WeaponAbilityCooldownManager.isCoolingDown(world, mob, stack), "simply swords cooldown should be locked");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void swordAbilityFires(TestContext context) {
        ServerWorld world = context.getWorld();
        Loadout loadout = loadout("{\"mainhand\":[{\"item\":\"simplyswords:shadowsting\",\"ability\":{\"cooldown\":100,\"chance\":1,\"interval\":1}}]}");
        MobEntity mob = fighter(context, loadout.mainhand().get(0).create(world.getRandom()), 3);
        HeldAbilities abilities = new HeldAbilities(mob);
        float multiplier = RPGMinibossesEntities.config.abilityCooldownMultiplier;
        RPGMinibossesEntities.config.abilityCooldownMultiplier = 0.5F;
        try {
            abilities.tick(world, loadout);
        } finally {
            RPGMinibossesEntities.config.abilityCooldownMultiplier = multiplier;
        }
        long expected = world.getTime() + 50;
        check(abilities.readyAt(Hand.MAIN_HAND) == expected, "readyAt " + abilities.readyAt(Hand.MAIN_HAND) + " expected " + expected);
        abilities.tick(world, loadout);
        check(abilities.readyAt(Hand.MAIN_HAND) == expected, "ability fired again during cooldown");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void triggerConditionsHold(TestContext context) {
        ServerWorld world = context.getWorld();
        String[] blocked = {
                "{\"chance\":0,\"interval\":1}",
                "{\"chance\":1,\"interval\":1,\"max_range\":2}",
                "{\"chance\":1,\"interval\":1,\"min_range\":6}",
                "{\"chance\":1,\"interval\":1,\"below_health\":0.5}",
                "{\"chance\":1,\"interval\":1,\"enabled\":false}"
        };
        for (String ability : blocked) {
            Loadout loadout = loadout("{\"mainhand\":[{\"item\":\"simplyswords:shadowsting\",\"ability\":" + ability + "}]}");
            MobEntity mob = fighter(context, loadout.mainhand().get(0).create(world.getRandom()), 4);
            HeldAbilities abilities = new HeldAbilities(mob);
            abilities.tick(world, loadout);
            check(abilities.readyAt(Hand.MAIN_HAND) == 0, "fired despite " + ability);
        }
        Loadout loadout = loadout("{\"mainhand\":[{\"item\":\"simplyswords:shadowsting\",\"ability\":{\"chance\":1,\"interval\":1}}]}");
        MobEntity mob = fighter(context, loadout.mainhand().get(0).create(world.getRandom()), 4);
        HeldAbilities abilities = new HeldAbilities(mob);
        boolean enabled = RPGMinibossesEntities.config.heldAbilities;
        RPGMinibossesEntities.config.heldAbilities = false;
        try {
            abilities.tick(world, loadout);
        } finally {
            RPGMinibossesEntities.config.heldAbilities = enabled;
        }
        check(abilities.readyAt(Hand.MAIN_HAND) == 0, "fired with heldAbilities off");
        check(WeaponAbilityCooldownManager.isCoolingDown(world, mob, mob.getMainHandStack()), "lock should hold with heldAbilities off");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void spellCasts(TestContext context) {
        ServerWorld world = context.getWorld();
        Loadout loadout = loadout("{\"mainhand\":[{\"item\":\"minecraft:stick\",\"spells\":[\"rpg-minibosses:fireball\"],\"ability\":{\"chance\":1,\"interval\":1,\"cooldown\":60}}]}");
        MobEntity mob = fighter(context, new ItemStack(Items.STICK), 5);
        HeldAbilities abilities = new HeldAbilities(mob);
        abilities.tick(world, loadout);
        check(abilities.readyAt(Hand.MAIN_HAND) == world.getTime() + 60, "spell did not go on cooldown");
        Box box = mob.getBoundingBox().expand(8);
        check(!world.getEntitiesByClass(ProjectileEntity.class, box, p -> p.getOwner() == mob).isEmpty(), "no spell projectile spawned");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void selfSpellIgnoresRange(TestContext context) {
        ServerWorld world = context.getWorld();
        Loadout loadout = loadout("{\"mainhand\":[{\"item\":\"minecraft:stick\",\"spells\":[\"rpg-minibosses-gametest:self_buff\"],\"ability\":{\"chance\":1,\"interval\":1,\"cooldown\":40}}]}");
        MobEntity mob = fighter(context, new ItemStack(Items.STICK), 5);
        HeldAbilities abilities = new HeldAbilities(mob);
        abilities.tick(world, loadout);
        check(abilities.readyAt(Hand.MAIN_HAND) == world.getTime() + 40, "self spell should cast with the target past spell range");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void itemSpellsUsedWithoutEntrySpells(TestContext context) {
        ServerWorld world = context.getWorld();
        ItemStack stick = new ItemStack(Items.STICK);
        SpellContainerHelper.addContainerToItemStack(new SpellContainer(false, null, 1, List.of("rpg-minibosses-gametest:self_buff")), stick);
        MobEntity mob = fighter(context, stick, 4);
        HeldAbilities abilities = new HeldAbilities(mob);
        abilities.tick(world, loadout("{\"mainhand\":[{\"item\":\"minecraft:stick\",\"ability\":{\"chance\":1,\"interval\":1,\"cooldown\":40}}]}"));
        check(abilities.readyAt(Hand.MAIN_HAND) == world.getTime() + 40, "item spell did not cast");
        check(mob.hasStatusEffect(StatusEffects.ABSORPTION), "item spell had no effect");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void spellOutOfRangeKeepsCooldown(TestContext context) {
        ServerWorld world = context.getWorld();
        Loadout loadout = loadout("{\"mainhand\":[{\"item\":\"minecraft:stick\",\"spells\":[\"rpg-minibosses-gametest:beam_test\"],\"ability\":{\"chance\":1,\"interval\":1,\"cooldown\":40,\"max_range\":64}}]}");
        MobEntity mob = fighter(context, new ItemStack(Items.STICK), 20);
        HeldAbilities abilities = new HeldAbilities(mob);
        abilities.tick(world, loadout);
        check(abilities.readyAt(Hand.MAIN_HAND) == 0, "cast past spell range");
        mob.getTarget().refreshPositionAndAngles(mob.getX() + 4, mob.getY(), mob.getZ(), 0, 0);
        abilities.tick(world, loadout);
        check(abilities.readyAt(Hand.MAIN_HAND) == world.getTime() + 40, "should cast once the target is in range");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void customImpactSpellSkipped(TestContext context) {
        ServerWorld world = context.getWorld();
        MobEntity mob = fighter(context, new ItemStack(Items.STICK), 2);
        HeldAbilities abilities = new HeldAbilities(mob);
        abilities.tick(world, spellLoadout("rpg-minibosses:dark_matter"));
        check(abilities.readyAt(Hand.MAIN_HAND) == 0, "custom_impact spell should not cast");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void spellCooldownMultiplier(TestContext context) {
        ServerWorld world = context.getWorld();
        MobEntity mob = fighter(context, new ItemStack(Items.STICK), 4);
        HeldAbilities abilities = new HeldAbilities(mob);
        float multiplier = RPGMinibossesEntities.config.abilityCooldownMultiplier;
        RPGMinibossesEntities.config.abilityCooldownMultiplier = 2;
        try {
            abilities.tick(world, spellLoadout("rpg-minibosses-gametest:self_buff"));
        } finally {
            RPGMinibossesEntities.config.abilityCooldownMultiplier = multiplier;
        }
        check(abilities.readyAt(Hand.MAIN_HAND) == world.getTime() + 80, "multiplier 2 should double 40 to 80, got " + (abilities.readyAt(Hand.MAIN_HAND) - world.getTime()));
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void spellsOffWithHeldAbilitiesOff(TestContext context) {
        ServerWorld world = context.getWorld();
        MobEntity mob = fighter(context, new ItemStack(Items.STICK), 4);
        HeldAbilities abilities = new HeldAbilities(mob);
        boolean enabled = RPGMinibossesEntities.config.heldAbilities;
        RPGMinibossesEntities.config.heldAbilities = false;
        try {
            abilities.tick(world, spellLoadout("rpg-minibosses-gametest:self_buff"));
        } finally {
            RPGMinibossesEntities.config.heldAbilities = enabled;
        }
        check(abilities.readyAt(Hand.MAIN_HAND) == 0, "spell cast with heldAbilities off");
        check(!mob.hasStatusEffect(StatusEffects.ABSORPTION), "spell effect applied with heldAbilities off");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void noTargetNoCast(TestContext context) {
        ServerWorld world = context.getWorld();
        Loadout loadout = spellLoadout("rpg-minibosses-gametest:self_buff");
        MobEntity lonely = fighter(context, new ItemStack(Items.STICK), 4);
        lonely.setTarget(null);
        HeldAbilities abilities = new HeldAbilities(lonely);
        abilities.tick(world, loadout);
        check(abilities.readyAt(Hand.MAIN_HAND) == 0, "cast with no target");
        MobEntity mob = fighter(context, new ItemStack(Items.STICK), 4);
        mob.getTarget().kill();
        abilities = new HeldAbilities(mob);
        abilities.tick(world, loadout);
        check(abilities.readyAt(Hand.MAIN_HAND) == 0, "cast at a dead target");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void unknownItemIsSkipped(TestContext context) {
        Loadout loadout = loadout("{\"mainhand\":[{\"item\":\"nosuchmod:blade\"},{\"item\":\"minecraft:iron_sword\"}]}");
        check(loadout.mainhand().size() == 1, "unknown item should be skipped, got " + loadout.mainhand().size());
        check(loadout.mainhand().get(0).matches(new ItemStack(Items.IRON_SWORD)), "known item should still load");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void armorSlotsHaveNoPool(TestContext context) {
        Loadout loadout = loadout("{\"mainhand\":[{\"item\":\"minecraft:iron_helmet\",\"drop_chance\":1}]}");
        check(loadout.find(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET)) == null, "armor slot should not read the mainhand pool");
        check(loadout.find(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HELMET)) != null, "mainhand should still match");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void cooldownSurvivesSave(TestContext context) {
        MobEntity mob = fighter(context, ItemStack.EMPTY, 3);
        HeldAbilities abilities = new HeldAbilities(mob);
        NbtCompound nbt = new NbtCompound();
        nbt.putLongArray("HeldAbilityReadyAt", new long[]{1234L, 5678L});
        abilities.read(nbt);
        NbtCompound saved = new NbtCompound();
        abilities.write(saved);
        HeldAbilities loaded = new HeldAbilities(mob);
        loaded.read(saved);
        check(loaded.readyAt(Hand.MAIN_HAND) == 1234L && loaded.readyAt(Hand.OFF_HAND) == 5678L, "readyAt lost on save");
        context.complete();
    }

    private static Loadout spellLoadout(String spell) {
        return loadout("{\"mainhand\":[{\"item\":\"minecraft:stick\",\"spells\":[\"" + spell + "\"],\"ability\":{\"chance\":1,\"interval\":1,\"cooldown\":40}}]}");
    }

    private static <T extends MinibossEntity> T boss(TestContext context, EntityType<T> type, double x, double z) {
        T boss = context.spawnEntity(type, new BlockPos(1, 2, 1));
        boss.setAiDisabled(true);
        boss.setNoGravity(true);
        boss.refreshPositionAndAngles(boss.getX() + x, boss.getY(), boss.getZ() + z, 0, 0);
        return boss;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void areaSpellSkipsBosses(TestContext context) {
        ServerWorld world = context.getWorld();
        JuggernautEntity caster = boss(context, RPGMinibossesEntities.JUGGERNAUT_ENTITY_ENTRY.entityType(), 0, 0);
        TemplarEntity templar = boss(context, RPGMinibossesEntities.TEMPLAR_ENTITY_ENTRY.entityType(), 0, 2);
        MobEntity golem = frozen(context, EntityType.IRON_GOLEM, new BlockPos(1, 2, 1));
        golem.refreshPositionAndAngles(caster.getX() + 2, caster.getY(), caster.getZ(), 0, 0);
        context.waitAndRun(2, () -> {
            caster.removeStatusEffect(caster.getIntroEffect().value());
            templar.removeStatusEffect(templar.getIntroEffect().value());
        });
        context.waitAndRun(4, () -> {
            check(caster.notPetrified() && templar.notPetrified(), "bosses still petrified");
            caster.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
            caster.getAttributeInstance(SpellSchools.FIRE.attribute).setBaseValue(20);
            caster.setTarget(golem);
            float templarHealth = templar.getHealth();
            float golemHealth = golem.getHealth();
            HeldAbilities abilities = new HeldAbilities(caster);
            abilities.tick(world, spellLoadout("rpg-minibosses:fire_nova"));
            check(abilities.readyAt(Hand.MAIN_HAND) == world.getTime() + 40, "nova did not cast");
            check(golem.getHealth() < golemHealth, "nova should hit the target");
            check(templar.getHealth() == templarHealth, "nova should not hit another miniboss");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void otherSpellTypesCast(TestContext context) {
        ServerWorld world = context.getWorld();
        for (String spell : new String[]{"meteor_test", "cloud_test", "beam_test"}) {
            MobEntity mob = fighter(context, new ItemStack(Items.STICK), 4);
            HeldAbilities abilities = new HeldAbilities(mob);
            abilities.tick(world, spellLoadout("rpg-minibosses-gametest:" + spell));
            check(abilities.readyAt(Hand.MAIN_HAND) == world.getTime() + 40, spell + " did not cast");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bossSavesCooldowns(TestContext context) {
        ServerWorld world = context.getWorld();
        JuggernautEntity boss = context.spawnEntity(RPGMinibossesEntities.JUGGERNAUT_ENTITY_ENTRY.entityType(), new BlockPos(1, 2, 1));
        NbtCompound cooldowns = new NbtCompound();
        cooldowns.putLongArray("HeldAbilityReadyAt", new long[]{world.getTime() + 500, world.getTime() + 300});
        boss.getHeldAbilities().read(cooldowns);
        NbtCompound saved = boss.writeNbt(new NbtCompound());
        MinibossEntity loaded = RPGMinibossesEntities.JUGGERNAUT_ENTITY_ENTRY.entityType().create(world);
        loaded.readNbt(saved);
        check(loaded.getHeldAbilities().readyAt(Hand.MAIN_HAND) == world.getTime() + 500, "mainhand cooldown lost on save");
        check(loaded.getHeldAbilities().readyAt(Hand.OFF_HAND) == world.getTime() + 300, "offhand cooldown lost on save");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tagEntries(TestContext context) {
        Random random = context.getWorld().getRandom();
        Loadout loadout = loadout("{\"mainhand\":[{\"tag\":\"minecraft:planks\"}],\"offhand\":[{\"tag\":\"rpg-minibosses-gametest:nothing\"}]}");
        LoadoutEntry planks = loadout.mainhand().get(0);
        for (int i = 0; i < 20; i++) {
            check(planks.create(random).isIn(ItemTags.PLANKS), "tag entry made something outside the tag");
        }
        check(planks.matches(new ItemStack(Items.BIRCH_PLANKS)), "tag entry should match any plank");
        check(!planks.matches(new ItemStack(Items.STICK)), "tag entry matched a stick");
        check(loadout.offhand().get(0).create(random).isEmpty(), "empty tag should give nothing");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void weightedRoll(TestContext context) {
        Random random = context.getWorld().getRandom();
        Loadout loadout = loadout("{\"mainhand\":[{\"item\":\"minecraft:iron_sword\",\"weight\":0},{\"item\":\"minecraft:air\",\"weight\":1}],\"offhand\":[{\"item\":\"minecraft:shield\",\"weight\":0}]}");
        for (int i = 0; i < 50; i++) {
            LoadoutEntry entry = loadout.roll(EquipmentSlot.MAINHAND, random);
            check(entry != null && entry.create(random).isEmpty(), "weight 0 entry got rolled");
        }
        check(loadout.roll(EquipmentSlot.OFFHAND, random) == null, "all-zero weights should roll nothing");
        Loadout split = loadout("{\"mainhand\":[{\"item\":\"minecraft:iron_sword\",\"weight\":3},{\"item\":\"minecraft:stick\",\"weight\":1}]}");
        int swords = 0;
        for (int i = 0; i < 400; i++) {
            if (split.roll(EquipmentSlot.MAINHAND, random).create(random).isOf(Items.IRON_SWORD)) {
                swords++;
            }
        }
        check(swords > 240 && swords < 360, "3:1 weights gave " + swords + "/400 swords");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void entryNbtApplies(TestContext context) {
        Loadout loadout = loadout("{\"mainhand\":[{\"item\":\"minecraft:iron_sword\",\"nbt\":\"{Custom:7,Damage:5}\"}]}");
        ItemStack stack = loadout.mainhand().get(0).create(context.getWorld().getRandom());
        check(stack.getNbt() != null && stack.getNbt().getInt("Custom") == 7, "custom nbt missing");
        check(stack.getDamage() == 5, "damage nbt not applied, got " + stack.getDamage());
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void droppedSwordIsDormant(TestContext context) {
        TemplarEntity templar = context.spawnEntity(RPGMinibossesEntities.TEMPLAR_ENTITY_ENTRY.entityType(), new BlockPos(1, 2, 1));
        templar.setNoGravity(true);
        context.waitAndRun(2, () -> {
            ItemStack stack = new ItemStack(Registries.ITEM.get(new Identifier("simplyswords", "shadowsting")));
            AwakeningApi.setLevel(stack, 8);
            templar.equipStack(EquipmentSlot.MAINHAND, stack);
            float chance = RPGMinibossesEntities.config.equipmentDropChance;
            RPGMinibossesEntities.config.equipmentDropChance = 2.0F;
            try {
                templar.kill();
            } finally {
                RPGMinibossesEntities.config.equipmentDropChance = chance;
            }
            ItemEntity dropped = context.getWorld().getEntitiesByClass(ItemEntity.class, templar.getBoundingBox().expand(4), e -> e.getStack().isOf(stack.getItem()))
                    .stream().findFirst().orElseThrow(() -> new GameTestException("sword was not dropped"));
            check(AwakeningApi.getLevel(dropped.getStack()) == 0, "dropped sword awakening " + AwakeningApi.getLevel(dropped.getStack()));
            context.complete();
        });
    }

    private static ItemStack unique(String id) {
        ItemStack stack = new ItemStack(Registries.ITEM.get(new Identifier("simplyswords", id)));
        AwakeningApi.setLevel(stack, 8);
        return stack;
    }

    private static void parryCase(TestContext context, boolean heldAbilities) {
        ServerWorld world = context.getWorld();
        JuggernautEntity boss = context.spawnEntity(RPGMinibossesEntities.JUGGERNAUT_ENTITY_ENTRY.entityType(), new BlockPos(1, 2, 1));
        boss.setAiDisabled(true);
        boss.setNoGravity(true);
        MobEntity golem = frozen(context, EntityType.IRON_GOLEM, new BlockPos(3, 2, 1));
        context.waitAndRun(2, () -> boss.removeStatusEffect(boss.getIntroEffect().value()));
        context.waitAndRun(4, () -> {
            check(boss.notPetrified(), "boss still petrified");
            boss.equipStack(EquipmentSlot.MAINHAND, unique("stormbringer"));
            boss.setTarget(golem);
            boolean enabled = RPGMinibossesEntities.config.heldAbilities;
            RPGMinibossesEntities.config.heldAbilities = heldAbilities;
            try {
                float health = boss.getHealth();
                boolean hit = boss.damage(world.getDamageSources().mobAttack(golem), 4);
                if (heldAbilities) {
                    check(!hit && boss.getHealth() == health, "first hit should be parried");
                    check(boss.getHeldAbilities().readyAt(Hand.MAIN_HAND) > world.getTime(), "parry should put the ability on cooldown");
                    check(boss.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.RESISTANCE), "parry should grant resistance");
                    boss.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.RESISTANCE);
                    check(boss.damage(world.getDamageSources().mobAttack(golem), 4) && boss.getHealth() < health, "second hit should land during cooldown");
                } else {
                    check(hit && boss.getHealth() < health, "hit should land with heldAbilities off");
                    check(boss.getHeldAbilities().readyAt(Hand.MAIN_HAND) == 0, "nothing should go on cooldown with heldAbilities off");
                }
            } finally {
                RPGMinibossesEntities.config.heldAbilities = enabled;
            }
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stormbringerParries(TestContext context) {
        parryCase(context, true);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stormbringerParryRespectsToggle(TestContext context) {
        parryCase(context, false);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void disabledEntryDoesNotParry(TestContext context) {
        ServerWorld world = context.getWorld();
        Loadout off = loadout("{\"mainhand\":[{\"item\":\"simplyswords:stormbringer\",\"ability\":{\"enabled\":false}}]}");
        Loadout on = loadout("{\"mainhand\":[{\"item\":\"simplyswords:stormbringer\"}]}");
        MobEntity mob = fighter(context, unique("stormbringer"), 2);
        HeldAbilities abilities = new HeldAbilities(mob);
        check(!abilities.openParry(world, off), "disabled entry should not open the parry");
        check(WeaponAbilityCooldownManager.isCoolingDown(world, mob, mob.getMainHandStack()), "disabled entry should keep the sword locked");
        check(abilities.openParry(world, on), "enabled entry should open the parry");
        check(!WeaponAbilityCooldownManager.isCoolingDown(world, mob, mob.getMainHandStack()), "open parry should unlock the sword");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void offhandAbilityFires(TestContext context) {
        ServerWorld world = context.getWorld();
        Loadout loadout = loadout("{\"offhand\":[{\"item\":\"simplyswords:shadowsting\",\"ability\":{\"cooldown\":80,\"chance\":1,\"interval\":1}}]}");
        MobEntity mob = fighter(context, ItemStack.EMPTY, 3);
        mob.equipStack(EquipmentSlot.OFFHAND, loadout.offhand().get(0).create(world.getRandom()));
        HeldAbilities abilities = new HeldAbilities(mob);
        abilities.tick(world, loadout);
        check(abilities.readyAt(Hand.OFF_HAND) == world.getTime() + 80, "offhand readyAt " + abilities.readyAt(Hand.OFF_HAND));
        check(abilities.readyAt(Hand.MAIN_HAND) == 0, "empty mainhand should not go on cooldown");
        check(WeaponAbilityCooldownManager.isCoolingDown(world, mob, mob.getOffHandStack()), "offhand should be locked again after firing");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bothHandsKeepSeparateTimers(TestContext context) {
        ServerWorld world = context.getWorld();
        Loadout loadout = loadout("""
                {"mainhand": [{"item": "simplyswords:shadowsting", "ability": {"cooldown": 100, "chance": 1, "interval": 1}}],
                 "offhand": [{"item": "minecraft:stick", "spells": ["rpg-minibosses:fireball"], "ability": {"cooldown": 30, "chance": 1, "interval": 1}}]}""");
        MobEntity mob = fighter(context, loadout.mainhand().get(0).create(world.getRandom()), 4);
        mob.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.STICK));
        HeldAbilities abilities = new HeldAbilities(mob);
        abilities.tick(world, loadout);
        long now = world.getTime();
        check(abilities.readyAt(Hand.MAIN_HAND) == now + 100, "mainhand readyAt " + abilities.readyAt(Hand.MAIN_HAND));
        check(abilities.readyAt(Hand.OFF_HAND) == now + 30, "offhand readyAt " + abilities.readyAt(Hand.OFF_HAND));
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void mercenaryOnlyTakesRangedMainhand(TestContext context) {
        ArtilleristEntity mercenary = boss(context, RPGMinibossesEntities.ARTILLERIST_ENTITY_ENTRY.entityType(), 0, 0);
        check(mercenary.canHold(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW)), "bow should be allowed");
        check(mercenary.canHold(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW)), "crossbow should be allowed");
        check(!mercenary.canHold(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD)), "sword should be refused");
        check(!mercenary.canHold(EquipmentSlot.MAINHAND, ItemStack.EMPTY), "empty hand should be refused");
        check(mercenary.canHold(EquipmentSlot.OFFHAND, new ItemStack(Items.DIAMOND_SWORD)), "offhand is not limited");
        context.complete();
    }

    private static PersistentProjectileEntity mercenaryShot(TestContext context, ItemStack weapon) {
        ArtilleristEntity mercenary = boss(context, RPGMinibossesEntities.ARTILLERIST_ENTITY_ENTRY.entityType(), 0, 0);
        MobEntity target = frozen(context, EntityType.IRON_GOLEM, new BlockPos(1, 2, 1));
        target.refreshPositionAndAngles(mercenary.getX() + 8, mercenary.getY(), mercenary.getZ(), 0, 0);
        mercenary.equipStack(EquipmentSlot.MAINHAND, weapon);
        mercenary.setTarget(target);
        mercenary.shoot(target, 1.6F);
        List<PersistentProjectileEntity> arrows = context.getWorld().getEntitiesByClass(PersistentProjectileEntity.class, mercenary.getBoundingBox().expand(4), arrow -> arrow.getOwner() == mercenary);
        check(arrows.size() == 1, "expected one arrow, got " + arrows.size());
        return arrows.get(0);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void mercenaryBowUsesEnchantments(TestContext context) {
        ItemStack bow = new ItemStack(Items.BOW);
        bow.addEnchantment(Enchantments.POWER, 5);
        bow.addEnchantment(Enchantments.FLAME, 1);
        PersistentProjectileEntity arrow = mercenaryShot(context, bow);
        check(Math.abs(arrow.getDamage() - 5.0) < 0.001, "power 5 arrow damage " + arrow.getDamage());
        check(arrow.isOnFire(), "flame bow arrow should burn");
        check(!arrow.isShotFromCrossbow(), "bow arrow should not count as crossbow");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void mercenaryCrossbowPierces(TestContext context) {
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        crossbow.addEnchantment(Enchantments.PIERCING, 3);
        PersistentProjectileEntity arrow = mercenaryShot(context, crossbow);
        check(arrow.isShotFromCrossbow(), "crossbow arrow should count as crossbow");
        check(arrow.getPierceLevel() == 3, "pierce level " + arrow.getPierceLevel());
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void spellPoolEntryCasts(TestContext context) {
        ServerWorld world = context.getWorld();
        MobEntity mob = fighter(context, new ItemStack(Items.STICK), 4);
        HeldAbilities abilities = new HeldAbilities(mob);
        abilities.tick(world, loadout("{\"mainhand\":[{\"item\":\"minecraft:stick\",\"spell_pool\":\"rpg-minibosses-gametest:test\",\"ability\":{\"chance\":1,\"interval\":1,\"cooldown\":40}}]}"));
        check(abilities.readyAt(Hand.MAIN_HAND) == world.getTime() + 40, "pool spell did not cast");
        check(mob.hasStatusEffect(StatusEffects.ABSORPTION), "pool spell had no effect");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bookFallsBackToPool(TestContext context) {
        ServerWorld world = context.getWorld();
        ItemStack book = new ItemStack(Items.BOOK);
        SpellContainerHelper.addContainerToItemStack(new SpellContainer(false, "rpg-minibosses-gametest:test", 1, List.of()), book);
        MobEntity mob = fighter(context, book, 4);
        HeldAbilities abilities = new HeldAbilities(mob);
        abilities.tick(world, loadout("{\"mainhand\":[{\"item\":\"minecraft:book\",\"ability\":{\"chance\":1,\"interval\":1,\"cooldown\":40}}]}"));
        check(abilities.readyAt(Hand.MAIN_HAND) == world.getTime() + 40, "book pool spell did not cast");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void emptyTagEntrySkipped(TestContext context) {
        Random random = context.getWorld().getRandom();
        Loadout loadout = loadout("{\"mainhand\":[{\"tag\":\"rpg-minibosses-gametest:nothing\",\"weight\":100},{\"item\":\"minecraft:stick\"}]}");
        for (int i = 0; i < 30; i++) {
            check(loadout.roll(EquipmentSlot.MAINHAND, random).create(random).isOf(Items.STICK), "rolled the empty tag");
        }
        context.complete();
    }

    private static void spellArrows(TestContext context, String spell, int at, int expected, Consumer<PersistentProjectileEntity> first) {
        ServerWorld world = context.getWorld();
        ArtilleristEntity mercenary = boss(context, RPGMinibossesEntities.ARTILLERIST_ENTITY_ENTRY.entityType(), 0, 0);
        MobEntity target = frozen(context, EntityType.IRON_GOLEM, new BlockPos(1, 2, 1));
        target.refreshPositionAndAngles(mercenary.getX() + 24, mercenary.getY(), mercenary.getZ(), 0, 0);
        mercenary.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        mercenary.setTarget(target);
        HeldAbilities abilities = new HeldAbilities(mercenary);
        abilities.tick(world, loadout("{\"mainhand\":[{\"item\":\"minecraft:crossbow\",\"spells\":[\"" + spell + "\"],\"ability\":{\"chance\":1,\"interval\":1,\"cooldown\":40,\"max_range\":32}}]}"));
        check(abilities.readyAt(Hand.MAIN_HAND) == world.getTime() + 40, spell + " did not cast");
        Box area = mercenary.getBoundingBox().expand(32);
        check(world.getEntitiesByClass(PersistentProjectileEntity.class, area, arrow -> arrow.getOwner() == mercenary).isEmpty(), "arrow fired before the shoot_heavy windup");
        context.runAtTick(MERCENARY_WINDUP + 2, () -> {
            List<PersistentProjectileEntity> arrows = world.getEntitiesByClass(PersistentProjectileEntity.class, area, arrow -> arrow.getOwner() == mercenary);
            check(arrows.size() == 1, "expected one arrow after the windup, got " + arrows.size());
            check(new Identifier(spell).equals(((ArrowExtension) arrows.get(0)).getCarriedSpellId()), "arrow carries no spell");
            first.accept(arrows.get(0));
        });
        context.runAtTick(MERCENARY_WINDUP + at, () -> {
            int count = world.getEntitiesByClass(PersistentProjectileEntity.class, area, arrow -> arrow.getOwner() == mercenary).size();
            check(count == expected, "expected " + expected + " arrows later, got " + count);
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void mercenaryShootsArrowSpell(TestContext context) {
        spellArrows(context, "rpg-minibosses-gametest:arrow_test", 6, 2, arrow -> {
            check(Math.abs(arrow.getDamage() - 4.0) < 0.001, "arrow damage " + arrow.getDamage());
            check(arrow.isCritical(), "shoot_arrow arrow should crit");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void mercenaryShootsItemUseSpell(TestContext context) {
        spellArrows(context, "rpg-minibosses-gametest:item_use_arrow_test", 6, 1,
                arrow -> check(Math.abs(arrow.getDamage() - 6.0) < 0.001, "arrow perk damage " + arrow.getDamage()));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void arrowSpellAppliesOnHit(TestContext context) {
        ServerWorld world = context.getWorld();
        ArtilleristEntity mercenary = boss(context, RPGMinibossesEntities.ARTILLERIST_ENTITY_ENTRY.entityType(), 0, 0);
        MobEntity target = frozen(context, EntityType.IRON_GOLEM, new BlockPos(1, 2, 1));
        target.refreshPositionAndAngles(mercenary.getX() + 4, mercenary.getY(), mercenary.getZ(), 0, 0);
        mercenary.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        mercenary.setTarget(target);
        new HeldAbilities(mercenary).tick(world, loadout("{\"mainhand\":[{\"item\":\"minecraft:crossbow\",\"spells\":[\"rpg-minibosses-gametest:item_use_arrow_test\"],\"ability\":{\"chance\":1,\"interval\":1,\"cooldown\":40}}]}"));
        context.addInstantFinalTask(() -> check(target.hasStatusEffect(StatusEffects.SLOWNESS), "arrow hit did not apply the spell"));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void arrowSpellNeedsMercenary(TestContext context) {
        ServerWorld world = context.getWorld();
        MobEntity mob = fighter(context, new ItemStack(Items.STICK), 4);
        HeldAbilities abilities = new HeldAbilities(mob);
        abilities.tick(world, spellLoadout("rpg-minibosses-gametest:arrow_test"));
        check(abilities.readyAt(Hand.MAIN_HAND) == 0, "non mercenary shot an arrow spell");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void healSpellSkipped(TestContext context) {
        ServerWorld world = context.getWorld();
        MobEntity mob = fighter(context, new ItemStack(Items.STICK), 4);
        HeldAbilities abilities = new HeldAbilities(mob);
        abilities.tick(world, spellLoadout("rpg-minibosses-gametest:heal_test"));
        check(abilities.readyAt(Hand.MAIN_HAND) == 0, "boss cast a heal");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void extraArrowsStopWhenMercenaryDies(TestContext context) {
        spellArrows(context, "rpg-minibosses-gametest:arrow_test", 6, 1, arrow -> ((ArtilleristEntity) arrow.getOwner()).setHealth(0));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void mercenaryCrossbowShootsGivenProjectile(TestContext context) {
        ServerWorld world = context.getWorld();
        ArtilleristEntity mercenary = boss(context, RPGMinibossesEntities.ARTILLERIST_ENTITY_ENTRY.entityType(), 0, 0);
        MobEntity target = frozen(context, EntityType.IRON_GOLEM, new BlockPos(1, 2, 1));
        target.refreshPositionAndAngles(mercenary.getX() + 8, mercenary.getY(), mercenary.getZ(), 0, 0);
        ArrowEntity arrow = new ArrowEntity(world, mercenary);
        mercenary.shoot(target, new ItemStack(Items.CROSSBOW), arrow, 0);
        check(arrow.getVelocity().length() > 1.2, "given arrow speed " + arrow.getVelocity().length());
        int spawned = world.getEntitiesByClass(PersistentProjectileEntity.class, mercenary.getBoundingBox().expand(8), a -> a.getOwner() == mercenary).size();
        check(spawned == 0, "override spawned its own arrow, got " + spawned);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void deadMercenaryDoesNotShoot(TestContext context) {
        ArtilleristEntity mercenary = boss(context, RPGMinibossesEntities.ARTILLERIST_ENTITY_ENTRY.entityType(), 0, 0);
        MobEntity target = frozen(context, EntityType.IRON_GOLEM, new BlockPos(1, 2, 1));
        target.refreshPositionAndAngles(mercenary.getX() + 8, mercenary.getY(), mercenary.getZ(), 0, 0);
        mercenary.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        mercenary.setTarget(target);
        mercenary.setHealth(0);
        mercenary.shoot(target, 1.6F);
        int spawned = context.getWorld().getEntitiesByClass(PersistentProjectileEntity.class, mercenary.getBoundingBox().expand(8), a -> a.getOwner() == mercenary).size();
        check(spawned == 0, "dead mercenary shot " + spawned + " arrows");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void itemUseSpellWithoutPerksSkipped(TestContext context) {
        ServerWorld world = context.getWorld();
        ArtilleristEntity mercenary = boss(context, RPGMinibossesEntities.ARTILLERIST_ENTITY_ENTRY.entityType(), 0, 0);
        MobEntity target = frozen(context, EntityType.IRON_GOLEM, new BlockPos(1, 2, 1));
        target.refreshPositionAndAngles(mercenary.getX() + 4, mercenary.getY(), mercenary.getZ(), 0, 0);
        mercenary.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        mercenary.setTarget(target);
        HeldAbilities abilities = new HeldAbilities(mercenary);
        abilities.tick(world, loadout("{\"mainhand\":[{\"item\":\"minecraft:crossbow\",\"spells\":[\"rpg-minibosses-gametest:item_use_buff_test\"],\"ability\":{\"chance\":1,\"interval\":1,\"cooldown\":40}}]}"));
        check(abilities.readyAt(Hand.MAIN_HAND) == 0, "item use spell with no arrow perks got cast");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void arrowSpellNeedsLineOfSight(TestContext context) {
        ServerWorld world = context.getWorld();
        ArtilleristEntity mercenary = boss(context, RPGMinibossesEntities.ARTILLERIST_ENTITY_ENTRY.entityType(), 0, 0);
        MobEntity target = frozen(context, EntityType.IRON_GOLEM, new BlockPos(1, 2, 1));
        target.refreshPositionAndAngles(mercenary.getX() + 5, mercenary.getY(), mercenary.getZ(), 0, 0);
        for (int y = 1; y <= 6; y++) {
            for (int z = 0; z <= 2; z++) {
                context.setBlockState(new BlockPos(3, y, z), Blocks.STONE);
            }
        }
        mercenary.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        mercenary.setTarget(target);
        HeldAbilities abilities = new HeldAbilities(mercenary);
        abilities.tick(world, loadout("{\"mainhand\":[{\"item\":\"minecraft:crossbow\",\"spells\":[\"rpg-minibosses-gametest:arrow_test\"],\"ability\":{\"chance\":1,\"interval\":1,\"cooldown\":40}}]}"));
        check(abilities.readyAt(Hand.MAIN_HAND) == 0, "shot an arrow spell through a wall");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void spellPoolList(TestContext context) {
        ServerWorld world = context.getWorld();
        Loadout loadout = loadout("{\"mainhand\":[{\"item\":\"minecraft:stick\",\"spell_pool\":[\"rpg-minibosses-gametest:heals\",\"rpg-minibosses-gametest:test\"],\"ability\":{\"chance\":1,\"interval\":1,\"cooldown\":40}}]}");
        check(loadout.mainhand().get(0).spellPools().size() == 2, "spell_pool list not parsed");
        MobEntity mob = fighter(context, new ItemStack(Items.STICK), 4);
        HeldAbilities abilities = new HeldAbilities(mob);
        abilities.tick(world, loadout);
        check(abilities.readyAt(Hand.MAIN_HAND) == world.getTime() + 40, "second pool in the list was not read");
        check(mob.hasStatusEffect(StatusEffects.ABSORPTION), "pool spell had no effect");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shippedSpellEntriesReachFar(TestContext context) {
        for (EntityType<?> type : List.of(RPGMinibossesEntities.ARTILLERIST_ENTITY_ENTRY.entityType(), RPGMinibossesEntities.M_ARTILLERIST_ENTITY_ENTRY.entityType(),
                RPGMinibossesEntities.ARCHMAGE_FIRE_ENTITY_ENTRY.entityType(), RPGMinibossesEntities.M_ARCHMAGE_FIRE_ENTITY_ENTRY.entityType())) {
            Loadout loadout = Loadouts.get(type);
            check(loadout != null, type + " loadout missing");
            for (LoadoutEntry entry : loadout.mainhand()) {
                if (!entry.spellPools().isEmpty()) {
                    check(entry.ability().maxRange() == 24, type + " entry " + entry.spellPools() + " max_range " + entry.ability().maxRange());
                }
            }
        }
        context.complete();
    }

    private static Spell animated(String release, String cast) {
        Spell spell = new Spell();
        spell.release = new Spell.Release();
        spell.release.animation = release;
        spell.cast = new Spell.Cast();
        spell.cast.animation = cast;
        return spell;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void spellMotionFromAnimation(TestContext context) {
        check(SpellMotion.of(animated("death_knights:obliterate", null)) == SpellMotion.STRIKE, "obliterate");
        check(SpellMotion.of(animated("death_knights:strike_2h", null)) == SpellMotion.STRIKE, "strike_2h");
        check(SpellMotion.of(animated("spellbladenext:flameslash", null)) == SpellMotion.STRIKE, "flameslash");
        check(SpellMotion.of(animated("spell_engine:one_handed_projectile_release", null)) == SpellMotion.CAST, "projectile release");
        check(SpellMotion.of(animated("spell_engine:one_handed_area_release", null)) == SpellMotion.AREA, "area release");
        check(SpellMotion.of(animated("spell_engine:dual_handed_ground_release", null)) == SpellMotion.AREA, "ground release");
        check(SpellMotion.of(animated(null, "spell_engine:two_handed_channeling")) == SpellMotion.CHANNEL, "channel from cast");
        check(SpellMotion.of(animated("spell_engine:archery_release", null)) == SpellMotion.ARCHERY, "archery");
        check(SpellMotion.of(animated("spell_engine:shout", null)) == SpellMotion.NONE, "shout should not animate");
        check(SpellMotion.of(animated("spell_engine:two_handed_spin_static", null)) == SpellMotion.NONE, "whirlwind should not animate");
        check(SpellMotion.of(animated(null, null)) == SpellMotion.NONE, "no animation");
        context.complete();
    }

    private static ArchmageFireEntity archmage(TestContext context) {
        ArchmageFireEntity archmage = boss(context, RPGMinibossesEntities.ARCHMAGE_FIRE_ENTITY_ENTRY.entityType(), 0, 0);
        MobEntity target = frozen(context, EntityType.IRON_GOLEM, new BlockPos(1, 2, 1));
        target.refreshPositionAndAngles(archmage.getX() + 4, archmage.getY(), archmage.getZ(), 0, 0);
        archmage.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
        archmage.setTarget(target);
        return archmage;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void busyBossSkipsSpell(TestContext context) {
        ServerWorld world = context.getWorld();
        ArchmageFireEntity archmage = archmage(context);
        archmage.spellAnimation(SpellMotion.AREA);
        HeldAbilities abilities = new HeldAbilities(archmage);
        abilities.tick(world, spellLoadout("rpg-minibosses-gametest:self_buff"));
        check(abilities.readyAt(Hand.MAIN_HAND) == 0, "cast while mid wave");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void areaSpellWaitsForWave(TestContext context) {
        ServerWorld world = context.getWorld();
        ArchmageFireEntity archmage = archmage(context);
        HeldAbilities abilities = new HeldAbilities(archmage);
        abilities.tick(world, spellLoadout("rpg-minibosses-gametest:area_buff"));
        check(abilities.readyAt(Hand.MAIN_HAND) == world.getTime() + 40, "area spell did not cast");
        check(!archmage.hasStatusEffect(StatusEffects.ABSORPTION), "spell landed before the wave hit");
        check(archmage.busy(), "boss not busy during the wave");
        context.runAtTick(22, () -> {
            check(archmage.hasStatusEffect(StatusEffects.ABSORPTION), "spell never landed after the wave");
            check(!archmage.busy(), "boss still busy after the wave");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void deadBossDropsWindup(TestContext context) {
        ServerWorld world = context.getWorld();
        ArchmageFireEntity archmage = archmage(context);
        HeldAbilities abilities = new HeldAbilities(archmage);
        abilities.tick(world, spellLoadout("rpg-minibosses-gametest:area_buff"));
        context.runAtTick(5, () -> archmage.setHealth(0));
        context.runAtTick(22, () -> {
            check(!archmage.hasStatusEffect(StatusEffects.ABSORPTION), "spell released after the boss died");
            context.complete();
        });
    }
}

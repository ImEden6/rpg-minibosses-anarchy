package com.cleannrooster.rpg_minibosses.test;

import com.cleannrooster.rpg_minibosses.entity.JuggernautEntity;
import com.cleannrooster.rpg_minibosses.entity.RPGMinibossesEntities;
import com.cleannrooster.rpg_minibosses.entity.TemplarEntity;
import com.cleannrooster.rpg_minibosses.loadout.HeldAbilities;
import com.cleannrooster.rpg_minibosses.loadout.Loadout;
import com.cleannrooster.rpg_minibosses.loadout.LoadoutEntry;
import com.cleannrooster.rpg_minibosses.loadout.Loadouts;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.sweenus.simplyswords.api.AwakeningApi;
import net.sweenus.simplyswords.world.WeaponAbilityCooldownManager;

public class LoadoutTests implements FabricGameTest {

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
        LoadoutEntry entry = templar.roll(EquipmentSlot.MAINHAND, context.getWorld().getRandom());
        check(entry != null && entry.create(context.getWorld().getRandom()).isOf(Items.DIAMOND_SWORD), "templar should roll a diamond sword");
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
            check(templar.getMainHandStack().isOf(Items.DIAMOND_SWORD), "templar holds " + templar.getMainHandStack());
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
}

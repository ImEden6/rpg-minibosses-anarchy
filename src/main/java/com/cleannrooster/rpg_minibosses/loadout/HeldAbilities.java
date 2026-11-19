package com.cleannrooster.rpg_minibosses.loadout;

import com.cleannrooster.rpg_minibosses.entity.MinibossEntity;
import com.cleannrooster.rpg_minibosses.entity.RPGMinibossesEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellContainer;
import net.spell_engine.api.spell.SpellInfo;
import net.spell_engine.internals.SpellContainerHelper;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellRegistry;
import net.spell_engine.particle.ParticleHelper;
import net.spell_engine.utils.SoundHelper;
import net.spell_engine.utils.TargetHelper;
import net.spell_power.api.SpellPower;
import net.sweenus.simplyswords.api.SimplySwordsAPI;
import net.sweenus.simplyswords.api.WeaponAbilityActivationSource;
import net.sweenus.simplyswords.api.WeaponAbilityContext;
import net.sweenus.simplyswords.item.interfaces.UniqueWeaponActiveAbility;
import net.sweenus.simplyswords.world.WeaponAbilityCooldownManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HeldAbilities {
    private final MobEntity mob;
    private final long[] readyAt = new long[2];

    public HeldAbilities(MobEntity mob) {
        this.mob = mob;
    }

    public void tick(ServerWorld world, @Nullable Loadout loadout) {
        for (Hand hand : Hand.values()) {
            ItemStack stack = mob.getStackInHand(hand);
            if (isSword(stack)) {
                WeaponAbilityCooldownManager.setCooldown(world, mob, stack, 2);
            }
        }
        LivingEntity target = mob.getTarget();
        if (!RPGMinibossesEntities.config.heldAbilities || target == null || !target.isAlive()) {
            return;
        }
        for (Hand hand : Hand.values()) {
            ItemStack stack = mob.getStackInHand(hand);
            if (stack.isEmpty() || readyAt[hand.ordinal()] > world.getTime()) {
                continue;
            }
            LoadoutEntry entry = loadout == null ? null : loadout.find(slot(hand), stack);
            AbilitySettings settings = settings(entry);
            if (!settings.enabled() || mob.age % settings.interval() != 0) {
                continue;
            }
            double distance = mob.distanceTo(target);
            if (distance < settings.minRange() || distance > settings.maxRange()
                    || mob.getHealth() / mob.getMaxHealth() > settings.belowHealth()
                    || mob.getRandom().nextFloat() >= settings.chance()) {
                continue;
            }
            int cooldown = isSword(stack) ? useSword(world, stack, hand, target, settings) : castSpell(world, stack, target, entry, settings);
            if (cooldown >= 0) {
                readyAt[hand.ordinal()] = readyTime(world, cooldown);
            }
        }
    }

    public long readyAt(Hand hand) {
        return readyAt[hand.ordinal()];
    }

    public boolean openParry(ServerWorld world, @Nullable Loadout loadout) {
        ItemStack stack = mob.getMainHandStack();
        if (!isSword(stack)) {
            return false;
        }
        AbilitySettings settings = settings(loadout == null ? null : loadout.find(EquipmentSlot.MAINHAND, stack));
        if (!RPGMinibossesEntities.config.heldAbilities || !settings.enabled() || readyAt[0] > world.getTime()) {
            WeaponAbilityCooldownManager.setCooldown(world, mob, stack, 2);
            return false;
        }
        WeaponAbilityCooldownManager.clearCooldown(mob, stack);
        return true;
    }

    public void closeParry(ServerWorld world, @Nullable Loadout loadout) {
        ItemStack stack = mob.getMainHandStack();
        if (WeaponAbilityCooldownManager.isCoolingDown(world, mob, stack)) {
            AbilitySettings settings = settings(loadout == null ? null : loadout.find(EquipmentSlot.MAINHAND, stack));
            readyAt[0] = readyTime(world, swordCooldown(context(world, stack, Hand.MAIN_HAND, mob.getTarget()), settings));
        }
        WeaponAbilityCooldownManager.setCooldown(world, mob, stack, 2);
    }

    private int useSword(ServerWorld world, ItemStack stack, Hand hand, LivingEntity target, AbilitySettings settings) {
        WeaponAbilityContext context = context(world, stack, hand, target);
        WeaponAbilityCooldownManager.clearCooldown(mob, stack);
        int cooldown = swordCooldown(context, settings);
        boolean used = SimplySwordsAPI.tryActivateWeaponAbility(context);
        WeaponAbilityCooldownManager.setCooldown(world, mob, stack, 2);
        return used ? cooldown : -1;
    }

    private int castSpell(ServerWorld world, ItemStack stack, LivingEntity target, @Nullable LoadoutEntry entry, AbilitySettings settings) {
        List<String> ids = entry != null && !entry.spells().isEmpty()
                ? entry.spells().stream().map(Identifier::toString).toList()
                : spellsOf(stack);
        if (ids.isEmpty()) {
            return -1;
        }
        Identifier id = new Identifier(ids.get(mob.getRandom().nextInt(ids.size())));
        Spell spell = SpellRegistry.getSpell(id);
        if (spell == null || spell.release == null || spell.release.custom_impact) {
            return -1;
        }
        Spell.Release.Target.Type type = spell.release.target.type;
        if (type != Spell.Release.Target.Type.SELF && type != Spell.Release.Target.Type.CLOUD && mob.distanceTo(target) > spell.range) {
            return -1;
        }
        SpellInfo info = new SpellInfo(spell, id);
        SpellHelper.ImpactContext context = new SpellHelper.ImpactContext()
                .power(SpellPower.getSpellPower(spell.school, mob))
                .position(mob.getPos())
                .target(SpellHelper.impactTargetingMode(spell));
        mob.getLookControl().lookAt(target);
        switch (type) {
            case PROJECTILE -> SpellHelper.shootProjectile(world, mob, target, info, context);
            case METEOR -> SpellHelper.fallProjectile(world, mob, target, info, context);
            case CLOUD -> SpellHelper.placeCloud(world, mob, info, context);
            case SELF -> SpellHelper.performImpacts(world, mob, mob, mob, info, context);
            case CURSOR, BEAM -> SpellHelper.performImpacts(world, mob, target, mob, info, context);
            case AREA -> {
                for (Entity hit : TargetHelper.targetsFromArea(mob, spell.range, spell.release.target.area, e -> e instanceof LivingEntity && !(e instanceof MinibossEntity) && !mob.isTeammate(e))) {
                    SpellHelper.performImpacts(world, mob, hit, mob, info, context.position(mob.getPos().add(0, mob.getHeight() / 2, 0)));
                }
            }
            default -> {
                return -1;
            }
        }
        ParticleHelper.sendBatches(mob, spell.release.particles);
        SoundHelper.playSound(world, mob, spell.release.sound);
        if (settings.cooldown() != null) {
            return settings.cooldown();
        }
        return Math.max(20, Math.round(SpellHelper.getCooldownDuration(mob, spell, stack) * 20));
    }

    public void write(NbtCompound nbt) {
        nbt.putLongArray("HeldAbilityReadyAt", readyAt);
    }

    public void read(NbtCompound nbt) {
        long[] saved = nbt.getLongArray("HeldAbilityReadyAt");
        System.arraycopy(saved, 0, readyAt, 0, Math.min(saved.length, readyAt.length));
    }

    private static AbilitySettings settings(@Nullable LoadoutEntry entry) {
        return entry == null ? AbilitySettings.DEFAULT : entry.ability();
    }

    private static int swordCooldown(WeaponAbilityContext context, AbilitySettings settings) {
        return settings.cooldown() != null ? settings.cooldown() : SimplySwordsAPI.getWeaponAbilityCooldownTicks(context);
    }

    private static long readyTime(ServerWorld world, int ticks) {
        return world.getTime() + Math.round(ticks * RPGMinibossesEntities.config.abilityCooldownMultiplier);
    }

    private List<String> spellsOf(ItemStack stack) {
        SpellContainer container = SpellContainerHelper.containerFromItemStack(stack);
        return container == null || container.spell_ids == null ? List.of() : container.spell_ids;
    }

    private WeaponAbilityContext context(ServerWorld world, ItemStack stack, Hand hand, @Nullable LivingEntity target) {
        return WeaponAbilityContext.of(world, stack, mob, null, target, hand, WeaponAbilityActivationSource.MOB);
    }

    private static boolean isSword(ItemStack stack) {
        return stack.getItem() instanceof UniqueWeaponActiveAbility;
    }

    private static EquipmentSlot slot(Hand hand) {
        return hand == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }
}

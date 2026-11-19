package com.cleannrooster.rpg_minibosses.loadout;

import com.cleannrooster.rpg_minibosses.RPGMinibosses;
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
import net.spell_engine.internals.WorldScheduler;
import net.spell_engine.particle.ParticleHelper;
import net.spell_engine.utils.SoundHelper;
import net.spell_engine.utils.TargetHelper;
import net.spell_power.api.SpellPower;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class HeldAbilities {
    private static final Set<Identifier> MISSING_SPELLS = new HashSet<>();
    private final MobEntity mob;
    private final long[] readyAt = new long[2];
    private final int[] rolled = {-1, -1};

    public HeldAbilities(MobEntity mob) {
        this.mob = mob;
    }

    public void tick(ServerWorld world, @Nullable Loadout loadout) {
        for (Hand hand : Hand.values()) {
            ItemStack stack = mob.getStackInHand(hand);
            if (isSword(stack)) {
                SimplySwords.hold(world, mob, stack);
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
            LoadoutEntry entry = entry(loadout, hand);
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

    public void rolled(Hand hand, int index) {
        rolled[hand.ordinal()] = index;
    }

    @Nullable
    public LoadoutEntry entry(@Nullable Loadout loadout, Hand hand) {
        return loadout == null ? null : loadout.find(slot(hand), mob.getStackInHand(hand), rolled[hand.ordinal()]);
    }

    public boolean openParry(ServerWorld world, @Nullable Loadout loadout) {
        ItemStack stack = mob.getMainHandStack();
        if (!isSword(stack)) {
            return false;
        }
        AbilitySettings settings = settings(entry(loadout, Hand.MAIN_HAND));
        if (!RPGMinibossesEntities.config.heldAbilities || !settings.enabled() || readyAt[0] > world.getTime()) {
            SimplySwords.hold(world, mob, stack);
            return false;
        }
        SimplySwords.release(mob, stack);
        return true;
    }

    public void closeParry(ServerWorld world, @Nullable Loadout loadout) {
        ItemStack stack = mob.getMainHandStack();
        if (!isSword(stack)) {
            return;
        }
        if (SimplySwords.coolingDown(world, mob, stack)) {
            AbilitySettings settings = settings(entry(loadout, Hand.MAIN_HAND));
            readyAt[0] = readyTime(world, swordCooldown(world, stack, Hand.MAIN_HAND, mob.getTarget(), settings));
        }
        SimplySwords.hold(world, mob, stack);
    }

    private int useSword(ServerWorld world, ItemStack stack, Hand hand, LivingEntity target, AbilitySettings settings) {
        SimplySwords.release(mob, stack);
        int cooldown = swordCooldown(world, stack, hand, target, settings);
        boolean used = SimplySwords.activate(world, mob, stack, hand, target);
        SimplySwords.hold(world, mob, stack);
        return used ? cooldown : -1;
    }

    private int castSpell(ServerWorld world, ItemStack stack, LivingEntity target, @Nullable LoadoutEntry entry, AbilitySettings settings) {
        MinibossEntity boss = RPGMinibossesEntities.config.spellAnimations && mob instanceof MinibossEntity miniboss ? miniboss : null;
        if (boss != null && boss.busy()) {
            return -1;
        }
        Set<Identifier> ids = new LinkedHashSet<>(entry != null && !entry.spells().isEmpty() ? entry.spells() : spellsOf(stack));
        if (entry != null) {
            for (Identifier pool : entry.spellPools()) {
                ids.addAll(SpellRegistry.spellPool(pool).spellIds());
            }
        }
        List<SpellInfo> usable = new ArrayList<>();
        for (Identifier spellId : ids) {
            Spell candidate = SpellRegistry.getSpell(spellId);
            if (candidate == null) {
                if (MISSING_SPELLS.add(spellId)) {
                    RPGMinibosses.LOGGER.warn("Unknown spell {} on {}", spellId, stack);
                }
            } else if (canCast(candidate, target)) {
                usable.add(new SpellInfo(candidate, spellId));
            }
        }
        if (usable.isEmpty()) {
            return -1;
        }
        SpellInfo info = usable.get(mob.getRandom().nextInt(usable.size()));
        Spell spell = info.spell();
        mob.getLookControl().lookAt(target);
        int windup = boss != null ? boss.spellAnimation(SpellMotion.of(spell)) : 0;
        if (windup > 0) {
            ((WorldScheduler) world).schedule(windup, () -> {
                if (mob.isAlive() && target.isAlive() && canCast(spell, target)) {
                    mob.getLookControl().lookAt(target);
                    release(world, target, info);
                }
            });
        } else {
            release(world, target, info);
        }
        if (settings.cooldown() != null) {
            return settings.cooldown();
        }
        return Math.max(20, Math.round(SpellHelper.getCooldownDuration(mob, spell, stack) * 20));
    }

    private void release(ServerWorld world, LivingEntity target, SpellInfo info) {
        Spell spell = info.spell();
        if (shootsArrow(spell)) {
            shootArrows(world, (MinibossEntity) mob, target, info);
        } else {
            SpellHelper.ImpactContext context = new SpellHelper.ImpactContext()
                    .power(SpellPower.getSpellPower(spell.school, mob))
                    .position(mob.getPos())
                    .target(SpellHelper.impactTargetingMode(spell));
            switch (spell.release.target.type) {
                case PROJECTILE -> SpellHelper.shootProjectile(world, mob, target, info, context);
                case METEOR -> SpellHelper.fallProjectile(world, mob, target, info, context);
                case CLOUD -> SpellHelper.placeCloud(world, mob, info, context);
                case SELF -> SpellHelper.performImpacts(world, mob, mob, mob, info, context);
                case CURSOR, BEAM -> SpellHelper.performImpacts(world, mob, target, mob, info, context);
                case AREA -> {
                    for (Entity hit : TargetHelper.targetsFromArea(mob, spell.range, spell.release.target.area, e -> e instanceof LivingEntity && !mob.isTeammate(e))) {
                        SpellHelper.performImpacts(world, mob, hit, mob, info, context.position(mob.getPos().add(0, mob.getHeight() / 2, 0)));
                    }
                }
                default -> {
                    return;
                }
            }
        }
        if (spell.release != null) {
            ParticleHelper.sendBatches(mob, spell.release.particles);
            SoundHelper.playSound(world, mob, spell.release.sound);
        }
    }

    private void shootArrows(ServerWorld world, MinibossEntity boss, LivingEntity target, SpellInfo info) {
        boss.shootSpellArrow(target, info);
        Spell.Release release = info.spell().release;
        if (release == null || release.target.shoot_arrow == null) {
            return;
        }
        Spell.LaunchProperties launch = release.target.shoot_arrow.launch_properties;
        for (int i = 1; i <= launch.extra_launch_count; i++) {
            ((WorldScheduler) world).schedule(i * launch.extra_launch_delay, () -> {
                if (boss.isAlive() && target.isAlive()) {
                    boss.shootSpellArrow(target, info);
                }
            });
        }
    }

    private boolean canCast(Spell spell, LivingEntity target) {
        if (heals(spell)) {
            return false;
        }
        if (shootsArrow(spell)) {
            return mob instanceof MinibossEntity boss && boss.shootsSpellArrows() && mob.canSee(target);
        }
        if (spell.release == null || spell.release.custom_impact) {
            return false;
        }
        Spell.Release.Target.Type type = spell.release.target.type;
        if (type == Spell.Release.Target.Type.SELF || type == Spell.Release.Target.Type.CLOUD) {
            return true;
        }
        if (mob.distanceTo(target) > spell.range) {
            return false;
        }
        return type != Spell.Release.Target.Type.BEAM && type != Spell.Release.Target.Type.CURSOR || mob.canSee(target);
    }

    static boolean shootsArrow(Spell spell) {
        return spell.mode == Spell.Mode.ITEM_USE && spell.arrow_perks != null
                || spell.release != null && spell.release.target != null && spell.release.target.type == Spell.Release.Target.Type.SHOOT_ARROW;
    }

    private static boolean heals(Spell spell) {
        if (spell.impact == null) {
            return false;
        }
        for (Spell.Impact impact : spell.impact) {
            if (impact.action != null && impact.action.type == Spell.Impact.Action.Type.HEAL) {
                return true;
            }
        }
        return false;
    }

    public void write(NbtCompound nbt) {
        nbt.putLongArray("HeldAbilityReadyAt", readyAt);
        nbt.putIntArray("LoadoutEntries", rolled);
    }

    public void read(NbtCompound nbt) {
        long[] saved = nbt.getLongArray("HeldAbilityReadyAt");
        System.arraycopy(saved, 0, readyAt, 0, Math.min(saved.length, readyAt.length));
        int[] entries = nbt.getIntArray("LoadoutEntries");
        System.arraycopy(entries, 0, rolled, 0, Math.min(entries.length, rolled.length));
    }

    private static AbilitySettings settings(@Nullable LoadoutEntry entry) {
        return entry == null ? AbilitySettings.DEFAULT : entry.ability();
    }

    private int swordCooldown(ServerWorld world, ItemStack stack, Hand hand, @Nullable LivingEntity target, AbilitySettings settings) {
        return settings.cooldown() != null ? settings.cooldown() : SimplySwords.cooldown(world, mob, stack, hand, target);
    }

    private static long readyTime(ServerWorld world, int ticks) {
        return world.getTime() + Math.round(ticks * RPGMinibossesEntities.config.abilityCooldownMultiplier);
    }

    private List<Identifier> spellsOf(ItemStack stack) {
        SpellContainer container = SpellContainerHelper.containerFromItemStack(stack);
        if (container == null) {
            return List.of();
        }
        if ((container.spell_ids == null || container.spell_ids.isEmpty()) && container.pool != null && !container.pool.isEmpty()) {
            return SpellRegistry.spellPool(new Identifier(container.pool)).spellIds();
        }
        return container.spell_ids == null ? List.of() : container.spell_ids.stream().map(Identifier::new).toList();
    }

    private static boolean isSword(ItemStack stack) {
        return Loadouts.SIMPLY_SWORDS && SimplySwords.isUnique(stack);
    }

    private static EquipmentSlot slot(Hand hand) {
        return hand == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }
}

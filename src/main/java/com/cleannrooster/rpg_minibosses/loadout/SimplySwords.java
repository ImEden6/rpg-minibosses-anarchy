package com.cleannrooster.rpg_minibosses.loadout;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.sweenus.simplyswords.api.AwakeningApi;
import net.sweenus.simplyswords.api.SimplySwordsAPI;
import net.sweenus.simplyswords.api.WeaponAbilityActivationSource;
import net.sweenus.simplyswords.api.WeaponAbilityContext;
import net.sweenus.simplyswords.item.interfaces.UniqueWeaponActiveAbility;
import net.sweenus.simplyswords.registry.ComponentTypeRegistry;
import net.sweenus.simplyswords.world.WeaponAbilityCooldownManager;
import org.jetbrains.annotations.Nullable;

public class SimplySwords {
    static boolean isUnique(ItemStack stack) {
        return stack.getItem() instanceof UniqueWeaponActiveAbility;
    }

    static void hold(ServerWorld world, MobEntity mob, ItemStack stack) {
        WeaponAbilityCooldownManager.setCooldown(world, mob, stack, 2);
    }

    static void release(MobEntity mob, ItemStack stack) {
        WeaponAbilityCooldownManager.clearCooldown(mob, stack);
    }

    static boolean coolingDown(ServerWorld world, MobEntity mob, ItemStack stack) {
        return WeaponAbilityCooldownManager.isCoolingDown(world, mob, stack);
    }

    static int cooldown(ServerWorld world, MobEntity mob, ItemStack stack, Hand hand, @Nullable LivingEntity target) {
        return SimplySwordsAPI.getWeaponAbilityCooldownTicks(context(world, mob, stack, hand, target));
    }

    static boolean activate(ServerWorld world, MobEntity mob, ItemStack stack, Hand hand, LivingEntity target) {
        return SimplySwordsAPI.tryActivateWeaponAbility(context(world, mob, stack, hand, target));
    }

    static void awaken(ItemStack stack, @Nullable Integer level) {
        AwakeningApi.setLevel(stack, level != null ? level : ComponentTypeRegistry.AWAKENING.contains(stack) ? AwakeningApi.getLevel(stack) : 8);
    }

    public static void putToSleep(ItemStack stack) {
        AwakeningApi.setLevel(stack, 0);
    }

    private static WeaponAbilityContext context(ServerWorld world, MobEntity mob, ItemStack stack, Hand hand, @Nullable LivingEntity target) {
        return WeaponAbilityContext.of(world, stack, mob, null, target, hand, WeaponAbilityActivationSource.MOB);
    }
}

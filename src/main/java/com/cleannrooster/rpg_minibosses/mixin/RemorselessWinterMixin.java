package com.cleannrooster.rpg_minibosses.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "elocindev.deathknights.spells.frost.RemorselessWinter", remap = false)
public abstract class RemorselessWinterMixin extends StatusEffect {
    @Shadow
    private boolean hasAmbientPlayed;

    protected RemorselessWinterMixin() {
        super(StatusEffectCategory.BENEFICIAL, 0);
    }

    @Inject(method = "method_5562", at = @At("HEAD"), cancellable = true, require = 0)
    private void endOnMobs(LivingEntity entity, AttributeContainer attributes, int amplifier, CallbackInfo ci) {
        if (!entity.getWorld().isClient() && !(entity instanceof ServerPlayerEntity)) {
            hasAmbientPlayed = false;
            super.onRemoved(entity, attributes, amplifier);
            ci.cancel();
        }
    }
}

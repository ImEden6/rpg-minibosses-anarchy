package com.cleannrooster.rpg_minibosses.mixin;

import com.cleannrooster.rpg_minibosses.RPGMinibosses;
import com.cleannrooster.rpg_minibosses.entity.RPGMinibossesEntities;
import net.minecraft.item.Item;
import net.minecraft.loot.LootTable;
import net.minecraft.util.Identifier;
import net.spell_engine.api.loot.LootConfigV2;
import net.spell_engine.api.loot.LootHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;

@Mixin(value = LootHelper.class, remap = false)
public class LootHelperMixin {
    @Inject(method = "configureV2", at = @At("HEAD"), cancellable = true)
    private static void skipBossLoot(Identifier id, LootTable.Builder tableBuilder, LootConfigV2 config, HashMap<String, Item> itemCache, CallbackInfo ci) {
        if (id.getNamespace().equals(RPGMinibosses.MOD_ID) && !RPGMinibossesEntities.config.rpgSeriesLoot) {
            ci.cancel();
        }
    }
}

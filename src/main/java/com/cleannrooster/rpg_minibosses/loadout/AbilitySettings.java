package com.cleannrooster.rpg_minibosses.loadout;

import com.google.gson.JsonObject;
import net.minecraft.util.JsonHelper;
import org.jetbrains.annotations.Nullable;

public record AbilitySettings(boolean enabled, @Nullable Integer cooldown, double minRange, double maxRange,
                              float chance, float belowHealth, int interval) {
    public static final AbilitySettings DEFAULT = fromJson(new JsonObject());

    public static AbilitySettings fromJson(JsonObject json) {
        return new AbilitySettings(
                JsonHelper.getBoolean(json, "enabled", true),
                json.has("cooldown") ? JsonHelper.getInt(json, "cooldown") : null,
                JsonHelper.getDouble(json, "min_range", 0),
                JsonHelper.getDouble(json, "max_range", 10),
                JsonHelper.getFloat(json, "chance", 0.25F),
                JsonHelper.getFloat(json, "below_health", 1.0F),
                Math.max(1, JsonHelper.getInt(json, "interval", 10)));
    }
}

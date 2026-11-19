package com.cleannrooster.rpg_minibosses.loadout;

import com.cleannrooster.rpg_minibosses.RPGMinibosses;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record Loadout(List<LoadoutEntry> mainhand, List<LoadoutEntry> offhand) {

    public static Loadout fromJson(JsonObject json) {
        return new Loadout(pool(json, "mainhand"), pool(json, "offhand"));
    }

    private static List<LoadoutEntry> pool(JsonObject json, String key) {
        List<LoadoutEntry> entries = new ArrayList<>();
        for (JsonElement element : JsonHelper.getArray(json, key, new com.google.gson.JsonArray())) {
            try {
                LoadoutEntry entry = LoadoutEntry.fromJson(element.getAsJsonObject());
                if (entry != null) {
                    entries.add(entry);
                }
            } catch (Exception e) {
                RPGMinibosses.LOGGER.warn("Skipping bad loadout entry {}: {}", element, e.getMessage());
            }
        }
        return entries;
    }

    public List<LoadoutEntry> pool(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> mainhand;
            case OFFHAND -> offhand;
            default -> List.of();
        };
    }

    @Nullable
    public LoadoutEntry roll(EquipmentSlot slot, Random random) {
        List<LoadoutEntry> entries = pool(slot);
        int total = entries.stream().mapToInt(LoadoutEntry::weight).sum();
        if (total <= 0) {
            return null;
        }
        int pick = random.nextInt(total);
        for (LoadoutEntry entry : entries) {
            pick -= entry.weight();
            if (pick < 0) {
                return entry;
            }
        }
        return null;
    }

    @Nullable
    public LoadoutEntry find(EquipmentSlot slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (LoadoutEntry entry : pool(slot)) {
            if (entry.matches(stack)) {
                return entry;
            }
        }
        return null;
    }
}

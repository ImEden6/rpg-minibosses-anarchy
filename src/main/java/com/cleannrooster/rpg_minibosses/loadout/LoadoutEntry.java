package com.cleannrooster.rpg_minibosses.loadout;

import com.cleannrooster.rpg_minibosses.RPGMinibosses;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.math.random.Random;
import net.sweenus.simplyswords.api.AwakeningApi;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record LoadoutEntry(@Nullable Item item, @Nullable TagKey<Item> tag, int weight, @Nullable Integer awakening,
                           @Nullable Float dropChance, @Nullable NbtCompound nbt, List<Identifier> spells, AbilitySettings ability) {

    @Nullable
    public static LoadoutEntry fromJson(JsonObject json) throws CommandSyntaxException {
        Item item = null;
        TagKey<Item> tag = null;
        if (json.has("tag")) {
            tag = TagKey.of(RegistryKeys.ITEM, new Identifier(JsonHelper.getString(json, "tag")));
        } else {
            Identifier id = new Identifier(JsonHelper.getString(json, "item", "minecraft:air"));
            item = Registries.ITEM.getOrEmpty(id).orElse(null);
            if (item == null) {
                RPGMinibosses.LOGGER.warn("Skipping loadout entry with unknown item {}", id);
                return null;
            }
        }
        List<Identifier> spells = new ArrayList<>();
        for (JsonElement spell : JsonHelper.getArray(json, "spells", new com.google.gson.JsonArray())) {
            spells.add(new Identifier(spell.getAsString()));
        }
        return new LoadoutEntry(
                item,
                tag,
                Math.max(0, JsonHelper.getInt(json, "weight", 1)),
                json.has("awakening") ? JsonHelper.getInt(json, "awakening") : null,
                json.has("drop_chance") ? JsonHelper.getFloat(json, "drop_chance") : null,
                json.has("nbt") ? StringNbtReader.parse(JsonHelper.getString(json, "nbt")) : null,
                spells,
                AbilitySettings.fromJson(JsonHelper.getObject(json, "ability", new JsonObject())));
    }

    public boolean matches(ItemStack stack) {
        return tag != null ? stack.isIn(tag) : stack.isOf(item);
    }

    public ItemStack create(Random random) {
        Item picked = item;
        if (tag != null) {
            List<RegistryEntry<Item>> items = new ArrayList<>();
            Registries.ITEM.iterateEntries(tag).forEach(items::add);
            picked = items.isEmpty() ? Items.AIR : items.get(random.nextInt(items.size())).value();
        }
        ItemStack stack = new ItemStack(picked);
        if (stack.isEmpty()) {
            return stack;
        }
        if (nbt != null) {
            stack.getOrCreateNbt().copyFrom(nbt);
        }
        AwakeningApi.setLevel(stack, awakening == null ? 8 : awakening);
        return stack;
    }
}

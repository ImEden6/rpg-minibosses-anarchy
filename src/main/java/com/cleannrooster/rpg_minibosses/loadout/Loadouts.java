package com.cleannrooster.rpg_minibosses.loadout;

import com.cleannrooster.rpg_minibosses.RPGMinibosses;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

public class Loadouts implements SimpleSynchronousResourceReloadListener {
    public static final boolean SIMPLY_SWORDS = FabricLoader.getInstance().isModLoaded("simplyswords");
    private static final String FOLDER = "miniboss_loadouts";
    private static Map<Identifier, Loadout> loadouts = Map.of();

    public static void register() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new Loadouts());
    }

    @Nullable
    public static Loadout get(EntityType<?> type) {
        return loadouts.get(Registries.ENTITY_TYPE.getId(type));
    }

    @Override
    public Identifier getFabricId() {
        return new Identifier(RPGMinibosses.MOD_ID, FOLDER);
    }

    @Override
    public void reload(ResourceManager manager) {
        Map<Identifier, Loadout> loaded = new HashMap<>();
        for (Map.Entry<Identifier, Resource> file : manager.findResources(FOLDER, id -> id.getPath().endsWith(".json")).entrySet()) {
            String path = file.getKey().getPath();
            Identifier id = new Identifier(file.getKey().getNamespace(), path.substring(FOLDER.length() + 1, path.length() - 5));
            try (Reader reader = file.getValue().getReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                loaded.put(id, Loadout.fromJson(json));
            } catch (Exception e) {
                RPGMinibosses.LOGGER.error("Couldn't load miniboss loadout {}", file.getKey(), e);
            }
        }
        loadouts = loaded;
        RPGMinibosses.LOGGER.info("Loaded {} miniboss loadouts", loaded.size());
    }
}

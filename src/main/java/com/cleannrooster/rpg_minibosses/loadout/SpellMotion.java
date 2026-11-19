package com.cleannrooster.rpg_minibosses.loadout;

import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;

public enum SpellMotion {
    NONE, STRIKE, CAST, AREA, CHANNEL, ARCHERY;

    public static SpellMotion of(Spell spell) {
        if (HeldAbilities.shootsArrow(spell)) {
            return ARCHERY;
        }
        String animation = spell.release != null && spell.release.animation != null ? spell.release.animation
                : spell.cast != null ? spell.cast.animation : null;
        Identifier id = animation == null ? null : Identifier.tryParse(animation);
        if (id == null) {
            return NONE;
        }
        String path = id.getPath();
        if (path.contains("archery")) {
            return ARCHERY;
        }
        if (path.contains("strike") || path.contains("slash") || path.equals("marrowrend") || path.equals("obliterate")) {
            return STRIKE;
        }
        if (path.contains("throw") || path.contains("projectile")) {
            return CAST;
        }
        if (path.contains("area_release") || path.contains("ground_release")) {
            return AREA;
        }
        if (path.contains("channeling") || path.contains("sky_charge")) {
            return CHANNEL;
        }
        return NONE;
    }
}

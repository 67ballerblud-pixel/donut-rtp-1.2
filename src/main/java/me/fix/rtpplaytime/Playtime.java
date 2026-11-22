package me.fix.rtpplaytime;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

/**
 * Reads a player's playtime in hours. With source AUTO it uses UltimateDonutSmp's own playtime (the same number
 * /playtime shows and the one UltimateDonutSmp's RTP checks), read by reflection so this plugin does not need the
 * UltimateDonutSmp jar to build. It falls back to the vanilla "time played" statistic.
 */
final class Playtime {

    private final RtpPlaytime plugin;
    private boolean warned;

    Playtime(RtpPlaytime plugin) {
        this.plugin = plugin;
    }

    double hours(Player p) {
        String src = plugin.getConfig().getString("playtime.source", "AUTO").toUpperCase(Locale.ROOT);
        if (!src.equals("VANILLA")) {
            double h = fromUltimateDonutSmp(p);
            if (h >= 0) return h;
            if (src.equals("ULTIMATEDONUTSMP")) return 0;
        }
        return p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 72000.0; // ticks -> hours
    }

    /** Hours from UltimateDonutSmp, or -1 if it isn't installed or its API could not be read. */
    private double fromUltimateDonutSmp(Player p) {
        try {
            Plugin uds = Bukkit.getPluginManager().getPlugin("UltimateDonutSmp");
            if (uds == null || !uds.isEnabled()) return -1;
            Object manager = uds.getClass().getMethod("getPlayerDataManager").invoke(uds);
            if (manager == null) return -1;
            Object data = manager.getClass().getMethod("get", Player.class).invoke(manager, p);
            if (data == null) return 0;
            Object seconds = data.getClass().getMethod("getTotalPlaytimeSeconds").invoke(data);
            return ((Number) seconds).longValue() / 3600.0;
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("Could not read UltimateDonutSmp playtime (" + t + "). Falling back to vanilla playtime.");
            }
            return -1;
        }
    }
}

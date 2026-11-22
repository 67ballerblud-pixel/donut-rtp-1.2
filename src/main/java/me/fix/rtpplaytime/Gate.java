package me.fix.rtpplaytime;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The actual rule: to RTP into a world a player needs enough playtime.
 * <ul>
 * <li>Final gate: DonutRTP's cancellable RtpPreTeleportEvent (fires for the menu, the command and RTP zones).</li>
 * <li>Early gate: "/rtp &lt;destination&gt;" and menu clicks are refused before the warmup even starts.</li>
 * <li>Menu lore: locked destinations show what they need.</li>
 * </ul>
 */
final class Gate implements Listener {

    private static final Set<String> RTP_LABELS = Set.of("rtp", "randomtp");
    private static final String DEFAULT_DENIED =
            "&cYou need at least {required} hours of playtime to RTP to {world}. &7(Current: {current}h, {remaining} to go)";

    private final RtpPlaytime plugin;
    private final Playtime playtime;
    private final Plugin rtp;

    Gate(RtpPlaytime plugin, Playtime playtime, Plugin rtp) {
        this.plugin = plugin;
        this.playtime = playtime;
        this.rtp = rtp;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // ------------------------------------------------------------------ helpers

    static String color(String s) {
        return s.replace('&', '\u00a7');
    }

    static String num(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.1f", d);
    }

    static String one(double d) {
        return String.format(Locale.ROOT, "%.1f", d);
    }

    static String remaining(double hours) {
        long mins = (long) Math.ceil(Math.max(0, hours) * 60);
        long h = mins / 60;
        long m = mins % 60;
        return h > 0 ? h + "h " + m + "m" : m + "m";
    }

    /** Hours of playtime needed for a world (a world name beats its environment). 0 = no requirement. */
    double requiredFor(World w) {
        ConfigurationSection s = cfg().getConfigurationSection("requirements");
        if (s == null || w == null) return 0;
        for (String k : s.getKeys(false)) {
            if (k.equalsIgnoreCase(w.getName())) return s.getDouble(k);
        }
        for (String k : s.getKeys(false)) {
            if (k.equalsIgnoreCase(w.getEnvironment().name())) return s.getDouble(k);
        }
        return 0;
    }

    /** All configured requirements, for /rtpplaytime check. */
    Map<String, Double> requirements() {
        Map<String, Double> out = new LinkedHashMap<>();
        ConfigurationSection s = cfg().getConfigurationSection("requirements");
        if (s != null) for (String k : s.getKeys(false)) out.put(k, s.getDouble(k));
        return out;
    }

    private String worldLabel(World w) {
        ConfigurationSection s = cfg().getConfigurationSection("messages.world-names");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                if (k.equalsIgnoreCase(w.getName())) return s.getString(k, w.getName());
            }
            for (String k : s.getKeys(false)) {
                if (k.equalsIgnoreCase(w.getEnvironment().name())) return s.getString(k, w.getName());
            }
        }
        return w.getName();
    }

    private boolean bypasses(Player p) {
        String perm = cfg().getString("bypass-permission", "donutrtp.playtime.bypass");
        return perm != null && !perm.isBlank() && p.hasPermission(perm);
    }

    /** The coloured refusal message, or null if the player may go to this world. */
    String denial(Player p, World w) {
        if (w == null) return null;
        double need = requiredFor(w);
        if (need <= 0 || bypasses(p)) return null;
        double have = playtime.hours(p);
        if (have >= need) return null;
        String msg = cfg().getString("messages.prefix", "") + cfg().getString("messages.denied", DEFAULT_DENIED);
        return color(msg.replace("{required}", num(need)).replace("{current}", one(have))
                .replace("{remaining}", remaining(need - have)).replace("{world}", worldLabel(w)));
    }

    void deny(Player p, String message) {
        p.sendMessage(message);
        String key = cfg().getString("deny-sound", "entity.villager.no");
        if (key != null && !key.isBlank() && !key.equalsIgnoreCase("none")) {
            p.playSound(p.getLocation(), key, 1f, 1f);
        }
    }

    // ------------------------------------------------------------------ final gate: DonutRTP's own event

    /** Called for io.nightbeam.donutrtp.api.RtpPreTeleportEvent (registered by reflection in RtpPlaytime). */
    void onPreTeleport(Event ev) {
        try {
            if (!(ev instanceof PlayerEvent pe) || !(ev instanceof Cancellable cancellable)) return;
            Player p = pe.getPlayer();
            Object source = ev.getClass().getMethod("getSource").invoke(ev);
            if (source != null && cfg().getStringList("exempt-sources").stream().anyMatch(s -> s.equalsIgnoreCase(source.toString()))) {
                return;
            }
            Location loc = (Location) ev.getClass().getMethod("getLocation").invoke(ev);
            String denial = denial(p, loc == null ? null : loc.getWorld());
            if (denial == null) return;
            cancellable.setCancelled(true);
            deny(p, denial);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not check an RTP teleport: " + ex);
        }
    }

    // ------------------------------------------------------------------ DonutRTP config lookups

    private World destinationWorld(String id) {
        ConfigurationSection d = destinations();
        if (d == null) return null;
        for (String k : d.getKeys(false)) {
            if (k.equalsIgnoreCase(id)) {
                String wn = d.getString(k + ".world");
                return wn == null ? null : Bukkit.getWorld(wn);
            }
        }
        return null;
    }

    private String destinationAtSlot(int slot) {
        ConfigurationSection d = destinations();
        if (d == null) return null;
        for (String k : d.getKeys(false)) {
            if (d.getInt(k + ".gui.slot", -1) == slot) return k;
        }
        return null;
    }

    private ConfigurationSection destinations() {
        try {
            return rtp.getConfig().getConfigurationSection("destinations");
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isRtpMenu(Inventory inv) {
        InventoryHolder h = inv == null ? null : inv.getHolder();
        return h != null && h.getClass().getName().startsWith("io.nightbeam.donutrtp.gui.RtpMenu");
    }

    // ------------------------------------------------------------------ early gate: /rtp <destination>

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!cfg().getBoolean("early-check", true)) return;
        String[] parts = e.getMessage().trim().split("\\s+");
        if (parts.length < 2 || parts[0].length() < 2) return;
        String label = parts[0].substring(1).toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        if (!RTP_LABELS.contains(label)) return;
        if (parts[1].equalsIgnoreCase("player")) return; // staff form: /rtp player <name> [destination]
        String denial = denial(e.getPlayer(), destinationWorld(parts[1]));
        if (denial == null) return;
        e.setCancelled(true);
        deny(e.getPlayer(), denial);
    }

    // ------------------------------------------------------------------ early gate: menu clicks

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent e) {
        if (!cfg().getBoolean("early-check", true)) return;
        Inventory top = e.getView().getTopInventory();
        if (!isRtpMenu(top) || !(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) return;
        String dest = destinationAtSlot(slot);
        if (dest == null) return;
        String denial = denial(p, destinationWorld(dest));
        if (denial == null) return;
        e.setCancelled(true);
        deny(p, denial);
    }

    // ------------------------------------------------------------------ menu lore

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuOpen(InventoryOpenEvent e) {
        if (!cfg().getBoolean("menu-lore.enabled", true) || !(e.getPlayer() instanceof Player p)) return;
        Inventory inv = e.getInventory();
        if (!isRtpMenu(inv)) return;
        ConfigurationSection d = destinations();
        if (d == null) return;
        try {
            for (String id : d.getKeys(false)) {
                int slot = d.getInt(id + ".gui.slot", -1);
                if (slot < 0 || slot >= inv.getSize()) continue;
                World w = destinationWorld(id);
                double need = requiredFor(w);
                if (w == null || need <= 0 || bypasses(p)) continue;
                double have = playtime.hours(p);
                if (have >= need) continue;
                ItemStack it = inv.getItem(slot);
                if (it == null || !it.hasItemMeta()) continue;
                ItemMeta meta = it.getItemMeta();
                List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                for (String line : cfg().getStringList("menu-lore.locked")) {
                    lore.add(color(line.replace("{required}", num(need)).replace("{current}", one(have))
                            .replace("{remaining}", remaining(need - have)).replace("{world}", worldLabel(w))));
                }
                meta.setLore(lore);
                it.setItemMeta(meta);
                inv.setItem(slot, it);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not add playtime lore to the RTP menu: " + ex);
        }
    }
}

package me.fix.rtpplaytime;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DonutRTPPlaytime: playtime requirements for DonutRTP destinations. By default 5 hours for the Nether and 24 hours
 * for the End. It hooks DonutRTP's own RtpPreTeleportEvent by reflection, so it needs no DonutRTP jar to build and
 * does not modify DonutRTP at all.
 */
public final class RtpPlaytime extends JavaPlugin implements CommandExecutor, TabCompleter {

    private Playtime playtime;
    private Gate gate;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Plugin rtp = getServer().getPluginManager().getPlugin("DonutRTP");
        if (rtp == null || !rtp.isEnabled()) {
            getLogger().severe("DonutRTP is not installed or not enabled - disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        playtime = new Playtime(this);
        gate = new Gate(this, playtime, rtp);
        getServer().getPluginManager().registerEvents(gate, this);

        try {
            Class<? extends Event> cls = Class.forName("io.nightbeam.donutrtp.api.RtpPreTeleportEvent", true,
                    rtp.getClass().getClassLoader()).asSubclass(Event.class);
            getServer().getPluginManager().registerEvent(cls, gate, EventPriority.NORMAL, (l, e) -> gate.onPreTeleport(e), this, false);
        } catch (ReflectiveOperationException | ClassCastException ex) {
            getLogger().severe("This DonutRTP version has no RtpPreTeleportEvent (" + ex + ") - disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getCommand("rtpplaytime").setExecutor(this);
        getCommand("rtpplaytime").setTabCompleter(this);
        getLogger().info("Playtime gates active for DonutRTP: " + gate.requirements());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rtpplaytime.admin")) {
            sender.sendMessage(Gate.color("&cYou don't have permission."));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage(Gate.color("&aDonutRTPPlaytime config reloaded: " + gate.requirements()));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("check")) {
            Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player p ? p : null);
            if (target == null) {
                sender.sendMessage(Gate.color("&cPlayer not found (they must be online)."));
                return true;
            }
            double have = playtime.hours(target);
            sender.sendMessage(Gate.color("&e" + target.getName() + " &7has &f" + Gate.one(have) + "h &7of playtime."));
            for (Map.Entry<String, Double> en : gate.requirements().entrySet()) {
                boolean ok = have >= en.getValue();
                sender.sendMessage(Gate.color("  &7" + en.getKey() + ": needs &f" + Gate.num(en.getValue()) + "h " + (ok ? "&a(ok)" : "&c(locked)")));
            }
            return true;
        }
        sender.sendMessage(Gate.color("&e/rtpplaytime reload &7- reload the config"));
        sender.sendMessage(Gate.color("&e/rtpplaytime check [player] &7- show playtime and what is unlocked"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("reload", "check")) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
        }
        return out;
    }
}

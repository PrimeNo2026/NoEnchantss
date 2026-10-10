package noenchants;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class NoEnchants extends JavaPlugin {

    @Override
    public void onEnable() {
        EnchantListener listener = new EnchantListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        // Alle online Spieler beim Start sofort prüfen
        Bukkit.getScheduler().runTask(this, () -> {
            for (var p : Bukkit.getOnlinePlayers()) listener.scanAll(p);
        });
        getLogger().info("NoEnchants aktiviert.");
    }
}

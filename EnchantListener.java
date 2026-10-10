package noenchants;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public final class EnchantListener implements Listener {

    private static final Component PREFIX = Component.text("[Server] ", NamedTextColor.RED);

    private final NoEnchants plugin;

    public EnchantListener(NoEnchants plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ Erkennung

    private static boolean isForbidden(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.hasItemMeta()) return false;
        boolean isMace = item.getType() == Material.MACE;
        boolean isElytra = item.getType() == Material.ELYTRA;
        if (!isMace && !isElytra) return false;
        return !item.getItemMeta().getEnchants().isEmpty();
    }

    private static String itemName(ItemStack item) {
        return item.getType() == Material.MACE ? "Mace" : "Elytra";
    }

    // ------------------------------------------------------------------ Bereinigung

    /**
     * Entfernt alle Verzauberungen. Bei Elytras wird zusätzlich die Haltbarkeit halbiert,
     * falls sie noch mehr als die Hälfte der Maximalhaltbarkeit hat.
     */
    private static ItemStack strip(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        // Verzauberungen entfernen
        for (var ench : meta.getEnchants().keySet()) meta.removeEnchant(ench);

        // Elytra: Haltbarkeit halbieren
        if (item.getType() == Material.ELYTRA && meta instanceof Damageable d) {
            int maxDur = item.getType().getMaxDurability();
            int halfUsed = maxDur / 2; // halbierte Haltbarkeit = schon zur Hälfte beschädigt
            if (d.getDamage() < halfUsed) d.setDamage(halfUsed);
        }
        item.setItemMeta(meta);
        return item;
    }

    /** Prüft und bereinigt ein einzelnes Item, schickt dem Spieler eine Meldung. */
    private void check(Player p, ItemStack item) {
        if (!isForbidden(item)) return;
        String name = itemName(item);
        strip(item);
        p.sendMessage(PREFIX.append(Component.text(
                "Verzauberte " + name + "s sind auf diesem Server nicht erlaubt! Die Verzauberungen wurden entfernt.",
                NamedTextColor.YELLOW)));
    }

    /** Prüft das gesamte Inventar eines Spielers (Rüstung, Hotbar, Inhalt, Offhand). */
    public void scanAll(Player p) {
        for (ItemStack it : p.getInventory().getContents()) check(p, it);
        for (ItemStack it : p.getInventory().getArmorContents()) check(p, it);
        check(p, p.getInventory().getItemInOffHand());
        p.updateInventory();
    }

    // ------------------------------------------------------------------ Events: Spieler

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Kurzer Delay damit das Inventar vollständig geladen ist
        Bukkit.getScheduler().runTaskLater(plugin, () -> scanAll(e.getPlayer()), 5L);
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = e.getPlayer();
            check(p, p.getInventory().getItem(e.getNewSlot()));
            p.updateInventory();
        });
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> scanAll(e.getPlayer()));
    }

    // ------------------------------------------------------------------ Events: Inventar

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            check(p, e.getCurrentItem());
            check(p, e.getCursor());
            p.updateInventory();
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            scanAll(p);
        });
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        ItemStack item = e.getItem().getItemStack();
        if (!isForbidden(item)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            check(p, item);
            e.getItem().setItemStack(item);
            p.updateInventory();
        }, 1L);
    }

    // ------------------------------------------------------------------ Events: Amboss & Crafting

    /** Amboss: Verhindert dass man überhaupt eine verzauberte Mace/Elytra als Ergebnis bekommt. */
    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        ItemStack result = e.getResult();
        if (result == null) return;
        if (!isForbidden(result)) return;
        // Ergebnis bereinigen (Verzauberungen entfernen, Elytra halbieren)
        e.setResult(strip(result.clone()));
        // Spieler informieren
        if (e.getView().getPlayer() instanceof Player p) {
            p.sendMessage(PREFIX.append(Component.text(
                    "Verzauberte " + itemName(result) + "s sind nicht erlaubt. Verzauberungen wurden entfernt.",
                    NamedTextColor.YELLOW)));
        }
    }

    /** Crafting: Sollte theoretisch nicht vorkommen, aber sicherheitshalber abgedeckt. */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        ItemStack result = e.getInventory().getResult();
        if (!isForbidden(result)) return;
        e.getInventory().setResult(strip(result.clone()));
    }

    // ------------------------------------------------------------------ Events: Welt

    /** Items am Boden (z.B. aus Kisten oder Drops): sofort bereinigen. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            if (!(en instanceof Item item)) continue;
            ItemStack is = item.getItemStack();
            if (!isForbidden(is)) continue;
            strip(is);
            item.setItemStack(is);
        }
    }
}

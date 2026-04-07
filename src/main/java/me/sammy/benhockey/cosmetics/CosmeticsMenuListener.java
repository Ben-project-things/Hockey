package me.sammy.benhockey.cosmetics;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Handles /cosmetics GUI interactions.
 */
public class CosmeticsMenuListener implements Listener {

  private static final String MAIN_TITLE = "§8Cosmetics";
  private static final String STICKS_TITLE = "§b§lHockey Sticks";
  private static final String PARTICLE_TITLE = "§a§lParticle Selector";
  private final CosmeticsManager cosmeticsManager;

  public CosmeticsMenuListener(CosmeticsManager cosmeticsManager) {
    this.cosmeticsManager = cosmeticsManager;
  }

  public void openMainMenu(Player player) {
    Inventory inv = Bukkit.createInventory(null, 27, MAIN_TITLE);
    fill(inv, Material.GRAY_STAINED_GLASS_PANE, " ");
    inv.setItem(11, makeItem(Material.STICK, "&b&lHockey Sticks",
            List.of("&7Customize your base stick."), true));
    inv.setItem(15, makeItem(Material.COMPASS, "&a&lParticle Selector",
            List.of("&7Choose your puck particle."), true));
    player.openInventory(inv);
  }

  public void openStickMenu(Player player) {
    Inventory inv = Bukkit.createInventory(null, 54, STICKS_TITLE);
    fill(inv, Material.GRAY_STAINED_GLASS_PANE, " ");
    fillRow(inv, 5, Material.BLACK_STAINED_GLASS_PANE, " ");

    int slot = 0;
    for (CosmeticsManager.StickType type : CosmeticsManager.StickType.values()) {
      inv.setItem(slot++, makeItem(type.material, "&b" + type.fancy, List.of("&7Click to equip"), false));
    }

    CosmeticsManager.StickType selected = this.cosmeticsManager.getStickType(player.getUniqueId());
    inv.setItem(49, makeItem(selected.material, "&aCurrent: &f" + selected.fancy,
            List.of("&7Your selected hockey stick."), true));

    player.openInventory(inv);
  }

  public void openParticleMenu(Player player) {
    Inventory inv = Bukkit.createInventory(null, 54, PARTICLE_TITLE);
    fill(inv, Material.GRAY_STAINED_GLASS_PANE, " ");
    fillRow(inv, 5, Material.BLACK_STAINED_GLASS_PANE, " ");

    int slot = 0;
    for (CosmeticsManager.TrailParticle particle : CosmeticsManager.TrailParticle.values()) {
      if (slot >= 45) {
        break;
      }
      List<String> lore = new ArrayList<>();
      lore.add("&7Click to equip.");
      lore.add(player.hasPermission(particle.permission) ? "&aUnlocked" : "&cNo permission: " + particle.permission);
      inv.setItem(slot++, makeItem(particle.icon, "&a" + particle.fancyName, lore, false));
    }

    CosmeticsManager.TrailParticle selected = this.cosmeticsManager.getParticle(player.getUniqueId());
    inv.setItem(49, makeItem(selected.icon, "&aCurrent: &f" + selected.fancyName,
            List.of("&7Your selected puck particle."), true));

    player.openInventory(inv);
  }

  @EventHandler
  public void onMenuClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player)) {
      return;
    }

    Player player = (Player) event.getWhoClicked();
    String title = event.getView().getTitle();
    if (!MAIN_TITLE.equals(title) && !STICKS_TITLE.equals(title) && !PARTICLE_TITLE.equals(title)) {
      return;
    }

    event.setCancelled(true);
    int slot = event.getRawSlot();
    if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
      return;
    }

    if (MAIN_TITLE.equals(title)) {
      if (slot == 11) {
        openStickMenu(player);
      } else if (slot == 15) {
        openParticleMenu(player);
      }
      return;
    }

    if (STICKS_TITLE.equals(title)) {
      if (slot < CosmeticsManager.StickType.values().length) {
        CosmeticsManager.StickType selected = CosmeticsManager.StickType.values()[slot];
        this.cosmeticsManager.setStickType(player, selected);
        this.cosmeticsManager.refreshPlayerSticks(player);
        player.sendMessage("§6[§bBH§6] §aSelected stick: §f" + selected.fancy);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        openStickMenu(player);
      }
      return;
    }

    if (slot < CosmeticsManager.TrailParticle.values().length && slot < 45) {
      CosmeticsManager.TrailParticle selected = CosmeticsManager.TrailParticle.values()[slot];
      if (!player.hasPermission(selected.permission)) {
        player.sendMessage("§6[§bBH§6] §cYou do not have permission: §7" + selected.permission);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        return;
      }

      this.cosmeticsManager.setParticle(player, selected);
      player.sendMessage("§6[§bBH§6] §aSelected particle: §f" + selected.fancyName);
      player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
      openParticleMenu(player);
    }
  }

  private void fill(Inventory inv, Material material, String name) {
    for (int i = 0; i < inv.getSize(); i++) {
      inv.setItem(i, makeItem(material, name, List.of(), false));
    }
  }

  private void fillRow(Inventory inv, int row, Material material, String name) {
    int start = row * 9;
    for (int i = start; i < start + 9; i++) {
      inv.setItem(i, makeItem(material, name, List.of(), false));
    }
  }

  private ItemStack makeItem(Material material, String name, List<String> lore, boolean glow) {
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
    if (!lore.isEmpty()) {
      List<String> coloredLore = new ArrayList<>();
      for (String line : lore) {
        coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
      }
      meta.setLore(coloredLore);
    }
    if (glow) {
      meta.addEnchant(org.bukkit.enchantments.Enchantment.KNOCKBACK, 1, true);
      meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    }
    item.setItemMeta(meta);
    return item;
  }
}

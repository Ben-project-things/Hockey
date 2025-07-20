package me.sammy.benhockey.lobby;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import me.sammy.benhockey.game.Rink;

/**
 * Sets up the rink selection GUI that allows users to select rinks to join.
 */
public class RinkSelectionGUI {

  private final LobbyManager lobbyManager;
  private final Random random = new Random();

  private final Material[] rinkBlocks = {
          Material.BLUE_CONCRETE, Material.RED_CONCRETE, Material.LIME_CONCRETE,
          Material.YELLOW_CONCRETE, Material.PURPLE_CONCRETE, Material.ORANGE_CONCRETE
  };

  private final int[] centerSlots = {
          10, 11, 12, 13, 14, 15, 16,
          19, 20, 21, 22, 23, 24, 25
  };


  public RinkSelectionGUI(LobbyManager lobbyManager) {
    this.lobbyManager = lobbyManager;
  }

  /**
   * Opens the main GUI selection as well as sets up the rink for each rink
   * @param player is the player to open the GUI for
   */
  public void openMainGUI(Player player) {
    Inventory gui = Bukkit.createInventory(null, 36, "§bSelect a Rink");

    ItemStack filler = new ItemStack(Material.RED_STAINED_GLASS_PANE);
    ItemMeta fillerMeta = filler.getItemMeta();
    if (fillerMeta != null) {
      fillerMeta.setDisplayName("§cNo Rink Yet");
      filler.setItemMeta(fillerMeta);
    }

    for (int slot : centerSlots) {
      gui.setItem(slot, filler);
    }

    List<Rink> rinks = new ArrayList<>(lobbyManager.getRinks());
    int slotsUsed = Math.min(centerSlots.length, rinks.size());

    for (int i = 0; i < slotsUsed; i++) {
      Rink rink = rinks.get(i);
      Material block = rinkBlocks[random.nextInt(rinkBlocks.length)];
      ItemStack item = new ItemStack(block);
      ItemMeta meta = item.getItemMeta();

      meta.setDisplayName("§l" + rink.getName());

      List<String> lore = new ArrayList<>();
      lore.add("§a" + rink.getTotalPlayers() + " §7Players");

      meta.setLore(lore);
      item.setItemMeta(meta);

      gui.setItem(centerSlots[i], item);
    }

    player.openInventory(gui);
  }
}

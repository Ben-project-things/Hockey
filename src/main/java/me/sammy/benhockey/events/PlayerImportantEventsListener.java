package me.sammy.benhockey.events;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.sammy.benhockey.game.Rink;
import me.sammy.benhockey.lobby.LobbyManager;
import me.sammy.benhockey.lobby.RinkSelectionGUI;

/**
 * Class that deals with the logic of players joining the server.
 */
public class PlayerImportantEventsListener implements Listener {

  private final LobbyManager lobbyManager;

  public PlayerImportantEventsListener(LobbyManager lobbyManager) {
    this.lobbyManager = lobbyManager;
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent e) {
    lobbyManager.addPlayerToLobby(e.getPlayer());
  }

  @EventHandler
  public void onPlayerLeave(PlayerQuitEvent e) {
    Player player = e.getPlayer();
    Rink leaveServer = lobbyManager.getPlayerRink(player);
    if (leaveServer != null) {
      leaveServer.removePersonalPuck(player);
      leaveServer.removePlayerFromRink(player);
    }
  }

  @EventHandler
  public void onFoodLevelChange(FoodLevelChangeEvent e) {
    if (e.getEntity() instanceof Player) {
      e.setCancelled(true);
    }
  }

  @EventHandler
  public void onEntityExplode(EntityExplodeEvent e) {
    if (e.getEntityType() == EntityType.PRIMED_TNT) {
      e.blockList().clear();
    }
  }

  @EventHandler
  public void onPlayerDeath(PlayerDeathEvent e) {
    Player p = e.getPlayer();
    if (this.lobbyManager.isPlayerInLobby(p)) {
      p.teleport(lobbyManager.getLobbySpawn());
    }
    else {
      String team = this.lobbyManager.getPlayerRink(p).getTeam(p);
      if (team.equalsIgnoreCase("home")) {
        p.teleport(this.lobbyManager.getPlayerRink(p).getHomeBench());
      }
      else {
        p.teleport(this.lobbyManager.getPlayerRink(p).getAwayBench());
      }
    }
  }

  @EventHandler
  public void onPlayerPickUpItem(PlayerAttemptPickupItemEvent e) {
    if (e.getPlayer().getGameMode() == GameMode.ADVENTURE) {
      e.setCancelled(true);
    }
  }

  @EventHandler
  public void onPlayerDamaged(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player)) {
      return;
    }

    EntityDamageEvent.DamageCause cause = e.getCause();
    if (cause == EntityDamageEvent.DamageCause.FALL) {
      e.setCancelled(true);
    }

    if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
            cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {

      e.setDamage(0.0);
    }
  }

  @EventHandler
  public void onPlayerInteract(PlayerInteractEvent e) {
    if (lobbyManager.isPlayerInLobby(e.getPlayer())) {
      if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
        if (e.getPlayer().getInventory().getItemInMainHand().getType() == Material.COMPASS) {
          new RinkSelectionGUI(this.lobbyManager).openMainGUI(e.getPlayer());
        }
      }
    }
  }

  @EventHandler
  public void onInventoryClick(InventoryClickEvent e) {
    if (!(e.getWhoClicked() instanceof Player)) {
      return;
    }

    Player player = (Player) e.getWhoClicked();

    if (e.getView().getTitle().equals("§bSelect a Rink")) {
      e.setCancelled(true);

      ItemStack clicked = e.getCurrentItem();
      if (clicked == null || !clicked.hasItemMeta()) {
        return;
      }

      String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
      Rink rink = lobbyManager.getRinkByName(name);

      if (rink != null) {
        lobbyManager.movePlayerFromLobby(name, player);
        player.closeInventory();
      }
    }

    if (isProtectedItem(e.getCurrentItem())) {
      e.setCancelled(true);
    }
  }

  /**
   * Private helper to determine if a specific item is a protected item (meaning it can't be
   * moved or dropped).
   * @param item is the item to check
   * @return if it is a protected item.
   */
  private boolean isProtectedItem(ItemStack item) {
    if (item == null || !item.hasItemMeta()) {
      return false;
    }

    ItemMeta meta = item.getItemMeta();
    String displayName = meta.hasDisplayName() ? meta.getDisplayName() : "";


    if ((item.getType() == Material.LEATHER_CHESTPLATE || item.getType() == Material.LEATHER_LEGGINGS) &&
            (displayName.equals("§cHome Jersey") || displayName.equals("§9Away Jersey") ||
                    displayName.equals("§cHome Pants") || displayName.equals("§9Away Pants"))) {
      return true;
    }

    if ((item.getType() == Material.LEATHER_BOOTS || item.getType() == Material.LEATHER_HELMET) &&
            (displayName.equals("§cHome Visor") || displayName.equals("§9Away Visor") ||
                    displayName.equals("§cHome Skates") || displayName.equals("§9Away Skates"))) {
      return true;
    }

    if (item.getType() == Material.COMPASS &&
            "§aRink Selector".equals(displayName)) {
      return true;
    }

    if (item.getType() == Material.IRON_NUGGET &&
            "§6Ref Whistle".equals(displayName)) {
      return true;
    }

    if (item.getType() == Material.STICK  &&
            "§aHockey Stick".equals(displayName)) {
      return true;
    }

    return false;
  }

  @EventHandler
  public void onInventoryDrag(InventoryDragEvent e) {
    if (!(e.getWhoClicked() instanceof Player)) {
      return;
    }

    ItemStack item = e.getOldCursor();
    if (item.getType() == Material.STICK && item.hasItemMeta() && "§aHockey Stick".equals(item.getItemMeta().getDisplayName())) {
      e.setCancelled(true);
    }

    if (isProtectedItem(e.getOldCursor())) {
      e.setCancelled(true);
    }
  }

  @EventHandler
  public void onDropProtectedItem(PlayerDropItemEvent e) {
    if (isProtectedItem(e.getItemDrop().getItemStack())) {
      e.setCancelled(true);
    }
  }
}

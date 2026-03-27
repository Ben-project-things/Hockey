package me.sammy.benhockey.events;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.sammy.benhockey.game.Rink;
import me.sammy.benhockey.lobby.LobbyManager;
import me.sammy.benhockey.lobby.RinkSelectionGUI;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Class that deals with the logic of players joining the server.
 */
public class PlayerImportantEventsListener implements Listener {

  private final LobbyManager lobbyManager;
  private final Map<UUID, TeamNamePrompt> pendingTeamNames = new HashMap<>();
  private final Map<UUID, PenaltyPrompt> pendingPenalties = new HashMap<>();
  private static final String REF_MENU_TITLE = "§6Ref Menu";

  public PlayerImportantEventsListener(LobbyManager lobbyManager) {
    this.lobbyManager = lobbyManager;
  }

  private enum TeamNamePrompt {
    HOME,
    AWAY
  }

  private enum PenaltyPrompt {
    GIVE,
    EDIT,
    END
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent e) {
    lobbyManager.addPlayerToLobby(e.getPlayer());
  }

  @EventHandler
  public void onPlayerLeave(PlayerQuitEvent e) {
    Player player = e.getPlayer();
    this.pendingTeamNames.remove(player.getUniqueId());
    this.pendingPenalties.remove(player.getUniqueId());
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
    if (!lobbyManager.isPlayerInLobby(e.getPlayer())
            && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)
            && e.getPlayer().getInventory().getItemInMainHand().getType() == Material.CLOCK
            && e.getPlayer().getInventory().getItemInMainHand().hasItemMeta()
            && "§eRef Menu".equals(e.getPlayer().getInventory().getItemInMainHand().getItemMeta().getDisplayName())) {
      openRefMenu(e.getPlayer());
      return;
    }

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

    if (REF_MENU_TITLE.equals(e.getView().getTitle())) {
      e.setCancelled(true);
      if (!(e.getWhoClicked() instanceof Player)) {
        return;
      }

      Player player = (Player) e.getWhoClicked();
      Rink rink = lobbyManager.getPlayerRink(player);
      if (rink == null || lobbyManager.isPlayerInLobby(player)) {
        player.closeInventory();
        return;
      }

      ItemStack clicked = e.getCurrentItem();
      if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) {
        return;
      }

      String itemName = clicked.getItemMeta().getDisplayName();
      switch (itemName) {
        case "§aToggle Hitting":
          rink.toggleHits(player);
          break;
        case "§cLock/Unlock Teams":
          rink.lockTeams(player);
          break;
        case "§bSet Home Name":
          this.pendingTeamNames.put(player.getUniqueId(), TeamNamePrompt.HOME);
          player.sendMessage("§6[§bBH§6] §eType the new HOME team name in chat, or type cancel.");
          player.closeInventory();
          break;
        case "§9Set Away Name":
          this.pendingTeamNames.put(player.getUniqueId(), TeamNamePrompt.AWAY);
          player.sendMessage("§6[§bBH§6] §eType the new AWAY team name in chat, or type cancel.");
          player.closeInventory();
          break;
        case "§6Set Pregame":
          rink.setToPregame();
          break;
        case "§2Start Game":
          rink.startGame();
          break;
        case "§4End Game":
          if (rink.getGame() != null) {
            rink.endGame();
          } else {
            player.sendMessage("§6[§bBH§6] §cThere is no game currently running.");
          }
          break;
        case "§dGive Penalty":
          this.pendingPenalties.put(player.getUniqueId(), PenaltyPrompt.GIVE);
          player.sendMessage("§6[§bBH§6] §eType: <player> <reason> <time>. Example: Steve tripping 120");
          player.closeInventory();
          break;
        case "§5Edit Penalty":
          this.pendingPenalties.put(player.getUniqueId(), PenaltyPrompt.EDIT);
          player.sendMessage("§6[§bBH§6] §eType: <player> <time>. Example: Steve 60");
          player.closeInventory();
          break;
        case "§8End Penalty":
          this.pendingPenalties.put(player.getUniqueId(), PenaltyPrompt.END);
          player.sendMessage("§6[§bBH§6] §eType: <player>. Example: Steve");
          player.closeInventory();
          break;
        default:
          break;
      }
      return;
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
                    displayName.equals("§cHome Pants") || displayName.equals("§9Away Pants") ||
                    displayName.equals("§cHome Goalie Chest") || displayName.equals("§9Away Goalie Chest") ||
                    displayName.equals("§cHome Goalie Pads") || displayName.equals("§9Away Goalie Pads"))) {
      return true;
    }

    if ((item.getType() == Material.LEATHER_BOOTS || item.getType() == Material.LEATHER_HELMET
            || item.getType() == Material.IRON_BOOTS || item.getType() == Material.IRON_HELMET) &&
            (displayName.equals("§cHome Visor") || displayName.equals("§9Away Visor") ||
                    displayName.equals("§cHome Skates") || displayName.equals("§9Away Skates") ||
                    displayName.equals("§bGoalie Mask") || displayName.equals("§bGoalie Skates"))) {
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

    if (item.getType() == Material.CLOCK &&
            "§eRef Menu".equals(displayName)) {
      return true;
    }

    if (item.getType() == Material.STICK  &&
            "§aHockey Stick".equals(displayName)) {
      return true;
    }

    if (item.getType() == Material.SLIME_BALL &&
            "§bGloved Puck".equals(displayName)) {
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

  @EventHandler
  public void onPlayerChat(AsyncPlayerChatEvent e) {
    Player player = e.getPlayer();
    UUID playerId = player.getUniqueId();
    TeamNamePrompt teamPrompt = this.pendingTeamNames.get(playerId);
    PenaltyPrompt penaltyPrompt = this.pendingPenalties.get(playerId);

    if (teamPrompt == null && penaltyPrompt == null) {
      return;
    }

    e.setCancelled(true);
    String message = e.getMessage().trim();
    if (message.equalsIgnoreCase("cancel")) {
      this.pendingTeamNames.remove(playerId);
      this.pendingPenalties.remove(playerId);
      player.sendMessage("§6[§bBH§6] §cRef input cancelled.");
      return;
    }

    Rink rink = this.lobbyManager.getPlayerRink(player);
    if (rink == null || this.lobbyManager.isPlayerInLobby(player)) {
      this.pendingTeamNames.remove(playerId);
      this.pendingPenalties.remove(playerId);
      player.sendMessage("§6[§bBH§6] §cYou are not currently in a rink.");
      return;
    }

    if (teamPrompt != null) {
      this.pendingTeamNames.remove(playerId);
      String teamKey = teamPrompt == TeamNamePrompt.HOME ? "home" : "away";
      Bukkit.getScheduler().runTask(this.lobbyManager.getPlugin(), () -> rink.setTeamName(teamKey, message, player));
      return;
    }

    this.pendingPenalties.remove(playerId);
    String[] parts = message.split("\\s+");
    Bukkit.getScheduler().runTask(this.lobbyManager.getPlugin(), () -> {
      switch (penaltyPrompt) {
        case GIVE:
          if (parts.length != 3) {
            player.sendMessage("§6[§bBH§6] §aUsage: <player> <reason> <time>");
            return;
          }
          rink.givePenalty(player, "give", parts[0], parts[1], parts[2]);
          break;
        case EDIT:
          if (parts.length != 2) {
            player.sendMessage("§6[§bBH§6] §aUsage: <player> <time>");
            return;
          }
          rink.givePenalty(player, "edit", parts[0], "", parts[1]);
          break;
        case END:
          if (parts.length != 1) {
            player.sendMessage("§6[§bBH§6] §aUsage: <player>");
            return;
          }
          rink.givePenalty(player, "end", parts[0], "", "0");
          break;
        default:
          break;
      }
    });
  }

  private void openRefMenu(Player player) {
    Inventory inv = Bukkit.createInventory(null, 27, REF_MENU_TITLE);
    inv.setItem(10, createMenuItem(Material.IRON_SWORD, "§aToggle Hitting"));
    inv.setItem(11, createMenuItem(Material.CHAIN, "§cLock/Unlock Teams"));
    inv.setItem(12, createMenuItem(Material.PAPER, "§bSet Home Name"));
    inv.setItem(13, createMenuItem(Material.PAPER, "§9Set Away Name"));
    inv.setItem(14, createMenuItem(Material.GOLDEN_AXE, "§dGive Penalty"));
    inv.setItem(15, createMenuItem(Material.BOOK, "§5Edit Penalty"));
    inv.setItem(16, createMenuItem(Material.BARRIER, "§8End Penalty"));
    inv.setItem(21, createMenuItem(Material.YELLOW_WOOL, "§6Set Pregame"));
    inv.setItem(22, createMenuItem(Material.LIME_WOOL, "§2Start Game"));
    inv.setItem(23, createMenuItem(Material.RED_WOOL, "§4End Game"));
    player.openInventory(inv);
  }

  private ItemStack createMenuItem(Material material, String name) {
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    meta.setDisplayName(name);
    item.setItemMeta(meta);
    return item;
  }
}

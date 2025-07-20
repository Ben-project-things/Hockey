package me.sammy.benhockey.lobby;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import me.sammy.benhockey.game.Rink;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class LobbyManager {

  private final Set<Player> lobbyPlayers = new HashSet<>();
  private final Set<Rink> rinks = new HashSet<>();
  private final Map<UUID, RinkBuilder> rinkBuilders = new HashMap<>();
  private final Location lobbySpawn;
  private final JavaPlugin plugin;


  public LobbyManager(JavaPlugin plugin) {
    this.lobbySpawn = Objects.requireNonNull(Bukkit.getWorld("world")).getHighestBlockAt(0, 0).getLocation().add(0.5, 1, 0.5);
    this.plugin = plugin;
  }

  /**
   * Adds a player to the lobby.
   * @param player is the player to add
   */
  public void addPlayerToLobby(Player player) {
    player.teleport(lobbySpawn);
    player.setGameMode(GameMode.ADVENTURE);
    player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
    player.setLevel(0);

    Inventory inventory = player.getInventory();
    inventory.clear();
    ItemStack compass = new ItemStack(Material.COMPASS);
    ItemMeta meta = compass.getItemMeta();
    meta.setDisplayName("§aRink Selector");
    compass.setItemMeta(meta);
    inventory.addItem(compass);


    lobbyPlayers.add(player);
    player.sendMessage("§6[§bBH§6] §aYou are in the main lobby. Use the compass to go to rinks.");
  }

  /**
   * Moves a player from the lobby to the specified rink.
   * @param rinkName is the rink to move them to
   * @param player is the player to move
   */
  public void movePlayerFromLobby(String rinkName, Player player) {
    for (Rink rink : rinks) {
      if (rink.getName().equalsIgnoreCase(rinkName)) {
        lobbyPlayers.remove(player);
        rink.addFanToRink(player);
        return;
      }
    }
    player.sendMessage("§6[§bBH§6] §cNo rink with that name exists.");
  }

  /**
   * Starts the setup command for creating a rink.
   * @param rinkName is the name of the new rink
   * @param player is the player that wants to create the rink
   */
  public void createRink(String rinkName, Player player) {
    for (Rink rink : rinks) {
      if (rink.getName().equalsIgnoreCase(rinkName)) {
        player.sendMessage("§6[§bBH§6] §cA rink with that name already exists.");
        return;
      }
    }

    Location centerIce = player.getLocation();
    RinkBuilder builder = new RinkBuilder(rinkName, centerIce, plugin);
    rinkBuilders.put(player.getUniqueId(), builder);

    player.sendMessage("§6[§bBH§6] §aStarted rink creation: §b" + rinkName);
    player.sendMessage("§6[§bBH§6] §aUse /setgoal home (red), /setgoal homebench, /setgoal away " +
            "(blue), /setgoal awaybench, and /setgoal penalty at §aeach position.");
  }

  /**
   * Sets specific locations associated with a rink.
   * @param type is the location place the player is setting up
   * @param player is the player that is setting up
   */
  public void setGoal(String type, Player player) {
    RinkBuilder builder = rinkBuilders.get(player.getUniqueId());
    if (builder == null) {
      player.sendMessage("§6[§bBH§6] §cYou haven't started creating a rink. " +
              "Use /createrink first.");
      return;
    }

    Location loc = player.getLocation();
    switch (type.toLowerCase()) {
      case "home":
        builder.setHomeGoal(loc);
        player.sendMessage("§6[§bBH§6] §cHome goal set.");
        break;
      case "away":
        builder.setAwayGoal(loc);
        player.sendMessage("§6[§bBH§6] §9Away goal set.");
        break;
      case "homebench":
        builder.setHomeBench(loc);
        player.sendMessage("§6[§bBH§6] §cHome bench set.");
        break;
      case "awaybench":
        builder.setAwayBench(loc);
        player.sendMessage("§6[§bBH§6] §9Away bench set.");
        break;
      case "penalty":
        builder.setPenaltyBox(loc);
        player.sendMessage("§6[§bBH§6] §7Penalty box set.");
        break;
      default:
        player.sendMessage("§6[§bBH§6] §aUsage: §7/setgoal " +
                "<home/away/penalty/homebench/awaybench>");
        return;
    }

    List<String> remaining = new ArrayList<>();
    if (builder.getHomeGoal() == null) {
      remaining.add("Home Goal");
    }
    if (builder.getAwayGoal() == null) {
      remaining.add("Away Goal");
    }
    if (builder.getPenaltyBox() == null) {
      remaining.add("Penalty Box");
    }
    if (builder.getHomeBench() == null) {
      remaining.add("Home Bench");
    }
    if (builder.getAwayBench() == null) {
      remaining.add("Away Bench");
    }

    if (remaining.isEmpty()) {
      Rink newRink = builder.build();
      rinks.add(newRink);
      rinkBuilders.remove(player.getUniqueId());
      player.sendMessage("§6[§bBH§6] §aRink §b" + builder.getName() + " §acreated successfully!");
    } else {
      player.sendMessage("§6[§bBH§6] §eStill need to set: §7" + String.join(", ", remaining));
    }
  }

  /**
   * Cancels the rink setup from a user.
   * @param player is the player that issued the command
   */
  public void cancelRink(Player player) {
    if (rinkBuilders.remove(player.getUniqueId()) != null) {
      player.sendMessage("§6[§bBH§6] §aRink creation cancelled.");
    } else {
      player.sendMessage("§6[§bBH§6] §cYou are not currently setting up a rink.");
    }
  }

  /**
   * Deletes the specified rink.
   * @param rinkName is the rink name to delete
   * @param player is the player that sent the message
   */
  public void deleteRink(String rinkName, Player player) {
    if (rinks.isEmpty()) {
      player.sendMessage("§6[§bBH§6] §cNo Rink with that name exists.");
    }
    else {
      for (Rink rink : rinks) {
        if (Objects.equals(rink.getName(), rinkName)) {
          player.sendMessage("§6[§bBH§6] §aSuccessfully deleted rink: §7" + rinkName + " §a.");
          rinks.remove(rink);
          return;
        }
      }
      player.sendMessage("§6[§bBH§6] §cNo Rink with that name exists.");
    }
  }

  /**
   * Checks whether a player is in the lobby.
   * @param player is the player to check
   * @return true if they are in the lobby
   */
  public boolean isPlayerInLobby(Player player) {
    return lobbyPlayers.contains(player);
  }

  /**
   * Checks whether a player is on the home or away team
   * @param p is the player to check
   * @return true if they are in the lobby
   */
  public boolean isPlayerOnTeam(Player p) {
    Rink rink = this.getPlayerRink(p);
    if (rink == null) {
      return false;
    }

    String team = rink.getTeam(p);
    return "away".equals(team) || "home".equals(team);
  }

  /**
   * Checks whether a player is a keeper
   * @param p is the player to check
   * @return true if they are in the lobby
   */
  public boolean isPlayerAKeeper(Player p) {
    Rink rink = this.getPlayerRink(p);
    if (rink == null) {
      return false;
    }

    return rink.isKeeper(p);
  }

  /**
   * Removes all the players from the lobby.
   */
  public void removeLobbyPlayers() {
    lobbyPlayers.clear();
  }

  /**
   * Gets the specific rink a player is at.
   * @param player is the player to check
   * @return the rink the player is at.
   */
  public Rink getPlayerRink(Player player) {
    for (Rink rink : rinks) {
      if (rink.containsPlayer(player)) {
        return rink;
      }
    }
    return null;
  }

  /**
   * Gets the available rinks.
   * @return the set of rinks
   */
  public Set<Rink> getRinks() {
    return rinks;
  }

  /**
   * Gets the location of spawn.
   * @return spawn location
   */
  public Location getLobbySpawn() {
    return this.lobbySpawn;
  }

  /**
   * Returns the actual rink based off of the rink selected.
   * @param name is the name of the rink to find
   * @return the rink
   */
  public Rink getRinkByName(String name) {
    for (Rink rink : rinks) {
      if (rink.getName().equalsIgnoreCase(name)) {
        return rink;
      }
    }
    return null;
  }

}

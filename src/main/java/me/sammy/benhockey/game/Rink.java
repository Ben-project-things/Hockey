package me.sammy.benhockey.game;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


/**
 * The data involved with a rink.
 */
public class Rink {
  private final String name;
  private final Set<Player> refs = new HashSet<>();
  private final Set<Player> fans = new HashSet<>();
  private String homeTeamName;
  private final Set<Player> homeTeam = new HashSet<>();
  private String awayTeamName;
  private final Set<Player> awayTeam = new HashSet<>();
  private final Set<Player> goalies = new HashSet<>();
  private final Map<UUID, Slime> personalPucks = new HashMap<>();
  private Set<Player> fightingPlayers;
  private boolean allowHits = false;
  private boolean teamLocked = false;
  private Game game;
  private GameState state;
  private GameScoreboard scoreboard;
  private final JavaPlugin plugin;

  private final Location centerIce;
  private final Location homeGoalCenter;
  private final Location homeBench;
  private final Location awayGoalCenter;
  private final Location awayBench;
  private final List<Location> homeGoalZone;
  private final List<Location> awayGoalZone;
  private final Location penaltyBox;


  /**
   * Constructor for setting up positions of the rink.
   * @param name is the name of the rink
   * @param centerIce is the center of the rink
   * @param redGoal is the center block of the red goal
   * @param blueGoal is the center block of the blue goal
   */
  public Rink(String name, Location centerIce, Location redGoal,
              Location blueGoal, Location penaltyBox, Location homeBench,
              Location awayBench, JavaPlugin plugin) {
    this.name = name;
    this.centerIce = centerIce;
    this.penaltyBox = penaltyBox;
    this.homeTeamName = "Home";
    this.homeGoalCenter = redGoal;
    this.homeBench = homeBench;
    this.awayTeamName = "Away";
    this.awayGoalCenter = blueGoal;
    this.awayBench = awayBench;
    this.homeGoalZone = getGoalZone(this.homeGoalCenter, this.centerIce);
    this.awayGoalZone = getGoalZone(this.awayGoalCenter, this.centerIce);
    this.plugin = plugin;
    this.state = GameState.PREGAME;
    this.scoreboard = new GameScoreboard(this);
  }

  /**
   * Adds a player who joined the rink to the rink.
   * @param p is the player that typed the command
   */
  public void addFanToRink(Player p) {
  fans.add(p);
  Inventory inventory = p.getInventory();
  inventory.clear();
  p.setGameMode(GameMode.SPECTATOR);

  this.scoreboard.showToPlayer(p);
  p.teleport(new Location(p.getWorld(), centerIce.getX(), centerIce.getY() + 7, centerIce.getZ()));
}

  /**
   * Method that deals with handling when a player attempts to join a certain team.
   * @param team is the team the player wants to join
   * @param p is the player that typed the command
   */
  public void handleTeamJoin(String team, Player p) {
    Inventory inventory = p.getInventory();

    switch (team.toLowerCase()) {
      case "fan":
        inventory.clear();
        refs.remove(p);
        fans.remove(p);
        homeTeam.remove(p);
        awayTeam.remove(p);
        addFanToRink(p);
        break;

      case "ref":
        inventory.clear();
        refs.remove(p);
        fans.remove(p);
        homeTeam.remove(p);
        awayTeam.remove(p);
        refs.add(p);
        p.setGameMode(GameMode.CREATIVE);
        ItemStack refWhistle = new ItemStack(Material.IRON_NUGGET);
        ItemMeta meta = refWhistle.getItemMeta();
        meta.setDisplayName("§6Ref Whistle");
        refWhistle.setItemMeta(meta);
        inventory.setItem(8, refWhistle);

        p.setFlying(true);
        p.teleport(new Location(p.getWorld(), centerIce.getX(), centerIce.getY() + 7, centerIce.getZ()));
        createUniform("ref", p);
        break;

      case "home":
      case "away":

        if (teamLocked) {
          p.sendMessage("§6[§bFH§6] §7Teams are currently locked.");
          return;
        }

        inventory.clear();
        refs.remove(p);
        fans.remove(p);
        homeTeam.remove(p);
        awayTeam.remove(p);

        p.setLevel(1);
        p.setGameMode(GameMode.ADVENTURE);

        if (team.equalsIgnoreCase("home")) {
          homeTeam.add(p);
          createUniform("home", p);
          p.teleport(this.homeBench);
        } else {
          awayTeam.add(p);
          createUniform("away", p);
          p.teleport(this.awayBench);
        }

        inventory.addItem(createHockeyStick());
        break;

      default:
        p.sendMessage("§6[§bFH§6] §cUnknown team: " + team);
        break;
    }
  }


  /**
   * Creates the hockey stick item with desired attributes.
   * @return the hockey stick ItemStack
   */
  private ItemStack createHockeyStick() {
    ItemStack hockeyStick = new ItemStack(Material.STICK);
    ItemMeta meta = hockeyStick.getItemMeta();

    AttributeModifier attackSpeedModifier = new AttributeModifier(
            UUID.randomUUID(),
            "generic_attack_speed",
            2.5,
            AttributeModifier.Operation.ADD_NUMBER,
            EquipmentSlot.HAND
    );

    meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, attackSpeedModifier);
    meta.setDisplayName("§aHockey Stick");
    meta.addEnchant(Enchantment.KNOCKBACK, 1, true);
    hockeyStick.setItemMeta(meta);

    return hockeyStick;
  }


  /**
   * Creates and equips the player with their team's specific uniform.
   * @param team is the team the player is on
   * @param p is the player to give the uniform to
   */
  private void createUniform(String team, Player p) {
    Color color;
    String teamColorCode;
    String teamName;

    if (team.equalsIgnoreCase("home")) {
      color = Color.RED;
      teamColorCode = "§c";
      teamName = "Home";
    } else if (team.equalsIgnoreCase("away")) {
      color = Color.BLUE;
      teamColorCode = "§9";
      teamName = "Away";
    }
    else {
      color = Color.WHITE;
      teamColorCode = "§7";
      teamName = "Ref";
    }

    ItemStack visor = new ItemStack(Material.LEATHER_HELMET);
    LeatherArmorMeta visorItemMeta = (LeatherArmorMeta) visor.getItemMeta();
    visorItemMeta.setColor(Color.BLACK);
    visorItemMeta.setDisplayName(teamColorCode + teamName + " Visor");
    visorItemMeta.setUnbreakable(true);
    visor.setItemMeta(visorItemMeta);

    ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
    LeatherArmorMeta chestMeta = (LeatherArmorMeta) chestplate.getItemMeta();
    chestMeta.setColor(color);
    chestMeta.setDisplayName(teamColorCode + teamName + " Jersey");
    chestMeta.setUnbreakable(true);
    chestplate.setItemMeta(chestMeta);

    ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS);
    LeatherArmorMeta leggingsMeta = (LeatherArmorMeta) leggings.getItemMeta();
    leggingsMeta.setColor(color);
    leggingsMeta.setDisplayName(teamColorCode + teamName + " Pants");
    leggingsMeta.setUnbreakable(true);
    leggings.setItemMeta(leggingsMeta);

    ItemStack skates = new ItemStack(Material.LEATHER_BOOTS);
    LeatherArmorMeta skateMeta = (LeatherArmorMeta) skates.getItemMeta();
    skateMeta.setColor(Color.BLACK);
    skateMeta.setDisplayName(teamColorCode + teamName + " Skates");
    skateMeta.setUnbreakable(true);
    skates.setItemMeta(skateMeta);

    p.getInventory().setHelmet(visor);
    p.getInventory().setChestplate(chestplate);
    p.getInventory().setLeggings(leggings);
    p.getInventory().setBoots(skates);
  }


  /**
   * Removes the player from this rink.
   * @param p is the player to remove
   */
  public void removePlayerFromRink(Player p) {
    fans.remove(p);
    refs.remove(p);
    homeTeam.remove(p);
    awayTeam.remove(p);
    this.removePersonalPuck(p);
    this.scoreboard.hideFromPlayer(p);
  }

  /**
   * Returns the name of the rink.
   * @return the name
   */
  public String getName() {
    return this.name;
  }

  /**
   * Returns the name of the home team.
   * @return the name
   */
  public String getHomeTeamName() {
    return this.homeTeamName;
  }

  /**
   * Returns the name of the away team.
   * @return the name
   */
  public String getAwayTeamName() {
    return this.awayTeamName;
  }

  /**
   * Locks or unlocks teams, preventing or allow players to join the rink.
   * @param p is the player that sent the command
   */
  public void lockTeams(Player p) {
    if (teamLocked) {
      teamLocked = false;
      p.sendMessage("§6[§bBH§6] §cTeams are now unlocked.");
    }
    else {
      teamLocked = true;
      p.sendMessage("§6[§bBH§6] §cTeams are now locked.");
    }
  }


  /**
   * Skips to the face off to prevent waiting between delays.
   * @param p is the player that sent the command.
   */
  public void skipFO(Player p) {
    if (this.game == null) {
      p.sendMessage("§6[§bBH§6] §cThere is no active game to skip the faceoff.");
      return;
    }

    game.skipFaceOff(p);
  }

  /**
   * Gets the locations of the goal zone.
   * @param goalCenter is the goal center to set up.
   * @param rinkCenter is the rink center
   * @return the list of the locations that are in the goal zone
   */
  public List<Location> getGoalZone(Location goalCenter, Location rinkCenter) {
    List<Location> goalZone = new ArrayList<>();

    Vector goalToCenter = rinkCenter.toVector().subtract(goalCenter.toVector()).normalize();

    Vector goalDirection = goalToCenter.multiply(-1);
    //TODO modify a bit maybe
    Location base = goalCenter.clone().add(goalDirection.clone().multiply(1.43));

    Vector cross = new Vector(-goalDirection.getZ(), 0, goalDirection.getX());

    for (int i = -2; i <= 2; i++) {
      Vector offset = cross.clone().multiply(i);
      Location zoneBlock = base.clone().add(offset);
      goalZone.add(zoneBlock.getBlock().getLocation());
    }

    return goalZone;
  }

  /**
   * Gets the total amount of players at a rink.
   * @return the number of players at the rink
   */
  public int getTotalPlayers() {
    return fans.size() + homeTeam.size() + awayTeam.size() + refs.size();
  }

  /**
   * Gets all the players in the rink
   * @return a set of all the players in the rink
   */
  public Set<Player> getAllPlayers() {
    Set<Player> all = new HashSet<>();
    all.addAll(refs);
    all.addAll(fans);
    all.addAll(homeTeam);
    all.addAll(awayTeam);
    return all;
  }

  /**
   * Returns if the player is at this rink.
   * @return true if they are
   */
  public boolean containsPlayer(Player p) {
    return fans.contains(p) || homeTeam.contains(p)
            || awayTeam.contains(p) || refs.contains(p);
  }

  /**
   * Gets the Center Location of the Rink
   */
  public Location getCenterIce() {
    return this.centerIce;
  }

  /**
   * Gets the Location of the Red goal zone.
   */
  public List<Location> getHomeGoalZone() {
    return this.homeGoalZone;
  }

  /**
   * Gets the team of a specific player
   * @param p is the player to check
   * @return the string of team the player is on
   */
  public String getTeam(Player p) {
    if (homeTeam.contains(p)) {
      return "home";
    }
    if (awayTeam.contains(p)) {
      return "away";
    }
    if (refs.contains(p)) {
      return "ref";
    }

    return "none";
  }

  /**
   * Gets the Location of the Blue goal zone.
   */
  public List<Location> getAwayGoalZone() {
    return this.awayGoalZone;
  }

  //TODO Change later for when different types of games.
  /**
   * Starts the game.
   */
  public void startGame() {
    for (Slime puck : this.personalPucks.values()) {
      if (puck != null && !puck.isDead()) {
        puck.remove();
      }
    }

    this.personalPucks.clear();
    this.game = new NormalGame(this, this.plugin);
    this.state = GameState.GAME;
    this.game.startGame();
  }

  /**
   * Ends the game.
   */
  public void endGame() {
    this.state = GameState.END_GAME;
    this.game.endGame();
  }

  /**
   * Gets the GameState of the game.
   * @return the gameState
   */
  public GameState getGameState() {
    return this.state;
  }

  /**
   * Adds the player that last hit the slime.
   * @param p is the player to add to last hit
   */
  public void addPlayerLastHit(Player p) {
    if (this.game != null && this.state == GameState.GAME) {
      this.game.addLastHit(p);
    }
  }

  /**
   * Updates the time of the current game.
   * @param time is the time to change it to.
   * @param p is the player that sent the command
   */
  public void updateTime(String time, Player p) {
    if (this.game == null) {
      p.sendMessage("§6[§bBH§6] §cThere is no game to update.");
    }
    else {
      this.game.updateTime(time, p);
    }
  }

  public void displayStats(Player p) {
    if (this.game == null) {
      p.sendMessage("§6[§bBH§6] §cThere is no game to display.");
    }
    else {
      this.game.displayStats(p);
    }
  }

  /**
   * All the players on the home team.
   * @return the home team players
   */
  public Set<Player> getHomeTeamPlayers() {
    return this.homeTeam;
  }

  /**
   * All the players on the away team.
   * @return the away team players
   */
  public Set<Player> getAwayTeamPlayers() {
    return this.awayTeam;
  }

  /**
   * All the players that are refs
   * @return the refs
   */
  public Set<Player> getRefs() {
    return this.refs;
  }

  /**
   * Gets the current game associated with the rink.
   * @return the game
   */
  public Game getGame() {
    return this.game;
  }

  /**
   * Gets the current game associated with the rink.
   * @return the game
   */
  public GameScoreboard getScoreboard() {
    return this.scoreboard;
  }

  /**
   * Sets the current rink to pregame and updates the scoreboard.
   */
  public void setToPregame() {
    this.state = GameState.PREGAME;
    if (this.game != null) {
      this.game = null;
      this.scoreboard.update();
    }
  }

  /**
   * Sets the current rink to endgame.
   */
  public void setToEndGame() {
    this.state = GameState.END_GAME;
  }

  /**
   * Spawns a puck for the player at their given location.
   * @param p is the player that sent the command
   */
  public void summonPersonalPuck(Player p) {
    UUID playerId = p.getUniqueId();

    if (this.personalPucks.containsKey(playerId)) {
      Slime existingPuck = this.personalPucks.get(playerId);

      if (existingPuck == null || existingPuck.isDead()) {
        this.personalPucks.remove(playerId);
      } else {
        existingPuck.teleport(p.getLocation());
        return;
      }
    }

    Slime newPuck = (Slime) p.getWorld().spawnEntity(p.getLocation(), EntityType.SLIME);
    newPuck.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,
            Integer.MAX_VALUE, 150, false, false));
    newPuck.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
            Integer.MAX_VALUE, 150, false, false));
    newPuck.setSize(1);
    newPuck.setWander(false);
    newPuck.setAware(true);
    newPuck.setAI(true);
    newPuck.setGravity(true);
    newPuck.setRemoveWhenFarAway(false);
    newPuck.setPersistent(true);

    this.personalPucks.put(playerId, newPuck);
  }

  /**
   * Sets the current rink to pregame and updates the scoreboard.
   */
  public void toggleHits(Player p) {
    if (this.allowHits) {
      this.allowHits = false;
      p.sendMessage("§6[§bBH§6] §cHitting is now disabled.");
    }
    else {
      this.allowHits = true;
      p.sendMessage("§6[§bBH§6] §aHitting is now enabled.");
    }
  }

  /**
   * Returns whether hitting is enabled or disabled for this rink
   * @return true if its allowed
   */
  public boolean isHittingAllowed() {
    return this.allowHits;
  }

  /**
   * Gets the home bench location
   * @return the home location bench
   */
  public Location getHomeBench() {
    return this.homeBench;
  }

  /**
   * Gets the away bench location
   * @return the away location bench
   */
  public Location getAwayBench() {
    return this.awayBench;
  }

  /**
   * Gets the penalty box location.
   * @return the penalty box location
   */
  public Location getPenaltyBox() {
    return this.penaltyBox;
  }

  /**
   * Removes a player's personal puck as well as the player from the personal puck list.
   * @param p is the player that disconnected
   */
  public void removePersonalPuck(Player p) {
    UUID playerId = p.getUniqueId();
    Slime puck = this.personalPucks.get(playerId);
    if (puck != null && !puck.isDead()) {
      puck.remove();
    }
    this.personalPucks.remove(playerId);
  }

  /**
   * Checks whether the player is a keeper or not.
   * @param player is the player to check
   * @return true if the player is a keeper
   */
  public boolean isKeeper(Player player) {
    for (Player p : this.goalies) {
      if (p.getName().equalsIgnoreCase(player.getName())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Adds or removes a player from the goalie position depending on if they are already a goalie.
   * @param p is the player to add or remove from goalie.
   */
  public void changeToGoalie(Player p) {
    if (this.goalies.contains(p)) {
      this.goalies.remove(p);
      p.sendMessage("§6[§bBH§6] §cYou are no longer a goalie.");
    }
    else {
      this.goalies.add(p);
      p.sendMessage("§6[§bBH§6] §aYou are now a goalie.");
    }
  }

  /**
   * Method in which a whistle is blown to halt the game.
   * @param p is the player that blew the whistle
   */
  public void blowWhistle(Player p) {
    if (this.game == null || this.state != GameState.GAME) {
      p.sendMessage("§6[§bBH§6] §cThere is no game to stop.");
    }
    else {
      p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 50, -1);
      this.game.whistleBlown();
    }
  }

  /**
   * Method to give a penalty to a specific player.
   * @param commandSender is the person that sent the command
   * @param type is the type of penalty call (give, edit, end)
   * @param player is the player to give the pen to
   * @param reason is the reason for the pen
   * @param time is the time, in seconds, for the pen
   */
  public void givePenalty(Player commandSender, String type, String player, String reason,
                          String time) {
    if (this.state == GameState.GAME) {
      this.game.penalty(commandSender, type, player, reason, time);
    }
    else {
      commandSender.sendMessage("§6[§bBH§6] §cA game must be on going for you to give a penalty.");
    }
  }

  /**
   * Sets the team name to the specified name.
   * @param team is the team to change
   * @param teamName is the name to change to
   * @param p is the player that issued the command
   */
  public void setTeamName(String team, String teamName, Player p) {
    switch (team) {
      case "home":
        this.homeTeamName = teamName;
        p.sendMessage("§6[§bBH§6] §aHome team name changed to " + teamName + ".");
        break;
      case "away":
        this.awayTeamName = teamName;
        p.sendMessage("§6[§bBH§6] §aAway team name changed to " + teamName + ".");
        break;
      default:
        p.sendMessage("§6[§bFH§6] §cUnknown team: " + team);
        break;
    }
  }
}

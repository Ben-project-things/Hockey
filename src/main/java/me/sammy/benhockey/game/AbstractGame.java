package me.sammy.benhockey.game;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Represents the shared attributes any game implementation has.
 */
public abstract class AbstractGame implements Game {
  protected final UUID gameId;
  protected final Rink rink;
  protected final JavaPlugin plugin;
  protected int homeScore;
  protected int awayScore;
  protected int period;
  protected int timeLeft;
  protected boolean gamePaused;

  protected Entity puck;
  protected final LinkedList<Player> lastHits = new LinkedList<>();
  private final Map<StatsKey, GameStats> playerStats = new HashMap<>();
  protected final Map<UUID, Integer> activePenalties = new HashMap<>();

  protected BukkitRunnable periodTimer;
  protected BukkitRunnable countdownTimer;
  protected BukkitRunnable puckChecker;
  protected BukkitRunnable pendingDelayedTask;

  /**
   * Represents the constructor for an abstract game in which all games share these fields.
   * @param rink is the rink associated with this game
   * @param plugin is a reference to the java plugin for scheduling events
   */
  public AbstractGame(Rink rink, JavaPlugin plugin) {
    this.rink = rink;
    this.plugin = plugin;
    this.gameId = UUID.randomUUID();
    this.homeScore = 0;
    this.awayScore = 0;
    this.period = 1;
    this.timeLeft = 300000;
    this.gamePaused = false;

  }

  @Override
  public void startGame() {
    rink.getScoreboard().update();
    this.startFaceoff();
  }

  /**
   * Starts a face off
   */
  protected void startFaceoff() {
    this.countdownTimer = new BukkitRunnable() {
      int secondsLeft = 5;

      @Override
      public void run() {
        if (secondsLeft <= 0) {
          summonPuck(rink.getCenterIce());
          startGoalChecks();
          startGameTimer(timeLeft);
          cancel();
          return;
        }

        for (Player p : rink.getAllPlayers()) {
          p.sendTitle("", "§a" + secondsLeft, 5, 10, 5);
          p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 25, -1);
        }

        secondsLeft--;
      }
    };

    this.countdownTimer.runTaskTimer(this.plugin, 0, 20);
  }

  /**
   * Starts the in-game timer for a period.
   * @param durationMilli the number of seconds to count down from.
   */
  protected void startGameTimer(int durationMilli) {
    if (this.timeLeft <= 0) {
      this.timeLeft = durationMilli;
    }
    this.rink.getScoreboard().update();

    if (this.periodTimer != null) {
      this.periodTimer.cancel();
    }

    this.periodTimer = new BukkitRunnable() {
      @Override
      public void run() {
        if (timeLeft <= 0) {
          endPeriod();
          cancel();
          return;
        }

        if (timeLeft == 1000 || timeLeft == 2000 || timeLeft == 3000 ||
                timeLeft == 4000 || timeLeft == 5000) {
          for (Player p : rink.getAllPlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 150, -1);
          }
        }

        timeLeft -= 100;
        updatePenalties();
        rink.getScoreboard().update();
      }
    };
    this.periodTimer.runTaskTimer(this.plugin, 0, 2);
  }

  /**
   * Method that deals with checking when the puck enters a goal zone.
   */
  private void startGoalChecks() {
    if (puckChecker != null) {
      puckChecker.cancel();
    }

    puckChecker = new BukkitRunnable() {
      @Override
      public void run() {
        if (puck == null || puck.isDead()) {
          return;
        }

        Location loc = puck.getLocation();

        Location blockLoc = loc.getBlock().getLocation();
        if (rink.getHomeGoalZone().contains(blockLoc)) {
          scoreGoal("away");
        } else if (rink.getAwayGoalZone().contains(blockLoc)) {
          scoreGoal("home");
        }
      }
    };

    puckChecker.runTaskTimer(plugin, 0, 1);
  }

  /**
   * Runs the logic behind ending a period.
   */
  protected void endPeriod() {
    for (Player p : rink.getAllPlayers()) {
      p.sendTitle("", "§bEnd of Period " + period, 10, 20, 10);
      p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
    }

    if (puck != null && !puck.isDead()) {
      puck.remove();
    }

    if (puckChecker != null) {
      puckChecker.cancel();
    }

    period++;
    timeLeft = 300000;

    if (period > 3) {
      if (homeScore == awayScore) {
        runAfterDelay(30, this::startFaceoff);
      } else {
        endGame();
      }
    } else {
      runAfterDelay(30, this::startFaceoff);
    }
  }

  /**
   * Deals with the logic when a team scores a goal.
   * @param scoringTeam is the team that scored
   */
  protected void scoreGoal(String scoringTeam) {
    if (this.periodTimer != null) {
      this.periodTimer.cancel();
      this.periodTimer = null;
    }

    this.puck.remove();
    this.puck = null;

    Location goalLoc = scoringTeam.equalsIgnoreCase("home") ?
            this.rink.getAwayGoalZone().get(2) : this.rink.getHomeGoalZone().get(2);
    GoalContribution gc;
    String teamColor;

    if (scoringTeam.equalsIgnoreCase("home")) {
      this.homeScore++;
      teamColor = "c";
      gc = getGoalScorerAndAssists("home");
    } else {
      this.awayScore++;
      teamColor = "9";
      gc = getGoalScorerAndAssists("away");
    }

    if (gc.getScorer() != null && !gc.isOwnGoal()) {
      getOrCreateStats(gc.getScorer()).addGoal();
    }

    if (gc.isOwnGoal()) {
      String ownGoalTeam = this.rink.getTeam(gc.getScorer());
      for (int i = 1; i < this.lastHits.size(); i++) {
        Player playerToCheck = this.lastHits.get(i);
        String playerTeam = this.rink.getTeam(playerToCheck);

        if (playerTeam != null && !playerTeam.equalsIgnoreCase(ownGoalTeam)) {
          getOrCreateStats(playerToCheck).addGoal();
          break;
        }
      }
    }

    for (Player assists : gc.getAssisters()) {
      getOrCreateStats(assists).addAssist();
    }


    World world = goalLoc.getWorld();

    world.createExplosion(goalLoc, 8.0f, false, false);

    this.rink.getScoreboard().update();
    String title;
    String subtitle;

    if (gc.isOwnGoal()) {
      title = "§6Own Goal";
      subtitle = "§7Scored by: " + gc.getScorer().getName();
    } else {
      title = "§" + teamColor + gc.getScorer().getName() + "§" + teamColor + " Scored";
      if (gc.getAssisters().isEmpty()) {
        subtitle = "";
      } else {
        String assists = gc.getAssisters().stream()
                .map(Player::getName)
                .collect(Collectors.joining(", "));
        subtitle = "§7Assisted by: " + assists;
      }
    }

    for (Player p : this.rink.getAllPlayers()) {
      p.sendTitle(title, subtitle, 10,
              40, 10);
      p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    this.lastHits.clear();

    if (period > 3) {
      runAfterDelay(5, this::endGame);
    } else {
      runAfterDelay(10, this::startFaceoff);
    }
  }

  /**
   * Helper method to run a certain runnable after a specified delay.
   * @param seconds is the delay in seconds.
   * @param task is the task to run
   */
  private void runAfterDelay(int seconds, Runnable task) {
    if (this.pendingDelayedTask != null) {
      this.pendingDelayedTask.cancel();
    }

    this.pendingDelayedTask = new BukkitRunnable() {
      @Override
      public void run() {
        pendingDelayedTask = null;
        task.run();
      }
    };
    this.pendingDelayedTask.runTaskLater(this.plugin, seconds * 20L);
  }

  @Override
  public void endGame() {
    this.rink.setToEndGame();
    if (this.countdownTimer != null) {
      this.countdownTimer.cancel();
      this.countdownTimer = null;
    }

    if (this.periodTimer != null) {
      this.periodTimer.cancel();
    }

    if (this.puckChecker != null) {
      this.puckChecker.cancel();
      this.puckChecker = null;
    }

    if (this.pendingDelayedTask != null) {
      this.pendingDelayedTask.cancel();
      this.pendingDelayedTask = null;
    }

    if (puck != null && !puck.isDead()) {
      puck.remove();
    }

    for (Player p : rink.getAllPlayers()) {
      p.sendTitle("", "§6Game Over", 10, 30, 10);
      p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
      p.sendMessage("§6[§bBH§6] §6Final Score: §c" + homeScore + " §7- §9" + awayScore);
    }
  }

  @Override
  public int getHomeScore() {
    return this.homeScore;
  }

  @Override
  public int getAwayScore() {
    return this.awayScore;
  }

  @Override
  public int getPeriod() {
    return this.period;
  }

  @Override
  public int getTimeLeft() {
    return this.timeLeft;
  }

  @Override
  public void skipFaceOff(Player player) {
    if (this.pendingDelayedTask != null) {
      this.pendingDelayedTask.cancel();
      this.pendingDelayedTask = null;
      this.rink.getScoreboard().update();
      this.startFaceoff();
      this.gamePaused = false;
      player.sendMessage("§6[§bBH§6] §aStarting the faceoff now.");
    }
    else if (this.gamePaused) {
      this.rink.getScoreboard().update();
      this.startFaceoff();
      this.gamePaused = false;
      player.sendMessage("§6[§bBH§6] §aStarting the faceoff now (game was paused).");
    }
    else {
      player.sendMessage("§6[§bBH§6] §cThere is no delay to skip right now.");
    }
  }

  @Override
  public void addLastHit(Player player) {
    getOrCreateStats(player).addTouch();
    lastHits.remove(player);
    lastHits.addFirst(player);
    if (lastHits.size() > 4) {
      lastHits.removeLast();
    }
  }

  /**
   * Creates a new game stats for a player once they do an action.
   * @param p is the player to create or add stats to their game stats
   * @return the game stats for a player
   */
  private GameStats getOrCreateStats(Player p) {
    String  team   = rink.getTeam(p);
    boolean isGK   = rink.isKeeper(p);

    StatsKey key   = new StatsKey(p.getUniqueId(), team, isGK);

    return playerStats.computeIfAbsent(
            key,
            k -> new GameStats(k.playerId(), k.team(), k.goalie())
    );
  }

  /**
   * Calculates the goal contributors.
   * @param scoringTeam the team that scored the goal
   * @return the goal contributors to the last goal
   */
  protected GoalContribution getGoalScorerAndAssists(String scoringTeam) {
    if (lastHits.isEmpty()) {
      return new GoalContribution(null, Collections.emptyList(), true);
    }

    Player scorer = lastHits.get(0);
    String scorerTeam = rink.getTeam(scorer);

    if (scorerTeam == null) {
      return new GoalContribution(null, Collections.emptyList(), true);
    }

    if (!scorerTeam.equalsIgnoreCase(scoringTeam)) {
      return new GoalContribution(scorer, Collections.emptyList(), true);
    }

    List<Player> assisters = new ArrayList<>();
    for (int i = 1; i < this.lastHits.size(); i++) {
      Player assister = this.lastHits.get(i);
      String assisterTeam = this.rink.getTeam(assister);

      if (assisterTeam == null || !assisterTeam.equalsIgnoreCase(scorerTeam)) break;
      assisters.add(assister);
    }

    return new GoalContribution(scorer, assisters, false);
  }

  @Override
  public void updateTime(String time, Player player) {
    try {
      double seconds = Double.parseDouble(time);
      this.timeLeft = (int) (seconds * 1000);
      this.rink.getScoreboard().update();

      player.sendMessage("§6[§bBH§6] Time updated.");
    } catch (NumberFormatException e) {
      player.sendMessage("§6[§bBH§6] §cInvalid time format. Please enter a number in seconds.");
    }
  }

  @Override
  public void displayStats(Player p) {
    p.sendMessage("§6===== §bGame Stats §6=====");

    printTeam(p, "home", "§c" + rink.getHomeTeamName() + " Team:");
    printTeam(p, "away", "§9" + rink.getAwayTeamName() + " Team:");
  }

  /**
   * Helper method which prints out the stats for a specific team.
   * @param p is the player that sent the command
   * @param side is the side the team is on
   * @param header is the header of the title for the teams
   */
  private void printTeam(Player p, String side, String header) {
    p.sendMessage(header);

    playerStats.entrySet().stream()
            .filter(entry -> entry.getKey().team().equalsIgnoreCase(side))
            .sorted(Comparator
                    .comparingInt((Map.Entry<StatsKey, GameStats> e) -> e.getValue().getPoints())
                    .reversed())
            .forEach(entry -> {
              UUID playerId = entry.getKey().playerId();
              OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);

              Player onlinePlayer = offlinePlayer.isOnline() ? offlinePlayer.getPlayer() : null;

              String formattedStats = (onlinePlayer != null)
                      ? entry.getValue().formatForPlayer(onlinePlayer)
                      : entry.getValue().formatForPlayer(offlinePlayer);

              p.sendMessage(formattedStats);
            });
  }

  @Override
  public void whistleBlown() {
    this.gamePaused = true;
    if (this.countdownTimer != null) {
      this.countdownTimer.cancel();
      this.countdownTimer = null;
    }

    if (this.periodTimer != null) {
      this.periodTimer.cancel();
    }

    if (this.pendingDelayedTask != null) {
      this.pendingDelayedTask.cancel();
      this.pendingDelayedTask = null;
    }

    if (puck != null && !puck.isDead()) {
      puck.remove();
    }
  }

  //TODO Fix this penalty stuff
  @Override
  public void penalty(Player commandSender, String type, String playerName, String reason,
                      String timeStr) {
    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
    Player onlinePlayer = (Player) offlinePlayer;
    UUID uuid = offlinePlayer.getUniqueId();

    int timeSeconds = 0;
    try {
      timeSeconds = Integer.parseInt(timeStr) * 1000;
    } catch (NumberFormatException e) {
      commandSender.sendMessage("§6[§bBH§6] §cInvalid time entered.");
      return;
    }

    switch (type.toLowerCase()) {
      case "give":
        if (this.activePenalties.containsKey(uuid)) {
          commandSender.sendMessage("§6[§bBH§6] §cPlayer already has a penalty.");
          return;
        }
        activePenalties.put(uuid, timeSeconds);
        onlinePlayer.teleport(rink.getPenaltyBox());
        for (Player p : rink.getAllPlayers()) {
          p.sendTitle("§6Penalty: " + reason, "§7Player: " + playerName + "| Time: " + timeStr +
                          "s",
                  10, 40, 10);
        }
        break;

      case "edit":
        if (!this.activePenalties.containsKey(uuid)) {
          commandSender.sendMessage("§6[§bBH§6] §cPlayer has no active penalty.");
          return;
        }

        this.activePenalties.put(uuid, timeSeconds);
        commandSender.sendMessage("§6[§bBH§6] §aPlayer " + playerName + "§a's penalty changed to " +
                timeSeconds / 100 + " seconds.");
        break;

      case "end":
        if (!this.activePenalties.containsKey(uuid)) {
          commandSender.sendMessage("§6[§bBH§6] §cPlayer has no active penalty.");
          return;
        }

        endPenalty(uuid);
        commandSender.sendMessage("§6[§bBH§6] §aPlayer " + playerName + "§a's penalty ended.");
        break;

      default:
        commandSender.sendMessage("§6[§bBH§6] §cUnknown penalty command type.");
        break;
    }
  }

  /**
   * Method to update the penalty times as the periodTimer goes on.
   */
  private void updatePenalties() {
    List<UUID> penaltiesToRemove = new ArrayList<>();

    for (Map.Entry<UUID, Integer> entry : activePenalties.entrySet()) {
      int newTime = entry.getValue() - 100;
      if (newTime <= 0) {
        penaltiesToRemove.add(entry.getKey());
      } else {
        activePenalties.put(entry.getKey(), newTime);
      }
    }

    for (UUID uuid : penaltiesToRemove) {
      endPenalty(uuid);
    }
  }

  /**
   * Ends a player's penalty.
   * @param uuid is the uuids of the player to end.
   */
  private void endPenalty(UUID uuid) {
    this.activePenalties.remove(uuid);
    OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
    if (p.isOnline()) {
      Player onlinePlayer = (Player) p;
      Location penaltyExit = rink.getPenaltyBox().clone().add(
              rink.getCenterIce().toVector().subtract(rink.getPenaltyBox().toVector()).normalize().multiply(3)
      );
      penaltyExit.setY(rink.getPenaltyBox().getY());
      penaltyExit.setX(penaltyExit.getBlockX() + 0.5);
      penaltyExit.setZ(penaltyExit.getBlockZ() + 0.5);
      onlinePlayer.teleport(penaltyExit);

      onlinePlayer.sendMessage("§6[§bBH§6] §aYour penalty has ended!");
    }
  }

  /**
   * Spawns a puck at the given location.
   * @param location is the location to spawn the puck
   */
  @Override
  public abstract void summonPuck(Location location);
}

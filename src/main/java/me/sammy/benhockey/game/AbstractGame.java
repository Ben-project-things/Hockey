package me.sammy.benhockey.game;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
  protected BukkitRunnable intermissionTimer;
  protected int intermissionTimeLeft;
  protected String intermissionLabel;
  protected boolean firstFaceoffTouchPending;
  protected boolean faceoffCountdownActive;
  private Location lastPuckLocation;
  private UUID mostRecentGoalScorerId;

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
    this.intermissionTimeLeft = 0;
    this.intermissionLabel = "Faceoff";
    this.firstFaceoffTouchPending = false;
    this.faceoffCountdownActive = false;
    this.lastPuckLocation = null;
    this.mostRecentGoalScorerId = null;

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
    this.faceoffCountdownActive = true;
    this.countdownTimer = new BukkitRunnable() {
      int secondsLeft = 5;

      @Override
      public void run() {
        if (secondsLeft <= 0) {
          summonPuck(rink.getCenterIce());
          faceoffCountdownActive = false;
          firstFaceoffTouchPending = true;
          if (puck != null) {
            lastPuckLocation = puck.getLocation().clone();
          }
          lastHits.clear();
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
        sendPenaltyTimers();
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
        lastPuckLocation = loc.clone();

        if (isEntirePuckInsideGoalZone(puck, "home")) {
          scoreGoal("away");
        } else if (isEntirePuckInsideGoalZone(puck, "away")) {
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
      sendStagedTitle(
              p,
              "§b§lEnd of Period " + period,
              "§6Score: §c" + homeScore + " §7- §9" + awayScore
      );
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
        this.intermissionLabel = "Faceoff";
        runAfterDelay(30, this::startFaceoff);
      } else {
        endGame();
      }
    } else {
      this.intermissionLabel = "Intermission";
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
    this.lastPuckLocation = null;
    this.faceoffCountdownActive = false;

    Location goalLoc = scoringTeam.equalsIgnoreCase("home")
            ? this.rink.getAwayGoalCenter()
            : this.rink.getHomeGoalCenter();
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
      this.mostRecentGoalScorerId = gc.getScorer().getUniqueId();
      GameStats scorerStats = getOrCreateStats(gc.getScorer());
      scorerStats.addGoal();
      scorerStats.addShotOnTarget();
    } else {
      this.mostRecentGoalScorerId = null;
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
    if (world != null) {
      Particle.DustOptions goalDust = scoringTeam.equalsIgnoreCase("home")
              ? new Particle.DustOptions(org.bukkit.Color.BLUE, 1.6f)
              : new Particle.DustOptions(org.bukkit.Color.RED, 1.6f);
      spawnGoalParticles(world, goalLoc, goalDust);
      world.playSound(goalLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.9f);
      pushPlayersFromGoal(goalLoc);
    }

    this.rink.getScoreboard().update();
    String title;
    String subtitle;

    String scoringTeamName = scoringTeam.equalsIgnoreCase("home")
            ? this.rink.getHomeTeamName()
            : this.rink.getAwayTeamName();
    if (gc.isOwnGoal()) {
      String scorerName = gc.getScorer() != null ? gc.getScorer().getName() : "Unknown";
      String scorerColor = teamColor;
      if (gc.getScorer() != null) {
        String scorerTeam = this.rink.getTeam(gc.getScorer());
        if ("home".equalsIgnoreCase(scorerTeam)) {
          scorerColor = "c";
        } else if ("away".equalsIgnoreCase(scorerTeam)) {
          scorerColor = "9";
        }
      }
      title = "§" + teamColor + "§l" + scoringTeamName + " GOAL";
      subtitle = "§7Scored by: §" + scorerColor + scorerName + " §8(§7Own Goal§8)";
    } else {
      title = "§" + teamColor + "§l" + scoringTeamName + " GOAL";
      subtitle = buildGoalSubtitle(teamColor, gc);
    }

    for (Player p : this.rink.getAllPlayers()) {
      sendStagedTitle(p, title, subtitle);
      p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    this.lastHits.clear();

    if (period > 3) {
      runAfterDelay(5, this::endGame);
    } else {
      this.intermissionLabel = "Faceoff";
      runAfterDelay(10, this::startFaceoff);
    }
  }

  @Override
  public void forceGoal(String scoringTeam) {
    if (!"home".equalsIgnoreCase(scoringTeam) && !"away".equalsIgnoreCase(scoringTeam)) {
      return;
    }

    if (this.puck == null || this.puck.isDead()) {
      return;
    }

    scoreGoal(scoringTeam.toLowerCase());
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

    if (this.intermissionTimer != null) {
      this.intermissionTimer.cancel();
    }

    if (this.intermissionLabel == null || this.intermissionLabel.isEmpty()) {
      this.intermissionLabel = "Faceoff";
    }
    this.intermissionTimeLeft = seconds * 1000;
    this.rink.getScoreboard().update();

    this.intermissionTimer = new BukkitRunnable() {
      @Override
      public void run() {
        intermissionTimeLeft -= 100;
        if (intermissionTimeLeft <= 0) {
          intermissionTimeLeft = 0;
          clearIntermissionTimers();
          this.cancel();
          intermissionTimer = null;
          rink.getScoreboard().update();
          return;
        }

        sendIntermissionTimer();
        rink.getScoreboard().update();
      }
    };
    this.intermissionTimer.runTaskTimer(this.plugin, 0L, 2L);

    this.pendingDelayedTask = new BukkitRunnable() {
      @Override
      public void run() {
        pendingDelayedTask = null;
        if (intermissionTimer != null) {
          intermissionTimer.cancel();
          intermissionTimer = null;
        }
        clearIntermissionTimers();
        intermissionTimeLeft = 0;
        rink.getScoreboard().update();
        task.run();
      }
    };
    this.pendingDelayedTask.runTaskLater(this.plugin, seconds * 20L);
  }

  @Override
  public void endGame() {
    this.rink.setToEndGame();
    this.faceoffCountdownActive = false;
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

    if (this.intermissionTimer != null) {
      this.intermissionTimer.cancel();
      this.intermissionTimer = null;
    }
    this.intermissionTimeLeft = 0;
    clearIntermissionTimers();

    if (puck != null && !puck.isDead()) {
      puck.remove();
    }
    this.lastPuckLocation = null;

    String winnerTitle;
    if (homeScore > awayScore) {
      winnerTitle = "§c§l" + rink.getHomeTeamName() + " Wins";
    } else if (awayScore > homeScore) {
      winnerTitle = "§9§l" + rink.getAwayTeamName() + " Wins";
    } else {
      winnerTitle = "§e§lTie Game";
    }

    for (Player p : rink.getAllPlayers()) {
      sendStagedTitle(
              p,
              winnerTitle,
              "§6Final Score: §c" + homeScore + " §7- §9" + awayScore
      );
      p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
      p.sendMessage("§6[§bBH§6] §6Final Score: §c" + homeScore + " §7- §9" + awayScore);
      displayStats(p);
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
  public int getIntermissionTimeLeft() {
    return this.intermissionTimeLeft;
  }

  @Override
  public String getIntermissionLabel() {
    return this.intermissionLabel;
  }

  @Override
  public String getPenaltySummary() {
    if (this.activePenalties.isEmpty()) {
      return "None";
    }

    return this.activePenalties.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .limit(3)
            .map(entry -> {
              OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.getKey());
              String name = offline.getName() != null ? offline.getName() : "Unknown";
              int seconds = Math.max(0, entry.getValue() / 1000);
              return name + " " + formatClock(seconds);
            })
            .collect(Collectors.joining(", "));
  }

  @Override
  public String getStrengthSummary() {
    if (this.activePenalties.isEmpty()) {
      return "";
    }

    int homePenalties = 0;
    int awayPenalties = 0;
    int homeSoonest = Integer.MAX_VALUE;
    int awaySoonest = Integer.MAX_VALUE;

    for (Map.Entry<UUID, Integer> entry : this.activePenalties.entrySet()) {
      Player penalized = Bukkit.getPlayer(entry.getKey());
      if (penalized == null) {
        continue;
      }

      String team = this.rink.getTeam(penalized);
      if ("home".equalsIgnoreCase(team)) {
        homePenalties++;
        homeSoonest = Math.min(homeSoonest, entry.getValue());
      } else if ("away".equalsIgnoreCase(team)) {
        awayPenalties++;
        awaySoonest = Math.min(awaySoonest, entry.getValue());
      }
    }

    if (homePenalties == awayPenalties && homePenalties > 0) {
      int remaining = Math.min(homeSoonest, awaySoonest);
      return "§eEven Strength: §f" + formatTime(remaining);
    }
    if (homePenalties > awayPenalties) {
      return "§9Power Play: §f" + formatTime(homeSoonest);
    }
    if (awayPenalties > homePenalties) {
      return "§cPower Play: §f" + formatTime(awaySoonest);
    }
    return "";
  }

  @Override
  public void skipFaceOff(Player player) {
    if (this.pendingDelayedTask != null) {
      this.pendingDelayedTask.cancel();
      this.pendingDelayedTask = null;
      if (this.intermissionTimer != null) {
        this.intermissionTimer.cancel();
        this.intermissionTimer = null;
      }
      this.intermissionTimeLeft = 0;
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
    if (this.firstFaceoffTouchPending) {
      this.firstFaceoffTouchPending = false;
    }
    getOrCreateStats(player).addTouch();
    lastHits.remove(player);
    lastHits.addFirst(player);
    if (lastHits.size() > 4) {
      lastHits.removeLast();
    }
  }

  @Override
  public void addShotOnTarget(Player player) {
    getOrCreateStats(player).addShotOnTarget();
  }

  @Override
  public void addGoalieSave(Player goalie) {
    getOrCreateStats(goalie).addSave();
  }

  @Override
  public Player getLastTouchPlayer() {
    if (this.lastHits.isEmpty()) {
      return null;
    }

    return this.lastHits.getFirst();
  }

  @Override
  public boolean isFaceoffFirstTouch() {
    return this.firstFaceoffTouchPending;
  }

  @Override
  public boolean consumeFaceoffFirstTouch() {
    if (!this.firstFaceoffTouchPending) {
      return false;
    }

    this.firstFaceoffTouchPending = false;
    return true;
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
    this.faceoffCountdownActive = false;
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

    if (this.intermissionTimer != null) {
      this.intermissionTimer.cancel();
      this.intermissionTimer = null;
    }
    this.intermissionTimeLeft = 0;
    clearIntermissionTimers();
    this.rink.getScoreboard().update();

    if (puck != null && !puck.isDead()) {
      puck.remove();
    }
  }

  @Override
  public void penalty(Player commandSender, String type, String playerName, String reason,
                      String timeStr) {
    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
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
        Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null) {
          onlinePlayer.teleport(rink.getPenaltyBox());
        }
        for (Player p : rink.getAllPlayers()) {
          sendStagedTitle(
                  p,
                  "§6§lPenalty: §e§l" + reason,
                  "§7Player: " + colorizePenaltyPlayerName(playerName) + "§8 | §7Time: §f" + timeStr + "s"
          );
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
        entry.setValue(newTime);
      }
    }

    for (UUID uuid : penaltiesToRemove) {
      endPenalty(uuid);
    }
  }

  private String formatClock(int seconds) {
    int minutes = seconds / 60;
    int remainder = seconds % 60;
    return String.format("%d:%02d", minutes, remainder);
  }

  /**
   * Gets the formatted time.
   * - If ≥ 1 minute: shows m:ss
   * - If < 1 minute: shows s.s
   * @param milliseconds total milliseconds remaining
   * @return formatted time string
   */
  private String formatTime(int milliseconds) {
    int safeMilliseconds = Math.max(0, milliseconds);
    int totalSeconds = safeMilliseconds / 1000;

    if (totalSeconds >= 60) {
      return formatClock(totalSeconds);
    }

    double secondsWithMillis = safeMilliseconds / 1000.0;
    return String.format("%.1f", secondsWithMillis);
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
      onlinePlayer.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));

      onlinePlayer.sendMessage("§6[§bBH§6] §aYour penalty has ended!");
    }
  }

  private void sendIntermissionTimer() {
    // Intentionally left blank: intermission timing is shown on the scoreboard only.
  }

  private void clearIntermissionTimers() {
    // Intentionally left blank: intermission timing is shown on the scoreboard only.
  }

  private boolean isEntirePuckInsideGoalZone(Entity puckEntity, String defendedSide) {
    Location goal = "home".equalsIgnoreCase(defendedSide)
            ? this.rink.getHomeGoalCenter()
            : this.rink.getAwayGoalCenter();
    List<Location> goalZone = "home".equalsIgnoreCase(defendedSide)
            ? this.rink.getHomeGoalZone()
            : this.rink.getAwayGoalZone();
    if (goalZone == null || goalZone.isEmpty()) {
      return false;
    }

    double minGoalX = Double.MAX_VALUE;
    double maxGoalX = -Double.MAX_VALUE;
    double minGoalZ = Double.MAX_VALUE;
    double maxGoalZ = -Double.MAX_VALUE;
    for (Location blockLoc : goalZone) {
      minGoalX = Math.min(minGoalX, blockLoc.getBlockX());
      maxGoalX = Math.max(maxGoalX, blockLoc.getBlockX() + 1.0);
      minGoalZ = Math.min(minGoalZ, blockLoc.getBlockZ());
      maxGoalZ = Math.max(maxGoalZ, blockLoc.getBlockZ() + 1.0);
    }

    BoundingBox puckBox = puckEntity.getBoundingBox();
    return puckBox.getMinX() >= minGoalX
            && puckBox.getMaxX() <= maxGoalX
            && puckBox.getMinZ() >= minGoalZ
            && puckBox.getMaxZ() <= maxGoalZ
            && puckBox.getMinY() >= goal.getY() - 0.82
            && puckBox.getMaxY() <= goal.getY() + 2.28;
  }

  private void sendPenaltyTimers() {
    Map<Player, Integer> penalizedPlayers = new HashMap<>();

    for (Map.Entry<UUID, Integer> entry : this.activePenalties.entrySet()) {
      Player penalized = Bukkit.getPlayer(entry.getKey());
      if (penalized == null) {
        continue;
      }
      penalizedPlayers.put(penalized, entry.getValue());
    }

    for (Player penalized : penalizedPlayers.keySet()) {
      int remaining = penalizedPlayers.getOrDefault(penalized, 0);
      penalized.spigot().sendMessage(
              ChatMessageType.ACTION_BAR,
              new TextComponent("§6Penalty Time: §f" + formatTime(remaining))
      );
    }

    for (Player ref : this.rink.getRefs()) {
      Map.Entry<Player, Integer> soonestPenalty = penalizedPlayers.entrySet().stream()
              .min(Map.Entry.comparingByValue())
              .orElse(null);
      if (soonestPenalty == null) {
        ref.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
        continue;
      }
      String penaltyMessage = "§6" + soonestPenalty.getKey().getName()
              + "'s Penalty Time: §f" + formatTime(soonestPenalty.getValue());
      ref.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(penaltyMessage));
    }

    for (Player arenaPlayer : this.rink.getAllPlayers()) {
      if (this.rink.getRefs().contains(arenaPlayer) || penalizedPlayers.containsKey(arenaPlayer)) {
        continue;
      }
      arenaPlayer.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
    }
  }

  private String colorizePenaltyPlayerName(String playerName) {
    Player online = Bukkit.getPlayerExact(playerName);
    if (online != null) {
      String team = this.rink.getTeam(online);
      if ("home".equalsIgnoreCase(team)) {
        return "§c" + playerName;
      }
      if ("away".equalsIgnoreCase(team)) {
        return "§9" + playerName;
      }
    }
    return "§7" + playerName;
  }

  private void spawnGoalParticles(World world, Location goalCenter, Particle.DustOptions goalDust) {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    int particles = 100;
    double radius = 5.0;

    for (int i = 0; i < particles; i++) {
      double angle = random.nextDouble(0, Math.PI * 2);
      double distance = radius * Math.sqrt(random.nextDouble());
      double xOffset = Math.cos(angle) * distance;
      double zOffset = Math.sin(angle) * distance;
      double yOffset = random.nextDouble(0.1, 2.1);

      Location spawn = goalCenter.clone().add(xOffset, yOffset, zOffset);
      world.spawnParticle(Particle.REDSTONE, spawn, 1, 0.1, 0.1, 0.1, 0.2, goalDust);
    }
  }

  private void pushPlayersFromGoal(Location goalLoc) {
    for (Player player : this.rink.getAllPlayers()) {
      Vector push = player.getLocation().toVector().subtract(goalLoc.toVector());
      double distance = Math.max(0.75, push.length());
      if (push.lengthSquared() < 0.0001) {
        push = this.rink.getCenterIce().toVector().subtract(goalLoc.toVector());
      }
      push.normalize();
      double strength = Math.max(0.25, 1.0 - (distance / 22.0));
      Vector velocity = push.multiply(strength * 1.35);
      velocity.setY(0.22 + (strength * 0.18));
      player.setFallDistance(0f);
      player.setVelocity(velocity);
    }
  }

  private void sendStagedTitle(Player player, String title, String subtitle) {
    player.sendTitle(title, "", 10, 200, 10);
    Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
      if (player.isOnline()) {
        player.sendTitle(title, subtitle, 10, 100, 10);
      }
    }, 40L);
  }

  private String buildGoalSubtitle(String teamColor, GoalContribution gc) {
    if (gc.getScorer() == null) {
      return "";
    }

    String scorer = formatPlayerWithStat(gc.getScorer(), true);
    if (gc.getAssisters().isEmpty()) {
      return "§7Scored by: §" + teamColor + scorer;
    }

    String assists = gc.getAssisters().stream()
            .map(player -> "§" + teamColor + formatPlayerWithStat(player, false))
            .collect(Collectors.joining("§7, "));
    return "§7Scored by: §" + teamColor + scorer + " §7Assisted By: " + assists;
  }

  private String formatPlayerWithStat(Player player, boolean goals) {
    int statValue = this.playerStats.entrySet().stream()
            .filter(entry -> entry.getKey().playerId().equals(player.getUniqueId()))
            .map(Map.Entry::getValue)
            .mapToInt(stats -> goals ? stats.getGoals() : stats.getAssists())
            .sum();
    if (statValue <= 1) {
      return player.getName();
    }
    return player.getName() + " §8(§7" + statValue + "§8)";
  }

  /**
   * Spawns a puck at the given location.
   * @param location is the location to spawn the puck
   */
  @Override
  public abstract void summonPuck(Location location);

  @Override
  public Entity getActivePuck() {
    if (this.puck == null || this.puck.isDead() || !this.puck.isValid()) {
      return null;
    }
    return this.puck;
  }

  @Override
  public Player getMostRecentGoalScorer() {
    if (this.mostRecentGoalScorerId == null) {
      return null;
    }
    return Bukkit.getPlayer(this.mostRecentGoalScorerId);
  }

  @Override
  public boolean isFaceoffCountdownActive() {
    return this.faceoffCountdownActive;
  }
}

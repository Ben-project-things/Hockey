package me.sammy.benhockey.game;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Represents an interface of any type of hockey game that can be run.
 */
public interface Game {

  /**
   * Starts the game
   */
  void startGame();

  /**
   * Ends the game
   */
  void endGame();

  /**
   * Displays the stats of the game to a specific player.
   * @param player is the player to display it to.
   */
  void displayStats(Player player);

  /**
   * Updates the time based off the given parameters.
   * @param time is the time to update it to.
   * @param player is the player that requested to update the time.
   */
  void updateTime(String time, Player player);

  /**
   * Gets the home score of the game.
   * @return the home score
   */
  int getHomeScore();

  /**
   * Gets the away score of the game.
   * @return the away score
   */
  int getAwayScore();

  /**
   * Gets the period of the game.
   * @return the period
   */
  int getPeriod();

  /**
   * Gets the time left in the game.
   * @return the time left
   */
  int getTimeLeft();

  /**
   * Gets the remaining intermission delay in milliseconds (0 if there is no active delay).
   * @return intermission time left
   */
  int getIntermissionTimeLeft();

  /**
   * Gets the label for the active delay timer shown on the scoreboard.
   * Typical values are "Faceoff" between goals and "Intermission" between periods.
   * @return delay label
   */
  String getIntermissionLabel();

  /**
   * Gets a compact, scoreboard-friendly list of active penalties and their remaining times.
   * @return active penalty summary text
   */
  String getPenaltySummary();

  /**
   * Gets the current manpower state for scoreboard display (for example, even strength or power play).
   * @return manpower summary text
   */
  String getStrengthSummary();

  /**
   * Adds the touch for the player's given player stats, also calculates the last 4 players to hit.
   * @param player is the player to add the stats to
   */
  void addLastHit(Player player);

  /**
   * Adds a shot-on-target stat for the given player.
   * @param player is the shooter to credit
   */
  void addShotOnTarget(Player player);

  /**
   * Adds a save stat for the given goalie.
   * @param goalie is the goalie to credit
   */
  void addGoalieSave(Player goalie);

  /**
   * Gets the player that most recently touched the puck for this game.
   * @return last touching player, or null if no touches yet
   */
  Player getLastTouchPlayer();

  /**
   * Returns whether the next puck touch is still the first one after a faceoff drop.
   * @return true if first faceoff touch is still pending
   */
  boolean isFaceoffFirstTouch();

  /**
   * Consumes the faceoff-first-touch flag and returns whether it had been pending.
   * This should be used when logic must run at most once per faceoff drop.
   * @return true if first faceoff touch was pending and is now consumed
   */
  boolean consumeFaceoffFirstTouch();

  /**
   * Skip the face off timer delay between periods and goals.
   */
  void skipFaceOff(Player player);

  /**
   * Blows the whistle to temporarily stop the game.
   */
  void whistleBlown();

  /**
   * The method that deals with the penalty functions.
   * @param commandSender is the player that sent the command
   * @param type is the type of penalty method to call
   * @param playerName is the player name of the player to give the penalty to
   * @param reason is the reason for the penalty
   * @param timeStr is how long the penalty is
   */
  void penalty(Player commandSender, String type, String playerName, String reason,
               String timeStr);

  /**
   * Spawns a puck at the given location.
   * @param location is the location to spawn the puck
   */
  void summonPuck(Location location);

  /**
   * Forces a goal for a specific team using the standard goal handling flow.
   * @param scoringTeam is the team that should be credited with the goal ("home" or "away")
   */
  void forceGoal(String scoringTeam);

  /**
   * Gets the current active puck entity for this game.
   * @return active puck, or null when no puck exists
   */
  Entity getActivePuck();

  /**
   * Gets the most recent goal scorer.
   * @return scorer, or null if none is available
   */
  Player getMostRecentGoalScorer();

  /**
   * Returns whether the faceoff countdown title is currently running.
   * @return true when the faceoff countdown is active
   */
  boolean isFaceoffCountdownActive();
}

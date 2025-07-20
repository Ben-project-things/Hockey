package me.sammy.benhockey.game;

import org.bukkit.Location;
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
   * Adds the touch for the player's given player stats, also calculates the last 4 players to hit.
   * @param player is the player to add the stats to
   */
  void addLastHit(Player player);

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
}

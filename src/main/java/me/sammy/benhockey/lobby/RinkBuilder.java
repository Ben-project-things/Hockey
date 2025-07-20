package me.sammy.benhockey.lobby;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import me.sammy.benhockey.game.Rink;

/**
 * Class that assists in setting up a rink.
 */
public class RinkBuilder {
  private final String name;
  private final Location centerIce;
  private Location homeGoal;
  private Location homeBench;
  private Location awayGoal;
  private Location awayBench;
  private Location penaltyBox;
  private final JavaPlugin plugin;

  /**
   * The initial constructor of a rink
   * @param name is the name of the rink
   * @param centerIce is the location of center ice (it is the location where the user starts the
   *                 command)
   */
  public RinkBuilder(String name, Location centerIce, JavaPlugin plugin) {
    this.name = name;
    this.centerIce = centerIce;
    this.plugin = plugin;
  }

  /**
   * Sets the location of the red goal.
   * @param loc is the location
   */
  public void setHomeGoal(Location loc) {
    this.homeGoal = loc;
  }

  /**
   * Sets the location of the blue goal.
   * @param loc is the location
   */
  public void setHomeBench(Location loc) {
    this.homeBench = loc;
  }

  /**
   * Sets the location of the blue goal.
   * @param loc is the location
   */
  public void setAwayGoal(Location loc) {
    this.awayGoal = loc;
  }

  /**
   * Sets the location of the blue goal.
   * @param loc is the location
   */
  public void setAwayBench(Location loc) {
    this.awayBench = loc;
  }

  /**
   * Sets the location of the penalty box.
   * @param loc is the location
   */
  public void setPenaltyBox(Location loc) {
    this.penaltyBox = loc;
  }


  /**
   * Gets the location of the home goal.
   * @return the location of the home goal
   */
  public Location getHomeGoal() {
    return this.homeGoal;
  }

  /**
   * Gets the location of the away goal.
   * @return the location of the away goal
   */
  public Location getAwayGoal() {
    return this.awayGoal;
  }

  /**
   * Gets the location of the home bench
   * @return the location of the home bench
   */
  public Location getHomeBench() {
    return this.homeBench;
  }

  /**
   * Gets the location of the away bench
   * @return the location of the away bench
   */
  public Location getAwayBench() {
    return this.awayBench;
  }

  /**
   * Gets the location of the penalty box.
   * @return the location of the penalty box
   */
  public Location getPenaltyBox() {
    return this.penaltyBox;
  }

  /**
   * Builds the actual rink with the selected values.
   * @return the actual rink built
   */
  public Rink build() {
    return new Rink(name, centerIce, homeGoal, awayGoal, penaltyBox, homeBench, awayBench, plugin);
  }

  /**
   * Returns the name of the rink.
   * @return the name
   */
  public String getName() {
    return name;
  }
}

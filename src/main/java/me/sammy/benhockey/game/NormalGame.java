package me.sammy.benhockey.game;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Slime;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Represents a normal hockey game.
 */
public class NormalGame extends AbstractGame {

  public NormalGame(Rink rink, JavaPlugin plugin) {
    super(rink, plugin);
  }

  @Override
  public void summonPuck(Location location) {
    Slime slimePuck = (Slime) location.getWorld().spawnEntity(location, EntityType.SLIME);

    slimePuck.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,
            Integer.MAX_VALUE, 150, false, false));
    slimePuck.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
            Integer.MAX_VALUE, 150, false, false));
    slimePuck.setSize(1);
    slimePuck.setWander(false);
    slimePuck.setAware(true);
    slimePuck.setAI(true);
    slimePuck.setGravity(true);
    slimePuck.setRemoveWhenFarAway(false);
    slimePuck.setPersistent(true);

    this.puck = slimePuck;
  }
}

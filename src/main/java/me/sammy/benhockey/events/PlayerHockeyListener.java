package me.sammy.benhockey.events;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import me.sammy.benhockey.game.GameState;
import me.sammy.benhockey.game.Rink;
import me.sammy.benhockey.lobby.LobbyManager;

/**
 * Deals with the logic with the hockey stick.
 */
public class PlayerHockeyListener implements Listener {

  private final LobbyManager lobbyManager;
  private HashMap<UUID, Double> charges = new HashMap();
  private final JavaPlugin plugin;
  private final Set<UUID> rightClickCooldown = new HashSet<>();
  private final Set<UUID> slideCooldown = new HashSet<>();
  private final Map<UUID, Slime> goalieGloveSlime = new HashMap<>();
  private final Map<UUID, BukkitTask> gloveTimers = new HashMap<>();
  private final Set<UUID> glovedGoalies = new HashSet<>();
  private final Set<UUID> recentBounces = new HashSet<>();

  public PlayerHockeyListener(LobbyManager lobbyManager, JavaPlugin plugin) {
    this.lobbyManager = lobbyManager;
    this.plugin = plugin;
  }

  @EventHandler
  public void onPlayerLeave(PlayerQuitEvent e) {
    this.charges.remove(e.getPlayer().getUniqueId());

  }

  @EventHandler
  public void onSneak(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();

    if (lobbyManager.isPlayerInLobby(p)) {
      return;
    }

    if (e.isSneaking() && lobbyManager.isPlayerOnTeam(p) && !lobbyManager.isPlayerAKeeper(p)) {
      charges.put(p.getUniqueId(), 0.0);
    } else {
      p.setExp(0.0f);
      charges.remove(p.getUniqueId());
    }
  }

  @EventHandler
  public void onGoalieSlide(PlayerItemHeldEvent e) {
    Player p = e.getPlayer();
    UUID uuid = p.getUniqueId();

    if (!lobbyManager.isPlayerAKeeper(p)) {
      return;
    }

    if (this.slideCooldown.contains(uuid)) {
      return;
    }

    int newSlot = e.getNewSlot();

    if (newSlot == 1) {
      slidePlayer(p, "Left");
      p.getInventory().setHeldItemSlot(0);
    }

    if (newSlot == 2) {
      slidePlayer(p, "Right");
      p.getInventory().setHeldItemSlot(0);
    }

    e.setCancelled(true);
    this.slideCooldown.add(uuid);
    Bukkit.getScheduler().runTaskLater(plugin, () -> slideCooldown.remove(uuid), 40L);
    p.playSound(p.getLocation(), Sound.BLOCK_SCAFFOLDING_PLACE, 50f, 1.2f);
  }

  /**
   * Helper method to check which direction to slide for keepers.
   * @param p is the player to slide
   * @param direction is the direction to slide them
   */
  private void slidePlayer(Player p, String direction) {
    Location loc = p.getLocation();
    float yaw = loc.getYaw();

    double radians = Math.toRadians(yaw);
    Vector slide = new Vector(Math.cos(radians), 0, Math.sin(radians));

    if (Objects.equals(direction, "Right")) {
      slide.multiply(-1);
    }

    slide.multiply(0.5);
    p.setVelocity(slide);
  }

  @EventHandler
  public void onPlayerWhistle(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player player = e.getPlayer();
    Rink playerRink = lobbyManager.getPlayerRink(player);

    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    ItemStack interactItem = player.getInventory().getItemInMainHand();
    if (interactItem.getType() == Material.IRON_NUGGET &&
            interactItem.hasItemMeta() &&
            "§6Ref Whistle".equals(interactItem.getItemMeta().getDisplayName())) {
      playerRink.blowWhistle(player);
    }
  }

  @EventHandler
  public void onPlayerStickInteract(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player player = e.getPlayer();
    UUID uuid = player.getUniqueId();

    if (lobbyManager.isPlayerAKeeper(player) && this.glovedGoalies.contains(uuid)) {
      dropGlovedPuck(player);
      return;
    }

    if (rightClickCooldown.contains(uuid)) {
      return;
    }

    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    ItemStack interactItem = player.getInventory().getItemInMainHand();
    if (interactItem.getType() == Material.STICK &&
            interactItem.hasItemMeta() &&
            "§aHockey Stick".equals(interactItem.getItemMeta().getDisplayName())) {

      this.rightClickCooldown.add(uuid);
      Bukkit.getScheduler().runTaskLater(plugin, () -> rightClickCooldown.remove(uuid), 1L);

      int currentLevel = player.getLevel();
      int newLevel = (currentLevel >= 3) ? 1 : currentLevel + 1;
      player.setLevel(newLevel);

      player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.2f);

      ItemMeta meta = interactItem.getItemMeta();
      if (meta == null) {
        return;
      }
      meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_SPEED);

      AttributeModifier attackSpeedModifier = new AttributeModifier(
              UUID.randomUUID(),
              "generic_attack_speed",
              (newLevel == 3) ? -0.5 : 2.5,
              AttributeModifier.Operation.ADD_NUMBER,
              EquipmentSlot.HAND
      );
      meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, attackSpeedModifier);
      meta.removeEnchant(Enchantment.KNOCKBACK);
      meta.addEnchant(Enchantment.KNOCKBACK, Math.min(newLevel, 3), true);
      interactItem.setItemMeta(meta);
    }
  }

  /**
   * Gets a safe drop location for the puck to ensure that it doesn't go into the ground or boards.
   * @param goalie is the goalie that gloved
   * @return a safe location
   */
  private Location getSafeDropLocation(Player goalie) {
    Location loc = goalie.getLocation().clone();
    loc.add(loc.getDirection().setY(0).normalize().multiply(1.2));

    Block blockAt = loc.getBlock();
    if (!blockAt.isPassable()) {
      loc.add(0, 1, 0);
    }

    Location finalLoc = loc.clone();
    while (finalLoc.getBlock().getType().isAir() && finalLoc.getY() > 0) {
      finalLoc.subtract(0, 1, 0);
    }
    finalLoc.setY(finalLoc.getY() + 1.01);
    return finalLoc;
  }


  @EventHandler
  public void onDropHockeyStick(PlayerDropItemEvent e) {
    ItemStack droppedItem = e.getItemDrop().getItemStack();
    if (droppedItem.getType() != Material.STICK || !droppedItem.hasItemMeta() || !droppedItem.getItemMeta().getDisplayName().equals("§aHockey Stick")) {
      return;
    }

    e.setCancelled(true);
  }

  /**
   * Deals with the logic behind a player right clicking on a slime and pulling it back
   * @param e is the player interacting with the entity event.

  @EventHandler
  public void onRightClick(PlayerInteractEntityEvent e) {
    Entity entity = e.getRightClicked();
    Player player = e.getPlayer();
    //TODO Maybe some cool interaction here

    if (!(entity instanceof Slime)) {
      return;
    }

    Slime puck = (Slime) entity;
  }
  */

  @EventHandler
  public void onHitSlime(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Player) || !(e.getEntity() instanceof Slime)) {
      return;
    }

    Slime slime = (Slime) e.getEntity();

    Player player = (Player) e.getDamager();
    ItemStack item = player.getInventory().getItemInMainHand();
    ItemMeta meta = (item != null && item.getType() == Material.STICK)
            ? item.getItemMeta() : null;

    if (meta == null || !meta.hasDisplayName() || !meta.getDisplayName().equals("§aHockey Stick")) {
      return;
    }

    double speed = slime.getVelocity().length();

    if (lobbyManager.isPlayerAKeeper(player) && speed > 0.5) {
      handleGoalieGlove(player, slime);
      this.lobbyManager.getPlayerRink(player).addPlayerLastHit(player);
    }

    this.resetPower(player);

    this.lobbyManager.getPlayerRink(player).addPlayerLastHit(player);

    double charge = this.charges.getOrDefault(player.getUniqueId(), 0.0);
    double extraY = charge * 0.65;

    Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
      Vector updatedVelocity = slime.getVelocity();
      updatedVelocity.setY(updatedVelocity.getY() + extraY);
      slime.setVelocity(updatedVelocity);
    }, 1L);
  }

  //TODO Fix this
  /**
   * Method to deal with the handling of when a goalie gloves the puck.
   * @param goalie is the goalie that gloved the puck
   * @param puck is the specific slime entity
   */
  private void handleGoalieGlove(Player goalie, Slime puck) {
    UUID goalieId = goalie.getUniqueId();

    Location puckLoc = puck.getLocation();
    this.goalieGloveSlime.put(goalieId, puck);
    this.glovedGoalies.add(goalieId);
    puck.setVelocity(new Vector(0, 0, 0));
    Location stash = puck.getLocation().clone().add(0, -25, 0);
    puck.teleport(stash);

    puckLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, puckLoc, 10, 0.2, 0.2, 0.2, 0.01);

    BukkitTask task = new BukkitRunnable() {
      int seconds = 3;

      @Override
      public void run() {
        if (!goalie.isOnline()) {
          glovedGoalies.remove(goalieId);
          gloveTimers.remove(goalieId);
          goalieGloveSlime.remove(goalieId);
          this.cancel();
          return;
        }

        if (seconds == 0) {
          if (glovedGoalies.contains(goalieId)) {
            dropGlovedPuck(goalie);
            this.cancel();
            return;
          }
        }

        goalie.playSound(goalie.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 20f, 1f);
        seconds--;
      }
    }.runTaskTimer(plugin, 0L, 20L);

    this.gloveTimers.put(goalieId, task);
  }

  private void dropGlovedPuck(Player goalie) {
    UUID id = goalie.getUniqueId();
    Slime puck = goalieGloveSlime.remove(id);
    BukkitTask t = gloveTimers.remove(id);

    if (t != null) {
      t.cancel();
    }

    if (puck == null) {
      return;
    }

    Location drop = getSafeDropLocation(goalie);

    puck.teleport(drop);
    glovedGoalies.remove(id);

    puck.setVelocity(new Vector(0, 0.02, 0));
  }

  @EventHandler
  public void onPuckDamage(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Slime)) {
      return;
    }

    if (e.getCause() == EntityDamageEvent.DamageCause.FALL ||
            e.getCause() == EntityDamageEvent.DamageCause.LAVA ||
            e.getCause() == EntityDamageEvent.DamageCause.FIRE ||
            e.getCause() == EntityDamageEvent.DamageCause.VOID) {
      e.setCancelled(true);
    }
  }

  @EventHandler
  public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent e) {
    Player player = e.getPlayer();

    if (player.getGameMode() == GameMode.ADVENTURE) {
      e.setCancelled(true);
    }

    ItemStack item = player.getInventory().getItemInMainHand();

    if (item != null && item.getType() == Material.STICK &&
            item.hasItemMeta() &&
            item.getItemMeta().hasDisplayName() &&
            item.getItemMeta().getDisplayName().equals("§aHockey Stick")) {

      player.setLevel(1);

      ItemMeta meta = item.getItemMeta();
      meta.removeEnchant(Enchantment.KNOCKBACK);
      meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_SPEED);

      AttributeModifier attackSpeedModifier = new AttributeModifier(
              UUID.randomUUID(),
              "generic_attack_speed",
              2.5,
              AttributeModifier.Operation.ADD_NUMBER,
              EquipmentSlot.HAND
      );
      meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, attackSpeedModifier);
      meta.addEnchant(Enchantment.KNOCKBACK, 1, true);
      item.setItemMeta(meta);

      player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.2f);
      player.getInventory().setItemInMainHand(item);
    }
  }

  /**
   * Runs the updates for when a player is shifting and increases their xp bar.
   */
  public void update() {
    for (UUID uuid : this.charges.keySet()) {
      Player p = this.plugin.getServer().getPlayer(uuid);
      if (p == null) {
        continue;
      }

      double charge = this.charges.get(uuid);
      double nextCharge = 1.0 - (1.0 - charge) * 0.9;
      nextCharge = Math.min(nextCharge, 0.99);

      this.charges.put(uuid, nextCharge);
      p.setExp((float) nextCharge);
    }

    for (World world : Bukkit.getWorlds()) {
      for (Slime slime : world.getEntitiesByClass(Slime.class)) {
        world.spawnParticle(Particle.FLAME, slime.getLocation(), 1, 0, 0, 0, 0);
        applyBoardBounce(slime);
      }
    }
  }

  /**
   * Method to apply the board bounce and normal slime physics.
   * @param slime is the slime to apply it to
   */
  private void applyBoardBounce(Slime slime) {
    if (slime.isDead() || !slime.isValid()) {
      return;
    }

    UUID slimeId = slime.getUniqueId();
    if (this.recentBounces.contains(slimeId)) {
      return;
    }

    Location puckLoc = slime.getLocation();
    Vector velocity = slime.getVelocity();
    Vector newVelocity = velocity.clone();
    boolean bounced = false;

    if (!slime.isOnGround()) {
      Vector frictionVel = velocity.clone();
      frictionVel.setX(frictionVel.getX() / 0.98);
      frictionVel.setZ(frictionVel.getZ() / 0.98);
      slime.setVelocity(frictionVel);
    }

    if (Math.abs(velocity.getX()) < 0.05 && Math.abs(velocity.getZ()) < 0.05) {
      return;
    }

    Block currentBlock = puckLoc.getBlock();
    int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    for (int[] dir : directions) {
      int dx = dir[0];
      int dz = dir[1];

      if ((dx != 0 && velocity.getX() * dx <= 0) ||
              (dz != 0 && velocity.getZ() * dz <= 0)) {
        continue;
      }

      Block adjacentBlock = currentBlock.getRelative(dx, 0, dz);
      if (!adjacentBlock.getType().isAir()) {
        if (dx != 0) {
          newVelocity.setX(-velocity.getX() * 0.5);
          bounced = true;
        }
        if (dz != 0) {
          newVelocity.setZ(-velocity.getZ() * 0.5);
          bounced = true;
        }

        if (newVelocity.length() < 0.05) {
          newVelocity.add(new Vector(dx * 0.1, 0, dz * 0.1));
        }
      }
    }

    if (bounced) {
      slime.setVelocity(newVelocity);
      slime.getWorld().playSound(puckLoc, Sound.BLOCK_DEEPSLATE_TILES_BREAK, 75f, 0.2f);

      this.recentBounces.add(slimeId);
      Bukkit.getScheduler().runTaskLater(plugin, () -> {
        this.recentBounces.remove(slimeId);
      }, 1L);
    }
  }


  @EventHandler
  public void onHitPlayer(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof Player) || !(e.getDamager() instanceof Player)) {
      return;
    }

    Player damaged = (Player) e.getEntity();
    Player damager = (Player) e.getDamager();

    if (lobbyManager.isPlayerInLobby(damaged) || lobbyManager.isPlayerInLobby(damager)) {
      e.setCancelled(true);
      return;
    }

    Rink damagedRink = lobbyManager.getPlayerRink(damaged);
    Rink damagerRink = lobbyManager.getPlayerRink(damager);

    if (damagedRink == null || !damagedRink.equals(damagerRink)) {
      e.setCancelled(true);
      return;
    }

    String damagedTeam = damagedRink.getTeam(damaged);
    String damagerTeam = damagerRink.getTeam(damager);

    if (damagedTeam == null || damagerTeam == null || damagedTeam.equalsIgnoreCase(damagerTeam)) {
      e.setCancelled(true);
      return;
    }

    if (!damagedRink.isHittingAllowed() || damagedRink.getGameState() != GameState.GAME) {
      e.setCancelled(true);
      return;
    }


    for (Player p : damagerRink.getRefs()) {
      p.sendMessage("§6[§bBH§6] §e" + damager.getName() + " hit "
              + damaged.getName() + "§7 (Power " + damager.getLevel() + ")");
    }

    this.resetPower(damager);
    e.setDamage(0);
  }

  /**
   * Helper method to reset a player's power when they hit with a stick.
   * @param p is the player in question
   */
  private void resetPower(Player p) {
    ItemStack item = p.getInventory().getItemInMainHand();
    ItemMeta meta = item.getType() == Material.STICK
            ? item.getItemMeta() : null;

    if (meta == null || !meta.hasDisplayName() || !meta.getDisplayName().equals("§aHockey Stick")) {
      return;
    }

    meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_SPEED);

    AttributeModifier attackSpeedModifier = new AttributeModifier(
            UUID.randomUUID(),
            "generic_attack_speed",
            2.5,
            AttributeModifier.Operation.ADD_NUMBER,
            EquipmentSlot.HAND
    );
    meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, attackSpeedModifier);

    p.setLevel(1);
    meta.addEnchant(Enchantment.KNOCKBACK, 1, true);
    item.setItemMeta(meta);
    p.getInventory().setItemInMainHand(item);
  }
}

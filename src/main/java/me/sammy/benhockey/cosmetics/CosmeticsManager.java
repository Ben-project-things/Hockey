package me.sammy.benhockey.cosmetics;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles player cosmetic persistence and creation.
 */
public class CosmeticsManager {

  private static final String HOCKEY_STICK_NAME = "§aHockey Stick";
  private static final String GOALIE_STICK_NAME = "§bGoalie Stick";

  private final JavaPlugin plugin;
  private final File cosmeticsFile;
  private final YamlConfiguration cosmeticsConfig;
  private final Map<UUID, StickType> playerStickTypes = new HashMap<>();
  private final Map<UUID, TrailParticle> playerParticles = new HashMap<>();
  private final Map<UUID, GoaliePadType> playerGoaliePads = new HashMap<>();
  private int rainbowTick = 0;
  private int noteTick = 0;

  public CosmeticsManager(JavaPlugin plugin) {
    this.plugin = plugin;
    if (!plugin.getDataFolder().exists()) {
      plugin.getDataFolder().mkdirs();
    }
    this.cosmeticsFile = new File(plugin.getDataFolder(), "cosmetics.yml");
    this.cosmeticsConfig = YamlConfiguration.loadConfiguration(this.cosmeticsFile);
  }

  public void loadPlayer(UUID playerId) {
    String base = "players." + playerId;
    this.playerStickTypes.put(playerId, StickType.fromKey(this.cosmeticsConfig.getString(base + ".stick", StickType.STICK.key)));
    this.playerParticles.put(playerId,
            TrailParticle.fromKey(this.cosmeticsConfig.getString(base + ".particle", TrailParticle.EMERALD.key)));
    this.playerGoaliePads.put(playerId,
            GoaliePadType.fromKey(this.cosmeticsConfig.getString(base + ".goaliepad", GoaliePadType.IRON.key)));
  }

  public void saveAll() {
    for (UUID playerId : this.playerStickTypes.keySet()) {
      savePlayer(playerId);
    }
    try {
      this.cosmeticsConfig.save(this.cosmeticsFile);
    } catch (IOException e) {
      this.plugin.getLogger().warning("Failed to save cosmetics.yml: " + e.getMessage());
    }
  }

  private void savePlayer(UUID playerId) {
    String base = "players." + playerId;
    this.cosmeticsConfig.set(base + ".stick", getStickType(playerId).key);
    this.cosmeticsConfig.set(base + ".particle", getParticle(playerId).key);
    this.cosmeticsConfig.set(base + ".goaliepad", getGoaliePadType(playerId).key);
  }

  public StickType getStickType(UUID playerId) {
    return this.playerStickTypes.getOrDefault(playerId, StickType.STICK);
  }

  public TrailParticle getParticle(UUID playerId) {
    return this.playerParticles.getOrDefault(playerId, TrailParticle.EMERALD);
  }

  public GoaliePadType getGoaliePadType(UUID playerId) {
    return this.playerGoaliePads.getOrDefault(playerId, GoaliePadType.IRON);
  }

  public void setStickType(Player player, StickType type) {
    this.playerStickTypes.put(player.getUniqueId(), type);
    savePlayer(player.getUniqueId());
    saveAll();
  }

  public void setParticle(Player player, TrailParticle particle) {
    this.playerParticles.put(player.getUniqueId(), particle);
    savePlayer(player.getUniqueId());
    saveAll();
  }

  public void setGoaliePadType(Player player, GoaliePadType padType) {
    this.playerGoaliePads.put(player.getUniqueId(), padType);
    savePlayer(player.getUniqueId());
    saveAll();
  }

  public ItemStack createStickItem(Player player, boolean goalie) {
    StickType stickType = getStickType(player.getUniqueId());
    return createStickItem(stickType, goalie);
  }

  public ItemStack createStickItem(StickType stickType, boolean goalie) {
    ItemStack hockeyStick = new ItemStack(stickType.material);
    ItemMeta meta = hockeyStick.getItemMeta();
    if (meta == null) {
      return hockeyStick;
    }

    AttributeModifier attackSpeedModifier = new AttributeModifier(
            UUID.randomUUID(),
            "generic_attack_speed",
            2.5,
            AttributeModifier.Operation.ADD_NUMBER,
            EquipmentSlot.HAND
    );

    meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, attackSpeedModifier);
    meta.setDisplayName(goalie ? GOALIE_STICK_NAME : HOCKEY_STICK_NAME);
    meta.setUnbreakable(true);
    meta.addEnchant(Enchantment.KNOCKBACK, 1, true);
    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
    hockeyStick.setItemMeta(meta);
    return hockeyStick;
  }

  public ItemStack createGoaliePadBoots(UUID playerId) {
    GoaliePadType type = getGoaliePadType(playerId);
    return createGoaliePadBootsForType(type);
  }

  public ItemStack createGoaliePadBootsForType(GoaliePadType type) {
    ItemStack boots = new ItemStack(type.material);
    ItemMeta meta = boots.getItemMeta();
    if (meta == null) {
      return boots;
    }
    meta.setDisplayName("§bGoalie Pads: §f" + type.fancyName);
    if (meta instanceof LeatherArmorMeta && type.leatherColor != null) {
      ((LeatherArmorMeta) meta).setColor(type.leatherColor);
    }
    boots.setItemMeta(meta);
    return boots;
  }


  public void refreshPlayerSticks(Player player) {
    for (int i = 0; i < player.getInventory().getSize(); i++) {
      ItemStack current = player.getInventory().getItem(i);
      if (current == null || !isValidStickMaterial(current.getType()) || !current.hasItemMeta()) {
        continue;
      }
      ItemMeta meta = current.getItemMeta();
      if (meta == null || !meta.hasDisplayName()) {
        continue;
      }
      if (GOALIE_STICK_NAME.equals(meta.getDisplayName())) {
        player.getInventory().setItem(i, createStickItem(player, true));
      } else if (HOCKEY_STICK_NAME.equals(meta.getDisplayName())) {
        player.getInventory().setItem(i, createStickItem(player, false));
      }
    }
  }

  public void spawnParticleFor(Player player, Location location) {
    TrailParticle selected = getParticle(player.getUniqueId());
    if (selected == TrailParticle.NOTE) {
      double noteColor = (this.noteTick % 24) / 24.0;
      this.noteTick++;
      player.spawnParticle(Particle.NOTE, location, 0, noteColor, 0, 0, 1.0);
      return;
    }
    if (selected == TrailParticle.RAINBOW) {
      Particle.DustOptions[] rainbowDust = {
          new Particle.DustOptions(Color.RED, 1.35f),
          new Particle.DustOptions(Color.fromRGB(255, 127, 0), 1.35f),
          new Particle.DustOptions(Color.YELLOW, 1.35f),
          new Particle.DustOptions(Color.LIME, 1.35f),
          new Particle.DustOptions(Color.AQUA, 1.35f),
          new Particle.DustOptions(Color.BLUE, 1.35f),
          new Particle.DustOptions(Color.fromRGB(143, 0, 255), 1.35f)
      };
      Particle.DustOptions dust = rainbowDust[(this.rainbowTick / 3) % rainbowDust.length];
      this.rainbowTick++;
      player.spawnParticle(Particle.REDSTONE, location, 4, 0.08, 0.08, 0.08, 0.01, dust);
      return;
    }
    this.rainbowTick++;
    if (selected.dustOptions != null) {
      player.spawnParticle(selected.particle, location, selected.count, selected.offsetX, selected.offsetY,
              selected.offsetZ, selected.speed, selected.dustOptions);
      return;
    }
    if (selected.dataMaterial != null) {
      player.spawnParticle(selected.particle, location, selected.count, selected.offsetX, selected.offsetY,
              selected.offsetZ, selected.speed, Bukkit.createBlockData(selected.dataMaterial));
      return;
    }
    player.spawnParticle(selected.particle, location, selected.count, selected.offsetX, selected.offsetY,
            selected.offsetZ, selected.speed);
  }

  public static boolean isValidStickMaterial(Material material) {
    return StickType.MATERIALS.contains(material);
  }

  public enum StickType {
    STICK("stick", Material.STICK, "Wooden Stick"),
    BONE("bone", Material.BONE, "Bone Stick"),
    BLAZE_ROD("blazerod", Material.BLAZE_ROD, "Blaze Rod"),
    BAMBOO("bamboo", Material.BAMBOO, "Bamboo"),
    WOODEN_AXE("wooden_axe", Material.WOODEN_AXE, "Wooden Axe"),
    STONE_AXE("stone_axe", Material.STONE_AXE, "Stone Axe"),
    IRON_AXE("iron_axe", Material.IRON_AXE, "Iron Axe"),
    GOLDEN_AXE("golden_axe", Material.GOLDEN_AXE, "Golden Axe"),
    DIAMOND_AXE("diamond_axe", Material.DIAMOND_AXE, "Diamond Axe"),
    NETHERITE_AXE("netherite_axe", Material.NETHERITE_AXE, "Netherite Axe"),
    WOODEN_HOE("wooden_hoe", Material.WOODEN_HOE, "Wooden Hoe"),
    STONE_HOE("stone_hoe", Material.STONE_HOE, "Stone Hoe"),
    IRON_HOE("iron_hoe", Material.IRON_HOE, "Iron Hoe"),
    GOLDEN_HOE("golden_hoe", Material.GOLDEN_HOE, "Golden Hoe"),
    DIAMOND_HOE("diamond_hoe", Material.DIAMOND_HOE, "Diamond Hoe"),
    NETHERITE_HOE("netherite_hoe", Material.NETHERITE_HOE, "Netherite Hoe");

    private static final EnumSet<Material> MATERIALS = EnumSet.noneOf(Material.class);

    static {
      Arrays.stream(values()).forEach(value -> MATERIALS.add(value.material));
    }

    public final String key;
    public final Material material;
    public final String fancy;

    StickType(String key, Material material, String fancy) {
      this.key = key;
      this.material = material;
      this.fancy = fancy;
    }

    public static StickType fromKey(String key) {
      for (StickType type : values()) {
        if (type.key.equalsIgnoreCase(key)) {
          return type;
        }
      }
      return STICK;
    }
  }

  public enum TrailParticle {
    INSTANT("instantspell", Particle.SPELL_INSTANT, Material.FIREWORK_ROCKET, "Default Particle", 1, 0, 0, 0, 0),
    FLAME("flame", Particle.FLAME, Material.FLINT_AND_STEEL, "Flame", 2, 0, 0, 0, 0),
    CRIT("crit", Particle.CRIT, Material.GOLDEN_SWORD, "Crit", 5, 0, 0, 0, 0),
    BLUE_CRIT("bluecrit", Particle.CRIT_MAGIC, Material.DIAMOND_SWORD, "Blue Crit", 5, 0, 0, 0, 0),
    EMERALD("emerald", Particle.VILLAGER_HAPPY, Material.EMERALD, "Emerald", 5, 0, 0, 0, 0),
    SNOW("snow", Particle.SNOWBALL, Material.SNOWBALL, "Snow", 5, 0, 0, 0, 0),
    NOTE("note", Particle.NOTE, Material.JUKEBOX, "Note", 1, 0, 0, 0, 0),
    PURPLE("purple", Particle.SPELL_WITCH, Material.PURPLE_STAINED_GLASS, "Purple", 5, 0, 0, 0, 0),
    RAINBOW("rainbow", Particle.REDSTONE, Material.BEACON, "Rainbow", 5, 0, 0, 0, 0),
    HEART("heart", Particle.HEART, Material.APPLE, "Heart", 1, 0, 0, 0, 0),
    SPLASH("splash", Particle.WATER_SPLASH, Material.WATER_BUCKET, "Splash", 5, 0, 0, 0, 0),
    SLIME("slime", Particle.SLIME, Material.SLIME_BALL, "Slime", 1, 0, 0, 0, 0),
    LAVA("lava", Particle.DRIP_LAVA, Material.LAVA_BUCKET, "Lava", 5, 0, 0, 0, 0),
    CLOUD("cloud", Particle.CLOUD, Material.WHITE_WOOL, "Cloud", 1, 0, 0, 0, 0),
    RED("red", Particle.REDSTONE, Material.RED_WOOL, "Red", 4, 0.08, 0.08, 0.08, 0.01,
            new Particle.DustOptions(Color.RED, 1.3f), null),
    GREEN("green", Particle.REDSTONE, Material.GREEN_WOOL, "Green", 4, 0.08, 0.08, 0.08, 0.01,
            new Particle.DustOptions(Color.GREEN, 1.3f), null),
    ORANGE("orange", Particle.REDSTONE, Material.ORANGE_WOOL, "Orange", 4, 0.08, 0.08, 0.08, 0.01,
            new Particle.DustOptions(Color.ORANGE, 1.3f), null),
    YELLOW("yellow", Particle.REDSTONE, Material.YELLOW_WOOL, "Yellow", 4, 0.08, 0.08, 0.08, 0.01,
            new Particle.DustOptions(Color.YELLOW, 1.3f), null),
    BLUE("blue", Particle.REDSTONE, Material.BLUE_WOOL, "Blue", 4, 0.08, 0.08, 0.08, 0.01,
            new Particle.DustOptions(Color.BLUE, 1.3f), null),
    INDIGO("indigo", Particle.REDSTONE, Material.PURPLE_WOOL, "Indigo", 4, 0.08, 0.08, 0.08, 0.01,
            new Particle.DustOptions(Color.PURPLE, 1.3f), null);

    public final String key;
    public final Particle particle;
    public final Material icon;
    public final String fancyName;
    public final int count;
    public final double offsetX;
    public final double offsetY;
    public final double offsetZ;
    public final double speed;
    public final Particle.DustOptions dustOptions;
    public final Material dataMaterial;

    TrailParticle(String key, Particle particle, Material icon, String fancyName,
        int count, double offsetX, double offsetY, double offsetZ, double speed) {
      this(key, particle, icon, fancyName, count, offsetX, offsetY, offsetZ, speed, null, null);
    }

    TrailParticle(String key, Particle particle, Material icon, String fancyName,
                  int count, double offsetX, double offsetY, double offsetZ, double speed,
                  Material dataMaterial) {
      this(key, particle, icon, fancyName, count, offsetX, offsetY, offsetZ, speed, null, dataMaterial);
    }

    TrailParticle(String key, Particle particle, Material icon, String fancyName,
                  int count, double offsetX, double offsetY, double offsetZ, double speed,
                  Particle.DustOptions dustOptions, Material dataMaterial) {
      this.key = key;
      this.particle = particle;
      this.icon = icon;
      this.fancyName = fancyName;
      this.count = count;
      this.offsetX = offsetX;
      this.offsetY = offsetY;
      this.offsetZ = offsetZ;
      this.speed = speed;
      this.dustOptions = dustOptions;
      this.dataMaterial = dataMaterial;
    }

    public static TrailParticle fromKey(String key) {
      for (TrailParticle value : values()) {
        if (value.key.equalsIgnoreCase(key)) {
          return value;
        }
      }
      return EMERALD;
    }
  }

  public enum GoaliePadType {
    LEATHER("leather", Material.LEATHER_BOOTS, "Leather", null),
    GOLD("gold", Material.GOLDEN_BOOTS, "Gold", null),
    IRON("iron", Material.IRON_BOOTS, "Iron", null),
    NETHERITE("netherite", Material.NETHERITE_BOOTS, "Netherite", null),
    DIAMOND("diamond", Material.DIAMOND_BOOTS, "Diamond", null),
    CHAINMAIL("chainmail", Material.CHAINMAIL_BOOTS, "Chainmail", null),
    LEATHER_WHITE("leather_white", Material.LEATHER_BOOTS, "Leather White", Color.WHITE),
    LEATHER_BLACK("leather_black", Material.LEATHER_BOOTS, "Leather Black", Color.BLACK),
    LEATHER_RED("leather_red", Material.LEATHER_BOOTS, "Leather Red", Color.RED),
    LEATHER_BLUE("leather_blue", Material.LEATHER_BOOTS, "Leather Blue", Color.BLUE),
    LEATHER_GREEN("leather_green", Material.LEATHER_BOOTS, "Leather Green", Color.GREEN),
    LEATHER_YELLOW("leather_yellow", Material.LEATHER_BOOTS, "Leather Yellow", Color.YELLOW),
    LEATHER_ORANGE("leather_orange", Material.LEATHER_BOOTS, "Leather Orange", Color.ORANGE),
    LEATHER_PURPLE("leather_purple", Material.LEATHER_BOOTS, "Leather Purple", Color.PURPLE),
    LEATHER_PINK("leather_pink", Material.LEATHER_BOOTS, "Leather Pink", Color.FUCHSIA),
    LEATHER_CYAN("leather_cyan", Material.LEATHER_BOOTS, "Leather Cyan", Color.AQUA);

    public final String key;
    public final Material material;
    public final String fancyName;
    public final Color leatherColor;

    GoaliePadType(String key, Material material, String fancyName, Color leatherColor) {
      this.key = key;
      this.material = material;
      this.fancyName = fancyName;
      this.leatherColor = leatherColor;
    }

    public static GoaliePadType fromKey(String key) {
      for (GoaliePadType type : values()) {
        if (type.key.equalsIgnoreCase(key)) {
          return type;
        }
      }
      return IRON;
    }
  }
}

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.LavaAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.command.Commands;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.ParticleEffect;
import com.projectkorra.projectkorra.util.TempBlock;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Consumer;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class MagmaRush extends LavaAbility implements AddonAbility {
	
	private final String path = "";
	
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.SELECT_RANGE)
	private double select_range;
	@Attribute(Attribute.SPEED)
	private double speed;
	@Attribute(Attribute.DAMAGE)
	private double damage;
	@Attribute(Attribute.RANGE)
	private double range;
	@Attribute(Attribute.RADIUS)
	private int radius;
	@Attribute(Attribute.FIRE_TICK)
	private int fire_ticks;
	@Attribute("RevertTime")
	private long lava_revert_time;
	
	private boolean update;
	
	private Block target;
	private Location origin, location;
	private Vector direction;
	private List<Block> lavaBlocks;
	private Set<Integer> indices;
	private Set<FallingBlock> magmaBlocks;
	
	public MagmaRush(Player player) {
		super(player);
		
		if (!bPlayer.canBend(this)) {
			return;
		} else if (!bPlayer.canLavabend()) {
			return;
		} else if (!GeneralMethods.isRegionProtectedFromBuild(this, player.getLocation())) {
			return;
		}
		this.cooldown = ConfigManager.getConfig().getLong(path + "Cooldown");
		this.select_range = ConfigManager.getConfig().getDouble(path + "SelectRange");
		this.speed = ConfigManager.getConfig().getDouble(path + "Speed");
		this.damage = ConfigManager.getConfig().getDouble(path + "Damage");
		this.range = ConfigManager.getConfig().getDouble(path + "Range");
		this.radius = ConfigManager.getConfig().getInt(path + "Radius");
		this.fire_ticks = ConfigManager.getConfig().getInt(path + "FireTicks");
		this.lava_revert_time = ConfigManager.getConfig().getLong(path + "LavaRevertTime");
		
		this.target = rayTraceBlock(player, this.select_range);
		
		if (this.target == null) return;
		if (GeneralMethods.isRegionProtectedFromBuild(this, this.target.getLocation())) {
			return;
		}
		
		this.origin = this.target.getLocation().clone();
		this.location = this.origin.clone();
		this.direction = player.getLocation().getDirection();
		this.lavaBlocks =
				GeneralMethods.getBlocksAroundPoint(this.origin.clone(), 1.75)
						.stream()
						.filter(b -> (isAir(b.getRelative(BlockFace.UP).getType()) || isPlant(b.getRelative(BlockFace.UP))) && isEarthbendable(b) && !isIndestructible(b) && !GeneralMethods.isRegionProtectedFromBuild(this, b.getLocation()))
						.collect(Collectors.toList());
		this.indices = new HashSet<>();
		this.magmaBlocks = new HashSet<>();
		
		if (GeneralMethods.isRegionProtectedFromBuild(this, this.target.getLocation())) {
			return;
		} else if (GeneralMethods.isRegionProtectedFromBuild(this, player.getLocation())) {
			return;
		}
		playEarthbendingSound(this.origin);
		prepare();
		start();
	}
	
	@Override
	public void progress() {
		if (!player.isSneaking()) {
			if (!update) {
				remove();
				return;
			}
		} else {
			this.direction = player.getLocation().getDirection();
		}
		if (update) {
			rush();
		}
		prepareLavaPool();
	}
	
	private void rush() {
		this.direction = player.getLocation().getDirection();
		this.location.add(this.direction.multiply(this.speed));
		
		if (ThreadLocalRandom.current().nextInt(100) < 30) {
			playLavabendingSound(this.location);
		}
		if (this.location.distanceSquared(this.origin) > this.range * this.range) {
			remove();
			return;
		}
		this.direction.setY(0);
		
		Block top = GeneralMethods.getTopBlock(this.location, 2);
		
		if (!isTransparent(top.getRelative(BlockFace.UP))) {
			remove();
			return;
		}
		if (!isEarthbendable(top)) {
			Block under = top.getRelative(BlockFace.DOWN);
			
			if (isTransparent(top) && isEarthbendable(under)) {
				top = under;
			} else {
				remove();
				return;
			}
		}
		this.location.setY(top.getY() + 1);
		
		for (Location location : shape(this.location)) {
			FallingBlock fallingBlock = GeneralMethods.spawnFallingBlock(location.clone(), Material.MAGMA_BLOCK);
			fallingBlock.setVelocity(new Vector(0, 0.3, 0));
			fallingBlock.setDropItem(false);
			
			fallingBlock.setMetadata("pa:magmarush", new FixedMetadataValue(ProjectKorra.plugin, 0));
			
			this.magmaBlocks.add(fallingBlock);
		}
		
		for (FallingBlock magma : this.magmaBlocks) {
			for (Entity entity : GeneralMethods.getEntitiesAroundPoint(magma.getLocation(), 1)) {
				if (entity instanceof ArmorStand || entity.getUniqueId() == player.getUniqueId()) continue;
				
				if (entity instanceof LivingEntity) {
					if (entity instanceof Player) {
						if (Commands.invincible.contains(entity.getName())) continue;
					}
					DamageHandler.damageEntity(entity, this.damage, this);
					entity.setFireTicks(this.fire_ticks);
				}
			}
		}
	}
	
	private void prepare() {
		for (Location location : shape(this.target.getLocation())) {
			FallingBlock fallingBlock = GeneralMethods.spawnFallingBlock(location.clone(), Material.MAGMA_BLOCK);
			fallingBlock.setVelocity(new Vector(0, 0.5, 0));
			fallingBlock.setDropItem(false);
			
			fallingBlock.setMetadata("pa:magmarush", new FixedMetadataValue(ProjectKorra.plugin, 0));
		}
		for (Block block : this.lavaBlocks) {
			ParticleEffect.LAVA.display(block.getLocation(), 1, 0, 0, 0, 0);
		}
	}
	
	private void prepareLavaPool() {
		if (this.indices.size() < this.lavaBlocks.size()) {
			int idx = ThreadLocalRandom.current().nextInt(this.lavaBlocks.size());
			
			if (this.indices.contains(idx)) {
				while (this.indices.contains(idx)) {
					idx = ThreadLocalRandom.current().nextInt(this.lavaBlocks.size());
				}
			}
			new TempBlock(this.lavaBlocks.get(idx), Material.LAVA.createBlockData(), this.lava_revert_time);
		}
	}
	
	private List<Location> shape(Location location) {
		return GeneralMethods.getBlocksAroundPoint(location, 1.75)
				.stream()
				.filter(b -> (isAir(b.getRelative(BlockFace.UP).getType()) || (isPlant(b.getRelative(BlockFace.UP)) && !isLeaves(b.getRelative(BlockFace.UP))) && !isWood(b.getRelative(BlockFace.UP)))
							&& isEarthbendable(b) && !isIndestructible(b) && !GeneralMethods.isRegionProtectedFromBuild(this, b.getLocation()))
				.map(b -> (Location) b.getRelative(BlockFace.UP).getLocation().clone())
				.collect(Collectors.toList());
	}
	
	public void update(boolean update) {
		this.update = update;
	}
	
	public static void update(Player player, boolean update) {
		getAbility(player, MagmaRush.class).update(update);
	}

    public static Block rayTraceBlock(Player player, double range) {
		RayTraceResult result = player.getWorld().rayTraceBlocks(player.getEyeLocation().clone(), player.getEyeLocation().getDirection(), range);
		if (result != null) {
			return result.getHitBlock();
		}
		return null;
	}

    public static boolean isIndestructible(Material type) {
		switch (type) {
			case COMMAND_BLOCK:
			case BEDROCK:
			case BARRIER:
			case END_PORTAL:
			case END_PORTAL_FRAME:
			case NETHER_PORTAL:
			case STRUCTURE_BLOCK:
				return true;
		}
		return false;
	}
	
	public static boolean isIndestructible(Block block) {
		return isIndestructible(block.getType());
	}
	
	public static boolean isWood(Material type) {
		if (type.name().contains("_WOOD") || type.name().contains("_LOG")) {
			return true;
		}
		return false;
	}
	
	public static boolean isWood(Block block) {
		return isWood(block.getType());
	}
	
	public static boolean isLeaves(Material type) {
		return type.name().contains("LEAVES");
	}
	
	public static boolean isLeaves(Block block) {
		return isLeaves(block.getType());
	}
	
	@Override
	public boolean isSneakAbility() {
		return true;
	}
	
	@Override
	public boolean isHarmlessAbility() {
		return false;
	}
	
	@Override
	public long getCooldown() {
		return cooldown;
	}
	
	@Override
	public String getName() {
		return "MagmaRush";
	}
	
	@Override
	public Location getLocation() {
		return null;
	}
	
	@Override
	public List<Location> getLocations() {
		return this.magmaBlocks.stream().map(magma -> (Location) magma.getLocation()).collect(Collectors.toList());
	}
	
	@Override
	public double getCollisionRadius() {
		return 1;
	}
	
	@Override
	public void remove() {
		bPlayer.addCooldown(this);
		super.remove();
	}
	
	@Override
	public void load() { }
	
	@Override
	public void stop() { }
	
	@Override
	public boolean isEnabled() {
		return ConfigManager.getConfig().getBoolean("MagmaRush.Enabled", true);
	}
	
	@Override
	public String getAuthor() {
		return "Prride";
	}
	
	@Override
	public String getVersion() {
		return "1.0.0";
	}
	
	@Override
	public String getDescription() {
		return Element.EARTH.getColor() + "Launch a wave of destructive magma!";
	}
	
	@Override
	public String getInstructions() {
		return "Hold sneak on an earth block, and left click. You are able to control the ability while sneaking.";
	}
}

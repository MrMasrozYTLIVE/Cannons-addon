package at.pavlov.cannons.scheduler;

import at.pavlov.cannons.Cannons;
import at.pavlov.cannons.Enum.FakeBlockType;
import at.pavlov.cannons.container.FakeBlockEntry;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public class FakeBlockHandler {
    private final Cannons plugin;

    private ArrayList<FakeBlockEntry> list = new ArrayList<FakeBlockEntry>();

    private long lastAiming;
    private long lastImpactPredictor;


    /**
     * Constructor
     * @param plugin - Cannons instance
     */
    public FakeBlockHandler(Cannons plugin)
    {
        this.plugin = plugin;
    }

    /**
     * starts the scheduler of the teleporter
     */
    public void setupScheduler()
    {
        //changing angles for aiming mode
        plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable()
        {
            public void run() {
                removeOldBlocks();
                removeOldBlockType();
            }

        }, 1L, 1L);
    }


    /**
     * removes old blocks form the players vision
     */
    private void removeOldBlocks()
    {
        Iterator<FakeBlockEntry> iter = list.iterator();
        while(iter.hasNext())
        {
            FakeBlockEntry next = iter.next();
            Player player = next.getPlayerBukkit();

            //if player is offline remove this one
            if (player == null) {
                iter.remove();
                continue;
            }

            if (next.isExpired())
            {
                //send real block to player
                Location loc = next.getLocation();
                if (loc != null)
                {
                    player.sendBlockChange(loc, loc.getBlock().getBlockData());
                    // plugin.logDebug("expired fake block: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ", " + next.getType().toString());
                }
                //remove this entry
                iter.remove();
            }
        }
    }

    /**
     * removes previous entries for this type of fake blocks
     */
    private void removeOldBlockType()
    {
        Iterator<FakeBlockEntry> iter = list.iterator();
        while(iter.hasNext())
        {
            FakeBlockEntry next = iter.next();
            //if older and if the type matches
            if (next.getStartTime() < (lastAiming - 50) && (next.getType() == FakeBlockType.AIMING)
                    || next.getStartTime() < (lastImpactPredictor - 50) && (next.getType() == FakeBlockType.IMPACT_PREDICTOR))
            {
                //send real block to player
                Player player = next.getPlayerBukkit();
                Location loc = next.getLocation();
                if (player != null && loc != null)
                {
                    player.sendBlockChange(loc, loc.getBlock().getBlockData());
                }

                //remove this entry
                iter.remove();
                //plugin.logDebug("remove older fake entry: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ", " + next.getType().toString() + " stime " + next.getStartTime());
            }
        }
    }

    /**
     * Creates a sphere of fake block
     * @param loc center of the sphere
     * @param r radius of the sphere
     * @param blockData material of the fake block
     * @return a map of the locations and fake block data in the sphere
     */
    @Nullable
    public Map<Location, BlockData> imitateSphere(@NotNull Location loc, int r, @NotNull BlockData blockData) {
        Map<Location, BlockData> blockChangeMap = new HashMap<>();
        for (int x = -r; x <=r; x++) {
            for (int y = -r; y<=r; y++) {
                for (int z = -r; z<=r; z++) {
                    Location newL = loc.clone().add(x, y, z);
                    if (newL.distanceSquared(loc) <= r * r) {
                        blockChangeMap.put(newL, blockData);
                    }
                }
            }
        }
        return blockChangeMap;
    }


    /**
     * Registers fake block changes and sends them to the given player
     * @param player the player to be notified
     * @param blockChangeMap a map containing the locations and their corresponding fake block data
     * @param type the type of fake block change
     * @param duration delay until the block disappears again in seconds
     */
    public void sendBlockChanges(Player player, Map<Location, BlockData> blockChangeMap, FakeBlockType type, double duration) {
        if (player == null) {
            return;
        }

        for (Map.Entry<Location, BlockData> entry: blockChangeMap.entrySet()) {
            registerBlockChangeToPlayer(player, entry.getKey(), entry.getValue(), type, duration);
        }

        player.sendMultiBlockChange(blockChangeMap);
    }

    /**
     * creates a line of blocks at the give location
     * @param loc starting location of the line
     * @param direction direction of the line
     * @param offset offset from the starting point
     * @param length lenght of the line
     * @param player name of the player
     */
    public void imitateLine(final Player player, Location loc, Vector direction, int offset, int length, BlockData blockData, FakeBlockType type, double duration)
    {
        if(loc == null || player == null)
            return;

        BlockIterator iter = new BlockIterator(loc.getWorld(), loc.toVector(), direction, offset, length);
        Map<Location, BlockData> blockChangeMap = new HashMap<>();
        while (iter.hasNext())
        {
            Location blockLoc = iter.next().getLocation();
            blockChangeMap.put(blockLoc, blockData);
            registerBlockChangeToPlayer(player, blockLoc, blockData, type, duration);
        }
        player.sendMultiBlockChange(blockChangeMap);
    }

    /**
     * sends fake block to the given player
     * @param player player to display the blocks
     * @param loc location of the block
     * @param blockData type of the block
     * @param duration how long to remove the block in [s]
     */
    private void registerBlockChangeToPlayer(final Player player, final Location loc, BlockData blockData, FakeBlockType type, double duration)
    {
        //only show block in air
        if(loc.getBlock().isEmpty())
        {
            FakeBlockEntry fakeBlockEntry = new FakeBlockEntry(loc, player, type, (long) (duration*20.0));


            boolean found = false;
            for (FakeBlockEntry block : list)
            {
                if (block.equals(fakeBlockEntry))
                {
                    //renew entry
                    //plugin.logDebug("renew block at: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ", " + type.toString());
                    block.setStartTime(System.currentTimeMillis());
                    found = true;
                    //there is only one block here
                    break;
                }
            }
            if (!found)
            {
                //player.sendBlockChange(loc, blockData);
                list.add(fakeBlockEntry);
            }


            if (type == FakeBlockType.IMPACT_PREDICTOR)
                lastImpactPredictor = System.currentTimeMillis();
            if (type == FakeBlockType.AIMING)
                lastAiming = System.currentTimeMillis();

        }
    }

    /**
     * returns true if the distance is in between the min and max limits of the imitate block distance
     * @param player player the check
     * @param loc location of the block
     * @return true if the distance is in the limits
     */
    public boolean isBetweenLimits(Player player, Location loc)
    {
        if (player == null || loc == null)
            return false;

        double dist = player.getLocation().distance(loc);
        if (dist > plugin.getMyConfig().getImitatedBlockMinimumDistance() &&
            dist < plugin.getMyConfig().getImitatedBlockMaximumDistance())
            return true;
        return false;
    }

    /**
     * returns true if the distance is below max limit of the imitate block distance
     * @param player player the check
     * @param loc location of the block
     * @return true if the distance is smaller than upper limit
     */
    public boolean belowMaxLimit(Player player, Location loc)
    {
        if (player == null || loc == null)
            return false;

        double dist = player.getLocation().distance(loc);
        if (dist < plugin.getMyConfig().getImitatedBlockMaximumDistance())
            return true;
        return false;
    }

}
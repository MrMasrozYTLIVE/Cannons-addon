package at.pavlov.cannons.listener;

import at.pavlov.cannons.Enum.ProjectileCause;
import at.pavlov.cannons.event.ProjectileImpactEvent;
import at.pavlov.cannons.projectile.FlyingProjectile;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class ProjectileRicochet implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onProjectileImpact(ProjectileImpactEvent event) {
        FlyingProjectile flyingProjectile = event.getFlyingProjectile();
        Location impactBlock = flyingProjectile.getImpactBlock();
        if (impactBlock == null) {
            return;
        }

        BlockFace blockFaceHit = event.getHitBlockFace();
        if (blockFaceHit == null) {
            return;
        }

        Vector cannonballDirection = flyingProjectile.getVelocity().normalize();
        int size = 5;
        Block[][][] blocks = new Block[size][size][size];

        for (int x = -size / 2; x <= size / 2; x++) {
            for (int y = -size / 2; y <= size / 2; y++) {
                for (int z = -size / 2; z <= size / 2; z++) {
                    blocks[x + size / 2][y + size / 2][z + size / 2] = impactBlock.clone().add(x, y, z).getBlock();
                }
            }
        }

        double[][][] data = new double[size][size][size];

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    if (blocks[x][y][z].getType() != Material.AIR) {
                        data[x][y][z] = 1.0;
                    }
                }
            }
        }

        RealMatrix matrix = MatrixUtils.createRealMatrix(data);
        EigenDecomposition decomposition = new EigenDecomposition(matrix);
        RealVector eigenvector = decomposition.getEigenvectors().getColumn(0);

        double dotProduct = cannonballDirection.(eigenvector);
        double angle = Math.toDegrees(Math.acos(dotProduct));
        double reflectionAngle = 0;
        if (angle < 70) {
            reflectionAngle = (180 - angle) / 180.0;
        } else {
            return;
        }

        Vector vectRicochet = flyingProjectile.getVelocity().clone().multiply(reflectionAngle);

        // Ignore slow cannonballs
        if (vectRicochet.length() < 0.6) {
            return;
        }

        // Ricochet the projectile using reflection angle
        if (reflectionAngle != 0) {
            Vector newDirection = blockFaceHit.getDirection().rotateAroundAxis(blockFaceHit.getNormal(), Math.toRadians(reflectionAngle * 180));
            flyingProjectile.setVelocity(newDirection.multiply(flyingProjectile.getVelocity().length()));
            flyingProjectile.setShooter(null);
            return;
        }

        // Ricochet
        new BukkitRunnable() {
            double time = 0;
            double gravity = 20;
            double velocity = vectRicochet.length();
            Vector direction = vectRicochet.normalize();
            Location particleLoc = impactBlock.clone();

            @Override
            public void run() {
                // calculate current position of the ricochet projectile
                double x = impactBlock.getX() + vectRicochet.getX() * time;
                double y = impactBlock.getY() + vectRicochet.getY() * time - 0.5 * gravity * time * time;
                double z = impactBlock.getZ() + vectRicochet.getZ() * time;
                Location currentLoc = new Location(impactBlock.getWorld(), x, y, z);

                // spawn particles along the ricochet path
                for (double t = 0; t < 1; t += 0.1) {
                    particleLoc.add(direction.multiply(0.1));
                    particleLoc.getWorld().spawnParticle(Particle.SMOKE_NORMAL, particleLoc, 1, 0, 0, 0, 0);
                }

                // stop if the projectile hits the ground or a solid block
                if (currentLoc.getBlock().getType() != Material.AIR) {
                    flyingProjectile.setCannonUID(null);
                    this.cancel();
                    return;
                }

                // update the velocity vector to account for gravity
                velocity -= gravity * 0.05;
                direction = direction.setY(velocity / vectRicochet.length());

                // update the position of the projectile
                flyingProjectile.setVelocity(direction.multiply(vectRicochet.length()));
                flyingProjectile.setProjectileCause(ProjectileCause.RICOCHET);
                flyingProjectile.setShooter(null);
                flyingProjectile.setImpactBlock(currentLoc.getBlock().getLocation().add(0.5, 0.5, 0.5));
                time += 0.05;
            }
        }.runTaskTimer(Bukkit.getPluginManager().getPlugin("Cannons"), 0, 1);
    }
}


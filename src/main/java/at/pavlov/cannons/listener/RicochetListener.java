package at.pavlov.cannons.listener;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class RicochetListener implements Listener {

    private static final double RICOCHET_ANGLE_THRESHOLD = 70.0;
    private static final int BLOCK_CUBOID_SIZE = 5;

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile == null) {
            return;
        }

        World world = projectile.getWorld();
        if (world == null) {
            return;
        }

        Entity shooter = (Entity) projectile.getShooter();
        if (shooter == null) {
            return;
        }

        // Check if the hit block is solid
        Location impactLocation = projectile.getLocation();
        Material impactBlockMaterial = world.getBlockAt(impactLocation).getType();
        if (!impactBlockMaterial.isSolid()) {
            return;
        }

        // Check if the angle of impact is greater than the threshold for ricochet
        Vector velocity = projectile.getVelocity();
        Vector normal = event.getHitBlockFace().getDirection();
        double angle = Math.toDegrees(Math.acos(velocity.dot(normal) / (velocity.length() * normal.length())));
        if (angle > RICOCHET_ANGLE_THRESHOLD) {
            // Ricochet the projectile once
            Vector reflection = velocity.subtract(normal.multiply(2 * velocity.dot(normal) / normal.lengthSquared()));
            projectile.setVelocity(reflection);
            projectile.setShooter(null); // clear the shooter to prevent damage to self
            return;
        }

        // Get the 5x5x5 cuboid of blocks around the impact location
        int halfCuboidSize = BLOCK_CUBOID_SIZE / 2;
        List<Location> blockLocations = new ArrayList<>();
        for (int x = impactLocation.getBlockX() - halfCuboidSize; x <= impactLocation.getBlockX() + halfCuboidSize; x++) {
            for (int y = impactLocation.getBlockY() - halfCuboidSize; y <= impactLocation.getBlockY() + halfCuboidSize; y++) {
                for (int z = impactLocation.getBlockZ() - halfCuboidSize; z <= impactLocation.getBlockZ() + halfCuboidSize; z++) {
                    blockLocations.add(new Location(world, x, y, z));
                }
            }
        }
        double[][][] blockMatrix = new double[BLOCK_CUBOID_SIZE][BLOCK_CUBOID_SIZE][BLOCK_CUBOID_SIZE];
        for (int i = 0; i < blockLocations.size(); i++) {
            Location blockLocation = blockLocations.get(i);
            Material blockMaterial = world.getBlockAt(blockLocation).getType();
            if (blockMaterial.isSolid()) {
                blockMatrix[i / (BLOCK_CUBOID_SIZE * BLOCK_CUBOID_SIZE)][(i / BLOCK_CUBOID_SIZE) % BLOCK_CUBOID_SIZE][i % BLOCK_CUBOID_SIZE] = 1.0;
            }
        }

        // Calculate centroid of blocks
        Vector centroid = new Vector(0, 0, 0);
        for (Location blockLocation : blockLocations) {
            centroid.add(new Vector(blockLocation.getX(), blockLocation.getY(), blockLocation.getZ()));
        }
        centroid.multiply(1.0 / blockLocations.size());

        // Calculate POV for eigenvector calculation
        Vector cannonDirection = projectile.getVelocity().normalize();
        Vector POV = centroid.clone().add(cannonDirection.clone().multiply(-1 * BLOCK_CUBOID_SIZE));

        // Calculate eigenvector
        double[][] mat = new double[BLOCK_CUBOID_SIZE][BLOCK_CUBOID_SIZE];
        for (int i = 0; i < BLOCK_CUBOID_SIZE; i++) {
            for (int j = 0; j < BLOCK_CUBOID_SIZE; j++) {
                for (int k = 0; k < BLOCK_CUBOID_SIZE; k++) {
                    double dx = i - POV.getX();
                    double dy = j - POV.getY();
                    double dz = k - POV.getZ();
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    mat[i][j] += blockMatrix[i][j][k] * Math.exp(-1 * distance);
                }
            }
        }

        RealMatrix matrix = MatrixUtils.createRealMatrix(mat);
        EigenDecomposition eigDecomp = new EigenDecomposition(matrix);
        double[] eigVals = eigDecomp.getRealEigenvalues();
        RealMatrix eigVecs = eigDecomp.getV();

        // Find the eigenvector with the smallest eigenvalue
        int smallestEigIndex = 0;
        double smallestEigVal = eigVals[0];
        for (int i = 1; i < eigVals.length; i++) {
            if (eigVals[i] < smallestEigVal) {
                smallestEigIndex = i;
                smallestEigVal = eigVals[i];
            }
        }
        RealMatrix smallestEigVec = eigVecs.getColumnMatrix(smallestEigIndex);

        // Calculate new projectile velocity based on eigenvector
        double[] cannonDirectionArray = {cannonDirection.getX(), cannonDirection.getY(), cannonDirection.getZ() };
        RealMatrix cannonDirectionMatrix = MatrixUtils.createColumnRealMatrix(cannonDirectionArray);
        RealMatrix dotProductMatrix = cannonDirectionMatrix.transpose().multiply(smallestEigVec);
        double dotProduct = dotProductMatrix.getEntry(0, 0);
        RealMatrix scaledEigVec = smallestEigVec.scalarMultiply(2 * dotProduct);
        RealMatrix newVelocityMatrix = scaledEigVec.subtract(cannonDirectionMatrix);
        double norm = newVelocityMatrix.getNorm();
        RealMatrix normalizedVelocityMatrix = newVelocityMatrix.scalarMultiply(1/norm);
        Vector newVelocity = new Vector(normalizedVelocityMatrix.getEntry(0, 0), normalizedVelocityMatrix.getEntry(1, 0), normalizedVelocityMatrix.getEntry(2, 0)).multiply(projectile.getVelocity().length());
        projectile.setVelocity(newVelocity);
        projectile.setShooter(null);

        // clear the shooter to prevent damage to self

    }
}

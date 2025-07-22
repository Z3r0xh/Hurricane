package org.geysermc.hurricane;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.PointedDripstone;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.Contract;

import java.lang.reflect.*;
import java.util.Arrays;

public final class CollisionFix implements Listener {
    private final boolean bambooEnabled;
    private final BoundingBox originalBambooBoundingBox = box(6.5D, 0.0D, 6.5D, 9.5D, 16D, 9.5D);

    private final boolean pointedDripstoneEnabled;
    private final BoundingBox tipMergeDripstoneBox = box(5D, 0D, 5D, 11D, 16D, 11D);
    private final BoundingBox tipUpDripstoneBox = box(5D, 0D, 5D, 11D, 11D, 11D);
    private final BoundingBox tipDownDripstoneBox = box(5D, 5D, 5D, 11D, 16D, 11D);
    private final BoundingBox frustumDripstoneBox = box(4D, 0D, 4D, 12D, 16D, 12D);
    private final BoundingBox middleDripstoneBox = box(3D, 0D, 3D, 13D, 16D, 13D);
    private final BoundingBox baseDripstoneBox = box(2D, 0D, 2D, 14D, 16D, 14D);

    public CollisionFix(Plugin plugin, boolean bambooEnabled, boolean pointedDripstoneEnabled) {
        // Make any given block have zero collision. Lagback solved...!
        this.bambooEnabled = bambooEnabled;
        this.pointedDripstoneEnabled = pointedDripstoneEnabled;

        // Log server version information
        String serverVersion = plugin.getServer().getVersion();
        plugin.getLogger().info("Server version: " + serverVersion);

        if (bambooEnabled) {
            try {
                final Class<?> bambooBlockClass = NMSReflection.getNMSClass("world.level.block", "BlockBamboo");
                final boolean newerThanOrEqualTo1170 = NMSReflection.mojmap;
                // Codec field being first bumps all fields - as of 1.20.4
                final boolean newerThanOrEqualTo1204 = Arrays.stream(bambooBlockClass.getFields()).anyMatch(field -> field.getType().getSimpleName().equals("MapCodec"));
                final boolean newerThanOrEqualTo1215 = checkClassExists("world.level.block", "LeafLitterBlock");
                
                plugin.getLogger().info("Version detection - 1.17.0+: " + newerThanOrEqualTo1170 + 
                    ", 1.20.4+: " + newerThanOrEqualTo1204 + ", 1.21.5+: " + newerThanOrEqualTo1215);
                
                String fieldName = newerThanOrEqualTo1215 ? "S" : newerThanOrEqualTo1204 ? "g" : newerThanOrEqualTo1170 ? "f" : "c";
                plugin.getLogger().info("Attempting to access bamboo field: " + fieldName);
                
                final Field bambooBoundingBox = ReflectionAPI.getFieldAccessible(bambooBlockClass, fieldName);
                
                // Verificar el tipo del campo antes de aplicar el fix
                String fieldType = bambooBoundingBox.getType().getSimpleName();
                plugin.getLogger().info("Bamboo field type detected: " + fieldType);
                
                boolean success = applyNoBoundingBox(bambooBoundingBox, plugin);
                if (success) {
                    plugin.getLogger().info("Bamboo collision hack enabled.");
                } else {
                    plugin.getLogger().warning("Bamboo collision hack failed - unsupported field type: " + fieldType);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error enabling bamboo collision hack: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        if (pointedDripstoneEnabled) {
            // We need to disable all dripstone collision, and there's six...
            try {
                final Class<?> dripstoneBlockClass = NMSReflection.getMojmapNMSClass("world.level.block.PointedDripstoneBlock");
                // The method names change between versions, but there's always six next to each other.
                // There is one we do not need to touch (1.18+) because it doesn't deal with collision.
                boolean foundBoundingBoxes = false;
                int boundingBoxCount = 0;
                int successCount = 0;
                
                for (Field field : dripstoneBlockClass.getDeclaredFields()) {
                    if (boundingBoxCount >= 6) {
                        // Don't apply more than necessary
                        break;
                    }
                    if (Modifier.isStatic(field.getModifiers()) && field.getType().getSimpleName().equals("VoxelShape")) {
                        foundBoundingBoxes = true;
                        boundingBoxCount++;
                        boolean success = applyNoBoundingBox(field, plugin);
                        if (success) {
                            successCount++;
                        }
                    } else if (foundBoundingBoxes) {
                        break;
                    }
                }
                plugin.getLogger().info("Dripstone collision hack enabled (" + successCount + "/" + boundingBoxCount + " fields modified).");
            } catch (Exception e) {
                plugin.getLogger().severe("Error enabling dripstone collision hack: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Because the "fixed" blocks have an empty bounding box, they can be placed inside players... prevent that to the best of
     * our ability.
     */
    @EventHandler
    public void onBlockPlace(final BlockPlaceEvent event) {
        final Block placed = event.getBlockPlaced();
        final Material material = placed.getType();
        if (this.bambooEnabled && material.equals(Material.BAMBOO)) {
            testIfCanBuild(event, this.originalBambooBoundingBox);
        } else if (this.pointedDripstoneEnabled && material.equals(Material.POINTED_DRIPSTONE)) {
            final PointedDripstone data = (PointedDripstone) placed.getBlockData();
            final BoundingBox boundingBox;
            switch (data.getThickness()) {
                case TIP:
                    boundingBox = data.getVerticalDirection() == BlockFace.DOWN ? tipDownDripstoneBox : tipUpDripstoneBox;
                    break;
                case TIP_MERGE:
                    boundingBox = tipMergeDripstoneBox;
                    break;
                case FRUSTUM:
                    boundingBox = frustumDripstoneBox;
                    break;
                case MIDDLE:
                    boundingBox = middleDripstoneBox;
                    break;
                case BASE:
                default:
                    boundingBox = baseDripstoneBox;
                    break;
            }
            testIfCanBuild(event, boundingBox);
        }
    }

    private void testIfCanBuild(final BlockPlaceEvent event, final BoundingBox box) {
        final BoundingBox currentBoundingBox = box.clone().shift(event.getBlockPlaced().getLocation());
        if (event.getPlayer().getBoundingBox().overlaps(currentBoundingBox)) {
            // Don't place this block as it intersects
            event.setBuild(false);
        }
    }

    /**
     * Emulates NMS Block#box
     */
    @Contract("_, _, _, _, _, _-> new")
    private BoundingBox box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return new BoundingBox(minX / 16D, minY / 16D, minZ / 16D, maxX / 16D, maxY / 16D, maxZ / 16D);
    }

    /**
     * Verifica silenciosamente si una clase existe sin generar warnings en los logs
     */
    private static boolean checkClassExists(String packageName, String className) {
        try {
            // Intentar con Mojmap primero
            Class.forName("net.minecraft." + packageName + "." + className);
            return true;
        } catch (ClassNotFoundException e) {
            try {
                // Intentar directamente
                Class.forName("net.minecraft." + className);
                return true;
            } catch (ClassNotFoundException ex) {
                // No existe, pero no loggeamos el error
                return false;
            }
        }
    }

    private static boolean applyNoBoundingBox(Field field, Plugin plugin) {
        try {
            final double x1 = 0, y1 = 0, z1 = 0, x2 = 0, y2 = 0, z2 = 0;
            String fieldTypeName = field.getType().getSimpleName();
            
            if (fieldTypeName.equals("AxisAlignedBB")) {
                Class<?> boundingBoxClass = field.getType();
                Constructor<?> boundingBoxConstructor = boundingBoxClass.getConstructor(double.class, double.class, double.class,
                        double.class, double.class, double.class);
                Object boundingBox = boundingBoxConstructor.newInstance(x1, y1, z1, x2, y2, z2);
                ReflectionAPI.setFinalValue(field, boundingBox);
                return true;
            } else if (fieldTypeName.equals("VoxelShape")) {
                Method createVoxelShape;
                try {
                    // 1.18+ - obfuscated methods
                    createVoxelShape = ReflectionAPI.getMethod(NMSReflection.getNMSClass("world.phys.shapes", "VoxelShapes"), "b",
                            double.class, double.class, double.class, double.class, double.class, double.class);
                } catch (NoSuchMethodException e) {
                    createVoxelShape = ReflectionAPI.getMethod(NMSReflection.getNMSClass("world.phys.shapes", "VoxelShapes"), "create",
                            double.class, double.class, double.class, double.class, double.class, double.class);
                }
                Object boundingBox = ReflectionAPI.invokeMethod(createVoxelShape, x1, y1, z1, x2, y2, z2);
                ReflectionAPI.setFinalValue(field, boundingBox);
                return true;
            } else {
                // Log información sobre el tipo desconocido para debugging
                plugin.getLogger().warning("Unknown field type for collision fix: " + fieldTypeName + 
                    " (Full type: " + field.getType().getName() + ")");
                return false;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to apply no bounding box to field " + field.getName() + ": " + e.getMessage());
            return false;
        }
    }
}
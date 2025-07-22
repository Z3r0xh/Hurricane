package org.geysermc.hurricane;

import org.bukkit.Bukkit;

// From ViaRewind Legacy Support
public final class NMSReflection {
    private static String version;
    /**
     * Cheap hack to allow different fields.
     */
    public static boolean mojmap = true;

    public static String getVersion() {
        if (version != null) {
            return version;
        }
        
        try {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            String[] parts = packageName.split("\\.");
            
            // Para versiones modernas (1.17+) que usan Mojmap, no hay versión en el paquete
            if (parts.length < 4) {
                version = "modern"; // Indicador para versiones modernas
                return version;
            }
            
            version = parts[3];
            return version;
        } catch (Exception e) {
            // Si falla, asumimos versión moderna
            version = "modern";
            return version;
        }
    }

    /**
     * 1.17+
     */
    public static Class<?> getMojmapNMSClass(String name) {
        try {
            return Class.forName("net.minecraft." + name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static Class<?> getNMSClass(String post1_16Prefix, String name) {
        Class<?> newNMSClass = getMojmapNMSClass(post1_16Prefix + "." + name);
        if (newNMSClass != null) {
            return newNMSClass;
        }

        // Else Mojmap/post-1.17 is not in effect
        mojmap = false;
        String serverVersion = getVersion();
        
        // Para versiones modernas que no tienen el formato antiguo
        if ("modern".equals(serverVersion)) {
            // Intentar con Mojmap directamente
            try {
                return Class.forName("net.minecraft." + post1_16Prefix.replace("/", ".") + "." + name);
            } catch (ClassNotFoundException e) {
                // Si falla, intentar sin prefijo
                try {
                    return Class.forName("net.minecraft." + name);
                } catch (ClassNotFoundException ex) {
                    ex.printStackTrace();
                    return null;
                }
            }
        }
        
        try {
            return Class.forName("net.minecraft.server." + serverVersion + "." + name);
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
            return null;
        }
    }
}
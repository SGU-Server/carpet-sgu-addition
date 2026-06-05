package org.carpet.sgu.compat;

import net.fabricmc.loader.api.FabricLoader;
import java.lang.reflect.Method;

public class LmsIntegrationHelper {
    private static boolean initialized = false;
    private static Method forceOfflineMethod = null;

    public static boolean shouldForceOfflineProfile(String username) {
        if (!initialized) {
            if (FabricLoader.getInstance().isModLoaded("carpet-lms-addition")) {
                try {
                    Class<?> lmsClass = Class.forName("cn.nm.lms.carpetlmsaddition.bot.FakePlayerSpawner");
                    forceOfflineMethod = lmsClass.getMethod("shouldForceOfflineProfile", String.class);
                } catch (Exception ignored) {
                }
            }
            initialized = true;
        }

        if (forceOfflineMethod != null) {
            try {
                return (Boolean) forceOfflineMethod.invoke(null, username);
            } catch (Exception ignored) {
            }
        }
        
        return false;
    }
}

package com.github.alexthe666.alexsmobs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Calendar;

public class AlexsMobs {
    public static final Logger LOGGER = LogManager.getLogger();
    public static final String MODID = "alexsmobs";
    
    private static boolean isAprilFools = false;
    private static boolean isHalloween = false;
    
    static {
        // Initialize date-based flags
        Calendar calendar = Calendar.getInstance();
        isAprilFools = calendar.get(Calendar.MONTH) + 1 == 4 && calendar.get(Calendar.DATE) == 1;
        isHalloween = calendar.get(Calendar.MONTH) + 1 == 10 && calendar.get(Calendar.DATE) >= 29 && calendar.get(Calendar.DATE) <= 31;
    }
    
    public static boolean isAprilFools() {
        return isAprilFools;
    }
    
    public static boolean isHalloween() {
        return isHalloween;
    }
}

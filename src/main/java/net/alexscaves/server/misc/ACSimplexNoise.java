package net.alexscaves.server.misc;

// Stub simplex noise implementation
public class ACSimplexNoise {
    public static double noise(double x, double y) {
        // Simplified noise - TODO: implement proper simplex noise
        return Math.sin(x) * Math.cos(y);
    }
    
    public static double noise(double x, double y, double z) {
        // Simplified noise - TODO: implement proper simplex noise
        return Math.sin(x) * Math.cos(y) * Math.sin(z);
    }
}

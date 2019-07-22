package cuchaz.enigma.translation.mapping.serde.mcp.mappings;

public enum JarDist {
    CLIENT, SERVER, BOTH;

    public static JarDist commonDist(JarDist dist1, JarDist dist2) {
        if (dist1 == BOTH) {
            return dist2;
        }
        if (dist2 == BOTH) {
            return dist1;
        }
        if (dist2 != dist1) {
            throw new IllegalArgumentException("Common dist for " + dist1 + " and " + dist2 + " is 'none', so member with it can't exist");
        }
        return dist1;
    }

    public static JarDist merge(JarDist dist1, JarDist dist2) {
        if (dist1 == dist2) {
            return dist1;
        }
        return BOTH;
    }
}

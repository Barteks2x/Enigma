package cuchaz.enigma.mcp.loader.mappings;

import java.util.Map;

public class McpParamEntry {
    private final String srgName;
    private final String mcpName;
    private final int side; // I have no idea what this value means, but mcp CSVs contain it. It appears to only ever be either 0 or 2.
    private final String comment;

    public McpParamEntry(String srgName, String mcpName, int side, String comment) {
        this.srgName = srgName;
        this.mcpName = mcpName;
        this.side = side;
        this.comment = comment;
    }

    public static McpParamEntry parse(String csvLine) {
        String[] parts = csvLine.split(",");
        return new McpParamEntry(parts[0], parts[1], Integer.parseInt(parts[2]), McpMappings.extractComment(parts));
    }

    public static McpParamEntry fromMcpbotCommand(String[] parts, Map<String, Integer> methodIdDistFromMappings, McpMappings existing) {
        String srg = parts[1];
        String deobf = parts[2];
        int side;
        McpParamEntry prevField = existing.getParam(srg);
        if (prevField != null) {
            side = prevField.side;
        } else {
            side = methodIdDistFromMappings.getOrDefault(McpMappings.getSrgIdStr(srg), JarDist.BOTH.ordinal());
        }
        String comment = McpMappings.extractMcpbotComment(parts);
        return new McpParamEntry(srg, deobf, side, comment);
    }

    public String getSrgName() {
        return srgName;
    }

    public String getMcpName() {
        return mcpName;
    }

    public int getSide() {
        return side;
    }

    public String getComment() {
        return comment;
    }

}

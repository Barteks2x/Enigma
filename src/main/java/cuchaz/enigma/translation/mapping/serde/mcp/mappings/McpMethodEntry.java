package cuchaz.enigma.translation.mapping.serde.mcp.mappings;

public class McpMethodEntry {
    private final String srgName;
    private final String mcpName;
    private final int side; // I have no idea what this value means, but mcp CSVs contain it. It appears to only ever be either 0 or 2.
    private final String comment;

    public McpMethodEntry(String srgName, String mcpName, int side, String comment) {
        this.srgName = srgName;
        this.mcpName = mcpName;
        this.side = side;
        this.comment = comment;
    }

    public static McpMethodEntry parse(String csvLine) {
        String[] parts = csvLine.split(",");
        return new McpMethodEntry(parts[0], parts[1], Integer.parseInt(parts[2]), McpMappings.extractComment(parts));
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

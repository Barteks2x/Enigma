package cuchaz.enigma.translation.mapping.serde.mcp.mappings;

import com.google.common.collect.Streams;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class McpMappings {

    private final Map<String, String> srg2mcp;

    private final Map<String, McpFieldEntry> mcpFields;
    private final Map<String, McpMethodEntry> mcpMethods;
    private final Map<String, McpParamEntry> mcpParams;

    public McpMappings(List<String> methods, List<String> fields, List<String> params) {
        // format: searge,name,(whatever)
        srg2mcp = Streams.concat(methods.stream().skip(1), fields.stream().skip(1), params.stream().skip(1))
                .map(s -> s.split(","))
                .collect(Collectors.toMap(p -> p[0], p -> p[1]));

        mcpFields = fields.stream()
                .skip(1)
                .map(McpFieldEntry::parse)
                .collect(Collectors.toMap(McpFieldEntry::getSrgName, p -> p));

        mcpMethods = methods.stream()
                .skip(1)
                .map(McpMethodEntry::parse)
                .collect(Collectors.toMap(McpMethodEntry::getSrgName, p -> p));

        mcpParams = fields.stream()
                .skip(1)
                .map(McpParamEntry::parse)
                .collect(Collectors.toMap(McpParamEntry::getSrgName, p -> p));
    }

    public static McpMappings create(Path path, boolean optional) throws IOException {
        Path methods = path.resolve("methods.csv");
        Path fields = path.resolve("fields.csv");
        Path params = path.resolve("params.csv");

        return new McpMappings(
                Files.exists(methods) || !optional ? Files.readAllLines(methods) : new ArrayList<>(),
                Files.exists(fields) || !optional ? Files.readAllLines(fields) : new ArrayList<>(),
                Files.exists(path) || !optional ? Files.readAllLines(params) : new ArrayList<>()
        );
    }

    public McpFieldEntry getField(String srgName) {
        return mcpFields.get(srgName);
    }

    public McpMethodEntry getMethod(String srgName) {
        return mcpMethods.get(srgName);
    }

    public McpParamEntry getParam(String srgName) {
        return mcpParams.get(srgName);
    }

    public String srg2mcp(String srg) {
        return srg2mcp.getOrDefault(srg, srg);
    }

    public Stream<Map.Entry<String, String>> entryStream() {
        return srg2mcp.entrySet().stream();
    }

    public Stream<String> keyStream() {
        return srg2mcp.keySet().stream();
    }

    public String map(String srgName) {
        return srg2mcp.getOrDefault(srgName, srgName);
    }

    public static String getSrgIdStr(String srg) {
        return srg.split("_")[1];
    }

    public static int getSrgIdInt(String srg) {
        return Integer.parseInt(getSrgIdStr(srg));
    }

    public static boolean isSrgMethod(String name) {
        return name.matches("func_\\d+_.+");
    }

    public static boolean isSrgField(String name) {
        return name.matches("field_\\d+_.+");
    }

    public static boolean isSrgParam(String name) {
        return name.matches("p_i?\\d+_\\d+_.*");
    }

    public static boolean isSrg(String name) {
        return isSrgField(name) || isSrgMethod(name) || isSrgParam(name);
    }

    public static String extractComment(String[] csvLine) {
        if (csvLine.length < 4) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 3; i < csvLine.length - 1; i++) {
            sb.append(csvLine[i]).append(",");
        }
        sb.append(csvLine[csvLine.length - 1]);
        return sb.toString();
    }

    public boolean hasMethod(String srg) {
        return srg2mcp.containsKey(srg);
    }

    public boolean hasParam(String srg) {
        return srg2mcp.containsKey(srg);
    }

    public boolean hasField(String srg) {
        return srg2mcp.containsKey(srg);
    }

    public Map<String, Integer> makeMethodId2SideMap() {
        return mcpMethods.entrySet().stream()
                .map(e -> new SimpleEntry<>(McpMappings.getSrgIdStr(e.getKey()), e.getValue().getSide()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}

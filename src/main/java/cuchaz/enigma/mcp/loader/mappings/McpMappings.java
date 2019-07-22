package cuchaz.enigma.mcp.loader.mappings;

import com.google.common.collect.Streams;
import cuchaz.enigma.mcp.loader.JarTypeInfo;
import cuchaz.enigma.mcp.loader.McpConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class McpMappings {

    private final Map<String, String> srg2mcp;

    private final Map<String, McpFieldEntry> mcpFields;
    private final Map<String, McpMethodEntry> mcpMethods;
    private final Map<String, McpParamEntry> mcpParams;

    public McpMappings(Map<String, String> srg2mcp, Map<String, McpFieldEntry> mcpFields, Map<String, McpMethodEntry> mcpMethods,
            Map<String, McpParamEntry> mcpParams) {
        this.srg2mcp = srg2mcp;
        this.mcpFields = mcpFields;
        this.mcpMethods = mcpMethods;
        this.mcpParams = mcpParams;
    }

    public static McpMappings create(Path path, boolean optional) throws IOException {
        Path methodsPath = path.resolve("methods.csv");
        Path fieldsPath = path.resolve("fields.csv");
        Path paramsPath = path.resolve("params.csv");

        List<String> methods = Files.exists(methodsPath) || !optional ? Files.readAllLines(methodsPath) : new ArrayList<>();
        List<String> fields = Files.exists(fieldsPath) || !optional ? Files.readAllLines(fieldsPath) : new ArrayList<>();
        List<String> params = Files.exists(paramsPath) || !optional ? Files.readAllLines(paramsPath) : new ArrayList<>();

        // format: searge,name,(whatever)
        Map<String, String> srg2mcp = Streams.concat(methods.stream().skip(1), fields.stream().skip(1), params.stream().skip(1))
                .map(s -> s.split(","))
                .collect(Collectors.toMap(p -> p[0], p -> p[1]));

        Map<String, McpFieldEntry> mcpFields = fields.stream()
                .skip(1)
                .map(McpFieldEntry::parse)
                .collect(Collectors.toMap(McpFieldEntry::getSrgName, p -> p));

        Map<String, McpMethodEntry> mcpMethods = methods.stream()
                .skip(1)
                .map(McpMethodEntry::parse)
                .collect(Collectors.toMap(McpMethodEntry::getSrgName, p -> p));

        Map<String, McpParamEntry> mcpParams = fields.stream()
                .skip(1)
                .map(McpParamEntry::parse)
                .collect(Collectors.toMap(McpParamEntry::getSrgName, p -> p));

        return new McpMappings(srg2mcp, mcpFields, mcpMethods, mcpParams);
    }

    public static McpMappings fromDeltas(Path path, JarTypeInfo jarInfo, McpConfig mcpConf, McpMappings existing) throws IOException {
        List<String> lines = Files.readAllLines(path);

        Map<String, String> srg2mcp = lines.stream()
                .map(s -> s.split(" "))
                .collect(Collectors.toMap(p -> p[1], p -> p[2]));

        Map<String, JarDist> fieldDist = jarInfo.makeSrgFieldDistMap();
        Map<String, JarDist> methodDist = jarInfo.makeSrgMethodDistMap(mcpConf.getConstructorIds());
        Map<String, Integer> methodIdDist = existing.makeMethodId2SideMap();

        Map<String, McpFieldEntry> mcpFields = lines.stream()
                .map(s -> s.split(" "))
                .filter(p -> p[0].equals("!sf"))
                .map(p -> McpFieldEntry.fromMcpbotCommand(p, fieldDist, existing))
                .collect(Collectors.toMap(McpFieldEntry::getSrgName, p -> p));

        Map<String, McpMethodEntry> mcpMethods = lines.stream()
                .map(s -> s.split(" "))
                .filter(p -> p[0].equals("!sm"))
                .map(p -> McpMethodEntry.fromMcpbotCommand(p, methodDist, existing))
                .collect(Collectors.toMap(McpMethodEntry::getSrgName, p -> p));

        Map<String, McpParamEntry> mcpParams = lines.stream()
                .map(s -> s.split(" "))
                .filter(p -> p[0].equals("!sp"))
                .map(p -> McpParamEntry.fromMcpbotCommand(p, methodIdDist, existing))
                .collect(Collectors.toMap(McpParamEntry::getSrgName, p -> p));

        return new McpMappings(srg2mcp, mcpFields, mcpMethods, mcpParams);
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

    public static String extractMcpbotComment(String[] mcpbotLine) {
        if (mcpbotLine.length < 4) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 3; i < mcpbotLine.length - 1; i++) {
            sb.append(mcpbotLine[i]).append(" ");
        }
        sb.append(mcpbotLine[mcpbotLine.length - 1]);
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

    public McpMappings applyDeltas(McpMappings deltas) {
        McpMappings ret = new McpMappings(new HashMap<>(srg2mcp), new HashMap<>(mcpFields), new HashMap<>(mcpMethods), new HashMap<>(mcpParams));
        ret.srg2mcp.putAll(deltas.srg2mcp);
        ret.mcpMethods.putAll(deltas.mcpMethods);
        ret.mcpFields.putAll(deltas.mcpFields);
        ret.mcpParams.putAll(deltas.mcpParams);
        return ret;
    }

    public Stream<Map.Entry<String, String>> fieldStream() {
        return mcpFields.values().stream().map(e -> new SimpleEntry<>(e.getSrgName(), e.getMcpName()));
    }
    public Stream<Map.Entry<String, String>> methodStream() {
        return mcpMethods.values().stream().map(e -> new SimpleEntry<>(e.getSrgName(), e.getMcpName()));
    }
    public Stream<Map.Entry<String, String>> paramStream() {
        return mcpParams.values().stream().map(e -> new SimpleEntry<>(e.getSrgName(), e.getMcpName()));
    }
}

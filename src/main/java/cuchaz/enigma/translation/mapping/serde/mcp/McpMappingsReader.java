package cuchaz.enigma.translation.mapping.serde.mcp;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;

import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.MappingSaveParameters;
import cuchaz.enigma.translation.mapping.serde.MappingsReader;
import cuchaz.enigma.translation.mapping.serde.mcp.mappings.McpMappings;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.translation.mapping.tree.HashEntryTree;
import cuchaz.enigma.translation.mapping.tree.McpHashEntryTree;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.LocalVariableEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public enum McpMappingsReader implements MappingsReader {

    INSTANCE;

    @Override public EntryTree<EntryMapping> read(Path path, ProgressListener progress, MappingSaveParameters saveParameters)
            throws IOException {
        JarTypeInfo jarInfo = JarTypeInfo.fromJar(path.resolve("joined_srg.jar"));
        McpConfig mcpConfig = McpConfig.create(path, jarInfo);

        McpMappings mappings = McpMappings.create(path, false);
        HashEntryTree<EntryMapping> entries = new HashEntryTree<>();
        mcpConfig.obf2srgClasses.values().forEach(name -> {
            ClassEntry entry = mcpConfig.srg2srgClassEntry.get(name);
            entries.insert(entry, new EntryMapping(entry.getName()));
        });
        mcpConfig.srg2srgFieldEntry.forEach((srgClass, fields) -> fields.forEach((srg, srgEntry) -> {
            String srgName = srgEntry.getName();
            entries.insert(srgEntry, new EntryMapping(mappings.map(srgName)));
        }));
        mcpConfig.srg2srgMethodEntry.forEach((srgClass, methods) -> methods.forEach((srg, srgEntry) -> {
            String srgName = srgEntry.getName();
            entries.insert(srgEntry, new EntryMapping(mappings.map(srgName)));
        }));
        Map<String, Set<MethodEntry>> srgId2methodEntry = mcpConfig.srgId2MethodEntry;

        Map<String, Set<ClassEntry>> methodSrg2owner = mcpConfig.srg2srgMethodEntry.entrySet().stream()
                .flatMap(e -> e.getValue().entrySet().stream()
                        .filter(m -> McpMappings.isSrgMethod(m.getKey()))
                        .map(srg -> new SimpleEntry<>(srg.getKey(), srg.getValue().getParent()))
                ).collect(groupingBy(Map.Entry::getKey, mapping(SimpleEntry::getValue, Collectors.toSet())));

        mappings.entryStream()
                .filter(e -> McpMappings.isSrgParam(e.getKey()))
                .forEach(e -> {
                    String[] split = e.getKey().split("_");
                    // 0_1_2...
                    String id = split[1];
                    int localIndex = Integer.parseInt(split[2]);
                    Set<MethodEntry> methods = srgId2methodEntry.get(id);

                    if (methods != null) {
                        methods.forEach(methodEntry -> {
                            LocalVariableEntry param = new LocalVariableEntry(methodEntry, localIndex, e.getKey(), true);
                            entries.insert(param, new EntryMapping(e.getValue()));
                        });
                    }
                });
        return new McpHashEntryTree<>(entries, mappings, jarInfo, mcpConfig);
    }
}

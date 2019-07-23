package cuchaz.enigma.translation.mapping.serde.mcp;

import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.MappingSaveParameters;
import cuchaz.enigma.translation.mapping.serde.MappingsReader;
import cuchaz.enigma.translation.mapping.serde.mcp.mappings.McpMappings;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.translation.mapping.tree.HashEntryTree;
import cuchaz.enigma.translation.mapping.tree.McpHashEntryTree;
import cuchaz.enigma.translation.representation.MethodDescriptor;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.LocalVariableEntry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

public enum McpMappingsReader implements MappingsReader {

    INSTANCE;

    @Override public EntryTree<EntryMapping> read(Path path, ProgressListener progress, MappingSaveParameters saveParameters)
            throws IOException {
        final int stepCount = 12;

        AtomicInteger step = new AtomicInteger();

        progress.init(stepCount, "Loading MCP mappings");

        progress.step(step.getAndIncrement(), "Loading joined_srg.jar");
        JarTypeInfo jarInfo = JarTypeInfo.fromJar(path.resolve("joined_srg.jar"));

        McpConfig mcpConfig = McpConfig.create(path, jarInfo, msg -> progress.step(step.getAndIncrement(), msg));
        progress.step(step.getAndIncrement(), "Loading methods.csv, fields.csv and params.csv");
        McpMappings mappings = McpMappings.create(path, false);

        progress.step(step.getAndIncrement(), "Adding class mappings");
        HashEntryTree<EntryMapping> entries = new HashEntryTree<>();
        mcpConfig.obf2srgClasses.values().forEach(name -> {
            ClassEntry entry = mcpConfig.srg2srgClassEntry.get(name);
            entries.insert(entry, new EntryMapping(entry.getName()));
        });
        progress.step(step.getAndIncrement(), "Adding field mappings");
        mcpConfig.srg2srgFieldEntry.forEach((srgClass, fields) -> fields.forEach((srg, srgEntry) -> {
            String srgName = srgEntry.getName();
            entries.insert(srgEntry, new EntryMapping(mappings.map(srgName)));
        }));
        progress.step(step.getAndIncrement(), "Adding method mappings");
        mcpConfig.srg2srgMethodEntry.forEach((srgClass, methods) -> methods.forEach((srg, srgEntry) -> {
            String srgName = srgEntry.getName();
            entries.insert(srgEntry, new EntryMapping(mappings.map(srgName)));
        }));
        progress.step(step.getAndIncrement(), "Adding method parameter mappings");
        loadParams(jarInfo, mcpConfig, mappings, entries);
        if (step.get() != stepCount) {
            throw new Error("Incorrect step count, expected " + stepCount + " but got " + step.get() + ", fix the code");
        }
        return new McpHashEntryTree<>(entries, mappings, jarInfo, mcpConfig);
    }

    private void loadParams(JarTypeInfo jarInfo, McpConfig mcpConfig, McpMappings mappings, HashEntryTree<EntryMapping> entries) {
        mcpConfig.srgId2MethodEntry.entrySet().stream()
                .forEach(entry -> {
                    String srgId = entry.getKey();
                    entry.getValue().forEach(method -> {
                        MethodDescriptor desc = method.getDesc();
                        int argId = jarInfo.isStatic(srgId) ? 0 : 1;
                        for (TypeDescriptor type : desc.getArgumentDescs()) {
                            String paramSrg = String.format("p_%s_%d_", srgId, argId);
                            String mapped = mappings.map(paramSrg);

                            LocalVariableEntry param = new LocalVariableEntry(method, argId, "arg" + argId, true);
                            entries.insert(param, new EntryMapping(mapped));

                            argId += type.getSize();
                        }
                    });
                });
    }
}

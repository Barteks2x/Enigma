package cuchaz.enigma.translation.mapping.serde.mcp;

import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.MappingDelta;
import cuchaz.enigma.translation.mapping.MappingSaveParameters;
import cuchaz.enigma.translation.mapping.serde.MappingsWriter;
import cuchaz.enigma.translation.mapping.serde.mcp.mappings.JarDist;
import cuchaz.enigma.translation.mapping.serde.mcp.mappings.McpFieldEntry;
import cuchaz.enigma.translation.mapping.serde.mcp.mappings.McpMappings;
import cuchaz.enigma.translation.mapping.serde.mcp.mappings.McpMethodEntry;
import cuchaz.enigma.translation.mapping.tree.DeltaTrackingTree;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.translation.mapping.tree.McpHashEntryTree;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.LocalVariableEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public enum McpMappingsWriter implements MappingsWriter {
    WRITER_FULL(Mode.FULL),
    WRITER_DELTA(Mode.DELTA_MCPBOT);

    private final Mode mode;

    McpMappingsWriter(Mode mode) {
        this.mode = mode;
    }

    @Override public void write(EntryTree<EntryMapping> mappings, MappingDelta<EntryMapping> delta, Path path, ProgressListener progress,
            MappingSaveParameters saveParams) {
        try {
            EntryTree<EntryMapping> directMappings = mappings;
            if (mappings instanceof DeltaTrackingTree) {
                directMappings = ((DeltaTrackingTree<EntryMapping>) mappings).getDelegate();
            }
            switch (mode) {
                case DELTA_MCPBOT:
                    writeDelta(directMappings, delta, path);
                case FULL:
                    writeFull(directMappings, path);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void writeFull(EntryTree<EntryMapping> mappings, Path path) throws IOException {
        Function<String, JarDist> getFieldJarDist;
        Function<String, JarDist> getMethodJarDist;
        Function<String, JarDist> getMethodIdJarDist;
        Function<String, String> getFieldComment;
        Function<String, String> getMethodComment;

        McpMappings mcpMappings = mappings instanceof McpHashEntryTree ?
                ((McpHashEntryTree<EntryMapping>) mappings).getOriginaMcpMappings() : McpMappings.create(path, true);
        JarTypeInfo jarInfo = mappings instanceof McpHashEntryTree ?
                ((McpHashEntryTree<EntryMapping>) mappings).getJarTypeInfo() : JarTypeInfo.fromJar(path.resolve("joined_srg.jar"));
        McpConfig mcpConfig = mappings instanceof McpHashEntryTree ?
                ((McpHashEntryTree<EntryMapping>) mappings).getMcpConfig() : McpConfig.create(path, jarInfo);

        {
            Map<String, JarDist> fieldDist = jarInfo.makeSrgFieldDistMap();
            Map<String, JarDist> methodDist = jarInfo.makeSrgMethodDistMap(mcpConfig.constructorIds);

            Map<String, Integer> methodIdDistFromMappings = mcpMappings == null ? new HashMap<>() : mcpMappings.makeMethodId2SideMap();

            getFieldJarDist = srg -> {
                if (mcpMappings != null && mcpMappings.hasField(srg)) {
                    return JarDist.values()[mcpMappings.getField(srg).getSide()];
                }
                if (McpMappings.isSrgField(srg)) {
                    return fieldDist.getOrDefault(McpMappings.getSrgIdStr(srg), JarDist.BOTH);
                }
                return JarDist.BOTH;
            };
            getMethodJarDist = srg -> {
                if (mcpMappings != null && mcpMappings.hasMethod(srg)) {
                    return JarDist.values()[mcpMappings.getMethod(srg).getSide()];
                }
                if (McpMappings.isSrgMethod(srg)) {
                    return methodDist.getOrDefault(McpMappings.getSrgIdStr(srg), JarDist.BOTH);
                }
                return JarDist.BOTH;
            };
            getMethodIdJarDist = id -> {
                if (methodIdDistFromMappings.containsKey(id)) {
                    return JarDist.values()[methodIdDistFromMappings.get(id)];
                }
                return methodDist.getOrDefault(id, JarDist.BOTH);
            };

            if (mcpMappings != null) {
                getFieldComment = srg -> {
                    McpFieldEntry entry = mcpMappings.getField(srg);
                    return entry == null ? "" : entry.getComment();
                };
                getMethodComment = srg -> {
                    McpMethodEntry entry = mcpMappings.getMethod(srg);
                    return entry == null ? "" : entry.getComment();
                };
            } else {
                getFieldComment = s -> "";
                getMethodComment = s -> "";
            }
        }
        Predicate<Entry<?>> filter = e -> true;
        BiFunction<Entry<?>, String, String> toString = (entry, mapped) -> {
            if (entry instanceof FieldEntry) {
                String srg = entry.getName();
                return String.format("%s,%s,%d,%s", srg, mapped, getFieldJarDist.apply(srg).ordinal(), getFieldComment.apply(srg));
            } else if (entry instanceof MethodEntry) {
                String srg = entry.getName();
                return String.format("%s,%s,%d,%s", srg, mapped, getMethodJarDist.apply(srg).ordinal(), getMethodComment.apply(srg));
            } else if (entry instanceof LocalVariableEntry) {
                LocalVariableEntry var = (LocalVariableEntry) entry;
                MethodEntry varMethod = var.getParent();
                int idx = var.getIndex();
                String methodId = McpConfig.getMethodId(varMethod, mcpConfig.constructorIds);
                return String.format("p_%s_%d_,%s,%d", methodId, idx, mapped, getMethodIdJarDist.apply(methodId).ordinal());
            }
            throw new IllegalArgumentException(entry.toString());
        };

        OrderedMappingLines lines = write(mappings, mappings, filter, toString, mcpMappings, mcpConfig);

        try (PrintWriter out = new PrintWriter(new BufferedOutputStream(Files.newOutputStream(path.resolve("fields.csv"))))) {
            out.println("searge,name,side,desc");
            lines.fieldLines.forEach(out::println);
        }

        try (PrintWriter out = new PrintWriter(new BufferedOutputStream(Files.newOutputStream(path.resolve("methods.csv"))))) {
            out.println("searge,name,side,desc");
            lines.methodLines.forEach(out::println);
        }

        try (PrintWriter out = new PrintWriter(new BufferedOutputStream(Files.newOutputStream(path.resolve("params.csv"))))) {
            out.println("param,name,side");
            lines.paramLines.forEach(out::println);
        }
    }

    private void writeDelta(EntryTree<EntryMapping> mappings, MappingDelta<EntryMapping> delta, Path path) throws IOException {
        EntryTree<EntryMapping> originals =
                mappings instanceof McpHashEntryTree ? ((McpHashEntryTree<EntryMapping>) mappings).getOriginal() : null;

        McpMappings mcpMap = mappings instanceof McpHashEntryTree ?
                ((McpHashEntryTree<EntryMapping>) mappings).getOriginaMcpMappings() : null;
        JarTypeInfo info = mappings instanceof McpHashEntryTree ?
                ((McpHashEntryTree<EntryMapping>) mappings).getJarTypeInfo() : JarTypeInfo.fromJar(path.resolve("joined_srg.jar"));
        McpConfig mcpConfig = mappings instanceof McpHashEntryTree ?
                ((McpHashEntryTree<EntryMapping>) mappings).getMcpConfig() : McpConfig.create(path, info);

        Predicate<Entry<?>> filter = e -> {
            if (originals != null) {
                EntryMapping newMapping = mappings.get(e);
                EntryMapping oldMapping = originals.get(e);
                if (newMapping.equals(oldMapping)) {
                    return false;
                }
            }
            return true;
        };
        BiFunction<Entry<?>, String, String> toString = (entry, mapped) -> {
            if (entry instanceof MethodEntry) {
                return String.format("!sm %s %s", entry.getName(), mapped);
            } else if (entry instanceof FieldEntry) {
                return String.format("!sf %s %s", entry.getName(), mapped);
            } else if (entry instanceof LocalVariableEntry) {
                LocalVariableEntry var = (LocalVariableEntry) entry;
                MethodEntry varMethod = var.getParent();
                int idx = var.getIndex();
                String methodId = McpConfig.getMethodId(varMethod, mcpConfig.constructorIds);
                return String.format("!sp p_%s_%d_ %s", methodId, idx, mapped);
            }
            throw new IllegalArgumentException(entry.toString());
        };

        OrderedMappingLines lines = write(mappings, delta.getChanges(), filter, toString, mcpMap, mcpConfig);

        try (PrintWriter out = new PrintWriter(new BufferedOutputStream(Files.newOutputStream(path.resolve("deltas_mcpbot.txt"))))) {
            lines.fieldLines.forEach(out::println);
            lines.methodLines.forEach(out::println);
            lines.paramLines.forEach(out::println);
        }
    }

    private OrderedMappingLines write(EntryTree<EntryMapping> mappings, EntryTree<?> toWrite, Predicate<Entry<?>> filter,
            BiFunction<Entry<?>, String, String> toString, McpMappings mcpMap, McpConfig mcpConfig) {
        Map<String, String> fieldOutputs = new HashMap<>();
        Map<String, String> methodOutputs = new HashMap<>();
        Map<String, String> paramOutputs = new HashMap<>();

        toWrite.forEach(e -> {
            Entry<?> entry = e.getEntry();
            if (!e.hasValue()) {
                return;
            }
            if (!filter.test(entry)) {
                return;
            }
            if (entry instanceof MethodEntry) {
                handleMethodEntry(mappings, methodOutputs, (MethodEntry) entry, toString, mcpMap);
            } else if (entry instanceof FieldEntry) {
                handleFieldEntry(mappings, fieldOutputs, (FieldEntry) entry, toString, mcpMap);
            } else if (entry instanceof LocalVariableEntry) {
                handleLocalEntry(mappings, paramOutputs, (LocalVariableEntry) entry, toString, mcpMap, mcpConfig);
            }
        });
        return sortMappings(fieldOutputs, methodOutputs, paramOutputs);
    }

    private OrderedMappingLines sortMappings(Map<String, String> fieldOutputs,
            Map<String, String> methodOutputs, Map<String, String> paramOutputs) {
        List<String> fieldOrder = new ArrayList<>(fieldOutputs.keySet());
        List<String> methodOrder = new ArrayList<>(methodOutputs.keySet());
        List<String> paramOrder = new ArrayList<>(paramOutputs.keySet());

        Collator collator = Collator.getInstance(Locale.ROOT);
        fieldOrder.sort(collator::compare);
        methodOrder.sort(collator::compare);
        paramOrder.sort(collator::compare);

        List<String> fieldLines = fieldOrder.stream().map(fieldOutputs::get).collect(Collectors.toList());
        List<String> methodLines = methodOrder.stream().map(methodOutputs::get).collect(Collectors.toList());
        List<String> paramLines = paramOrder.stream().map(paramOutputs::get).collect(Collectors.toList());
        return new OrderedMappingLines(methodLines, fieldLines, paramLines);
    }

    private void handleMethodEntry(EntryTree<EntryMapping> mappings, Map<String, String> methodOutputs, MethodEntry entry,
            BiFunction<Entry<?>, String, String> toString, McpMappings mcpMap) {
        MethodEntry method = entry;
        if (!McpMappings.isSrgMethod(method.getName())) {
            System.out.println("Remapping non-srg method " + method + ", ignoring");
            return;
        }
        String mapped = mappings.get(method).getTargetName();
        if (mapped.equals(method.getName()) && McpMappings.isSrgMethod(mapped) && (mcpMap == null || !mcpMap.hasMethod(mapped))) {
            return;
        }
        String line = toString.apply(method, mapped);
        methodOutputs.put(method.getName(), line);
    }

    private void handleFieldEntry(EntryTree<EntryMapping> mappings, Map<String, String> fieldOutputs, FieldEntry entry,
            BiFunction<Entry<?>, String, String> toString, McpMappings mcpMap) {
        FieldEntry field = entry;
        if (!McpMappings.isSrgField(field.getName())) {
            System.out.println("Remapping non-srg field " + field + ", ignoring");
            return;
        }
        String mapped = mappings.get(field).getTargetName();
        if (mapped.equals(field.getName()) && McpMappings.isSrgField(mapped) && (mcpMap == null || !mcpMap.hasField(mapped))) {
            return;
        }
        String line = toString.apply(field, mapped);
        fieldOutputs.put(field.getName(), line);
    }

    private void handleLocalEntry(EntryTree<EntryMapping> mappings, Map<String, String> paramOutputs,
            LocalVariableEntry entry, BiFunction<Entry<?>, String, String> toString, McpMappings mcpMap, McpConfig mcpConf) {
        LocalVariableEntry var = entry;
        if (!var.isArgument()) {
            System.out.println("Remapping non-argument local variable " + var + ", ignoring");
        }
        MethodEntry varMethod = var.getParent();
        if (varMethod == null) {
            System.out.println("Remapping local variable with no method, ignoring");
            return;
        }
        if (!McpMappings.isSrgMethod(varMethod.getName()) && !varMethod.getName().equals("<init>")) {
            System.out.println("Remapping non-srg method parameter " + var + ", ignoring");
            return;
        }
        int idx = var.getIndex();
        String methodId = McpConfig.getMethodId(varMethod, mcpConf.constructorIds);
        if (methodId == null) {
            System.out.println("Remapping parameter of constructor " + varMethod + " without ID, ignoring");
            return;
        }
        String paramSrg = String.format("p_%s_%d_", methodId, idx);

        String mapped =  mappings.get(var).getTargetName();
        if (mapped.equals(paramSrg) && McpMappings.isSrgParam(mapped) && (mcpMap == null || !mcpMap.hasParam(mapped))) {
            return;
        }
        String line = toString.apply(var, mapped);
        paramOutputs.put(paramSrg, line);
    }

    private static class OrderedMappingLines {

        final List<String> methodLines;
        final List<String> fieldLines;
        final List<String> paramLines;

        public OrderedMappingLines(List<String> methodLines, List<String> fieldLines, List<String> paramLines) {
            this.methodLines = methodLines;
            this.fieldLines = fieldLines;
            this.paramLines = paramLines;
        }
    }

    private enum Mode {
        FULL, DELTA_MCPBOT
    }
}

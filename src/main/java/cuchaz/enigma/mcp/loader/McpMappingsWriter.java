package cuchaz.enigma.mcp.loader;

import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.analysis.index.JarIndex;
import cuchaz.enigma.mcp.loader.mappings.JarDist;
import cuchaz.enigma.mcp.loader.mappings.McpFieldEntry;
import cuchaz.enigma.mcp.loader.mappings.McpMappings;
import cuchaz.enigma.mcp.loader.mappings.McpMethodEntry;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.MappingDelta;
import cuchaz.enigma.translation.mapping.serde.MappingsOption;
import cuchaz.enigma.translation.mapping.serde.MappingsWriter;
import cuchaz.enigma.translation.mapping.serde.PathType;
import cuchaz.enigma.translation.mapping.tree.DeltaTrackingTree;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.translation.mapping.tree.McpHashEntryTree;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.LocalVariableEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.utils.SupplierWithThrowable;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public enum McpMappingsWriter implements MappingsWriter {
    WRITER_FULL(Mode.FULL),
    WRITER_DELTA(Mode.DELTA_MCPBOT);

    private final Mode mode;

    McpMappingsWriter(Mode mode) {
        this.mode = mode;
    }


    @Override public EnumSet<PathType> getSupportedPathTypes() {
        return null;
    }

    @Override public boolean checkPath(Path path, Consumer<String> errorConsumer) {
        if (!MappingsWriter.super.checkPath(path, errorConsumer)) {
            return false;
        }
        boolean isOk = true;
        if (!Files.isRegularFile(path.resolve("joined_srg.jar"))) {
            errorConsumer.accept("File joined_srg.jar doesn't exist or is a directory");
            isOk = false;
        }
        if (!Files.isRegularFile(path.resolve("joined.tsrg"))) {
            errorConsumer.accept("File joined.tsrg doesn't exist or is a directory");
            isOk = false;
        }
        if (!Files.isRegularFile(path.resolve("constructors.txt"))) {
            errorConsumer.accept("File constructors.txt doesn't exist or is a directory");
            isOk = false;
        }
        return isOk;
    }

    @Override public void write(EntryTree<EntryMapping> mappings, MappingDelta<EntryMapping> delta, Path path, ProgressListener progress,
            Map<MappingsOption, String> options, SupplierWithThrowable<JarIndex, IOException> jarIndex) {
        try {
            EntryTree<EntryMapping> directMappings = mappings;
            if (mappings instanceof DeltaTrackingTree) {
                directMappings = ((DeltaTrackingTree<EntryMapping>) mappings).getDelegate();
            }
            switch (mode) {
                case DELTA_MCPBOT:
                    writeDelta(directMappings, delta, path);
                    break;
                case FULL:
                    writeFull(directMappings, path);
                    break;
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
                ((McpHashEntryTree<EntryMapping>) mappings).getMergedMcpMappings() : McpMappings.create(path, true);
        JarTypeInfo jarInfo = mappings instanceof McpHashEntryTree ?
                ((McpHashEntryTree<EntryMapping>) mappings).getJarTypeInfo() : JarTypeInfo.fromJar(path.resolve("joined_srg.jar"));
        McpConfig mcpConfig = mappings instanceof McpHashEntryTree ?
                ((McpHashEntryTree<EntryMapping>) mappings).getMcpConfig() : McpConfig.create(path, jarInfo, s->{});

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
        BiFunction<String, String, String> toString = (srg, mapped) -> {
            if (McpMappings.isSrgMethod(srg)) {
                return String.format("%s,%s,%d,%s", srg, mapped, getMethodJarDist.apply(srg).ordinal(), getMethodComment.apply(srg));
            } else if (McpMappings.isSrgField(srg)) {
                return String.format("%s,%s,%d,%s", srg, mapped, getFieldJarDist.apply(srg).ordinal(), getFieldComment.apply(srg));
            } else if (McpMappings.isSrgParam(srg)) {
                return String.format("p_%s,%s,%d",srg, mapped, getMethodIdJarDist.apply(McpMappings.getSrgIdStr(srg)).ordinal());
            }
            throw new IllegalArgumentException(srg);
        };

        OrderedMappingLines lines = write(mappings, mappings, filter, toString, mcpMappings, null);

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
        EntryTree<EntryMapping> originals = delta.getBaseMappings();

        McpMappings mcpMap = mappings instanceof McpHashEntryTree ?
                ((McpHashEntryTree<EntryMapping>) mappings).getOriginalMcpMappings() : null;
        McpMappings oldDelta = mappings instanceof McpHashEntryTree ?
                ((McpHashEntryTree<EntryMapping>) mappings).getOldDeltas() : null;

        Predicate<Entry<?>> filter = e -> {
            if (oldDelta != null && (oldDelta.hasField(e.getName()) || oldDelta.hasMethod(e.getName()) || oldDelta.hasParam(e.getName()))) {
                return true;
            }
            if (originals != null) {
                EntryMapping newMapping = mappings.get(e);
                EntryMapping oldMapping = originals.get(e);
                if (newMapping != null && newMapping.equals(oldMapping)) {
                    return false;
                }
            }
            return true;
        };
        BiFunction<String, String, String> toString = (srg, mapped) -> {
            if (McpMappings.isSrgMethod(srg)) {
                return String.format("!sm %s %s", srg, mapped);
            } else if (McpMappings.isSrgField(srg)) {
                return String.format("!sf %s %s", srg, mapped);
            } else if (McpMappings.isSrgParam(srg)) {
                return String.format("!sp %s %s", srg, mapped);
            }
            throw new IllegalArgumentException(srg);
        };

        OrderedMappingLines lines = write(mappings, delta.getChanges(), filter, toString, mcpMap, oldDelta);

        try (PrintWriter out = new PrintWriter(new BufferedOutputStream(Files.newOutputStream(path.resolve("deltas_mcpbot.txt"))))) {
            lines.fieldLines.forEach(out::println);
            lines.methodLines.forEach(out::println);
            lines.paramLines.forEach(out::println);
        }
    }

    private OrderedMappingLines write(EntryTree<EntryMapping> mappings, EntryTree<?> toWrite, Predicate<Entry<?>> filter,
            BiFunction<String, String, String> toString, McpMappings mcpMap, @Nullable McpMappings oldDeltas) {
        Map<String, String> srg2mappedFields = new HashMap<>();
        Map<String, String> srg2mappedMethods = new HashMap<>();
        Map<String, String> srg2mappedParams = new HashMap<>();

        if (oldDeltas != null) {
            oldDeltas.fieldStream().forEach(e -> srg2mappedFields.put(e.getKey(), e.getValue()));
            oldDeltas.methodStream().forEach(e -> srg2mappedMethods.put(e.getKey(), e.getValue()));
            oldDeltas.paramStream().forEach(e -> srg2mappedParams.put(e.getKey(), e.getValue()));
        }

        toWrite.forEach(e -> {
            Entry<?> entry = e.getEntry();
            if (!e.hasValue() || mappings.get(entry) == null) {
                srg2mappedFields.remove(entry.getName());
                srg2mappedMethods.remove(entry.getName());
                srg2mappedParams.remove(entry.getName());
                return;
            }
            if (!filter.test(entry)) {
                return;
            }
            if (entry instanceof MethodEntry) {
                handleMethodEntry(mappings, (MethodEntry) entry, srg2mappedMethods, mcpMap);
            } else if (entry instanceof FieldEntry) {
                handleFieldEntry(mappings, (FieldEntry) entry, srg2mappedFields, mcpMap);
            } else if (entry instanceof LocalVariableEntry) {
                handleLocalEntry(mappings, (LocalVariableEntry) entry, srg2mappedParams, mcpMap);
            }
        });

        Map<String, String> fieldOutputs = srg2mappedFields.entrySet().stream()
                .map(e->new SimpleEntry<>(e.getKey(), toString.apply(e.getKey(), e.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<String, String> methodOutputs = srg2mappedMethods.entrySet().stream()
                .map(e->new SimpleEntry<>(e.getKey(), toString.apply(e.getKey(), e.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<String, String> paramOutputs = srg2mappedParams.entrySet().stream()
                .map(e->new SimpleEntry<>(e.getKey(), toString.apply(e.getKey(), e.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

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

    private void handleMethodEntry(EntryTree<EntryMapping> mappings, MethodEntry entry, Map<String, String> srg2mapped, McpMappings mcpMap) {
        MethodEntry method = entry;
        if (!McpMappings.isSrgMethod(method.getName())) {
            System.out.println("Remapping non-srg method " + method + ", ignoring");
            return;
        }
        String mapped = mappings.get(method).getTargetName();
        if (mapped.equals(method.getName()) && McpMappings.isSrgMethod(mapped) && (mcpMap == null || !mcpMap.hasMethod(mapped))) {
            return;
        }
        srg2mapped.put(method.getName(), mapped);
    }

    private void handleFieldEntry(EntryTree<EntryMapping> mappings, FieldEntry entry,
            Map<String, String> srg2mapped, McpMappings mcpMap) {
        FieldEntry field = entry;
        if (!McpMappings.isSrgField(field.getName())) {
            System.out.println("Remapping non-srg field " + field + ", ignoring");
            return;
        }
        String mapped = mappings.get(field).getTargetName();
        if (mapped.equals(field.getName()) && McpMappings.isSrgField(mapped) && (mcpMap == null || !mcpMap.hasField(mapped))) {
            return;
        }
        srg2mapped.put(field.getName(), mapped);
    }

    private void handleLocalEntry(EntryTree<EntryMapping> mappings, LocalVariableEntry entry, Map<String, String> srg2mcp, McpMappings mcpMap) {
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
        if (!McpMappings.isSrgParam(var.getName())) {
            System.out.println("Remapping non-srg parameter " + var + ", ignoring");
            return;
        }
        String paramSrg = var.getName();

        String mapped =  mappings.get(var).getTargetName();
        if (mapped.equals(paramSrg) && McpMappings.isSrgParam(mapped) && (mcpMap == null || !mcpMap.hasParam(mapped))) {
            return;
        }
        srg2mcp.put(paramSrg, mapped);
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

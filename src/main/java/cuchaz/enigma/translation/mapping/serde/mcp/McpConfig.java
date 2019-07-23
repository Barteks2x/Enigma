package cuchaz.enigma.translation.mapping.serde.mcp;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.translation.mapping.serde.mcp.mappings.McpMappings;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class McpConfig {

    final Map<String, String> obf2srgClasses;
    final Map<String, ClassEntry> srg2srgClassEntry;
    final Map<String, Map<String, MethodEntry>> srg2srgMethodEntry;
    final Map<String, Set<MethodEntry>> srgId2MethodEntry;
    final Map<String, Map<String, FieldEntry>> srg2srgFieldEntry;
    final Map<MethodEntry, Integer> constructorIds;

    private McpConfig(Map<String, String> obf2srgClasses,
            Map<String, ClassEntry> srg2srgClassEntry,
            Map<String, Map<String, MethodEntry>> srg2srgMethodEntry,
            Map<String, Set<MethodEntry>> srgId2MethodEntry,
            Map<String, Map<String, FieldEntry>> srg2srgFieldEntry,
            Map<MethodEntry, Integer> constructors) {
        this.obf2srgClasses = obf2srgClasses;
        this.srg2srgClassEntry = srg2srgClassEntry;
        this.srg2srgMethodEntry = srg2srgMethodEntry;
        this.srgId2MethodEntry = srgId2MethodEntry;
        this.srg2srgFieldEntry = srg2srgFieldEntry;
        this.constructorIds = constructors;
    }

    public static McpConfig create(Path path, JarTypeInfo jarInfo, Consumer<String> progress) throws IOException {
        progress.accept("Loading McpConfig: reading joined.tsrg");
        List<String> tsrg = Files.readAllLines(path.resolve("joined.tsrg"));
        progress.accept("Loading McpConfig: reading constructors.txt");
        List<String> constructors = Files.readAllLines(path.resolve("constructors.txt"));
        return create(tsrg, constructors, jarInfo, progress);
    }

    static McpConfig create(List<String> tsrg, List<String> constructors, JarTypeInfo jarInfo, Consumer<String> progress) {
        Map<String, Map<String, MethodEntry>> srg2srgMethodEntry = new HashMap<>();
        Map<String, Set<MethodEntry>> srgId2MethodEntry = new HashMap<>();
        Map<String, Map<String, FieldEntry>> srg2srgFieldEntry = new HashMap<>();
        Map<String, String> obf2srgClasses = new HashMap<>();
        Map<String, ClassEntry> srg2srgClassEntry = new HashMap<>();

        progress.accept("Loading McpConfig: Preparing McpConfig class entries");
        loadClassEntries(tsrg,
                obf2srgClasses,
                srg2srgClassEntry);

        progress.accept("Loading McpConfig: Preparing McpConfig method and field entries");
        loadFieldMethodEntries(tsrg,
                jarInfo,
                srg2srgMethodEntry,
                srgId2MethodEntry,
                srg2srgFieldEntry,
                obf2srgClasses);

        progress.accept("Loading McpConfig: constructors");
        Map<MethodEntry, Integer> constructorIds = loadConstructors(constructors);

        progress.accept("Loading McpConfig: computing srg to method constructor map");
        constructorIds.forEach((entry, id) ->
                srgId2MethodEntry.computeIfAbsent("i" + id, x -> new HashSet<>()).add(entry));

        return new McpConfig(obf2srgClasses,
                srg2srgClassEntry,
                srg2srgMethodEntry,
                srgId2MethodEntry,
                srg2srgFieldEntry,
                constructorIds);
    }

    private static Map<MethodEntry, Integer> loadConstructors(List<String> lines) {
        Map<MethodEntry, Integer> constructors = new HashMap<>();
        for (String line : lines) {
            String[] parts = line.split(" ");
            int id = Integer.parseInt(parts[0]);
            String className = parts[1];
            String methodDesc = parts[2];

            MethodEntry entry = MethodEntry.parse(className, "<init>", methodDesc);
            constructors.put(entry, id);
        }
        return constructors;
    }

    private static void loadFieldMethodEntries(List<String> tsrg,
            JarTypeInfo jarInfo,
            Map<String, Map<String, MethodEntry>> srg2srgMethodEntry,
            Map<String, Set<MethodEntry>> srgId2MethodEntry,
            Map<String, Map<String, FieldEntry>> srg2srgFieldEntry,
            Map<String, String> obf2srgClasses) {
        String lastDeobfClass = null;

        for (String line : tsrg) {
            if (!line.startsWith("\t")) {
                String[] parts = line.split(" ");
                lastDeobfClass = parts[1];
                continue;
            }
            line = line.trim();
            String[] parts = line.split(" ");
            if (parts.length == 2) {
                // field
                String srgName = parts[1];
                String srgFieldClass = jarInfo.getFieldTypeBySrg(lastDeobfClass, srgName);
                Type srgFieldType = Type.getType(srgFieldClass);

                FieldEntry srgEntry = FieldEntry.parse(lastDeobfClass, srgName, srgFieldType.getDescriptor());

                srg2srgFieldEntry.computeIfAbsent(lastDeobfClass, x -> HashBiMap.create()).put(srgName, srgEntry);
            } else {
                String obfDesc = parts[1];
                String srgName = parts[2];
                String srgDesc = mapMethodDesc(Type.getMethodType(obfDesc), obf2srgClasses).getDescriptor();

                MethodEntry srgEntry = MethodEntry.parse(lastDeobfClass, srgName, srgDesc);

                srg2srgMethodEntry.computeIfAbsent(lastDeobfClass, x -> HashBiMap.create()).put(srgName, srgEntry);
                if (McpMappings.isSrgMethod(srgName)) {
                    String srgId = srgName.split("_")[1];
                    srgId2MethodEntry.computeIfAbsent(srgId, x -> new HashSet<>()).add(srgEntry);
                }
            }
        }
    }

    private static void loadClassEntries(List<String> tsrg,
            Map<String, String> obf2srgClasses,
            Map<String, ClassEntry> srg2srgClassEntry) {
        for (String line : tsrg) {
            if (!line.startsWith("\t")) {
                String[] parts = line.split(" ");
                obf2srgClasses.put(parts[0], parts[1]);
                String deobf = parts[1];

                ClassEntry outerClassDeobf = ClassEntry.getOuterClass(deobf);
                String innerClassDeobf = ClassEntry.getInnerName(deobf);
                ClassEntry deobfEntry = new ClassEntry(outerClassDeobf, innerClassDeobf);

                srg2srgClassEntry.put(deobf, deobfEntry);
            }
        }
    }

    private static Type mapMethodDesc(Type toMap, Map<String, String> classMap) {
        Type toMapRet = toMap.getReturnType();
        Type[] toMapArgs = toMap.getArgumentTypes();

        Type mappedRet = mapType(toMapRet, classMap);
        Type[] mappedArgs = new Type[toMapArgs.length];
        for (int i = 0; i < mappedArgs.length; i++) {
            mappedArgs[i] = mapType(toMapArgs[i], classMap);
        }

        return Type.getMethodType(mappedRet, mappedArgs);
    }

    private static Type mapType(Type toMap, Map<String, String> classMap) {
        if (toMap.getSort() < Type.ARRAY) {
            // primitive
            return toMap;
        }
        String unmapped = toMap.getInternalName();
        if (toMap.getSort() == Type.ARRAY) {
            Type mappedElement = mapType(toMap.getElementType(), classMap);
            return Type.getType("[" + mappedElement.getDescriptor());
        }

        return Type.getObjectType(classMap.getOrDefault(unmapped, unmapped));
    }

    public String getMethodId(MethodEntry method) {
        if (method.getName().equals("<init>")) {
            Integer id = constructorIds.get(method);
            if (id == null) {
                return null;
            }
            return "i" + id;
        } else {
            return method.getName().split("_")[1];
        }
    }
}

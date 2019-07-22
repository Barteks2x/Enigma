package cuchaz.enigma.translation.mapping.serde.mcp;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import cuchaz.enigma.translation.mapping.serde.mcp.mappings.McpMappings;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class McpConfig {

    final BiMap<String, String> obf2srgClasses;

    //final BiMap<String, ClassEntry> obf2obfClassEntry;
    final BiMap<String, ClassEntry> srg2srgClassEntry;

    //final Map<String, BiMap<String, MethodEntry>> srg2obfMethodEntry;
    final Map<String, BiMap<String, MethodEntry>> srg2srgMethodEntry;
    final BiMap<String, Set<MethodEntry>> srgId2MethodEntry;

    //final Map<String, BiMap<String, FieldEntry>> srg2obfFieldEntry;
    final Map<String, BiMap<String, FieldEntry>> srg2srgFieldEntry;

    final BiMap<MethodEntry, Integer> constructorIds;

    private McpConfig(BiMap<String, String> obf2srgClasses,
            //BiMap<String, ClassEntry> obf2obfClassEntry,
            BiMap<String, ClassEntry> srg2srgClassEntry,
            //Map<String, BiMap<String, MethodEntry>> srg2obfMethodEntry,
            Map<String, BiMap<String, MethodEntry>> srg2srgMethodEntry,
            BiMap<String, Set<MethodEntry>> srgId2MethodEntry,
            //Map<String, BiMap<String, FieldEntry>> srg2obfFieldEntry,
            Map<String, BiMap<String, FieldEntry>> srg2srgFieldEntry,
            BiMap<MethodEntry, Integer> constructors) {
        this.obf2srgClasses = obf2srgClasses;
        //this.obf2obfClassEntry = obf2obfClassEntry;
        this.srg2srgClassEntry = srg2srgClassEntry;
        //this.srg2obfMethodEntry = srg2obfMethodEntry;
        this.srg2srgMethodEntry = srg2srgMethodEntry;
        this.srgId2MethodEntry = srgId2MethodEntry;
        //this.srg2obfFieldEntry = srg2obfFieldEntry;
        this.srg2srgFieldEntry = srg2srgFieldEntry;
        this.constructorIds = constructors;
    }

    static McpConfig create(List<String> tsrg, List<String> constructors, JarTypeInfo jarInfo) {
        //Map<String, BiMap<String, MethodEntry>> srg2obfMethodEntry = HashBiMap.create();
        Map<String, BiMap<String, MethodEntry>> srg2srgMethodEntry = HashBiMap.create();
        BiMap<String, Set<MethodEntry>> srgId2MethodEntry = HashBiMap.create();
        //Map<String, BiMap<String, FieldEntry>> srg2obfFieldEntry = HashBiMap.create();
        Map<String, BiMap<String, FieldEntry>> srg2srgFieldEntry = HashBiMap.create();
        BiMap<String, String> obf2srgClasses = HashBiMap.create();
        //BiMap<String, ClassEntry> obf2obfClassEntry = HashBiMap.create();
        BiMap<String, ClassEntry> srg2srgClassEntry = HashBiMap.create();

        loadClassEntries(tsrg,
                obf2srgClasses,
                //obf2obfClassEntry,
                srg2srgClassEntry);
        loadFieldMethodEntries(tsrg,
                jarInfo,
                //srg2obfMethodEntry,
                srg2srgMethodEntry,
                srgId2MethodEntry,
                //srg2obfFieldEntry,
                srg2srgFieldEntry,
                obf2srgClasses);

        BiMap<MethodEntry, Integer> constructorIds = loadConstructors(constructors);

        constructorIds.forEach((entry, id) ->
                srgId2MethodEntry.computeIfAbsent("i" + id, x -> new HashSet<>()).add(entry));

        return new McpConfig(obf2srgClasses,
                //obf2obfClassEntry,
                srg2srgClassEntry,
                //srg2obfMethodEntry,
                srg2srgMethodEntry,
                srgId2MethodEntry,
                //srg2obfFieldEntry,
                srg2srgFieldEntry,
                constructorIds);
    }

    private static BiMap<MethodEntry, Integer> loadConstructors(List<String> lines) {
        BiMap<MethodEntry, Integer> constructors = HashBiMap.create();
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
            //Map<String, BiMap<String, MethodEntry>> srg2obfMethodEntry,
            Map<String, BiMap<String, MethodEntry>> srg2srgMethodEntry,
            BiMap<String, Set<MethodEntry>> srgId2MethodEntry,
            //Map<String, BiMap<String, FieldEntry>> srg2obfFieldEntry,
            Map<String, BiMap<String, FieldEntry>> srg2srgFieldEntry,
            BiMap<String, String> obf2srgClasses) {
        //String lastObfClass = null;
        String lastDeobfClass = null;

        for (String line : tsrg) {
            if (!line.startsWith("\t")) {
                String[] parts = line.split(" ");
                //lastObfClass = parts[0];
                lastDeobfClass = parts[1];
                continue;
            }
            line = line.trim();
            String[] parts = line.split(" ");
            if (parts.length == 2) {
                // field
                //String obfName = parts[0];
                String srgName = parts[1];
                String srgFieldClass = jarInfo.getFieldTypeBySrg(lastDeobfClass, srgName);
                Type srgFieldType = Type.getType(srgFieldClass);
                //Type obfFieldType = mapType(srgFieldType, obf2srgClasses.inverse());

                //FieldEntry obfEntry = FieldEntry.parse(lastObfClass, obfName, obfFieldType.getDescriptor());
                FieldEntry srgEntry = FieldEntry.parse(lastDeobfClass, srgName, srgFieldType.getDescriptor());

                srg2srgFieldEntry.computeIfAbsent(lastDeobfClass, x -> HashBiMap.create()).put(srgName, srgEntry);
                //srg2obfFieldEntry.computeIfAbsent(lastDeobfClass, x -> HashBiMap.create()).put(srgName, obfEntry);
            } else {
                //String obfName = parts[0];
                String obfDesc = parts[1];
                String srgName = parts[2];
                String srgDesc = mapMethodDesc(Type.getMethodType(obfDesc), obf2srgClasses).getDescriptor();

                //MethodEntry obfEntry = MethodEntry.parse(lastObfClass, obfName, obfDesc);
                MethodEntry srgEntry = MethodEntry.parse(lastDeobfClass, srgName, srgDesc);

                //srg2obfMethodEntry.computeIfAbsent(lastDeobfClass, x -> HashBiMap.create()).put(srgName, obfEntry);
                srg2srgMethodEntry.computeIfAbsent(lastDeobfClass, x -> HashBiMap.create()).put(srgName, srgEntry);
                if (McpMappings.isSrgMethod(srgName)) {
                    String srgId = srgName.split("_")[1];
                    srgId2MethodEntry.computeIfAbsent(srgId, x -> new HashSet<>()).add(srgEntry);
                }
            }
        }
    }

    private static void loadClassEntries(List<String> tsrg,
            BiMap<String, String> obf2srgClasses,
            //BiMap<String, ClassEntry> obf2obfClassEntry,
            BiMap<String, ClassEntry> srg2srgClassEntry) {
        for (String line : tsrg) {
            if (!line.startsWith("\t")) {
                String[] parts = line.split(" ");
                obf2srgClasses.put(parts[0], parts[1]);
                //String obf = parts[0];
                String deobf = parts[1];

                //ClassEntry outerClassObf = ClassEntry.getOuterClass(obf);
                //String innerClassObf = ClassEntry.getInnerName(obf);
                //ClassEntry obfEntry = new ClassEntry(outerClassObf, innerClassObf);

                ClassEntry outerClassDeobf = ClassEntry.getOuterClass(deobf);
                String innerClassDeobf = ClassEntry.getInnerName(deobf);
                ClassEntry deobfEntry = new ClassEntry(outerClassDeobf, innerClassDeobf);

                //obf2obfClassEntry.put(obf, obfEntry);
                srg2srgClassEntry.put(deobf, deobfEntry);
            }
        }
    }

    private static Type mapMethodDesc(Type toMap, BiMap<String, String> classMap) {
        Type toMapRet = toMap.getReturnType();
        Type[] toMapArgs = toMap.getArgumentTypes();

        Type mappedRet = mapType(toMapRet, classMap);
        Type[] mappedArgs = new Type[toMapArgs.length];
        for (int i = 0; i < mappedArgs.length; i++) {
            mappedArgs[i] = mapType(toMapArgs[i], classMap);
        }

        return Type.getMethodType(mappedRet, mappedArgs);
    }

    private static Type mapType(Type toMap, BiMap<String, String> classMap) {
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

    public static McpConfig create(Path path, JarTypeInfo jarInfo) throws IOException {
        return create(Files.readAllLines(path.resolve("joined.tsrg")),
                Files.readAllLines(path.resolve("constructors.txt")), jarInfo);
    }

    public static String getMethodId(MethodEntry method, BiMap<MethodEntry, Integer> constructorIds) {
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

package cuchaz.enigma.translation.mapping.serde.mcp;

import cuchaz.enigma.translation.mapping.serde.mcp.mappings.JarDist;
import cuchaz.enigma.translation.mapping.serde.mcp.mappings.McpMappings;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JarTypeInfo {

    private final Map<String, Map<String, String>> srgFieldTypeDescriptors = new HashMap<>();
    private final Map<String, Map<String, JarDist>> srgFieldSide = new HashMap<>();
    private final Map<String, Map<MethodEntry, JarDist>> srgMethodSide = new HashMap<>();
    private final Map<MethodEntry, JarDist> constructorSide = new HashMap<>();
    private final Map<String, JarDist> classSide = new HashMap<>();
    private final Set<String> staticSrgMethods = new HashSet<>();

    public boolean isStatic(String srgId) {
        return staticSrgMethods.contains(srgId);
    }

    public String getFieldTypeBySrg(String cl, String srgName) {
        Map<String, String> map = srgFieldTypeDescriptors.get(cl);
        return map.get(srgName);
    }

    public JarDist getClassDist(String clName) {
        return classSide.getOrDefault(clName, JarDist.BOTH);
    }

    public JarDist getFieldDist(String clName, String fieldSrg) {
        return srgFieldSide.getOrDefault(clName, Collections.emptyMap()).getOrDefault(fieldSrg, JarDist.BOTH);
    }

    public JarDist getMethodDist(String clName, String methodSrg) {
        return srgMethodSide.getOrDefault(clName, Collections.emptyMap()).getOrDefault(methodSrg, JarDist.BOTH);
    }

    public static JarTypeInfo fromJar(Path path) throws IOException {
        JarTypeInfo info = new JarTypeInfo();

        try (FileSystem fs = FileSystems.newFileSystem(path, null)) {
            for (Path root : fs.getRootDirectories()) {
                try (Stream<Path> stream = Files.walk(root)) {
                    List<Path> paths = stream.collect(Collectors.toList());
                    for (Path file : paths) {
                        Path name = file.getFileName();
                        if (name == null) {
                            continue;
                        }
                        if (!name.toString().endsWith(".class")) {
                            continue;
                        }
                        analyzeClass(info, Files.readAllBytes(file));
                    }
                }
            }
        }
        return info;
    }

    private static AnnotationVisitor onlyInAnnotationVisitor(Consumer<JarDist> setJarDist) {
        return new AnnotationVisitor(Opcodes.ASM7) {
            private boolean isInterface = false;
            private JarDist dist = JarDist.BOTH;

            @Override public void visitEnum(String name, String descriptor, String value) {
                if (name.equals("value") && descriptor.contains("Dist")) {
                    switch (value) {
                        case "CLIENT":
                            dist = JarDist.CLIENT;
                            break;
                        case "DEDICATED_SERVER":
                            dist = JarDist.SERVER;
                            break;
                    }
                }
            }

            @Override public void visit(String name, Object value) {
                if (!(value instanceof Type)) {
                    return;
                }
                if (name.equals("_interface") && !value.equals(Type.getType(Object.class))) {
                    isInterface = true;
                }
            }

            @Override public void visitEnd() {
                setJarDist.accept(isInterface ? JarDist.BOTH : dist);
            }
        };
    }

    private static void analyzeClass(JarTypeInfo info, byte[] bytecode) {
        ClassReader reader = new ClassReader(bytecode);

        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM7) {
            private String className;

            @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (descriptor.contains("OnlyIn")) {
                    return onlyInAnnotationVisitor(dist -> info.classSide.put(className, dist));
                }
                return null;
            }

            @Override public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                info.srgFieldTypeDescriptors.computeIfAbsent(name, x -> new HashMap<>());
                info.srgFieldSide.computeIfAbsent(name, x -> new HashMap<>());
                info.srgMethodSide.computeIfAbsent(name, x -> new HashMap<>());
                info.classSide.putIfAbsent(name, JarDist.BOTH);
                this.className = name;
            }

            @Override public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                info.srgFieldTypeDescriptors.get(className).put(name, descriptor);
                info.srgFieldSide.get(className).putIfAbsent(name, JarDist.BOTH);
                return new FieldVisitor(Opcodes.ASM7) {
                    @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        if (descriptor.contains("OnlyIn")) {
                            return onlyInAnnotationVisitor(dist -> info.srgFieldSide.get(className).merge(name, dist, JarDist::commonDist));
                        }
                        return null;
                    }
                };
            }

            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodEntry me = MethodEntry.parse(className, name, descriptor);
                info.srgMethodSide.get(className).putIfAbsent(me, JarDist.BOTH);
                if ((access & Opcodes.ACC_STATIC) != 0 && McpMappings.isSrgMethod(name)) {
                    info.staticSrgMethods.add(McpMappings.getSrgIdStr(name));
                }
                return new MethodVisitor(Opcodes.ASM7) {
                    @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        if (descriptor.contains("OnlyIn")) {
                            return onlyInAnnotationVisitor(dist -> info.srgMethodSide.get(className).merge(me, dist, JarDist::commonDist));
                        }
                        return null;
                    }
                };
            }
        };
        reader.accept(visitor, ClassReader.SKIP_CODE);
    }

    public Map<String, JarDist> makeSrgFieldDistMap() {
        return classSide.keySet().stream()
                .flatMap(clName -> srgFieldSide.get(clName)
                        .entrySet().stream()
                        .filter(f -> McpMappings.isSrgField(f.getKey()))
                        .map(e -> new SimpleEntry<>(e.getKey(), JarDist.commonDist(getClassDist(clName), e.getValue())))
                ).collect(Collectors.toMap(e -> McpMappings.getSrgIdStr(e.getKey()), Map.Entry::getValue, JarDist::merge));
    }

    public Map<String, JarDist> makeSrgMethodDistMap(Map<MethodEntry, Integer> constructorIds) {
        Map<String, JarDist> methods = classSide.keySet().stream()
                .flatMap(clName -> srgMethodSide.get(clName)
                        .entrySet().stream()
                        .filter(f -> McpMappings.isSrgMethod(f.getKey().getName()))
                        .map(e -> new SimpleEntry<>(e.getKey(), JarDist.commonDist(getClassDist(clName), e.getValue())))
                ).collect(Collectors.toMap(e -> McpMappings.getSrgIdStr(e.getKey().getName()), Map.Entry::getValue, JarDist::merge));
        Map<String, JarDist> constructors = classSide.keySet().stream()
                .flatMap(clName -> srgMethodSide.get(clName)
                        .entrySet().stream()
                        .filter(f -> f.getKey().getName().equals("<init>"))
                        .map(e -> new SimpleEntry<>(e.getKey(), JarDist.commonDist(getClassDist(clName), e.getValue())))
                )
                .filter(e -> constructorIds.containsKey(e.getKey()))
                .collect(Collectors.toMap(e -> "i" + constructorIds.get(e.getKey()), SimpleEntry::getValue, JarDist::merge));
        methods.putAll(constructors);
        return methods;
    }
}

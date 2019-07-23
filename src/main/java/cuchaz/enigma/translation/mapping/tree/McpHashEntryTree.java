package cuchaz.enigma.translation.mapping.tree;

import cuchaz.enigma.translation.Translator;
import cuchaz.enigma.translation.mapping.EntryMap;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.EntryResolver;
import cuchaz.enigma.translation.mapping.serde.mcp.JarTypeInfo;
import cuchaz.enigma.translation.mapping.serde.mcp.McpConfig;
import cuchaz.enigma.translation.mapping.serde.mcp.mappings.McpMappings;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.LocalVariableEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Stream;

import javax.annotation.Nullable;

public class McpHashEntryTree<T> implements EntryTree<T> {

    private final McpMappings originalMcpMappings;
    private final JarTypeInfo jarTypeInfo;
    private McpConfig mcpConfig;
    private final HashEntryTree<T> original;
    private final HashEntryTree<T> delegate;

    public McpHashEntryTree(HashEntryTree<T> original, McpMappings mappings, JarTypeInfo jarInfo, McpConfig mcpConfig) {
        this.originalMcpMappings = mappings;
        this.jarTypeInfo = jarInfo;
        this.mcpConfig = mcpConfig;
        this.original = original;
        this.delegate = new HashEntryTree<>(original);
    }

    @Override public Collection<Entry<?>> getChildren(Entry<?> entry) {
        return delegate.getChildren(entry);
    }

    @Override public Collection<Entry<?>> getSiblings(Entry<?> entry) {
        return delegate.getSiblings(entry);
    }

    @Nullable @Override public EntryTreeNode<T> findNode(Entry<?> entry) {
        return delegate.findNode(entry);
    }

    @Override public Stream<EntryTreeNode<T>> getRootNodes() {
        return delegate.getRootNodes();
    }

    @Override public EntryTree<T> translate(Translator translator, EntryResolver resolver, EntryMap<EntryMapping> mappings) {
        return delegate.translate(translator, resolver, mappings);
    }

    @Override public EntryStatus getEntryStatus(Entry<?> obf, Entry<?> deobf) {
        if (obf instanceof ClassEntry && deobf instanceof ClassEntry) {
            return EntryStatus.READONLY;
        }
        if ((obf instanceof MethodEntry && deobf instanceof MethodEntry) ||
                (obf instanceof FieldEntry && deobf instanceof FieldEntry)) {
            if (!McpMappings.isSrg(obf.getName())) {
                return EntryStatus.READONLY;
            }
            if (!obf.getName().equals(deobf.getName())) {
                return EntryStatus.MAPPED;
            }
            return EntryStatus.UNMAPPED;
        }
        if (obf instanceof LocalVariableEntry && deobf instanceof LocalVariableEntry) {
            if (!((LocalVariableEntry) obf).isArgument()) {
                return EntryStatus.READONLY;
            }
            return McpMappings.isSrgParam(deobf.getName()) ? EntryStatus.UNMAPPED : EntryStatus.MAPPED;
        }
        return EntryStatus.READONLY;
    }

    @Override public void insert(Entry<?> entry, T value) {
        delegate.insert(entry, value);
    }

    @Nullable @Override public T remove(Entry<?> entry) {
        return delegate.remove(entry);
    }

    @Nullable @Override public T get(Entry<?> entry) {
        return delegate.get(entry);
    }

    @Override public Stream<Entry<?>> getAllEntries() {
        return delegate.getAllEntries();
    }

    @Override public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override public Iterator<EntryTreeNode<T>> iterator() {
        return delegate.iterator();
    }

    public EntryTree<T> getOriginal() {
        return original;
    }

    public McpMappings getOriginaMcpMappings() {
        return originalMcpMappings;
    }

    public JarTypeInfo getJarTypeInfo() {
        return jarTypeInfo;
    }

    public McpConfig getMcpConfig() {
        return mcpConfig;
    }
}

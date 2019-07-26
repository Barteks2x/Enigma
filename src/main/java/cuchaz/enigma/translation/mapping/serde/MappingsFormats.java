package cuchaz.enigma.translation.mapping.serde;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.UnmodifiableIterator;

import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class MappingsFormats implements Iterable<Map.Entry<String, MappingsFormat>> {

    private final ImmutableMap<String, MappingsFormat> formats;

    public MappingsFormats(ImmutableMap<String, MappingsFormat> formats) {
        this.formats = formats;
    }

    public ImmutableMap<String, MappingsFormat> getAll() {
        return formats;
    }

    public MappingsFormat get(String id) {
        return formats.get(id);
    }

    @Override public UnmodifiableIterator<Map.Entry<String, MappingsFormat>> iterator() {
        return formats.entrySet().iterator();
    }

    public ImmutableSet<String> keySet() {
        return formats.keySet();
    }

    public ImmutableCollection<MappingsFormat> values() {
        return formats.values();
    }

    @Override public void forEach(Consumer<? super Map.Entry<String, MappingsFormat>> action) {
        formats.forEach((a, b) -> action.accept(new SimpleEntry<>(a, b)));
    }

    public void forEach(BiConsumer<String, MappingsFormat> action) {
        formats.forEach(action);
    }

    @Override public Spliterator<Map.Entry<String, MappingsFormat>> spliterator() {
        return formats.entrySet().spliterator();
    }

    public Stream<Map.Entry<String, MappingsFormat>> stream() {
        return formats.entrySet().stream();
    }
}

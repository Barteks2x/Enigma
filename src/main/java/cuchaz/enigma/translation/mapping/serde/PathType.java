package cuchaz.enigma.translation.mapping.serde;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

public enum PathType implements Predicate<Path> {
    FILE, DIRECTORY;

    @Override public boolean test(Path path) {
        boolean isDir = Files.isDirectory(path);
        //noinspection SimplifiableConditionalExpression because it's more readable
        return this == FILE ? !isDir : isDir;
    }
}

package cuchaz.enigma.utils;

@FunctionalInterface
public interface SupplierWithThrowable<T, E extends Throwable> {
    T get() throws E;
}

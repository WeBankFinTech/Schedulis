package azkaban.function;

@FunctionalInterface
public interface CheckedSupplier<T, R extends Exception> {

    T get() throws R;
}

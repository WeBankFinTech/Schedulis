package azkaban.function;

@FunctionalInterface
public interface TriadConsumer<T, R, U> {

    void accept(T t, R r, U u);
}

package azkaban.function;

@FunctionalInterface
public interface TriadFunction<T, R, U, A> {

    A apply(T t, R r, U u);
}

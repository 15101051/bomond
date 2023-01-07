package utils;

import java.util.Objects;

public class Pair<A, B> {
    private final A fir;
    private final B sec;

    public Pair(A a, B b) {
        this.fir = a;
        this.sec = b;
    }

    public A getFir() {
        return fir;
    }

    public B getSec() {
        return sec;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Pair<?, ?> that = (Pair<?, ?>) o;
        return Objects.equals(fir, that.fir) && Objects.equals(sec, that.sec);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fir, sec);
    }

}

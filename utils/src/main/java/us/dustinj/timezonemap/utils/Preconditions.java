package us.dustinj.timezonemap.utils;

public final class Preconditions {

    private Preconditions() {}

    public static void checkState(boolean check, String message) {
        if (!check) {
            throw new IllegalStateException(message);
        }
    }

    public static void checkArgument(boolean check, String message) {
        if (!check) {
            throw new IllegalArgumentException(message);
        }
    }
}

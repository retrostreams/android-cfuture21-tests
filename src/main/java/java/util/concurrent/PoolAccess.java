package java.util.concurrent;

public final class PoolAccess {

    public static ExecutorService commonPool() {
        return FJPool.commonPool();
    }

    public static int getCommonPoolParallelism() {
        return FJPool.getCommonPoolParallelism();
    }

    private PoolAccess() {
    }
}

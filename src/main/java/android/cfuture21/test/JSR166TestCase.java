/*
 * Written by Doug Lea and Martin Buchholz with assistance from
 * members of JCP JSR-166 Expert Group and released to the public
 * domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */
package android.cfuture21.test;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.BeforeMethod;

import java.util.concurrent.ThreadLocalRandom;
import junit.framework.TestCase;

public class JSR166TestCase extends TestCase {

    /**
     * Like Runnable, but with the freedom to throw anything.
     * junit folks had the same idea:
     * http://junit.org/junit5/docs/snapshot/api/org/junit/gen5/api/Executable.html
     */
    protected interface Action { public void run() throws Throwable; }

    public abstract class CheckedRunnable implements Runnable {
        protected abstract void realRun() throws Throwable;

        public final void run() {
            try {
                realRun();
            } catch (Throwable fail) {
                threadUnexpectedException(fail);
            }
        }
    }

    /**
     * Allows use of try-with-resources with per-test thread pools.
     */
    protected class PoolCleaner implements AutoCloseable {
        private final ExecutorService pool;
        public PoolCleaner(ExecutorService pool) { this.pool = pool; }
        public void close() { joinPool(pool); }
    }

    protected static final boolean expensiveTests =
            Boolean.getBoolean("jsr166.expensiveTests");

    /**
     * If true, also run tests that are not part of the official tck
     * because they test unspecified implementation details.
     */
//    protected static final boolean testImplementationDetails =
//        Boolean.getBoolean("jsr166.testImplementationDetails");
    protected static final boolean testImplementationDetails = true;

    public static final Integer zero  = new Integer(0);
    public static final Integer one   = new Integer(1);
    public static final Integer two   = new Integer(2);
    public static final Integer three = new Integer(3);
    public static final Integer four  = new Integer(4);

    // Delays for timing-dependent tests, in milliseconds.

    public static long SHORT_DELAY_MS;
    public static long SMALL_DELAY_MS;
    public static long MEDIUM_DELAY_MS;
    public static long LONG_DELAY_MS;

    /**
     * A delay significantly longer than LONG_DELAY_MS.
     * Use this in a thread that is waited for via awaitTermination(Thread).
     */
    public static long LONGER_DELAY_MS;

    private static final long RANDOM_EXPIRED_TIMEOUT;
    private static final TimeUnit RANDOM_TIMEUNIT;
    static {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        long[] timeouts = { Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE };
        RANDOM_EXPIRED_TIMEOUT = timeouts[rnd.nextInt(3)];
        TimeUnit[] timeUnits = TimeUnit.values();
        RANDOM_TIMEUNIT = timeUnits[rnd.nextInt(timeUnits.length)];
    }

    /**
     * The scaling factor to apply to standard delays used in tests.
     * May be initialized from any of:
     * - the "jsr166.delay.factor" system property
     * - the "test.timeout.factor" system property (as used by jtreg)
     *   See: http://openjdk.java.net/jtreg/tag-spec.html
     * - hard-coded fuzz factor when using a known slowpoke VM
     */
    private static final float delayFactor = delayFactor();

    private static final long TIMEOUT_DELAY_MS = (long) (12.0 * Math.cbrt(delayFactor));

    /**
     * Returns a timeout in milliseconds to be used in tests that verify that
     * operations block or time out. We want this to be longer than the OS
     * scheduling quantum, but not too long, so don't scale linearly with
     * delayFactor; we use "crazy" cube root instead.
     */
    static long timeoutMillis() {
        return TIMEOUT_DELAY_MS;
    }

    /**
     * The first exception encountered if any threadAssertXXX method fails.
     */
    private final AtomicReference<Throwable> threadFailure
        = new AtomicReference<>(null);

    /**
     * Returns the shortest timed delay. This can be scaled up for
     * slow machines using the jsr166.delay.factor system property,
     * or via jtreg's -timeoutFactor: flag.
     * http://openjdk.java.net/jtreg/command-help.html
     */
    protected long getShortDelay() {
        return (long) (50 * delayFactor);
    }

    private static float delayFactor() {
        float x;
        if (!Float.isNaN(x = systemPropertyValue("jsr166.delay.factor")))
            return x;
        if (!Float.isNaN(x = systemPropertyValue("test.timeout.factor")))
            return x;
        String prop = System.getProperty("java.vm.version");
        if (prop != null && prop.matches(".*debug.*"))
            return 4.0f; // How much slower is fastdebug than product?!
        return 1.0f;
    }

    /**
     * Sets delays as multiples of SHORT_DELAY.
     */
    protected void setDelays() {
        SHORT_DELAY_MS = getShortDelay();
        SMALL_DELAY_MS  = SHORT_DELAY_MS * 5;
        MEDIUM_DELAY_MS = SHORT_DELAY_MS * 10;
        LONG_DELAY_MS   = SHORT_DELAY_MS * 200;
        LONGER_DELAY_MS = 2 * LONG_DELAY_MS;
    }

    @BeforeMethod
    public void setUp() {
        setDelays();
    }

    /**
     * Fails with message "should throw exception".
     */
    public void shouldThrow() {
        fail("Should throw exception");
    }

    /**
     * Fails with message "should throw " + exceptionName.
     */
    public void shouldThrow(String exceptionName) {
        fail("Should throw " + exceptionName);
    }

    public void assertThrows(Class<? extends Throwable> expectedExceptionClass, Action... throwingActions) {
        for (Action throwingAction : throwingActions) {
            boolean threw = false;
            try {
                throwingAction.run();
            } catch (Throwable t) {
                threw = true;
                if (!expectedExceptionClass.isInstance(t)) {
                    AssertionError ae = new AssertionError(
                            "Expected " + expectedExceptionClass.getName() + ", got " + t.getClass().getName());
                    ae.initCause(t);
                    threadUnexpectedException(ae);
                }
            }
            if (!threw)
                shouldThrow(expectedExceptionClass.getName());
        }
    }

    /**
     * Runs all the given actions in parallel, failing if any fail.
     * Useful for running multiple variants of tests that are
     * necessarily individually slow because they must block.
     */
    protected void testInParallel(Action ... actions) {
        ExecutorService pool = Executors.newCachedThreadPool();
        PoolCleaner cleaner = null;
        try {
            cleaner = cleaner(pool);
            ArrayList<Future<?>> futures = new ArrayList<>(actions.length);
            for (final Action action : actions)
                futures.add(pool.submit(new CheckedRunnable() {
                    public void realRun() throws Throwable { action.run();}}));
            for (Future<?> future : futures)
                try {
                    assertNull(future.get(LONG_DELAY_MS, MILLISECONDS));
                } catch (ExecutionException ex) {
                    threadUnexpectedException(ex.getCause());
                } catch (Exception ex) {
                    threadUnexpectedException(ex);
                }
        } finally {
            if (cleaner != null) {
                cleaner.close();
            }
        }
    }

    protected PoolCleaner cleaner(ExecutorService pool) {
        return new PoolCleaner(pool);
    }

    /**
     * Records the given exception using {@link #threadRecordFailure},
     * then rethrows the exception, wrapping it in an AssertionError
     * if necessary.
     */
    public void threadUnexpectedException(Throwable t) {
        threadRecordFailure(t);
        t.printStackTrace();
        if (t instanceof RuntimeException)
            throw (RuntimeException) t;
        else if (t instanceof Error)
            throw (Error) t;
        else {
            AssertionError ae =
                new AssertionError("unexpected exception: " + t);
            ae.initCause(t);
            throw ae;
        }
    }

    /**
     * Just like fail(reason), but additionally recording (using
     * threadRecordFailure) any AssertionError thrown, so that the
     * current testcase will fail.
     */
    public void threadFail(String reason) {
        try {
            fail(reason);
        } catch (AssertionError fail) {
            threadRecordFailure(fail);
            throw fail;
        }
    }

    /**
     * Records an exception so that it can be rethrown later in the test
     * harness thread, triggering a test case failure.  Only the first
     * failure is recorded; subsequent calls to this method from within
     * the same test have no effect.
     */
    public void threadRecordFailure(Throwable t) {
        System.err.println(t);
        if (threadFailure.compareAndSet(null, t))
            dumpTestThreads();
    }

    /**
     * A debugging tool to print stack traces of most threads, as jstack does.
     * Uninteresting threads are filtered out.
     */
    static void dumpTestThreads() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            try {
                System.setSecurityManager(null);
            } catch (SecurityException giveUp) {
                return;
            }
        }

        System.err.println("------ stacktrace dump start ------");
        for (ThreadInfo info : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true))
            if (threadOfInterest(info))
                System.err.print(info);
        System.err.println("------ stacktrace dump end ------");

        if (sm != null) System.setSecurityManager(sm);
    }

    /** Returns true if thread info might be useful in a thread dump. */
    static boolean threadOfInterest(Object info_) {
        ThreadInfo info = (ThreadInfo) info_;
        final String name = info.getThreadName();
        String lockName;
        if (name == null)
            return true;
        if (name.equals("Signal Dispatcher")
            || name.equals("WedgedTestDetector"))
            return false;
        if (name.equals("Reference Handler")) {
            // Reference Handler stacktrace changed in JDK-8156500
            StackTraceElement[] stackTrace; String methodName;
            if ((stackTrace = info.getStackTrace()) != null
                && stackTrace.length > 0
                && (methodName = stackTrace[0].getMethodName()) != null
                && methodName.equals("waitForReferencePendingList"))
                return false;
            // jdk8 Reference Handler stacktrace
            if ((lockName = info.getLockName()) != null
                && lockName.startsWith("java.lang.ref"))
                return false;
        }
        if ((name.equals("Finalizer") || name.equals("Common-Cleaner"))
            && (lockName = info.getLockName()) != null
            && lockName.startsWith("java.lang.ref"))
            return false;
        if (name.startsWith("ForkJoinPool.commonPool-worker")
            && (lockName = info.getLockName()) != null
            && lockName.startsWith("java.util.concurrent.ForkJoinPool"))
            return false;
        return true;
    }

    /**
     * Checks that timed f.get() returns the expected value, and does not
     * wait for the timeout to elapse before returning.
     */
    <T> void checkTimedGet(Future<T> f, T expectedValue, long timeoutMillis) {
        long startTime = System.nanoTime();
        T actual = null;
        try {
            actual = f.get(timeoutMillis, MILLISECONDS);
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        assertEquals(expectedValue, actual);
        if (millisElapsedSince(startTime) > timeoutMillis/2)
            throw new AssertionError("timed get did not return promptly");
    }

    protected <T> void checkTimedGet(Future<T> f, T expectedValue) {
        checkTimedGet(f, expectedValue, LONG_DELAY_MS);
    }

    /**
     * Waits out termination of a thread pool or fails doing so.
     */
    void joinPool(ExecutorService pool) {
        try {
            pool.shutdown();
            if (!pool.awaitTermination(2 * LONG_DELAY_MS, MILLISECONDS)) {
                try {
                    threadFail("ExecutorService " + pool +
                               " did not terminate in a timely manner");
                } finally {
                    // last resort, for the benefit of subsequent tests
                    pool.shutdownNow();
                    pool.awaitTermination(MEDIUM_DELAY_MS, MILLISECONDS);
                }
            }
        } catch (SecurityException ok) {
            // Allowed in case test doesn't have privs
        } catch (InterruptedException fail) {
            threadFail("Unexpected InterruptedException");
        }
    }

    /**
     * Returns the same String as would be returned by {@link
     * Object#toString}, whether or not the given object's class
     * overrides toString().
     *
     * @see System#identityHashCode
     */
    static String identityString(Object x) {
        return x.getClass().getName()
            + "@" + Integer.toHexString(System.identityHashCode(x));
    }

    /**
     * Returns the number of milliseconds since time given by
     * startNanoTime, which must have been previously returned from a
     * call to {@link System#nanoTime()}.
     */
    protected static long millisElapsedSince(long startNanoTime) {
        return NANOSECONDS.toMillis(System.nanoTime() - startNanoTime);
    }

    /**
     * Returns the value of the system property, or NaN if not defined.
     */
    private static float systemPropertyValue(String name) {
        String floatString = System.getProperty(name);
        if (floatString == null)
            return Float.NaN;
        try {
            return Float.parseFloat(floatString);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                String.format("Bad float value in system property %s=%s",
                              name, floatString));
        }
    }

    /**
     * Returns a timeout that means "no waiting", i.e. not positive.
     */
    static long randomExpiredTimeout() { return RANDOM_EXPIRED_TIMEOUT; }

    /**
     * Returns a random non-null TimeUnit.
     */
    static TimeUnit randomTimeUnit() { return RANDOM_TIMEUNIT; }

    /**
     * Are we running on Android 7+ ?
     * 
     * @return {@code true} if yes, otherwise {@code false}.
     */
    static boolean isOpenJDKAndroid() {
        return isClassPresent("android.opengl.GLES32$DebugProc");
    }

    static boolean isClassPresent(String name) {
        Class<?> clazz = null;
        try {
            clazz = Class.forName(name, false,
                    JSR166TestCase.class.getClassLoader());
        } catch (Throwable notPresent) {
            // ignore
        }
        return clazz != null;
    }
}


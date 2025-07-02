import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadFactory implements ThreadFactory {
    private final String poolName;
    private final AtomicInteger threadCount = new AtomicInteger(1);

    public CustomThreadFactory(String poolName) {
        this.poolName = poolName;
    }

    @Override
    public Thread newThread(Runnable r) {
        String threadName = poolName + "-worker-" + threadCount.getAndIncrement();
        System.out.println("[ThreadFactory] Creating new thread: " + threadName);
        Thread t = new Thread(r, threadName);
        t.setUncaughtExceptionHandler((th, ex) ->
            System.out.println("[Worker] " + th.getName() + " terminated with exception: " + ex));
        return t;
    }
} 
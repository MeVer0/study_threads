import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

class Worker extends Thread {
    private final BlockingQueue<Runnable> queue;
    private final CustomThreadPoolExecutor pool;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private volatile boolean running = true;

    public Worker(BlockingQueue<Runnable> queue, CustomThreadPoolExecutor pool, long keepAliveTime, TimeUnit timeUnit, String name) {
        super(name);
        this.queue = queue;
        this.pool = pool;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
    }

    @Override
    public void run() {
        try {
            while (running && !pool.isShutdown()) {
                Runnable task = queue.poll(keepAliveTime, timeUnit);
                if (task != null) {
                    System.out.println("[Worker] " + getName() + " executes " + task);
                    task.run();
                } else {
                    if (pool.shouldTerminateWorker(this)) {
                        System.out.println("[Worker] " + getName() + " idle timeout, stopping.");
                        break;
                    }
                }
            }
        } catch (InterruptedException e) {
            // handle
        } finally {
            System.out.println("[Worker] " + getName() + " terminated.");
            pool.onWorkerExit(this);
        }
    }

    public void shutdown() {
        running = false;
        this.interrupt();
    }
} 
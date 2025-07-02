import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadPoolExecutor implements CustomExecutor {
    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int queueSize;
    private final int minSpareThreads;
    private final String poolName;
    private final CustomThreadFactory threadFactory;
    private final CustomRejectedExecutionHandler rejectedHandler = new CustomRejectedExecutionHandler();
    private final List<BlockingQueue<Runnable>> queues = new ArrayList<>();
    private final List<Worker> workers = new ArrayList<>();
    private final AtomicInteger rrIndex = new AtomicInteger(0);
    private volatile boolean shutdown = false;

    public CustomThreadPoolExecutor(int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit timeUnit, int queueSize, int minSpareThreads, String poolName) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.queueSize = queueSize;
        this.minSpareThreads = minSpareThreads;
        this.poolName = poolName;
        this.threadFactory = new CustomThreadFactory(poolName);
        for (int i = 0; i < corePoolSize; i++) {
            addWorker();
        }
    }

    private synchronized void addWorker() {
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(queueSize);
        queues.add(queue);
        Worker worker = new Worker(queue, this, keepAliveTime, timeUnit, poolName + "-worker-" + (workers.size() + 1));
        workers.add(worker);
        worker.start();
    }

    @Override
    public void execute(Runnable command) {
        if (shutdown) {
            rejectedHandler.rejected(command);
            return;
        }
        int idx = rrIndex.getAndIncrement() % workers.size();
        BlockingQueue<Runnable> queue = queues.get(idx);
        boolean offered = queue.offer(command);
        if (offered) {
            System.out.println("[Pool] Task accepted into queue #" + idx + ": " + command);
        } else {
            synchronized (this) {
                if (workers.size() < maxPoolSize) {
                    addWorker();
                    queues.get(queues.size() - 1).offer(command);
                    System.out.println("[Pool] Task accepted into new queue #" + (queues.size() - 1) + ": " + command);
                    return;
                }
            }
            rejectedHandler.rejected(command);
        }
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        FutureTask<T> task = new FutureTask<>(callable);
        execute(task);
        return task;
    }

    @Override
    public void shutdown() {
        shutdown = true;
        for (Worker w : workers) {
            w.shutdown();
        }
    }

    @Override
    public void shutdownNow() {
        shutdown = true;
        for (Worker w : workers) {
            w.shutdown();
        }
        for (BlockingQueue<Runnable> q : queues) {
            q.clear();
        }
    }

    public boolean isShutdown() {
        return shutdown;
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        long last = System.nanoTime();
        while (!workers.isEmpty()) {
            if (nanos <= 0) {
                return false;
            }
            long now = System.nanoTime();
            nanos -= (now - last);
            last = now;
            if (nanos > 0) {
                // Можно добавить более интеллектуальное ожидание, например, wait/notify
                // Для простоты пока просто спим немного
                Thread.sleep(Math.min(nanos / 1_000_000, 10)); // Спим максимум 10мс
            }
        }
        return true;
    }

    public synchronized boolean shouldTerminateWorker(Worker worker) {
        int runningWorkers = (int) workers.stream().filter(Thread::isAlive).count();
        int idleWorkers = (int) workers.stream().filter(w -> w.getState() == Thread.State.WAITING || w.getState() == Thread.State.TIMED_WAITING).count();
        if (workers.size() > corePoolSize && idleWorkers > minSpareThreads) {
            workers.remove(worker);
            queues.remove(workers.indexOf(worker));
            return true;
        }
        return false;
    }

    public synchronized void onWorkerExit(Worker worker) {
        workers.remove(worker);
    }
} 
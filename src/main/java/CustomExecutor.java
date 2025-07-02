// Основной интерфейс для кастомного пула потоков
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;

public interface CustomExecutor extends Executor {
    void execute(Runnable command);
    <T> Future<T> submit(Callable<T> callable);
    void shutdown();
    void shutdownNow();
}
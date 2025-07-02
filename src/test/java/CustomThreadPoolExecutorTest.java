import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadPoolExecutorTest {

    private CustomThreadPoolExecutor pool;

    // Перенаправляем System.out для захвата логов
    private final java.io.ByteArrayOutputStream outContent = new java.io.ByteArrayOutputStream();
    private final java.io.PrintStream originalOut = System.out;

    @BeforeEach
    public void setUp() {
        System.setOut(new java.io.PrintStream(outContent));
    }

    @AfterEach
    public void restoreStreams() {
        System.setOut(originalOut);
    }

    @Test
    @DisplayName("Пул инициализируется с corePoolSize потоками")
    void testPoolInitialization() {
        pool = new CustomThreadPoolExecutor(2, 4, 1, TimeUnit.SECONDS, 5, 1, "TestPool");
        try {
            Thread.sleep(100); // Даем время на создание потоков
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Проверяем, что создано corePoolSize потоков
        assertTrue(outContent.toString().contains("[ThreadFactory] Creating new thread: TestPool-worker-1"));
        assertTrue(outContent.toString().contains("[ThreadFactory] Creating new thread: TestPool-worker-2"));
        pool.shutdownNow();
    }

    @Test
    @DisplayName("Задачи выполняются корректно")
    void testTaskExecution() throws InterruptedException {
        pool = new CustomThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, 1, 0, "TestPool");
        AtomicInteger counter = new AtomicInteger(0);
        Runnable task = () -> counter.incrementAndGet();

        pool.execute(task);
        Thread.sleep(100); // Даем время на выполнение задачи
        assertEquals(1, counter.get());

        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("shutdown() корректно завершает работу и выполняет текущие задачи")
    void testShutdown() throws InterruptedException {
        pool = new CustomThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, 1, 0, "TestPool");
        CountDownLatch latch = new CountDownLatch(1);

        pool.execute(() -> {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            latch.countDown();
        });

        pool.shutdown();
        assertFalse(pool.isShutdown()); // shutdown() не сразу переводит в состояние shutdown
        assertTrue(pool.awaitTermination(1, TimeUnit.SECONDS));
        assertTrue(latch.await(100, TimeUnit.MILLISECONDS)); // Задача должна быть выполнена
        assertTrue(pool.isShutdown()); // После awaitTermination, должен быть shutdown
    }

    @Test
    @DisplayName("shutdownNow() немедленно завершает работу")
    void testShutdownNow() throws InterruptedException {
        pool = new CustomThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, 1, 0, "TestPool");
        CountDownLatch latch = new CountDownLatch(1);

        pool.execute(() -> {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            latch.countDown();
        });

        pool.shutdownNow();
        assertTrue(pool.isShutdown()); // shutdownNow() немедленно переводит в состояние shutdown
        assertFalse(latch.await(100, TimeUnit.MILLISECONDS)); // Задача не должна быть выполнена
    }

    @Test
    @DisplayName("Задачи отклоняются при переполнении очереди и максимальном числе потоков")
    void testRejectedExecution() throws InterruptedException {
        pool = new CustomThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, 1, 0, "TestPool");

        // Занимаем единственный поток
        pool.execute(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        });

        // Заполняем очередь
        pool.execute(() -> {});

        // Третья задача должна быть отклонена
        pool.execute(() -> {});

        Thread.sleep(100); // Даем время на обработку
        assertTrue(outContent.toString().contains("[Rejected] Task"));

        pool.shutdownNow();
    }

    @Test
    @DisplayName("Idle-воркеры завершаются по keepAliveTime")
    void testIdleWorkerTermination() throws InterruptedException {
        pool = new CustomThreadPoolExecutor(1, 2, 200, TimeUnit.MILLISECONDS, 5, 0, "TestPool");

        // Выполняем задачу, чтобы создать второй поток (maxPoolSize = 2)
        pool.execute(() -> {
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        });

        Thread.sleep(500); // Ждем, пока второй поток станет idle и завершится

        assertTrue(outContent.toString().contains("[Worker] TestPool-worker-2 idle timeout, stopping."));
        assertTrue(outContent.toString().contains("[Worker] TestPool-worker-2 terminated."));

        pool.shutdownNow();
    }
} 
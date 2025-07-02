import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        CustomThreadPoolExecutor pool = new CustomThreadPoolExecutor(
            2, 4, 5, TimeUnit.SECONDS, 5, 1, "MyPool"
        );

        for (int i = 0; i < 12; i++) {
            int id = i;
            pool.execute(() -> {
                System.out.println("Task " + id + " started");
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                System.out.println("Task " + id + " finished");
            });
        }

        Thread.sleep(15000);
        pool.shutdown();
        System.out.println("Pool shutdown initiated.");
    }
} 
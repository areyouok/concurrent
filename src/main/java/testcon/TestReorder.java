package testcon;

public class TestReorder {

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < LoopCount; i++)
            new TestReorder().run();
    }

    // LinkableList是一个无锁且不需要CAS的普通链表，满足单生产者单消费者的应用场景
    private final LinkableList<PendingTask> pendingTasks = new LinkableList<>();

    private int index;

    public static final long LoopCount = 10000;

    protected final long pendingTaskCount = 1000 * 10000; // 待处理任务总数
    protected long completedTaskCount; // 已经完成的任务数
    protected long result; // 存放计算结果

    protected final PendingTask[] pool = new PendingTask[(int)pendingTaskCount];

    public void run() throws Exception {
        for (int i = 0; i < pendingTaskCount; i++) {
            pool[i] = new PendingTask();
        }

        // 生产者创建pendingTaskCount个AsyncTask
        // 每个AsyncTask的工作就是计算从1到pendingTaskCount的和
        Thread producer = new Thread(() -> {
            for (int i = 1; i <= pendingTaskCount; i++) {
                produce(i);
            }
        });

        // 消费者不断从pendingTasks中取出AsyncTask执行
        Thread consumer = new Thread(() -> {
            while (completedTaskCount < pendingTaskCount) {
                consume();
            }
        });

        long t = System.currentTimeMillis();
        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
        t = System.currentTimeMillis() - t;

        // 如果result跟except相同，说明代码是ok的，如果不同，那就说明代码有bug
        long except = (1 + pendingTaskCount) * pendingTaskCount / 2;
        if (result == except) {
            System.out.println(
                    getClass().getSimpleName() + " result: " + result + ", ok, cost " + t + " ms");
        } else {
            System.out.println(
                    getClass().getSimpleName() + "result: " + result + ", not ok, except: " + except);
        }
    }

    public void produce(int value) {
        PendingTask pt = pool[index++];
        pt.value = value;
        pendingTasks.add(pt);
        if (pendingTasks.size() > 1)
            removeCompletedTasks();
    }

    private void removeCompletedTasks() {
        PendingTask pt = pendingTasks.getHead();
        while (pt != null && pt.isCompleted()) {
            pt = pt.getNext();
            pendingTasks.decrementSize();
            pendingTasks.setHead(pt);
        }
        if (pendingTasks.getHead() == null)
            pendingTasks.setTail(null);
    }

    public void consume() {
        PendingTask pt = pendingTasks.getHead();
        while (pt != null) {
            if (!pt.isCompleted()) {
                completedTaskCount++;
                result += pt.value;
                pt.setCompleted(true);
            }
            pt = pt.getNext();
        }
        Thread.yield(); // 去掉这一行性能会变慢
    }

    public static class PendingTask {

        private int value;
        private boolean completed;

        private PendingTask next;

        public boolean isCompleted() {
            return completed;
        }

        public void setCompleted(boolean completed) {
            this.completed = completed;
        }

        public void setNext(PendingTask next) {
            this.next = next;
        }

        public PendingTask getNext() {
            return next;
        }
    }

    public static class LinkableList<E extends PendingTask> {

        private E head;
        private E tail;
        private int size;

        public E getHead() {
            return head;
        }

        public void setHead(E head) {
            this.head = head;
        }

        public void setTail(E tail) {
            this.tail = tail;
        }

        public int size() {
            return size;
        }

        public void decrementSize() {
            size--;
        }

        public void add(E e) {
            size++;
            if (head == null) {
                head = tail = e;
            } else {
                tail.setNext(e);
                tail = e;
            }
        }
    }
}

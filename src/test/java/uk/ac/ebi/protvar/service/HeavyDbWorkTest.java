package uk.ac.ebi.protvar.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeavyDbWorkTest {

    // runs tasks that each hold their slot briefly; records how many of the job's tasks ran at once
    private static Thread run(HeavyDbWork.Job job, AtomicInteger total, AtomicInteger maxTotal, AtomicInteger maxJob) {
        AtomicInteger mine = new AtomicInteger();
        Thread t = new Thread(() -> {
            try {
                List<Future<Integer>> fs = new ArrayList<>();
                for (int i = 0; i < 12; i++) {
                    fs.add(job.submit(() -> {
                        maxJob.accumulateAndGet(mine.incrementAndGet(), Math::max);
                        maxTotal.accumulateAndGet(total.incrementAndGet(), Math::max);
                        Thread.sleep(20);
                        total.decrementAndGet();
                        mine.decrementAndGet();
                        return 1;
                    }));
                }
                for (Future<Integer> f : fs) f.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        t.start();
        return t;
    }

    @Test
    void slotsAreSharedAndEachJobKeepsToItsShare() throws Exception {
        HeavyDbWork work = new HeavyDbWork(4, 2, 10);
        AtomicInteger total = new AtomicInteger(), maxTotal = new AtomicInteger();
        AtomicInteger maxA = new AtomicInteger(), maxB = new AtomicInteger(), maxC = new AtomicInteger();
        List<Thread> jobs = List.of(run(work.job("a"), total, maxTotal, maxA),
                run(work.job("b"), total, maxTotal, maxB), run(work.job("c"), total, maxTotal, maxC));
        for (Thread t : jobs) t.join();
        work.shutdown();

        assertEquals(4, maxTotal.get());   // three jobs × 2 wanted 6; the 4 slots cap them
        assertEquals(2, maxA.get());
        assertEquals(2, maxB.get());
        assertEquals(2, maxC.get());
    }
}

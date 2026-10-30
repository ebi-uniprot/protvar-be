package uk.ac.ebi.protvar.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.LongAdder;

/**
 * One budget for the background work that hits the DB (and Redis) hardest: download partitions and upload-build
 * chunks. At most {@code slots} tasks run at once across all jobs. A job has at most {@code perJob} tasks queued
 * or running, so one large job can't starve the others, and its producer waits (back-pressure) rather than
 * queueing the whole job in memory. Keep slots well under the Hikari pool: web requests need the rest.
 */
@Component
public class HeavyDbWork {
    private static final Logger LOGGER = LoggerFactory.getLogger(HeavyDbWork.class);

    private final ExecutorService pool;
    private final int perJob;

    public HeavyDbWork(@Value("${app.db-work.slots:6}") int slots,
                       @Value("${app.db-work.per-job:3}") int perJob,
                       @Value("${spring.datasource.hikari.maximum-pool-size:10}") int dbPool) {
        this.pool = Executors.newFixedThreadPool(slots, new CustomizableThreadFactory("db-work-"));
        this.perJob = Math.min(perJob, slots);
        LOGGER.info("Heavy DB work: {} slots, {} per job (DB pool {})", slots, this.perJob, dbPool);
        if (slots > dbPool / 2) {
            LOGGER.warn("Heavy DB work slots ({}) exceed half the DB pool ({}): little is left for web requests",
                    slots, dbPool);
        }
    }

    /** A new job (a download, an upload build): its tasks share the per-job limit. */
    public Job job(String name) {
        return new Job(name);
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }

    public final class Job {
        private final String name;
        private final Semaphore permits = new Semaphore(perJob);
        private final LongAdder queuedMs = new LongAdder();   // time tasks waited for a free slot (other jobs)

        private Job(String name) {
            this.name = name;
        }

        /** Runs the task in a slot; blocks while the job already has its limit of tasks queued or running. */
        public <T> Future<T> submit(Callable<T> task) throws InterruptedException {
            permits.acquire();
            long submitted = System.currentTimeMillis();
            try {
                return pool.submit(() -> {
                    queuedMs.add(System.currentTimeMillis() - submitted);
                    try {
                        return task.call();
                    } finally {
                        permits.release();
                    }
                });
            } catch (RejectedExecutionException e) {
                permits.release();
                throw e;
            }
        }

        /** Total time this job's tasks waited for a slot held by other jobs; logged by the caller. */
        public long queuedMs() {
            return queuedMs.sum();
        }

        @Override
        public String toString() {
            return name;
        }
    }
}

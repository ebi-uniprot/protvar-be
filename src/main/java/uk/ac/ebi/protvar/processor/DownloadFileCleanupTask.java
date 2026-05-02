package uk.ac.ebi.protvar.processor;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.ac.ebi.protvar.config.RetentionProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Daily sweep of {@code app.data.folder} that deletes generated download
 * archives older than {@link RetentionProperties#getDownloads()}. Runs at 03:00
 * server time. Aligns the disk lifecycle with the Redis status TTL — anything
 * past the cutoff is already reported as EXPIRED to clients via the status
 * endpoint.
 *
 * <p>Only files matching {@code *.csv.zip} are removed. Anything else in the
 * data folder (logs, configs, manually placed artifacts) is left alone.
 */
@Component
@RequiredArgsConstructor
public class DownloadFileCleanupTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadFileCleanupTask.class);

    @Value("${app.data.folder}")
    private String dataFolder;

    private final RetentionProperties retention;

    @Scheduled(cron = "0 0 3 * * *")
    public void sweep() {
        Path root = Path.of(dataFolder);
        if (!Files.isDirectory(root)) {
            LOGGER.warn("Cleanup skipped — data folder not present: {}", root);
            return;
        }
        Instant cutoff = Instant.now().minus(retention.getDownloads());
        AtomicLong deletedFiles = new AtomicLong();
        AtomicLong freedBytes = new AtomicLong();

        try (Stream<Path> stream = Files.list(root)) {
            stream
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".csv.zip"))
                .forEach(p -> tryDeleteIfOld(p, cutoff, deletedFiles, freedBytes));
        } catch (IOException e) {
            LOGGER.warn("Cleanup failed to list {}: {}", root, e.getMessage());
            return;
        }

        if (deletedFiles.get() > 0) {
            LOGGER.info("Cleanup deleted {} download files ({} bytes freed)",
                    deletedFiles.get(), freedBytes.get());
        }
    }

    private void tryDeleteIfOld(Path file, Instant cutoff,
                                 AtomicLong deletedFiles, AtomicLong freedBytes) {
        try {
            Instant mtime = Files.getLastModifiedTime(file).toInstant();
            if (mtime.isAfter(cutoff)) return;
            long size = Files.size(file);
            Files.deleteIfExists(file);
            deletedFiles.incrementAndGet();
            freedBytes.addAndGet(size);
        } catch (IOException e) {
            LOGGER.warn("Cleanup couldn't delete {}: {}", file, e.getMessage());
        }
    }
}

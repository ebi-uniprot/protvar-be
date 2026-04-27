package uk.ac.ebi.protvar.processor;

/**
 * Thrown by {@link DownloadProcessor} when streamed rows exceed the
 * configured cap during a full download. Rare — the controller-side count
 * check at submit time catches most cases; this is the backstop for
 * filter-only paths where the bounded COUNT short-circuited.
 */
class DownloadTooLargeException extends RuntimeException {
}

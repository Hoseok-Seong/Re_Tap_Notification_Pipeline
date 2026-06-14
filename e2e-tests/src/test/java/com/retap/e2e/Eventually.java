package com.retap.e2e;

import java.time.Duration;

final class Eventually {

    private Eventually() {
    }

    static void untilAsserted(Duration timeout, ThrowingRunnable assertion) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        AssertionError lastAssertionError = null;

        while (System.nanoTime() < deadline) {
            try {
                assertion.run();
                return;
            } catch (AssertionError e) {
                lastAssertionError = e;
                Thread.sleep(250);
            }
        }

        if (lastAssertionError != null) {
            throw lastAssertionError;
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}

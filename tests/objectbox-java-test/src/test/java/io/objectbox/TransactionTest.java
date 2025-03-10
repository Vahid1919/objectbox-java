/*
 * Copyright 2017 ObjectBox Ltd. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.objectbox;

import io.objectbox.exception.DbException;
import io.objectbox.exception.DbExceptionListener;
import io.objectbox.exception.DbMaxReadersExceededException;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TransactionTest extends AbstractObjectBoxTest {

    private void prepareOneEntryWith1230() {
        // prepare the data
        Transaction transaction = store.beginTx();
        KeyValueCursor cursor = transaction.createKeyValueCursor();
        cursor.put(123, new byte[]{1, 2, 3, 0});
        cursor.close();
        assertTrue(transaction.isActive());
        transaction.commit();
        assertFalse(transaction.isActive());
    }

    @Test
    public void testTransactionCommitAndAbort() {
        prepareOneEntryWith1230();

        Transaction transaction = store.beginTx();
        KeyValueCursor cursor = transaction.createKeyValueCursor();
        cursor.put(123, new byte[]{3, 2, 1, 0});
        assertArrayEquals(new byte[]{3, 2, 1, 0}, cursor.get(123));
        cursor.close();
        transaction.abort();

        transaction = store.beginTx();
        cursor = transaction.createKeyValueCursor();
        assertArrayEquals(new byte[]{1, 2, 3, 0}, cursor.get(123));
        cursor.close();
        transaction.abort();
    }

    @Test
    public void testReadTransactionWhileWriting() {
        prepareOneEntryWith1230();

        Transaction txWrite = store.beginTx();
        Transaction txRead = store.beginReadTx();

        // start writing
        KeyValueCursor cursorWrite = txWrite.createKeyValueCursor();
        cursorWrite.put(123, new byte[]{3, 2, 1, 0});
        assertArrayEquals(new byte[]{3, 2, 1, 0}, cursorWrite.get(123));
        cursorWrite.close();

        // start reading the old value
        KeyValueCursor cursorRead = txRead.createKeyValueCursor();
        assertArrayEquals(new byte[]{1, 2, 3, 0}, cursorRead.get(123));
        cursorRead.close();

        // commit writing
        assertTrue(txRead.isReadOnly());
        assertFalse(txWrite.isReadOnly());

        assertTrue(txWrite.isActive());
        txWrite.commit();
        assertFalse(txWrite.isActive());

        // commit reading
        assertTrue(txRead.isActive());
        txRead.abort();
        assertFalse(txRead.isActive());

        // start reading again and get the new value
        txRead = store.beginReadTx();
        cursorRead = txRead.createKeyValueCursor();
        assertArrayEquals(new byte[]{3, 2, 1, 0}, cursorRead.get(123));
        cursorRead.close();

        txRead.abort();

        store.close();
    }

    @Test
    public void testTransactionReset() {
        prepareOneEntryWith1230();

        // write transaction

        Transaction transaction = store.beginTx();
        KeyValueCursor cursor = transaction.createKeyValueCursor();
        cursor.put(123, new byte[]{3, 2, 1, 0});
        assertArrayEquals(new byte[]{3, 2, 1, 0}, cursor.get(123));
        cursor.close();
        transaction.reset();
        assertTrue(transaction.isActive());

        cursor = transaction.createKeyValueCursor();
        assertArrayEquals(new byte[]{1, 2, 3, 0}, cursor.get(123));
        cursor.close();
        transaction.abort();

        transaction.reset();
        cursor = transaction.createKeyValueCursor();
        cursor.put(123, new byte[]{3, 2, 1, 0});
        assertArrayEquals(new byte[]{3, 2, 1, 0}, cursor.get(123));
        cursor.close();
        transaction.commit();

        transaction.reset();
        cursor = transaction.createKeyValueCursor();
        assertArrayEquals(new byte[]{3, 2, 1, 0}, cursor.get(123));
        cursor.close();
        transaction.commit();

        // read transaction

        transaction = store.beginReadTx();
        cursor = transaction.createKeyValueCursor();
        assertArrayEquals(new byte[]{3, 2, 1, 0}, cursor.get(123));
        cursor.close();
        transaction.reset();
        assertTrue(transaction.isActive());

        cursor = transaction.createKeyValueCursor();
        assertArrayEquals(new byte[]{3, 2, 1, 0}, cursor.get(123));
        cursor.close();
        transaction.abort();
    }

    @Test(expected = IllegalStateException.class)
    public void testCreateCursorAfterAbortException() {
        Transaction tx = store.beginReadTx();
        tx.abort();
        tx.createKeyValueCursor();
    }

    @Test(expected = IllegalStateException.class)
    public void testCommitAfterAbortException() {
        Transaction tx = store.beginTx();
        tx.abort();
        tx.commit();
    }

    @Test(expected = IllegalStateException.class)
    public void testCommitReadTxException() {
        Transaction tx = store.beginReadTx();
        try {
            tx.commit();
        } finally {
            tx.abort();
        }
    }

    @Test
    public void testCommitReadTxException_exceptionListener() {
        final Exception[] exs = {null};
        DbExceptionListener exceptionListener = e -> exs[0] = e;
        Transaction tx = store.beginReadTx();
        store.setDbExceptionListener(exceptionListener);
        try {
            tx.commit();
            fail("Should have thrown");
        } catch (IllegalStateException e) {
            tx.abort();
            assertSame(e, exs[0]);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testCancelExceptionOutsideDbExceptionListener() {
        DbExceptionListener.cancelCurrentException();
    }

    @Test
    public void testCommitReadTxException_cancelException() {
        final Exception[] exs = {null};
        DbExceptionListener exceptionListener = e -> {
            if (exs[0] != null) throw new RuntimeException("Called more than once");
            exs[0] = e;
            DbExceptionListener.cancelCurrentException();
        };
        Transaction tx = store.beginReadTx();
        store.setDbExceptionListener(exceptionListener);
        tx.commit();
        tx.abort();
        assertNotNull(exs[0]);
    }

    /*
    @Test
    public void testTransactionUsingAfterStoreClosed() {
        prepareOneEntryWith1230();

        // write transaction
        Transaction transaction = store.beginTx();
        Cursor cursor = transaction.createCursor();

        store.close();

        cursor.put(123, new byte[]{3, 2, 1});
        assertArrayEquals(new byte[]{3, 2, 1}, cursor.get(123));
        cursor.close();
        transaction.reset();
        assertEquals(true, transaction.isActive());
    }*/

    @Test
    @Ignore("Tests robustness in invalid usage scenarios with lots of errors raised and resources leaked." +
            "Only run this test manually from time to time, but spare regular test runs from those errors.")
    public void testTxGC() throws InterruptedException {
        // Trigger pending finalizers so we have less finalizers to run later
        System.gc();
        System.runFinalization();

        // Dangling TXs is exactly what we are testing here
        Transaction.TRACK_CREATION_STACK = false;

        // For a real test, use count = 100000 and check console output that TX get freed in between
        int count = runExtensiveTests ? 100000 : 1000;
        Thread[] threads = new Thread[count];
        final AtomicInteger threadsOK = new AtomicInteger();
        final AtomicInteger readersFull = new AtomicInteger();
        for (int i = 0; i < count; i++) {
            threads[i] = new Thread(() -> {
                try {
                    store.beginReadTx();
                } catch (DbMaxReadersExceededException e) {
                    readersFull.incrementAndGet();
                }
                threadsOK.incrementAndGet();
            });
        }
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        System.out.println("OK vs. MDB_READERS_FULL: " + threadsOK + " vs. " + readersFull);

        // Some effort to clean up the dangling TXs to avoid them surving the store closing, which
        // may cause issues for later running tests
        for (int i = 0; i < 10; i++) {
            System.gc();
            System.runFinalization();
        }

        assertEquals(count, threadsOK.get());
    }

    @Test
    public void testClose() {
        Transaction tx = store.beginReadTx();
        assertFalse(tx.isClosed());
        tx.close();
        assertTrue(tx.isClosed());

        // Double close should be fine
        tx.close();

        // Calling other methods should throw.
        assertThrowsTxClosed(tx::commit);
        assertThrowsTxClosed(tx::commitAndClose);
        assertThrowsTxClosed(tx::abort);
        assertThrowsTxClosed(tx::reset);
        assertThrowsTxClosed(tx::recycle);
        assertThrowsTxClosed(tx::renew);
        assertThrowsTxClosed(tx::createKeyValueCursor);
        assertThrowsTxClosed(() -> tx.createCursor(TestEntity.class));
        assertThrowsTxClosed(tx::isActive);
        assertThrowsTxClosed(tx::isRecycled);
    }

    private void assertThrowsTxClosed(ThrowingRunnable runnable) {
        IllegalStateException ex = assertThrows(IllegalStateException.class, runnable);
        assertEquals("Transaction is closed", ex.getMessage());
    }

    @Test
    public void testRunInTxRecursive() {
        final Box<TestEntity> box = getTestEntityBox();
        final long[] counts = {0, 0, 0};
        store.runInTx(() -> {
            box.put(new TestEntity());
            counts[0] = box.count();
            try {
                store.callInTx((Callable<Void>) () -> {
                    store.runInTx(() -> {
                        box.put(new TestEntity());
                        counts[1] = box.count();
                    });
                    box.put(new TestEntity());
                    counts[2] = box.count();
                    return null;
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals(1, counts[0]);
        assertEquals(2, counts[1]);
        assertEquals(3, counts[2]);
        assertEquals(3, box.count());
    }

    @Test
    public void testRunInReadTx() {
        final Box<TestEntity> box = getTestEntityBox();
        final long[] counts = {0, 0};
        box.put(new TestEntity());
        store.runInReadTx(() -> {
            counts[0] = box.count();
            store.runInReadTx(() -> counts[1] = box.count());
        });
        assertEquals(1, counts[0]);
        assertEquals(1, counts[1]);
    }

    @Test
    public void testCallInReadTx() {
        final Box<TestEntity> box = getTestEntityBox();
        box.put(new TestEntity());
        long[] counts = store.callInReadTx(() -> {
            long count1 = store.callInReadTx(box::count);
            return new long[]{box.count(), count1};
        });
        assertEquals(1, counts[0]);
        assertEquals(1, counts[1]);
    }

    @Test
    public void testRunInReadTxAndThenPut() {
        final Box<TestEntity> box = getTestEntityBox();
        store.runInReadTx(box::count);
        // Verify that box does not hang on to the read-only TX by doing a put
        box.put(new TestEntity());
        assertEquals(1, box.count());
    }

    @Test
    public void testRunInReadTx_recursiveWriteTxFails() {
        store.runInReadTx(() -> {
            try {
                store.runInTx(() -> {
                });
                fail("Should have thrown");
            } catch (IllegalStateException e) {
                // OK
            }
        });
    }

    @Test(expected = DbException.class)
    public void testRunInReadTx_putFails() {
        store.runInReadTx(() -> getTestEntityBox().put(new TestEntity()));
    }

    @Test
    public void testRunInTx_PutAfterRemoveAll() {
        final Box<TestEntity> box = getTestEntityBox();
        final long[] counts = {0};
        box.put(new TestEntity());
        store.runInTx(() -> {
            putTestEntities(2);
            box.removeAll();
            putTestEntity("hello", 3);
            counts[0] = box.count();
        });
        assertEquals(1, counts[0]);
    }

    @Test
    public void testCallInTxAsync_multiThreaded() throws InterruptedException {
        final Box<TestEntity> box = getTestEntityBox();
        final Thread mainTestThread = Thread.currentThread();
        final AtomicInteger number = new AtomicInteger();
        final AtomicInteger errorCount = new AtomicInteger();
        final int countThreads = runExtensiveTests ? 500 : 10;
        final int countEntities = runExtensiveTests ? 1000 : 100;
        final CountDownLatch threadsDoneLatch = new CountDownLatch(countThreads);

        Callable<Object> callable = () -> {
            assertNotSame(mainTestThread, Thread.currentThread());
            for (int i = 0; i < countEntities; i++) {
                TestEntity entity = new TestEntity();
                final int value = number.incrementAndGet();
                entity.setSimpleInt(value);
                long key = box.put(entity);
                TestEntity read = box.get(key);
                assertEquals(value, read.getSimpleInt());
            }
            return box.count();
        };
        TxCallback<Object> callback = (result, error) -> {
            if (error != null) {
                errorCount.incrementAndGet();
                error.printStackTrace();
            }
            threadsDoneLatch.countDown();
        };
        for (int i = 0; i < countThreads; i++) {
            store.callInTxAsync(callable, callback);
        }
        assertTrue(threadsDoneLatch.await(runExtensiveTests ? 120 : 5, TimeUnit.SECONDS));
        assertEquals(countThreads * countEntities, number.get());
        assertEquals(countThreads * countEntities, box.count());
        assertEquals(0, errorCount.get());
    }

    @Test
    public void testCallInTxAsync_Error() throws InterruptedException {
        Callable<Object> callable = () -> {
            TestEntity entity = new TestEntity();
            entity.setId(-1);
            getTestEntityBox().put(entity);
            return null;
        };
        final LinkedBlockingQueue<Throwable> queue = new LinkedBlockingQueue<>();
        TxCallback<Object> callback = (result, error) -> queue.add(error);
        store.callInTxAsync(callable, callback);

        Throwable result = queue.poll(5, TimeUnit.SECONDS);
        assertNotNull(result);
    }

    @Test
    public void transaction_unboundedThreadPool() throws Exception {
        runThreadPoolReaderTest(
                () -> {
                    Transaction tx = store.beginReadTx();
                    tx.close();
                }
        );
    }

    @Test
    public void runInReadTx_unboundedThreadPool() throws Exception {
        runThreadPoolReaderTest(
                () -> store.runInReadTx(() -> {
                })
        );
    }

    @Test
    public void callInReadTx_unboundedThreadPool() throws Exception {
        runThreadPoolReaderTest(
                () -> store.callInReadTx(() -> 1)
        );
    }

    @Test
    public void boxReader_unboundedThreadPool() throws Exception {
        runThreadPoolReaderTest(
                () -> {
                    store.boxFor(TestEntity.class).count();
                    store.closeThreadResources();
                }
        );
    }

    /**
     * Tests that a reader is available again after a transaction is closed on a thread.
     * To not exceed max readers this test simply does not allow any two threads
     * to have an active transaction at the same time, e.g. there should always be only one active reader.
     */
    private void runThreadPoolReaderTest(Runnable runnable) throws Exception {
        // Replace default store: transaction logging disabled and specific max readers.
        tearDown();
        store = createBoxStoreBuilder(null)
                .maxReaders(100)
                .debugFlags(0)
                .noReaderThreadLocals()  // This is the essential flag to make this test work
                .build();

        // Unbounded (but throttled) thread pool so number of threads run exceeds max readers.
        int numThreads = TestUtils.isWindows() ? 300 : 1000; // Less on Windows; had some resource issues on Windows CI
        ExecutorService pool = Executors.newCachedThreadPool();

        ArrayList<Future<Integer>> txTasks = new ArrayList<>(10000);
        final Object lock = new Object();
        for (int i = 0; i < 10000; i++) {
            final int txNumber = i;
            txTasks.add(pool.submit(() -> {
                // Lock to ensure no two threads have an active transaction at the same time.
                synchronized (lock) {
                    runnable.run();
                    return txNumber;
                }
            }));
            if (pool instanceof ThreadPoolExecutor && ((ThreadPoolExecutor) pool).getActiveCount() > numThreads) {
                Thread.sleep(1); // Throttle processing to limit thread resources
            }
        }

        //Iterate through all the txTasks and make sure all transactions succeeded.
        for (Future<Integer> txTask : txTasks) {
            txTask.get(1, TimeUnit.MINUTES);  // 1s would be enough for normally, but use 1 min to allow debug sessions
        }
    }
}
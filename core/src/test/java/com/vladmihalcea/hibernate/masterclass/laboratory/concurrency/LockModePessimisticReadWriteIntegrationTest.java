package com.vladmihalcea.hibernate.masterclass.laboratory.concurrency;

import com.vladmihalcea.hibernate.masterclass.laboratory.util.AbstractIntegrationTest;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.Session;
import org.hibernate.StaleObjectStateException;
import org.junit.Before;
import org.junit.Test;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;


/**
 * LockModePessimisticReadWriteIntegrationTest - Test to check LockMode.PESSIMISTIC_READ and LockMode.PESSIMISTIC_WRITE
 *
 * @author Vlad Mihalcea
 */
public class LockModePessimisticReadWriteIntegrationTest extends AbstractIntegrationTest {

    public static final int WAIT_MILLIS = 500;

    private static interface ProductLockRequestCallable {
        void lock(Session session, Product product);
    }

    private final CountDownLatch endLatch = new CountDownLatch(1);

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                Product.class
        };
    }

    @Before
    public void init() {
        super.init();
        doInTransaction(session -> {
            Product product = new Product();
            product.setId(1L);
            product.setDescription("USB Flash Drive");
            product.setPrice(BigDecimal.valueOf(12.99));
            session.persist(product);
            return null;
        });
    }

    private void testPessimisticLocking(ProductLockRequestCallable aliceLockRequestCallable, ProductLockRequestCallable bobProductLockRequestCallable) {
        doInTransaction(session -> {
            try {
                Product product = (Product) session.get(Product.class, 1L);
                aliceLockRequestCallable.lock(session, product);

                executeAsync(
                        () -> {
                            doInTransaction(_session -> {
                                Product _product = (Product) _session.get(Product.class, 1L);
                                bobProductLockRequestCallable.lock(_session, _product);
                                return null;
                            });
                            return null;
                        },
                        () -> {
                            endLatch.countDown();
                            return null;
                        }
                );
                sleep(WAIT_MILLIS);
            } catch (StaleObjectStateException expected) {
                LOGGER.info("Failure: ", expected);
            }
            return null;
        });
        awaitOnLatch(endLatch);
    }

    @Test
    public void testPessimisticReadDoesNotBlockPessimisticRead() throws InterruptedException {
        LOGGER.info("Test testPessimisticReadDoesNotBlockPessimisticRead");
        testPessimisticLocking(
                (session, product) -> {
                    session.buildLockRequest(new LockOptions(LockMode.PESSIMISTIC_READ)).lock(product);
                    LOGGER.info("PESSIMISTIC_READ acquired");
                },
                (session, product) -> {
                    session.buildLockRequest(new LockOptions(LockMode.PESSIMISTIC_READ)).lock(product);
                    LOGGER.info("PESSIMISTIC_READ acquired");
                }
        );
    }

    @Test
    public void testPessimisticReadBlocksPessimisticWrite() throws InterruptedException {
        LOGGER.info("Test PessimisticReadBlocksPessimisticWrite");
        testPessimisticLocking(
                (session, product) -> {
                    session.buildLockRequest(new LockOptions(LockMode.PESSIMISTIC_READ)).lock(product);
                    LOGGER.info("PESSIMISTIC_READ acquired");
                },
                (session, product) -> {
                    session.buildLockRequest(new LockOptions(LockMode.PESSIMISTIC_WRITE)).lock(product);
                    LOGGER.info("PESSIMISTIC_WRITE acquired");
                }
        );
    }

    @Test
    public void testPessimisticReadBlocksUpdate() throws InterruptedException {
        LOGGER.info("Test testPessimisticReadBlocksUpdate");
        testPessimisticLocking(
                (session, product) -> {
                    session.buildLockRequest(new LockOptions(LockMode.PESSIMISTIC_READ)).lock(product);
                    LOGGER.info("PESSIMISTIC_READ acquired");
                },
                (session, product) -> {
                    product.setDescription("USB Flash Memory Stick");
                    session.flush();
                    LOGGER.info("Implicit lock acquired");
                }
        );
    }

    @Test
    public void testPessimisticReadWithPessimisticWriteNoWait() throws InterruptedException {
        LOGGER.info("Test PessimisticReadWithPessimisticWriteNoWait");
        testPessimisticLocking(
                (session, product) -> {
                    session.buildLockRequest(new LockOptions(LockMode.PESSIMISTIC_READ)).lock(product);
                    LOGGER.info("PESSIMISTIC_READ acquired");
                },
                (session, product) -> {
                    session.buildLockRequest(new LockOptions(LockMode.PESSIMISTIC_WRITE)).setTimeOut(Session.LockRequest.PESSIMISTIC_NO_WAIT).lock(product);
                    LOGGER.info("PESSIMISTIC_WRITE acquired");
                }
        );
    }

    @Test
    public void testPessimisticWriteBlocksPessimisticWrite() throws InterruptedException {
        LOGGER.info("Test testPessimisticWriteBlocksPessimisticWrite");
        testPessimisticLocking(
                (session, product) -> {
                    session.buildLockRequest(new LockOptions(LockMode.PESSIMISTIC_WRITE)).lock(product);
                    LOGGER.info("PESSIMISTIC_WRITE acquired");
                },
                (session, product) -> {
                    session.buildLockRequest(new LockOptions(LockMode.PESSIMISTIC_WRITE)).setTimeOut(Session.LockRequest.PESSIMISTIC_NO_WAIT).lock(product);
                    LOGGER.info("PESSIMISTIC_WRITE acquired");
                }
        );
    }

    /**
     * Product - Product
     *
     * @author Vlad Mihalcea
     */
    @Entity(name = "Product")
    @Table(name = "product")
    public static class Product {

        @Id
        private Long id;

        private String description;

        private BigDecimal price;

        @Version
        private int version;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }
    }
}

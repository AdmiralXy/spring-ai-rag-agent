package io.github.admiralxy.agent.service.impl;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * Transaction manager without a resource: keeps transaction synchronization (commit/rollback callbacks) working in unit tests.
 */
class NoOpTransactionManager extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        // no resource to bind
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        // nothing to commit
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        // nothing to roll back
    }
}

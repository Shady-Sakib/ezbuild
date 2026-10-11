package ezbuild.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * Auditing and transactions. Multi-document transactions need a replica set (Atlas or the docker-compose
 * rs0). MONGO_TRANSACTIONS=false swaps in a no-op manager for a standalone mongod; stock stays safe through
 * atomic single-document updates plus compensation.
 */
@Slf4j
@Configuration
@EnableMongoAuditing
@EnableTransactionManagement
public class MongoConfig {

    @Bean
    public PlatformTransactionManager transactionManager(MongoDatabaseFactory factory, AppProperties props) {
        if (props.mongoTransactions()) {
            log.info("MongoDB multi-document transactions: ON (replica set required)");
            return new MongoTransactionManager(factory);
        }
        log.warn("MongoDB multi-document transactions: OFF (MONGO_TRANSACTIONS=false)");
        return new NoTransactionManager();
    }

    /** Only used when transactions are off. */
    static class NoTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) { }

        @Override
        protected void doCommit(DefaultTransactionStatus status) { }

        @Override
        protected void doRollback(DefaultTransactionStatus status) { }
    }
}

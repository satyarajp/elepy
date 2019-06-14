package com.elepy.tests.basic.slow;

import com.elepy.tests.basic.BasicEndToEndTest;
import com.elepy.tests.config.DatabaseConfigurations;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class PostgreSQLEndToEndTest extends BasicEndToEndTest {

    @Container
    private static final JdbcDatabaseContainer CONTAINER = new PostgreSQLContainer();

    public PostgreSQLEndToEndTest() {
        super(DatabaseConfigurations.createTestContainerConfiguration(
                CONTAINER,
                "org.hibernate.dialect.PostgreSQLDialect"
        ));
    }
}
package com.learn.liquibase.config;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class LiquibaseConfig {
    private final DataSource myDataSource;

    private void syncIfNecessary() {
        // Prior to version 2.3, the bag database generated its DB tables via
        // the Hibernate mappings.  This was kind of bad.  In version 2.3 it
        // uses Liquibase to handle database schema versioning.
        // This function checks to see if we have an old database that was
        // generated without Liquibase, then sets it up properly if we do.
        try (Connection conn = myDataSource.getConnection()) {
            DatabaseMetaData md = conn.getMetaData();
            boolean missingChangelog = !doesTableExist(md, "databasechangelog");
            log.debug("databasechangelog table " + (missingChangelog ? "doesn't exist." : "exists."));
            boolean bagTableExists = doesTableExist(md, "bags");
            log.debug("bags table " + (bagTableExists ? "exists." : "doesn't exist."));

            if (missingChangelog && bagTableExists) {
                log.info("Synchronizing existing database with Liquibase schema.");
                Database db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
                Liquibase lb = new Liquibase("db/changelog/db.changelog-1.0.yaml",
                        new ClassLoaderResourceAccessor(),
                        db);
                lb.changeLogSync("");
            }
        }
        catch (SQLException | LiquibaseException e) {
            log.error("Error checking database version", e);
        }
    }

    private boolean doesTableExist(DatabaseMetaData md, String tablename) throws SQLException {
        boolean tableExists = false;
        try (ResultSet rs = md.getTables(null, null, tablename.toUpperCase(), null)) {
            if (rs.next()) {
                tableExists = true;
            }
        }
        if (!tableExists) {
            try (ResultSet rs = md.getTables(null, null, tablename.toLowerCase(), null)) {
                if (rs.next()) {
                    tableExists = true;
                }
            }
        }
        return tableExists;
    }

    @Bean
    public SpringLiquibase liquibase() {
        log.info("Initializing Liquibase.");
        syncIfNecessary();
        SpringLiquibase lb = new SpringLiquibase();
        lb.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        lb.setDataSource(myDataSource);
        Map<String, String> params = new HashMap<>();
        params.put("verbose", "true");
        lb.setChangeLogParameters(params);
        lb.setShouldRun(true);
        return lb;
    }
}
package com.thenetworkplan.networkplan.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

/**
 * Affiche au démarrage la base réellement atteinte, pour vérifier d'un coup
 * d'œil que Spring Boot est bien branché sur PostgreSQL.
 */
@Component
public class DatabaseStartupCheck implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseStartupCheck.class);

    private final DataSource dataSource;

    public DatabaseStartupCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            log.info("=================================================");
            log.info(" Base de donnees connectee");
            log.info("   URL      : {}", meta.getURL());
            log.info("   Produit  : {} {}", meta.getDatabaseProductName(), meta.getDatabaseProductVersion());
            log.info("   Driver   : {} {}", meta.getDriverName(), meta.getDriverVersion());
            log.info("   Login    : {}", meta.getUserName());
            log.info("=================================================");
        }
    }
}

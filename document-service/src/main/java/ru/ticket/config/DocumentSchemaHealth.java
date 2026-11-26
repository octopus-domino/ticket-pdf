package ru.ticket.config;

import javax.sql.DataSource;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("documentSchema")
final class DocumentSchemaHealth implements HealthIndicator {
  private final JdbcTemplate jdbc;

  DocumentSchemaHealth(DataSource dataSource) {
    jdbc = new JdbcTemplate(dataSource);
    jdbc.setQueryTimeout(2);
  }

  @Override
  public Health health() {
    try {
      for (String table : new String[]{"users", "documents", "templates", "data_models",
          "data_schemas", "resource_revisions", "public_journeys", "journey_media"}) {
        jdbc.queryForList("SELECT id FROM " + table + " WHERE 1 = 0");
      }
      return Health.up().build();
    } catch (DataAccessException failure) {
      return Health.down().build();
    }
  }
}

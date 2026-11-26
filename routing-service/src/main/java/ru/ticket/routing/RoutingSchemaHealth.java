package ru.ticket.routing;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("routingSchema")
final class RoutingSchemaHealth implements HealthIndicator {
  private final JdbcTemplate jdbc;
  private final String schemaPrefix;

  RoutingSchemaHealth(DataSource dataSource,
      @Value("${spring.jpa.properties.hibernate.default_schema:}") String schema) {
    if (!schema.isEmpty() && !schema.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      throw new IllegalArgumentException("Invalid Routing schema identifier");
    }
    schemaPrefix = schema.isEmpty() ? "" : schema + ".";
    jdbc = new JdbcTemplate(dataSource);
    jdbc.setQueryTimeout(2);
  }

  @Override
  public Health health() {
    try {
      for (String table : new String[]{"journey_trip_drafts", "mystic_places", "building_history"}) {
        jdbc.queryForList("SELECT id FROM " + schemaPrefix + table + " WHERE 1 = 0");
      }
      return Health.up().build();
    } catch (DataAccessException failure) {
      return Health.down().build();
    }
  }
}

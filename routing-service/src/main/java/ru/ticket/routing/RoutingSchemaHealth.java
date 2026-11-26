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
      jdbc.queryForList("SELECT owner_id, trip_id, payload FROM " + schemaPrefix + "journey_trip_drafts WHERE 1 = 0");
      jdbc.queryForList("SELECT id FROM " + schemaPrefix + "mystic_places WHERE 1 = 0");
      jdbc.queryForList("SELECT id FROM " + schemaPrefix + "building_history WHERE 1 = 0");
      return Health.up().build();
    } catch (DataAccessException failure) {
      return Health.down().build();
    }
  }
}

package ru.ticket.workflow;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class AddressSchemaTest {
 @Test void upgradeCanBeRepeatedAndReservesExistingSitesAndWorkflows() throws Exception {
  try(var connection=java.sql.DriverManager.getConnection("jdbc:h2:mem:workflow-schema")){
   connection.createStatement().execute("CREATE TABLE journey_sites(slug VARCHAR(48) PRIMARY KEY)");
   connection.createStatement().execute("INSERT INTO journey_sites VALUES('existing-site')");
   var script=new ClassPathResource("db/upgrade-workflows.sql");
   ScriptUtils.executeSqlScript(connection,script);
   connection.createStatement().execute("INSERT INTO workflows(slug,owner_id,draft,updated_at) VALUES('existing-workflow','owner','{}',CURRENT_TIMESTAMP)");
   ScriptUtils.executeSqlScript(connection,script);ScriptUtils.executeSqlScript(connection,script);
   try(var rows=connection.createStatement().executeQuery("SELECT COUNT(*) FROM site_addresses")){rows.next();assertThat(rows.getInt(1)).isEqualTo(2);}
  }
 }
}

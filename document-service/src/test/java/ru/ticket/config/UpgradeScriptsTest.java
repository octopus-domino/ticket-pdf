package ru.ticket.config;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class UpgradeScriptsTest {
  @Test void additiveUpgradesCanRunTwiceAndPreserveExistingBusinessRows() throws Exception {
    try(var connection=DriverManager.getConnection("jdbc:h2:mem:migration-test")){
      connection.createStatement().execute("CREATE TABLE templates(id VARCHAR(36) PRIMARY KEY, name VARCHAR(200))");
      connection.createStatement().execute("INSERT INTO templates VALUES('existing','Preserved')");
      for(int run=0;run<2;run++)for(String script:new String[]{"email-ledger","template-folders","client-api","connectors","history","libraries","profile"})ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/upgrade-"+script+".sql"));
      try(var rows=connection.createStatement().executeQuery("SELECT name,folder_id FROM templates WHERE id='existing'")){assertTrue(rows.next());assertEquals("Preserved",rows.getString(1));assertNull(rows.getString(2));}
      for(String table:new String[]{"email_deliveries","api_client_keys","source_connections","resource_revisions","asset_libraries"})try(var rows=connection.createStatement().executeQuery("SELECT COUNT(*) FROM "+table)){assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
    }
  }
}

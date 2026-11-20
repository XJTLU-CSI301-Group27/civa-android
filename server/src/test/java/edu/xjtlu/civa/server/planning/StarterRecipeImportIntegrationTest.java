package edu.xjtlu.civa.server.planning;

import static org.junit.jupiter.api.Assertions.*;

import edu.xjtlu.civa.server.CivaServer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = CivaServer.class)
class StarterRecipeImportIntegrationTest {
  @Autowired JdbcTemplate jdbc;

  @Test
  void explicitImportIsRepeatablePreservesEditsAndRequiresActiveOwner() throws Exception {
    UUID actor = UUID.randomUUID(), outsider = UUID.randomUUID(), home = UUID.randomUUID();
    try {
      for (UUID id : new UUID[] {actor, outsider})
        jdbc.update(
            "INSERT INTO users(id,username,display_name,password_hash,zone_id) VALUES"
                + " (?,?,?,'unused','UTC')",
            id,
            "s" + id.toString().replace("-", "").substring(0, 20),
            "Starter test");
      jdbc.update("INSERT INTO households(id,name) VALUES (?,'Starter isolated test')", home);
      jdbc.update(
          "INSERT INTO memberships(id,household_id,user_id,role,active) VALUES"
              + " (?,?,?,'OWNER',true)",
          UUID.randomUUID(),
          home,
          actor);
      assertNotEquals(0, importRecipes(home, outsider));
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT count(*) FROM recipes WHERE household_id=?", Integer.class, home));
      assertEquals(0, importRecipes(home, actor));
      assertEquals(
          6,
          jdbc.queryForObject(
              "SELECT count(*) FROM recipes WHERE household_id=?", Integer.class, home));
      UUID recipe =
          jdbc.queryForObject(
              "SELECT id FROM recipes WHERE household_id=? ORDER BY id LIMIT 1", UUID.class, home);
      jdbc.update("UPDATE recipes SET name='Owner edited title' WHERE id=?", recipe);
      jdbc.update("DELETE FROM recipe_ingredients WHERE recipe_id=?", recipe);
      assertEquals(0, importRecipes(home, actor));
      assertEquals(
          "Owner edited title",
          jdbc.queryForObject("SELECT name FROM recipes WHERE id=?", String.class, recipe));
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT count(*) FROM recipe_ingredients WHERE recipe_id=?", Integer.class, recipe));
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT count(*) FROM meal_entries WHERE user_id=?", Integer.class, actor));
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT count(*) FROM cooking_events WHERE household_id=?", Integer.class, home));
    } finally {
      jdbc.update(
          "DELETE FROM recipe_ingredients WHERE recipe_id IN (SELECT id FROM recipes WHERE"
              + " household_id=?)",
          home);
      jdbc.update("DELETE FROM recipes WHERE household_id=?", home);
      jdbc.update("DELETE FROM memberships WHERE household_id=?", home);
      jdbc.update("DELETE FROM households WHERE id=?", home);
      jdbc.update("DELETE FROM users WHERE id IN (?,?)", actor, outsider);
    }
  }

  private int importRecipes(UUID home, UUID actor) throws Exception {
    Path script = Path.of("../scripts/bd_import_recipes.py").toAbsolutePath();
    Process process =
        new ProcessBuilder(
                "python",
                script.toString(),
                "--household",
                home.toString(),
                "--actor",
                actor.toString())
            .redirectErrorStream(true)
            .start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
      process.destroyForcibly();
      fail("Import timed out");
    }
    if (process.exitValue() != 0 && output.contains("Traceback"))
      fail("Import tooling failed: " + output.substring(0, Math.min(200, output.length())));
    return process.exitValue();
  }
}

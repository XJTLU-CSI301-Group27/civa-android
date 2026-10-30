package edu.xjtlu.civa.server.testing;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import java.util.stream.StreamSupport;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class SafeFlywayTestConfigurationTest {
    @Test void registersGuardAsTestAutoConfiguration() {
        var candidates = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader());
        assertTrue(StreamSupport.stream(candidates.spliterator(), false)
                .anyMatch(SafeFlywayTestConfiguration.class.getName()::equals));
    }

    @Test void rejectsTheWrongActualDatabaseBeforeMigration() throws Exception {
        Flyway flyway = mock(Flyway.class);
        Configuration configuration = mock(Configuration.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet result = mock(ResultSet.class);
        when(flyway.getConfiguration()).thenReturn(configuration);
        when(configuration.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT current_database()")).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getString(1)).thenReturn("production");
        assertThrows(IllegalStateException.class, () -> SafeFlywayTestConfiguration.verifyAndMigrate(flyway, "civa_test"));
        verify(flyway, never()).migrate();
        verify(connection).close();
    }

    @Test void allowsMigrationOnlyAfterCheckingTheMatchingTestDatabase() throws Exception {
        Flyway flyway = mock(Flyway.class);
        Configuration configuration = mock(Configuration.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet result = mock(ResultSet.class);
        when(flyway.getConfiguration()).thenReturn(configuration);
        when(configuration.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT current_database()")).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getString(1)).thenReturn("civa_test");
        SafeFlywayTestConfiguration.verifyAndMigrate(flyway, "civa_test");
        var order = inOrder(connection, flyway);
        order.verify(connection).close();
        order.verify(flyway).migrate();
    }
}

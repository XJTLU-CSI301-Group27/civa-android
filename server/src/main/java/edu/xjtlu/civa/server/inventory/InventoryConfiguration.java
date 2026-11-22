package edu.xjtlu.civa.server.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.xjtlu.civa.server.application.IdempotencyExecutor;
import edu.xjtlu.civa.server.config.JsonbCodec;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
class InventoryConfiguration {
  @Bean
  InventoryService inventoryService(
      JdbcTemplate jdbc,
      PlatformTransactionManager manager,
      IdempotencyExecutor executor,
      ObjectMapper json,
      JsonbCodec jsonb) {
    return new InventoryService(jdbc, manager, executor, json, jsonb, Clock.systemUTC());
  }

  @Bean
  InventoryMatchingService inventoryMatchingService(InventoryService inventory) {
    return new InventoryMatchingService(inventory);
  }
}

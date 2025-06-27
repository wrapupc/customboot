package framework.autoconfiguration;

import framework.config.DataSourceConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatasourceAutoConfiguration {

    @Bean
    @ConditionalOnProperty(value = "yunti.jdbc-scan", havingValue = "true", matchIfMissing = true)
    static DataSourceConfigurer databasesConfigurer() {
        return new DataSourceConfigurer();
    }
}

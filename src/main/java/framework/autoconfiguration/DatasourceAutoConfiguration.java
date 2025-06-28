package framework.autoconfiguration;

import framework.config.DataSourceConfigurer;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration
public class DatasourceAutoConfiguration {

    @Bean
    static DataSourceConfigurer databasesConfigurer() {
        return new DataSourceConfigurer();
    }
}

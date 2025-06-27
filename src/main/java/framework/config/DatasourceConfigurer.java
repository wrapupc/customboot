package framework.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import lombok.Data;
import lombok.val;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.*;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DatasourceConfigurer implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, ApplicationContextAware {
    private Environment env;
    private ApplicationContext applicationContext;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        final String jdbcPropertyPrefix = "spring.datasource";

        Map<String, JdbcProperty> jdbcPropertyMap = Binder.get(env)
                .bind(jdbcPropertyPrefix, Bindable.mapOf(String.class, JdbcProperty.class))
                .orElseGet(Collections::emptyMap);

        jdbcPropertyMap = jdbcPropertyMap.entrySet().stream().filter(item -> item.getValue().isEnabled()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (jdbcPropertyMap.isEmpty()) {
            return;
        }

        final String druidPropertyPrefix = "spring.datasource.druid";

        Properties druidProperties = Binder.get(env)
                .bind(druidPropertyPrefix, Bindable.of(Properties.class))
                .orElse(new Properties());

        jdbcPropertyMap.forEach((dbName, property) -> {
            AutowireCandidateQualifier qualifier = new AutowireCandidateQualifier(Qualifier.class, dbName);

            Consumer<AbstractBeanDefinition> bdConsumer = (bd) -> {
                bd.addQualifier(qualifier);
            };

            /* register DataSource.class*/
            String dsName = dbName + "DataSource";
            if (registry.containsBeanDefinition(dsName)) {
                AbstractBeanDefinition beanDefinition = (AbstractBeanDefinition) registry.getBeanDefinition(dsName);
                bdConsumer.accept(beanDefinition);
            } else {
                registerBean(registry, DruidDataSource.class, dsName, b -> {
                }, bdConsumer, bd -> bd.setInstanceSupplier(() -> {
                    DruidDataSource bean = DruidDataSourceBuilder.create().build();
                    bean.configFromPropeties(druidProperties);
                    Bindable<DruidDataSource> bindable =
                            Bindable.of(DruidDataSource.class).withExistingValue(bean);
                    return Binder.get(env).bind(jdbcPropertyPrefix + "." + dbName, bindable).get();
                }));
            }

            /* register TransactionManager.class*/
            registerBean(registry,
                    DataSourceTransactionManager.class,
                    dbName + "TransactionManager",
                    b -> b.addConstructorArgReference(dsName),
                    bdConsumer);


            /* register TransactionTemplate.class*/
            registerBean(registry,
                    TransactionTemplate.class,
                    dbName + "TransactionTemplate",
                    b -> b.addConstructorArgReference(dbName + "TransactionManager"),
                    bdConsumer);

            /* register JdbcTemplate.class*/
            registerBean(registry,
                    JdbcTemplate.class,
                    dbName + "JdbcTemplate",
                    b -> b.addConstructorArgReference(dsName),
                    bdConsumer);

            /* configure mybatis bean*/
            {
                registerBean(registry,
                        SqlSessionFactoryBean.class,
                        dbName + "sqlSessionFactory");
            }

        });

    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {
        System.out.println();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }

    @Data
    public static class JdbcProperty {
        private boolean enabled = true;
        private boolean primary = false;
        private String url;
    }

    private void registerBean(BeanDefinitionRegistry registry,
                              Class<?> beanClass, String beanName,
                              Consumer<BeanDefinitionBuilder> customizeBuilder,
                              Consumer<AbstractBeanDefinition>... customizeDefinitions) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(beanClass);
        if (customizeBuilder != null) {
            customizeBuilder.accept(builder);
        }
        builder.applyCustomizers(bd -> {
            AbstractBeanDefinition b = (AbstractBeanDefinition) bd;
            for (val c : customizeDefinitions) {
                c.accept(b);
            }
        });
        registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
    }
}

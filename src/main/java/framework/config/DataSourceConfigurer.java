package framework.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.boot.autoconfigure.SpringBootVFS;
import org.mybatis.spring.mapper.ClassPathMapperScanner;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.*;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
public class DataSourceConfigurer implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, ApplicationContextAware {
    private Environment env;
    private ApplicationContext applicationContext;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        List<String> packages = AutoConfigurationPackages.get(this.applicationContext);
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
            final String beanNamePrefix = getBeanNamePrefix(dbName, property);

            final AutowireCandidateQualifier qualifier = new AutowireCandidateQualifier(Qualifier.class, beanNamePrefix);

            final Consumer<AbstractBeanDefinition> bdConsumer = (bd) -> {
                bd.addQualifier(qualifier);
            };

            /* register DataSource.class*/
            final String dsName = beanNamePrefix + "DataSource";

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
            String txManagerName = beanNamePrefix + "TransactionManager";
            if (registry.containsBeanDefinition(txManagerName)) {
                AbstractBeanDefinition beanDefinition = (AbstractBeanDefinition) registry.getBeanDefinition(txManagerName);
                bdConsumer.accept(beanDefinition);
            } else {
                registerBean(registry,
                        DataSourceTransactionManager.class,
                        txManagerName,
                        b -> b.addConstructorArgReference(dsName),
                        bdConsumer);
            }


            /* register TransactionTemplate.class*/
            String txTemplateName = beanNamePrefix + "TransactionTemplate";
            if (registry.containsBeanDefinition(txTemplateName)) {
                AbstractBeanDefinition beanDefinition = (AbstractBeanDefinition) registry.getBeanDefinition(txTemplateName);
                bdConsumer.accept(beanDefinition);
            } else {
                registerBean(registry,
                        TransactionTemplate.class,
                        txTemplateName,
                        b -> b.addConstructorArgReference(txManagerName),
                        bdConsumer);
            }

            /* register JdbcTemplate.class*/
//            registerBean(registry,
//                    JdbcTemplate.class,
//                    beanNamePrefix + "JdbcTemplate",
//                    b -> b.addConstructorArgReference(dsName),
//                    bdConsumer);

            /* configure mybatis bean*/
            {
                /* register SqlSessionFactory.class*/
                String sqlSessionFactoryName = beanNamePrefix + "sqlSessionFactory";
                if (registry.containsBeanDefinition(sqlSessionFactoryName)) {
                    AbstractBeanDefinition beanDefinition = (AbstractBeanDefinition) registry.getBeanDefinition(sqlSessionFactoryName);
                    bdConsumer.accept(beanDefinition);
                } else {
                    registerBean(registry,
                            SqlSessionFactoryBean.class,
                            sqlSessionFactoryName,
                            bd -> {
                                bd.addPropertyReference("dataSource", dsName);
                                bd.addPropertyValue("vfs", SpringBootVFS.class);

//                            String configPattern = "classpath:/" + dbName + "-mybatis-config.xml";
//                            Resource configLocation = null;
//                            try {
//                                Resource[] configLocations = applicationContext.getResources(configPattern);
//                                if (configLocations.length >= 1) {
//                                    configLocation = configLocations[0];
//                                }
//                            } catch (IOException e) {
//                                log.error(e.getMessage());
//                            }


//                            if (configLocation != null) {
//                                bd.addPropertyValue("configLocation", configLocation);
//                            }


                                String locationPattern = "classpath:/mapper/" + dbName + "/*.xml";
                                Resource[] locations = null;
                                try {
                                    locations = applicationContext.getResources(locationPattern);
                                } catch (IOException e) {
                                    log.debug(e.getMessage());
                                }
                                if (locations != null) {
                                    bd.addPropertyValue("mapperLocations", locations);
                                }
                            },
                            bdConsumer);
                }
            }

            /* register SqlSessionTemplate.class*/
//            registerBean(registry,
//                    SqlSessionTemplate.class,
//                    beanNamePrefix + "SqlSessionTemplate",
//                    bd -> bd.addConstructorArgReference(beanNamePrefix + "SqlSessionFactory"),
//                    bdConsumer);

            /* configure MapperScanner */
            ClassPathMapperScanner scanner = new ClassPathMapperScanner(registry) {
                @Override
                protected void postProcessBeanDefinition(AbstractBeanDefinition beanDefinition, String beanName) {
                    super.postProcessBeanDefinition(beanDefinition, beanName);
                    bdConsumer.accept(beanDefinition);
                }
            };
            scanner.setAnnotationClass(Mapper.class);
//            scanner.setSqlSessionTemplateBeanName(beanNamePrefix + "SqlSessionTemplate");
            scanner.setSqlSessionFactoryBeanName(beanNamePrefix + "SqlSessionFactory");
            scanner.registerFilters();

            scanner.addExcludeFilter((reader, metadataReaderFactory) -> {
                AnnotationMetadata metadata = reader.getAnnotationMetadata();
                Map<String, Object> annotationAttributes = metadata.getAnnotationAttributes(Qualifier.class.getCanonicalName(), true);
                if (annotationAttributes == null) {
                    return true;
                }
                return !Objects.equals(annotationAttributes.get("value"), beanNamePrefix);
            });

            scanner.scan(packages.toArray(new String[0]));
        });

    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
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

    private static String getBeanNamePrefix(String dbName, JdbcProperty property) {
        if (!property.isEnableDynamicDataSource()) {
            return dbName;
        }
        String dynamicDataSourceGroupName = property.getDynamicDataSourceGroupName();
        if (dynamicDataSourceGroupName == null || dynamicDataSourceGroupName.isEmpty()) {
            return dbName;
        }
        return dynamicDataSourceGroupName;
    }


    @Data
    public static class JdbcProperty {
        private boolean enabled = true;
        private boolean primary = false;
        private String url;

        private boolean enableDynamicDataSource = true;
        private String dynamicDataSourceGroupName;
    }
}

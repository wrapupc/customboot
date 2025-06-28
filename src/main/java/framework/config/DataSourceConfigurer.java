package framework.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import com.clls.customboot.config.DynamicDataSource;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.mapper.ClassPathMapperScanner;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
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
        final String jdbcPropertyPrefix = "spring.datasource.enhance";

        Map<String, DataSourceProperty> dataSourcePropertyMap = Binder.get(env)
                .bind(jdbcPropertyPrefix, Bindable.mapOf(String.class, DataSourceProperty.class))
                .orElseGet(Collections::emptyMap);

        dataSourcePropertyMap = dataSourcePropertyMap.entrySet().stream().filter(item -> item.getValue().isEnabled()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (dataSourcePropertyMap.isEmpty()) {
            return;
        }

        final String druidPropertyPrefix = "spring.datasource.druid";

        Properties druidProperties = Binder.get(env)
                .bind(druidPropertyPrefix, Bindable.of(Properties.class))
                .orElse(new Properties());

        Map<String, List<Map.Entry<String, DataSourceProperty>>> dynamicDataSourcePropertyMap = dataSourcePropertyMap.entrySet().stream().filter(entry -> entry.getValue().isEnableDynamicDataSource())
                .collect(Collectors.groupingBy(item -> item.getValue().getDynamicDataSourceGroupName(), Collectors.toList()));

        dynamicDataSourcePropertyMap.forEach((dsPrefix, propertyList) -> {
            final String dsName = dsPrefix + "DataSource";

            final AutowireCandidateQualifier qualifier = new AutowireCandidateQualifier(Qualifier.class, dsPrefix);
            final Consumer<AbstractBeanDefinition> bdConsumer = (bd) -> {
                bd.addQualifier(qualifier);
            };

            /* register normal datasource for dynamic datasource */
            propertyList.forEach((dsPropertyEntry) -> {
                final String dbName = dsPropertyEntry.getKey();
                final String normalDsName = dbName + "DataSource";

                if (registry.containsBeanDefinition(normalDsName)) {
                    AbstractBeanDefinition beanDefinition = (AbstractBeanDefinition) registry.getBeanDefinition(normalDsName);
                    bdConsumer.accept(beanDefinition);
                } else {
                    registerBean(registry, DataSource.class, normalDsName, b -> {
                    }, bdConsumer, bd -> bd.setInstanceSupplier(() -> {
                        DruidDataSource bean = DruidDataSourceBuilder.create().build();
                        bean.configFromPropeties(druidProperties);
                        Bindable<DruidDataSource> bindable =
                                Bindable.of(DruidDataSource.class).withExistingValue(bean);
                        return Binder.get(env).bind(jdbcPropertyPrefix + "." + dbName, bindable).get();
                    }));
                }
            });

            /* register dynamic datasource*/
            ManagedMap<Object, Object> targetDataSources = new ManagedMap<>();
            List<String> defaultDataSourceBeanName = new ArrayList<>();

            propertyList.forEach(dsPropertyEntry -> {
                String dbName = dsPropertyEntry.getKey();
                String normalDsName = dbName + "DataSource";

                // 直接添加 RuntimeBeanReference
                targetDataSources.put(dbName, new RuntimeBeanReference(normalDsName));

                if (defaultDataSourceBeanName.isEmpty()) {
                    defaultDataSourceBeanName.add("roDataSource"); // TODO: read default from config
                }
            });

            // 注册动态数据源Bean
            registerBean(registry, DynamicDataSource.class, dsName, b -> {
                // Spring会自动解析ManagedMap中的RuntimeBeanReference
                b.addPropertyValue("targetDataSources", targetDataSources);

                // 添加默认数据源
                b.addPropertyValue("defaultTargetDataSource",
                        new RuntimeBeanReference(defaultDataSourceBeanName.get(0)));
            }, bd -> {
                bd.addQualifier(new AutowireCandidateQualifier(Qualifier.class, dsPrefix));
            });

        });

        Map<String, List<Map.Entry<String, DataSourceProperty>>> normalDataSourcePropertyMap = dataSourcePropertyMap.entrySet().stream().filter(entry -> !entry.getValue().isEnableDynamicDataSource())
                .collect(Collectors.groupingBy(item -> item.getValue().getDynamicDataSourceGroupName(), Collectors.toList()));

        dataSourcePropertyMap.forEach((dbName, property) -> {
            /* register DataSource.class*/
            final String beanNamePrefix = getBeanNamePrefix(dbName, property);
            final String dsName = beanNamePrefix + "DataSource";

            final AutowireCandidateQualifier qualifier = new AutowireCandidateQualifier(Qualifier.class, beanNamePrefix);
            final Consumer<AbstractBeanDefinition> bdConsumer = (bd) -> {
                bd.addQualifier(qualifier);
            };

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
        });

        dataSourcePropertyMap.forEach((dbName, property) -> {
            final String beanNamePrefix = getBeanNamePrefix(dbName, property);

            final AutowireCandidateQualifier qualifier = new AutowireCandidateQualifier(Qualifier.class, beanNamePrefix);
            final Consumer<AbstractBeanDefinition> bdConsumer = (bd) -> {
                bd.addQualifier(qualifier);
            };


            final String dsName = beanNamePrefix + "DataSource";
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
//            String jdbcTemplateName = beanNamePrefix + "JdbcTemplate";
//
//            if (registry.containsBeanDefinition(jdbcTemplateName)) {
//                AbstractBeanDefinition beanDefinition = (AbstractBeanDefinition) registry.getBeanDefinition(jdbcTemplateName);
//                bdConsumer.accept(beanDefinition);
//            } else {
//                registerBean(registry,
//                        JdbcTemplate.class,
//                        jdbcTemplateName,
//                        b -> b.addConstructorArgReference(dsName),
//                        bdConsumer);
//            }

            /* configure mybatis bean*/
            {
                /* register SqlSessionFactory.class*/
                String sqlSessionFactoryName = beanNamePrefix + "SqlSessionFactory";
                if (registry.containsBeanDefinition(sqlSessionFactoryName)) {
                    AbstractBeanDefinition beanDefinition = (AbstractBeanDefinition) registry.getBeanDefinition(sqlSessionFactoryName);
                    bdConsumer.accept(beanDefinition);
                } else {
                    registerBean(registry,
                            SqlSessionFactoryBean.class,
                            sqlSessionFactoryName,
                            bd -> {
                                bd.addPropertyReference("dataSource", dsName);
//                                bd.addPropertyValue("vfs", SpringBootVFS.class);

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


//                                String locationPattern = "classpath:/mapper/" + dbName + "/*.xml";
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
//            final String sqlSessionTemplateName = beanNamePrefix + "SqlSessionTemplate";
//            if (registry.containsBeanDefinition(sqlSessionTemplateName)) {
//                AbstractBeanDefinition beanDefinition = (AbstractBeanDefinition) registry.getBeanDefinition(sqlSessionTemplateName);
//                bdConsumer.accept(beanDefinition);
//            } else {
//                registerBean(registry,
//                        SqlSessionTemplate.class,
//                        sqlSessionTemplateName,
//                        bd -> bd.addConstructorArgReference(beanNamePrefix + "SqlSessionFactory"),
//                        bdConsumer);
//            }

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

    private static String getBeanNamePrefix(String dbName, DataSourceProperty property) {
        if (!property.isEnableDynamicDataSource()) {
            return dbName;
        }
        String dynamicDataSourceGroupName = property.getDynamicDataSourceGroupName();
        if (dynamicDataSourceGroupName == null || dynamicDataSourceGroupName.isEmpty()) {
            throw new RuntimeException("must specify dynamic data source group name if enabled!");
        }
        return dynamicDataSourceGroupName;
    }


    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataSourceProperty {
        private boolean enabled = true;
        private boolean primary = false;
        private String url;

        private boolean enableDynamicDataSource = true;
        private String dynamicDataSourceGroupName;
        private boolean isDynamicDefaultDataSource = false;
    }
}

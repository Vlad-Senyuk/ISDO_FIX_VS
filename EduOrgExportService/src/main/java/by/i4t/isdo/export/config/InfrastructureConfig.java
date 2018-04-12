package by.i4t.isdo.export.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.Database;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Created by roman on 28.11.2016.
 */
@Configuration
@EnableScheduling
@EnableJpaRepositories("by.i4t.repository")
public class InfrastructureConfig {
    @Autowired
    private Environment env;

    @Bean
    public DataSource dataSource() {
        SimpleDriverDataSource driverDataSource = new SimpleDriverDataSource();
        driverDataSource.setDriverClass(org.postgresql.Driver.class);
        driverDataSource.setUrl(env.getProperty("db.url"));
        driverDataSource.setUsername(env.getProperty("db.user"));
        driverDataSource.setPassword(env.getProperty("db.pass"));
        return driverDataSource;
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setDatabase(Database.POSTGRESQL);
        vendorAdapter.setGenerateDdl(true);
        vendorAdapter.setShowSql(true);
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setJpaVendorAdapter(vendorAdapter);
        factory.setPackagesToScan("by.i4t.objects");
        factory.setDataSource(dataSource());
        Properties props = new Properties();
        props.put("hibernate.event.merge.entity_copy_observer", "allow");
        factory.setJpaProperties(props);
        factory.afterPropertiesSet();
        return factory;
    }

    @Bean
    public PlatformTransactionManager transactionManager() {
        JpaTransactionManager txManager = new JpaTransactionManager();
        txManager.setEntityManagerFactory(entityManagerFactory().getObject());
        return txManager;
    }
}


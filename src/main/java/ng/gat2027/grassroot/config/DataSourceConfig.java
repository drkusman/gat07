package ng.gat2027.grassroot.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * When DATABASE_URL is set (Railway's PostgreSQL plugin, or your local PostgreSQL) it is converted from the
 * "postgresql://user:password@host:port/db" form into a JDBC data source. Otherwise Spring Boot's default
 * (the embedded H2 database configured in application.yml) is used.
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(org.springframework.boot.jdbc.autoconfigure.DataSourceProperties props) {
        String url = System.getenv("DATABASE_URL");
        if (url == null || url.isBlank()) {
            return props.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        }
        HikariDataSource ds = new HikariDataSource();
        if (url.startsWith("jdbc:")) {
            ds.setJdbcUrl(url);
            String u = System.getenv("DATABASE_USERNAME"), p = System.getenv("DATABASE_PASSWORD");
            if (u != null) ds.setUsername(u);
            if (p != null) ds.setPassword(p);
        } else {
            URI uri = URI.create(url.replaceFirst("^postgres://", "postgresql://"));
            String[] userInfo = uri.getUserInfo() == null ? new String[0] : uri.getUserInfo().split(":", 2);
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String query = uri.getQuery() == null ? "" : "?" + uri.getQuery();
            ds.setJdbcUrl("jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath() + query);
            if (userInfo.length > 0) ds.setUsername(URLDecoder.decode(userInfo[0], StandardCharsets.UTF_8));
            if (userInfo.length > 1) ds.setPassword(URLDecoder.decode(userInfo[1], StandardCharsets.UTF_8));
        }
        ds.setMaximumPoolSize(10);
        ds.setPoolName("gat-postgres");
        return ds;
    }
}

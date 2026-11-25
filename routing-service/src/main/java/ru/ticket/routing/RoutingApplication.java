package ru.ticket.routing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.beans.factory.ObjectProvider;
import ru.ticket.routing.application.CalculateRoad;
import ru.ticket.routing.application.RoadProvider;

@SpringBootApplication(scanBasePackages = {"ru.ticket.routing", "ru.ticket.walks"})
@EntityScan({"ru.ticket.walks", "ru.ticket.routing.trip"})
@EnableJpaRepositories({"ru.ticket.walks", "ru.ticket.routing.trip"})
public class RoutingApplication {
    public static void main(String[] args) {
        SpringApplication.run(RoutingApplication.class, args);
    }

    @Bean
    CalculateRoad calculateRoad(ObjectProvider<RoadProvider> providers) {
        return new CalculateRoad(providers.orderedStream().toList());
    }
}

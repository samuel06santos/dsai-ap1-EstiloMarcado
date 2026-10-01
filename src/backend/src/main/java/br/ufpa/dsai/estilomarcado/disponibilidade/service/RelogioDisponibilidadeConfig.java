package br.ufpa.dsai.estilomarcado.disponibilidade.service;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RelogioDisponibilidadeConfig {
    @Bean
    Clock relogioDisponibilidade() {
        return Clock.systemUTC();
    }
}

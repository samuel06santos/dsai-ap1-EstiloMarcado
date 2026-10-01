package br.ufpa.dsai.estilomarcado;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EstiloMarcadoApplication {

    public static void main(String[] args) {
        SpringApplication.run(EstiloMarcadoApplication.class, args);
    }
}

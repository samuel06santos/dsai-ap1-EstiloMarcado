package br.ufpa.dsai.estilomarcado.autenticacao.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableMethodSecurity
public class SegurancaConfig {

    @Bean
    PasswordEncoder passwordEncoder(@Value("${app.auth.password-cost:12}") int custo) {
        return new BCryptPasswordEncoder(custo);
    }

    @Bean
    UserDetailsService userDetailsService() {
        return username -> { throw new UsernameNotFoundException("autenticacao por formulario desabilitada"); };
    }

    @Bean
    CookieSerializer cookieSerializer(@Value("${SESSION_COOKIE_SECURE:false}") boolean seguro) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(seguro);
        serializer.setSameSite("Lax");
        return serializer;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            SecurityContextRepository contextRepository,
                                            ObjectMapper objectMapper) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");

        AuthenticationEntryPoint entrada = (request, response, ex) ->
                RespostaSeguranca.escrever(objectMapper, response, 401, "autenticacao necessaria");
        AccessDeniedHandler negado = (request, response, ex) ->
                RespostaSeguranca.escrever(objectMapper, response, 403, "acesso negado");

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .cors(cors -> { })
                .securityContext(context -> context
                        .securityContextRepository(contextRepository)
                        .requireExplicitSave(true))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entrada)
                        .accessDeniedHandler(negado))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/autenticacao/sessao").authenticated()
                        .requestMatchers("/api/autenticacao/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/unidades/*/servicos", "/api/servicos/*").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/unidades/*/publico", "/api/unidades/*/profissionais",
                                "/api/unidades/*/profissionais/*").permitAll()
                        .requestMatchers("/api/unidades/*/usuarios-internos/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/api/unidades/*/servicos").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.PUT, "/api/servicos/*").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/servicos/**").hasRole("ADMINISTRADOR")
                        .anyRequest().authenticated())
                .logout(logout -> logout.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .addFilterBefore(new SessaoAbsolutaFilter(), AuthorizationFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.auth.allowed-origins:http://localhost:4200}") String origens) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origens.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

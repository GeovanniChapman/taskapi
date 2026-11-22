package com.example.taskapi.auth;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.example.taskapi.entity.AppUser;
import com.example.taskapi.repository.AppUserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration
public class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	UserDetailsService userDetailsService(AppUserRepository users) {
		return email -> {
			AppUser user = users.findByEmail(email)
					.orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));

			return User.withUsername(user.getId().toString()).password(user.getPasswordHash()).authorities("USER")
					.build();
		};
	}

	@Bean
	AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passworEncoder) {
		var provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passworEncoder);

		return new ProviderManager(provider);
	}

	@Bean
	SecretKey jwtKey(@Value("${app.jwt.secret}") String base64Secret) {
		byte[] keyBytes = Base64.getDecoder().decode(base64Secret);

		if (keyBytes.length < 32) {
			throw new IllegalArgumentException("JWT secret must contain at least 32 random bytes");
		}

		return new SecretKeySpec(keyBytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtKey, @Value("${app.jwt.issuer}") String issuer,
			@Value("${app.jwt.audience}") String audience) {

		var decoder = NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();

		OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(audience)
				? OAuth2TokenValidatorResult.success()
				: invalidToken("Invalid audience");

		OAuth2TokenValidator<Jwt> subjectValidator = jwt -> {
			try {
				UUID.fromString(jwt.getSubject());
				return OAuth2TokenValidatorResult.success();
			} catch (IllegalArgumentException | NullPointerException ex) {
				return invalidToken("Invalid subject");
			}
		};

		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),
				audienceValidator, subjectValidator));

		return decoder;

	}

	private static OAuth2TokenValidatorResult invalidToken(String message) {
		return OAuth2TokenValidatorResult.failure(new OAuth2Error("Invalid token", message, null));
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http.cors(Customizer.withDefaults()).csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(
						auth -> auth.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login")
								.permitAll().requestMatchers("/api/tasks", "/api/tasks/**").hasAuthority("SCOPE_tasks")
								.anyRequest().denyAll())
				.oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults())).build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();

		configuration.setAllowedOrigins(List.of(
				"http://localhost:3000",
				"http://localhost:5173"));

		configuration.setAllowedMethods(List.of(
				"GET", "POST", "PUT", "DELETE", "OPTIONS"));

		configuration.setAllowedHeaders(List.of(
				"Authorization",
				"Content-Type"));

		configuration.setExposedHeaders(List.of("Location"));
		configuration.setAllowCredentials(false);
		configuration.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

		source.registerCorsConfiguration("/api/**", configuration);

		return source;
	}
}

package com.example.taskapi.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.taskapi.dto.ApiModels.LoginRequest;
import com.example.taskapi.dto.ApiModels.RegisterRequest;
import com.example.taskapi.dto.ApiModels.TokenResponse;
import com.example.taskapi.dto.ApiModels.UserResponse;
import com.example.taskapi.entity.AppUser;
import com.example.taskapi.repository.AppUserRepository;

@Service
public class AuthService {

	private final AppUserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final JwtEncoder jwtEncoder;
	private final String issuer;
	private final String audience;

	public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager, JwtEncoder jwtEncoder,
			@Value("${app.jwt.issuer}") String issuer, @Value("${app.jwt.audience}") String audience) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.jwtEncoder = jwtEncoder;
		this.issuer = issuer;
		this.audience = audience;
	}

	@Transactional
	public UserResponse register(RegisterRequest request) {
		// BCrypt limits password by UTF-8 bytes, no Java character count.
		if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes");
		}

		AppUser user = new AppUser(normalizeEmail(request.email()), passwordEncoder.encode(request.password()));

		AppUser saved = users.saveAndFlush(user);

		return new UserResponse(saved.getId(), saved.getEmail());
	}

	public TokenResponse login(LoginRequest request) {
		var authetication = authenticationManager.authenticate(UsernamePasswordAuthenticationToken
				.unauthenticated(normalizeEmail(request.email()), request.password()));
		Instant now = Instant.now();
		long expiresIn = 900;

		JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(authetication.getName())
				.audience(java.util.List.of(audience)).issuedAt(now).expiresAt(now.plusSeconds(expiresIn))
				.id(UUID.randomUUID().toString()).claim("scope", "tasks").build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

		return new TokenResponse(token, "Bearer", expiresIn);

	}

	private String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}
}

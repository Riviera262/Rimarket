package RiMarket.com.example.demo.module.identity.service;

import RiMarket.com.example.demo.module.identity.dto.AuthResponse;
import RiMarket.com.example.demo.module.identity.dto.LoginRequest;
import RiMarket.com.example.demo.module.identity.dto.RegisterRequest;
import RiMarket.com.example.demo.module.identity.dto.TokenPair;
import RiMarket.com.example.demo.module.identity.entity.Role;
import RiMarket.com.example.demo.module.identity.entity.User;
import RiMarket.com.example.demo.module.identity.entity.VerificationToken;
import RiMarket.com.example.demo.module.identity.entity.redis.RefreshToken;
import RiMarket.com.example.demo.module.identity.exception.IdentityErrorCode;
import RiMarket.com.example.demo.module.identity.repository.RefreshTokenRepository;
import RiMarket.com.example.demo.module.identity.repository.RoleRepository;
import RiMarket.com.example.demo.module.identity.repository.UserRepository;
import RiMarket.com.example.demo.module.identity.repository.VerificationTokenRepository;
import RiMarket.com.example.demo.shared.exception.AppException;
import RiMarket.com.example.demo.shared.exception.ErrorCode;
import RiMarket.com.example.demo.shared.security.JwtService;
import RiMarket.com.example.demo.shared.security.SecurityUser;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final EmailNotificationService emailNotificationService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenRepository refreshTokenRepository;

    // Refresh token (7 days)
    @Value("${application.security.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Transactional
    public void registerUser(RegisterRequest request) {
        // 1. Check if user exists (duplicate Email check)
        if (userRepository.existsByEmail(request.email())) {
            throw new AppException(IdentityErrorCode.USER_EMAIL_ALREADY_EXISTS);
        }

        // 2. Fetch the default role
        Role defaultRole = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseThrow(() -> new AppException(IdentityErrorCode.ROLE_NOT_FOUND)); // You'd add this to your enum

        // 3. Build a new user
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password())); //Hash password and save it
        user.setStatus("PENDING_VERIFICATION");
        user.getRoles().add(defaultRole);

        userRepository.save(user);

        // 4. Generate and save the token
        String rawToken = UUID.randomUUID().toString();
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setToken(rawToken);
        verificationToken.setUser(user);
        verificationToken.setExpiryDate(ZonedDateTime.now().plusHours(24));

        verificationTokenRepository.save(verificationToken);

        emailNotificationService.sendVerificationEmail(user.getEmail(), rawToken);

    }

    // Verify user email
    @Transactional
    public void verifyAccount(String token) {
        // 1. Find the token
        VerificationToken verificationToken = verificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new AppException(IdentityErrorCode.INVALID_VERIFICATION_TOKEN));

        // 2. Check if expired
        if (verificationToken.getExpiryDate().isBefore(ZonedDateTime.now())) {
            verificationTokenRepository.delete(verificationToken);
            throw new AppException(IdentityErrorCode.INVALID_VERIFICATION_TOKEN);
        }

        // 3. Get User and verify state
        User user = verificationToken.getUser();
        if ("ACTIVE".equals(user.getStatus())) {
            verificationTokenRepository.delete(verificationToken);
            throw new AppException(IdentityErrorCode.USER_ALREADY_VERIFIED);
        }

        // 4. User state change to "ACTIVE"
        user.setStatus("ACTIVE");
        userRepository.save(user);

        // 5. Burn the token
        verificationTokenRepository.delete(verificationToken);
    }

    public TokenPair login(LoginRequest request) {

        log.info("Refresh token expiration value: {}", refreshTokenExpiration);
        // 1. Authenticate credentials
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        // 2. Fetch the validated User
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();

        // 3. Generate the short-lived stateless access token (15 minutes)
        String accessToken = jwtService.generateAccessToken(securityUser);

        // 4. Generate the long-lived stateful refresh token (7 days)
        String rawRefreshToken = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .id(rawRefreshToken)
                .userId(securityUser.getUser().getId())
                .expiration(refreshTokenExpiration)
                .build();

        refreshTokenRepository.save(refreshToken);

        // 5. Return the pair to Controller
        return new TokenPair(accessToken, rawRefreshToken);
    }

    @Transactional
    public String refreshToken(String rawRefreshToken){
        //Check token if it exists in redis
        RefreshToken storedToken = refreshTokenRepository.findById(rawRefreshToken)
                .orElseThrow(() -> new AppException(IdentityErrorCode.INVALID_REFRESH_TOKEN));

        //Fetch the User from Postgres
        User user = userRepository.findById(storedToken.getUserId())
                .orElseThrow(() -> new AppException(IdentityErrorCode.USER_NOT_FOUND));

        //Security check user
        //Treat deleted user as not found
        if (user.isDeleted()){
            throw new AppException(IdentityErrorCode.USER_NOT_FOUND);
        }

        //Check if user got banned
        if (user.isLocked()){
            throw new AppException(IdentityErrorCode.USER_SUSPENDED);
        }

        //Check if user's email got verified or not
        if (!"ACTIVE".equals(user.getStatus())){
            throw new AppException(IdentityErrorCode.USER_NOT_VERIFIED);
        }
        //Generate AT for user
        SecurityUser securityUser = new SecurityUser(user);
        String newAccessToken = jwtService.generateAccessToken(securityUser);

        //Return AT, keep RT
        return newAccessToken;
    }

    @Transactional
    public void logout(String rawRefreshToken){
        refreshTokenRepository.findById(rawRefreshToken)
                .ifPresent(refreshTokenRepository::delete);;
    }
}
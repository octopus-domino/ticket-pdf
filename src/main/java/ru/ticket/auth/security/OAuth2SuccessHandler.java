package ru.ticket.auth.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import ru.ticket.auth.domain.Role;
import ru.ticket.auth.domain.User;
import ru.ticket.auth.repository.UserRepository;

import java.io.IOException;
import java.time.Duration;

@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserRepository users;
    private final String frontendUrl;
    private final boolean cookieSecure;

    public OAuth2SuccessHandler(JwtService jwtService,
                                UserRepository users,
                                @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
                                @Value("${app.cookie-secure:true}") boolean cookieSecure) {
        this.jwtService = jwtService;
        this.users = users;
        this.frontendUrl = frontendUrl;
        this.cookieSecure = cookieSecure;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String emailStr = null;
        String name = null;

        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            emailStr = oidcUser.getEmail();
            name = oidcUser.getFullName();
        } else if (principal instanceof OAuth2User oAuth2User) {
            emailStr = oAuth2User.getAttribute("email");
            name = oAuth2User.getAttribute("name");
        }

        if (emailStr == null || emailStr.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Email not provided by OAuth2 provider");
            return;
        }

        if (name == null || name.isBlank()) {
            name = emailStr.split("@")[0];
        }

        final String finalEmail = emailStr.toLowerCase();
        final String finalName = name;

        User user = users.findByEmailIgnoreCase(finalEmail)
                .orElseGet(() -> users.save(
                        new User(finalEmail, "OAUTH2_NO_PASSWORD", finalName, Role.USER)
                ));

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)   // true на проде (HTTPS), false — только для локальной разработки
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(Duration.ofDays(7))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        // /auth/callback — свободный путь, не конфликтует с /oauth2/* (OAuth protocol namespace)
        String redirectUrl = frontendUrl + "/auth/callback?token=" + accessToken;
        response.sendRedirect(redirectUrl);
    }
}
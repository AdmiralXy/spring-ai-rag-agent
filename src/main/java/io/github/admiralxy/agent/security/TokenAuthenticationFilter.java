package io.github.admiralxy.agent.security;

import io.github.admiralxy.agent.service.user.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates {@code Authorization: Bearer <token>} requests.
 */
@RequiredArgsConstructor
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";
    private static final String USER_ROLE = "ROLE_USER";

    private final TokenService tokenService;
    private final SecurityContextRepository contextRepository = new RequestAttributeSecurityContextRepository();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.startsWithIgnoreCase(header, BEARER)) {
            String token = header.substring(BEARER.length()).trim();
            tokenService.authenticate(token).ifPresent(user -> {
                var authentication = new UsernamePasswordAuthenticationToken(user, null,
                        AuthorityUtils.createAuthorityList(USER_ROLE));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
                // kept for the async dispatch of streaming responses
                contextRepository.saveContext(context, request, response);
            });
        }
        chain.doFilter(request, response);
    }
}

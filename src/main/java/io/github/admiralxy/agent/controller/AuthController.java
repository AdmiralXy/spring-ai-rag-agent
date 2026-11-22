package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.config.properties.SecurityProperties;
import io.github.admiralxy.agent.controller.request.auth.ChangePasswordRq;
import io.github.admiralxy.agent.controller.request.auth.LoginRq;
import io.github.admiralxy.agent.controller.request.auth.RegisterRq;
import io.github.admiralxy.agent.controller.request.auth.UploadKeyRq;
import io.github.admiralxy.agent.controller.response.auth.AuthConfigRs;
import io.github.admiralxy.agent.controller.response.auth.AuthRs;
import io.github.admiralxy.agent.controller.response.auth.UserRs;
import io.github.admiralxy.agent.entity.UserEntity;
import io.github.admiralxy.agent.exception.ApiException;
import io.github.admiralxy.agent.security.AuthUser;
import io.github.admiralxy.agent.service.file.Base64Content;
import io.github.admiralxy.agent.service.user.TokenService;
import io.github.admiralxy.agent.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping
public class AuthController {

    private static final String KEY_FIELD = "key";
    private static final String KEY_REQUIRED = "Field 'key' is required";

    private final UserService userService;
    private final TokenService tokenService;
    private final SecurityProperties securityProperties;

    @GetMapping("/auth/config")
    public AuthConfigRs config() {
        return new AuthConfigRs(securityProperties.isRegistrationEnabled());
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthRs register(@RequestBody RegisterRq rq) {
        UserEntity user = userService.register(rq.email(), rq.password());
        return new AuthRs(tokenService.issue(user.getId()), toRs(user));
    }

    @PostMapping("/auth/login")
    public AuthRs login(@RequestBody LoginRq rq) {
        UserEntity user = userService.authenticate(rq.email(), rq.password());
        return new AuthRs(tokenService.issue(user.getId()), toRs(user));
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal AuthUser user) {
        tokenService.revoke(user.tokenHash());
    }

    @GetMapping("/auth/me")
    public UserRs me(@AuthenticationPrincipal AuthUser user) {
        return toRs(userService.get(user.id()));
    }

    /**
     * Changes the password and signs out every other device.
     */
    @PutMapping("/account/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthUser user, @RequestBody ChangePasswordRq rq) {
        userService.changePassword(user, rq.currentPassword(), rq.newPassword());
        tokenService.revokeOthers(user.id(), user.tokenHash());
    }

    @PutMapping("/account/upload-key")
    public UserRs setUploadKey(@AuthenticationPrincipal AuthUser user, @RequestBody UploadKeyRq rq) {
        if (rq.key() == null) {
            throw ApiException.badRequest(KEY_REQUIRED);
        }
        userService.setUploadKey(user.id(), Base64Content.decodeField(rq.key(), KEY_FIELD));
        return toRs(userService.get(user.id()));
    }

    private static UserRs toRs(UserEntity user) {
        return new UserRs(user.getId(), user.getEmail(), user.getUploadKey() != null);
    }
}

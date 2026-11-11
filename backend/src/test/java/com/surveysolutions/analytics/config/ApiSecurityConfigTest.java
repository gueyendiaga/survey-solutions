package com.surveysolutions.analytics.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surveysolutions.analytics.dashboard.ApiAuthController;
import com.surveysolutions.analytics.config.HqApiUserPrincipal;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;

@WebMvcTest(controllers = {ApiSecurityTestController.class, ApiAuthController.class})
@Import({ApiSecurityConfig.class, ApiSecurityConfigTest.ApiUserAuthenticationProvider.class})
class ApiSecurityConfigTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void interviewDetailsAndPdfRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/interviews/interview-1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/interviews/interview-1/pdf")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyHeadquartersApiUsersCanCreateASession() throws Exception {
        MvcResult csrfResult = csrf();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"api-user","password":"secret"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        org.springframework.security.core.context.SecurityContext context =
                (org.springframework.security.core.context.SecurityContext) session.getAttribute(
                        org.springframework.security.web.context.HttpSessionSecurityContextRepository
                                .SPRING_SECURITY_CONTEXT_KEY);
        org.assertj.core.api.Assertions.assertThat(context.getAuthentication().getPrincipal())
                .isInstanceOf(HqApiUserPrincipal.class);
        org.assertj.core.api.Assertions.assertThat(
                        ((HqApiUserPrincipal) context.getAuthentication().getPrincipal()).password())
                .isEqualTo("secret");
        mockMvc.perform(get("/api/interviews/interview-1").session(session))
                .andExpect(status().isOk());

        MvcResult invalidCsrf = csrf();
        Cookie invalidCsrfCookie = invalidCsrf.getResponse().getCookie("XSRF-TOKEN");
        mockMvc.perform(post("/api/auth/login")
                        .cookie(invalidCsrfCookie)
                        .header("X-XSRF-TOKEN", invalidCsrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"reader","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedSessionCanWriteOnlyWithCsrfAndCanLogOut() throws Exception {
        MvcResult anonymousCsrf = csrf();
        Cookie csrfCookie = anonymousCsrf.getResponse().getCookie("XSRF-TOKEN");
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"api-user","password":"secret"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        MvcResult authenticatedCsrf = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();
        Cookie nextCsrfCookie = authenticatedCsrf.getResponse().getCookie("XSRF-TOKEN");

        mockMvc.perform(post("/api/sync").session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/sync")
                        .session(session)
                        .cookie(nextCsrfCookie)
                        .header("X-XSRF-TOKEN", nextCsrfCookie.getValue()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .cookie(nextCsrfCookie)
                        .header("X-XSRF-TOKEN", nextCsrfCookie.getValue()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/interviews/interview-1").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthCheckRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    private MvcResult csrf() throws Exception {
        return mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    }

    public static class ApiUserAuthenticationProvider implements AuthenticationProvider {
        @Override
        public Authentication authenticate(Authentication authentication) {
            if (!"api-user".equals(authentication.getName())
                    || !"secret".equals(authentication.getCredentials())) {
                throw new BadCredentialsException("Invalid test credentials");
            }
            return UsernamePasswordAuthenticationToken.authenticated(
                    new HqApiUserPrincipal(authentication.getName(), (String) authentication.getCredentials()),
                    null,
                    java.util.List.of(
                            new SimpleGrantedAuthority("ROLE_ANALYTICS_USER"),
                            new SimpleGrantedAuthority("ROLE_HQ_API_USER")));
        }

        @Override
        public boolean supports(Class<?> authentication) {
            return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
        }
    }
}

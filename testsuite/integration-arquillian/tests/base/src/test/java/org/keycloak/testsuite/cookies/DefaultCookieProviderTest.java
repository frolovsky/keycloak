package org.keycloak.testsuite.cookies;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.cookie.CookieProvider;
import org.keycloak.cookie.CookieType;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.testsuite.AbstractKeycloakTest;
import org.keycloak.testsuite.client.KeycloakTestingClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DefaultCookieProviderTest extends AbstractKeycloakTest {

    private KeycloakTestingClient testing;
    private SetHeaderFilter filter;

    @Before
    public void before() {
        filter = new SetHeaderFilter();
        String serverUrl = suiteContext.getAuthServerInfo().getContextRoot().toString() + "/auth";
        testing = createTestingClient(serverUrl);
    }

    @After
    public void after() {
        testing.close();
    }

    @Test
    public void testCookieDefaults() {
        Response response = testing.server("master").runWithResponse(session -> {
            CookieProvider cookies = session.getProvider(CookieProvider.class);
            cookies.set(CookieType.AUTH_SESSION_ID, "my-auth-session-id");
            cookies.set(CookieType.AUTH_STATE, "my-auth-state", 111);
            cookies.set(CookieType.AUTH_RESTART, "my-auth-restart");
            cookies.set(CookieType.AUTH_DETACHED, "my-auth-detached", 222);
            cookies.set(CookieType.IDENTITY, "my-identity", 333);
            cookies.set(CookieType.LOCALE, "my-locale");
            cookies.set(CookieType.LOGIN_HINT, "my-username");
            cookies.set(CookieType.SESSION, "my-session", 444);
            cookies.set(CookieType.WELCOME_CSRF, "my-welcome-csrf");
        });
        Assert.assertEquals(12, response.getCookies().size());
        assertCookie(response, "AUTH_SESSION_ID", "my-auth-session-id", "/auth/realms/master/", -1, true, true, "None", true);
        assertCookie(response, "KC_AUTH_STATE", "my-auth-state", "/auth/realms/master/", 111, true, false, null, false);
        assertCookie(response, "KC_RESTART", "my-auth-restart", "/auth/realms/master/", -1, true, true, null, false);
        assertCookie(response, "KC_STATE_CHECKER", "my-auth-detached", "/auth/realms/master/", 222, true, true, null, false);
        assertCookie(response, "KEYCLOAK_IDENTITY", "my-identity", "/auth/realms/master/", 333, true, true, "None", true);
        assertCookie(response, "KEYCLOAK_LOCALE", "my-locale", "/auth/realms/master/", -1, true, true, null, false);
        assertCookie(response, "KEYCLOAK_REMEMBER_ME", "my-username", "/auth/realms/master/", 31536000, true, true, null, false);
        assertCookie(response, "KEYCLOAK_SESSION", "my-session", "/auth/realms/master/", 444, true, false, "None", true);
        assertCookie(response, "WELCOME_STATE_CHECKER", "my-welcome-csrf", "/auth/realms/master/testing/run-on-server", 300, true, true, "Strict", false);
    }

    @Test
    public void testExpire() {
        filter.setHeader("Cookie", "AUTH_SESSION_ID=new;KC_RESTART=new;");

        Response response = testing.server().runWithResponse(session -> {
            session.getProvider(CookieProvider.class).expire(CookieType.AUTH_SESSION_ID);
            session.getProvider(CookieProvider.class).expire(CookieType.LOCALE);
        });

        Map<String, NewCookie> cookies = response.getCookies();
        Assert.assertEquals(1, cookies.size());
        assertCookie(response, "AUTH_SESSION_ID", "", "/auth/realms/master/", 0, false, false, null, false);
    }

    @Test
    public void testCookieHeaderWithSpaces() {
        filter.setHeader("Cookie", "terms_user=; KC_RESTART=eyJhbGciOiJIUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJhZDUyMjdhMy1iY2ZkLTRjZjAtYTdiNi0zOTk4MzVhMDg1NjYifQ.eyJjaWQiOiJodHRwczovL3Nzby5qYm9zcy5vcmciLCJwdHkiOiJzYW1sIiwicnVyaSI6Imh0dHBzOi8vc3NvLmpib3NzLm9yZy9sb2dpbj9wcm92aWRlcj1SZWRIYXRFeHRlcm5hbFByb3ZpZGVyIiwiYWN0IjoiQVVUSEVOVElDQVRFIiwibm90ZXMiOnsiU0FNTF9SRVFVRVNUX0lEIjoibXBmbXBhYWxkampqa2ZmcG5oYmJoYWdmZmJwam1rbGFqbWVlb2lsaiIsInNhbWxfYmluZGluZyI6InBvc3QifX0.d0QJSOQ6pJGzqcjqDTRwkRpU6fwYeICedL6R9Gqs8CQ; AUTH_SESSION_ID=451ec4be-a0c8-430e-b489-6580f195ccf0; AUTH_SESSION_ID=55000981-8b5e-4c8d-853f-ee4c582c1d0d;AUTH_SESSION_ID=451ec4be-a0c8-430e-b489-6580f195ccf0; AUTH_SESSION_ID=55000981-8b5e-4c8d-853f-ee4c582c1d0d;AUTH_SESSION_ID=451ec4be-a0c8-430e-b489-6580f195ccf0; AUTH_SESSION_ID=55000981-8b5e-4c8d-853f-ee4c582c1d0d4;");

        testing.server().run(session -> {
            String authSessionId = session.getProvider(CookieProvider.class).get(CookieType.AUTH_SESSION_ID);
            Assert.assertEquals("55000981-8b5e-4c8d-853f-ee4c582c1d0d4", authSessionId);
        });
    }

    @Test
    public void testSameSiteLegacyGet() {
        filter.setHeader("Cookie", "AUTH_SESSION_ID=new;AUTH_SESSION_ID_LEGACY=legacy;KC_RESTART_LEGACY=ignore");

        testing.server().run(session -> {
            Assert.assertEquals("new", session.getProvider(CookieProvider.class).get(CookieType.AUTH_SESSION_ID));
            Assert.assertEquals(null, session.getProvider(CookieProvider.class).get(CookieType.AUTH_RESTART));
        });

        filter.setHeader("Cookie", "AUTH_SESSION_ID_LEGACY=legacy");

        testing.server().run(session -> {
            Assert.assertEquals("legacy", session.getProvider(CookieProvider.class).get(CookieType.AUTH_SESSION_ID));
        });
    }

    @Test
    public void testSameSiteLegacyExpire() {
        filter.setHeader("Cookie", "AUTH_SESSION_ID=new;AUTH_SESSION_ID_LEGACY=legacy;KC_RESTART_LEGACY=ignore;KEYCLOAK_LOCALE=foobar");

        Response response = testing.server().runWithResponse(session -> {
            session.getProvider(CookieProvider.class).expire(CookieType.AUTH_SESSION_ID);
            session.getProvider(CookieProvider.class).expire(CookieType.AUTH_RESTART);
        });

        Map<String, NewCookie> cookies = response.getCookies();
        Assert.assertEquals(2, cookies.size());
        assertCookie(response, "AUTH_SESSION_ID", "", "/auth/realms/master/", 0, false, false, null, true);
    }

    private void assertCookie(Response response, String name, String value, String path, int maxAge, boolean secure, boolean httpOnly, String sameSite, boolean hasLegacy) {
        Map<String, NewCookie> cookies = response.getCookies();
        NewCookie cookie = cookies.get(name);
        Assert.assertNotNull(cookie);
        Assert.assertEquals(value, cookie.getValue());
        Assert.assertEquals(path, cookie.getPath());
        Assert.assertEquals(maxAge, cookie.getMaxAge());
        Assert.assertEquals(secure, cookie.isSecure());
        Assert.assertEquals(httpOnly, cookie.isHttpOnly());

        String setHeader = (String) response.getHeaders().get("Set-Cookie").stream().filter(v -> ((String) v).startsWith(name)).findFirst().get();
        if (sameSite == null) {
            Assert.assertFalse(setHeader.contains("SameSite"));
        } else {
            Assert.assertTrue(setHeader.contains("SameSite=" + sameSite));
        }

        if (hasLegacy) {
            assertCookie(response, name + "_LEGACY", value, path, maxAge, secure, httpOnly, null, false);
        }
    }

    private KeycloakTestingClient createTestingClient(String serverUrl) {
        ResteasyClientBuilder restEasyClientBuilder = KeycloakTestingClient.getRestEasyClientBuilder(serverUrl);
        ResteasyClient resteasyClient = restEasyClientBuilder.build();
        resteasyClient.register(filter);
        return KeycloakTestingClient.getInstance(serverUrl, resteasyClient);
    }

    @Override
    public void addTestRealms(List<RealmRepresentation> testRealms) {
    }

    public static class SetHeaderFilter implements ClientRequestFilter {

        private String key;
        private String value;

        public void setHeader(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public void filter(ClientRequestContext requestContext) throws IOException {
            if (key != null && value != null) {
                requestContext.getHeaders().add(key, value);
            }
        }
    }

}

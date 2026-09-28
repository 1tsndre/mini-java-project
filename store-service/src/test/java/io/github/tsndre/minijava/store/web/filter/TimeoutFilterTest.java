package io.github.tsndre.minijava.store.web.filter;

import io.github.tsndre.minijava.store.constant.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TimeoutFilterTest {

    private final JsonMapper json = JsonMapper.builder().build();

    private MockHttpServletResponse run(Duration timeout, FilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setAsyncSupported(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new TimeoutFilter(timeout, json).doFilter(request, response, chain);
        return response;
    }

    private String errorCode(MockHttpServletResponse response) throws Exception {
        JsonNode body = json.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.get("errors")).hasSize(1);
        return body.get("errors").get(0).get("code").asString();
    }

    @Test
    void passesThroughAFastResponse() throws Exception {
        MockHttpServletResponse response = run(Duration.ofSeconds(1), (req, res) -> {
            HttpServletResponse http = (HttpServletResponse) res;
            http.setHeader("X-Handler", "yes");
            http.setStatus(HttpServletResponse.SC_CREATED);
            http.getOutputStream().write("created".getBytes(StandardCharsets.UTF_8));
        });

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getHeader("X-Handler")).isEqualTo("yes");
        assertThat(response.getContentAsString()).isEqualTo("created");
    }

    @Test
    void implicitStatusOk() throws Exception {
        MockHttpServletResponse response = run(Duration.ofSeconds(1), (req, res) -> {
            res.setContentType("text/plain");
            res.getWriter().write("ok");
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).startsWith("text/plain");
        assertThat(response.getContentAsString()).isEqualTo("ok");
    }

    /** The handler only tries to respond after the filter has sent the 504. */
    @Test
    void aSlowHandlerGets504() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch handlerDone = new CountDownLatch(1);

        MockHttpServletResponse response = run(Duration.ofMillis(20), (req, res) -> {
            try {
                release.await();
                HttpServletResponse http = (HttpServletResponse) res;
                http.setHeader("X-Late", "should-not-leak");
                http.setStatus(200);
                http.getOutputStream().write("late body".getBytes(StandardCharsets.UTF_8));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                handlerDone.countDown();
            }
        });
        release.countDown();
        assertThat(handlerDone.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(response.getStatus()).isEqualTo(504);
        assertThat(response.getHeader("X-Late")).isNull();
        assertThat(errorCode(response)).isEqualTo(ErrorCode.TIMEOUT.value());
        assertThat(response.getContentAsString()).doesNotContain("late body").endsWith("\n");
    }

    /** A handler that started responding before the deadline is waited for, not cut off. */
    @Test
    void aStartedResponseIsCompletedNotReplaced() throws Exception {
        MockHttpServletResponse response = run(Duration.ofMillis(100), (req, res) -> {
            HttpServletResponse http = (HttpServletResponse) res;
            http.setStatus(200);
            http.getOutputStream().write("fin".getBytes(StandardCharsets.UTF_8));
            sleep(300);
            http.getOutputStream().write("ished".getBytes(StandardCharsets.UTF_8));
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("finished");
    }

    /**
     * A handler that answers right when the deadline passes races the 504; the response must be
     * exactly one of the two, never a mix.
     */
    @Test
    void aHandlerRacingTheTimeoutGivesOneCleanResponse() throws Exception {
        for (int i = 0; i < 200; i++) {
            MockHttpServletResponse response = run(Duration.ofMillis(1), (req, res) -> {
                sleep(1);
                HttpServletResponse http = (HttpServletResponse) res;
                for (int h = 0; h < 20; h++) {
                    http.setHeader("X-Debug-" + h, "v");
                }
                http.setStatus(500);
                http.setContentType("application/json");
                http.getOutputStream().write(
                        "{\"errors\":[{\"code\":\"INTERNAL_ERROR\",\"message\":\"db error\"}]}".getBytes(StandardCharsets.UTF_8));
            });

            if (response.getStatus() == 504) {
                assertThat(errorCode(response)).isEqualTo(ErrorCode.TIMEOUT.value());
                assertThat(response.getHeader("X-Debug-0")).isNull();
            } else {
                assertThat(response.getStatus()).isEqualTo(500);
                assertThat(errorCode(response)).isEqualTo(ErrorCode.INTERNAL.value());
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

package com.github.paweladamski.httpclientmock;

import org.apache.http.client.methods.HttpGet;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.github.paweladamski.httpclientmock.Requests.httpGet;
import static com.github.paweladamski.httpclientmock.Requests.httpPost;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class DebuggingTest {

    private HttpClientMock httpClientMock;
    private TestDebugger debugger;

    @Before
    public void setUp() {
        debugger = new TestDebugger();
        httpClientMock = new HttpClientMock("http://localhost", debugger);
    }

    @Test
    public void should_print_all_request_with_no_matching_rules() throws IOException {
        httpClientMock.onGet("/admin").doReturn("admin");

        httpClientMock.execute(new HttpGet("http://localhost/login"));
        httpClientMock.execute(new HttpGet("http://localhost/admin"));

        assertThat(debugger.requests, hasItem("http://localhost/login"));
        assertThat(debugger.requests, not(hasItem("http://localhost/admin")));
    }

    @Test
    public void should_print_all_request_when_debugging_is_turn_on() throws IOException {
        httpClientMock.onGet("/login").doReturn("login");
        httpClientMock.onGet("/user").doReturn("user");
        httpClientMock.onGet("/admin").doReturn("admin");

        httpClientMock.debugOn();
        httpClientMock.execute(new HttpGet("http://localhost/login"));
        httpClientMock.execute(new HttpGet("http://localhost/user"));
        httpClientMock.debugOff();
        httpClientMock.execute(new HttpGet("http://localhost/admin"));

        assertThat(debugger.requests, hasItem("http://localhost/login"));
        assertThat(debugger.requests, hasItem("http://localhost/user"));
        assertThat(debugger.requests, not(hasItem("http://localhost/admin")));
    }

    @Test
    public void should_debug_header_condition() throws IOException {
        httpClientMock
                .onGet("/login").withHeader("User-Agent", "Mozilla")
                .doReturn("mozilla");

        HttpGet getMozilla = new HttpGet("http://localhost/login");
        HttpGet getChrome = new HttpGet("http://localhost/login");
        getMozilla.addHeader("User-Agent", "Mozilla");
        getChrome.addHeader("User-Agent", "Chrome");

        httpClientMock.debugOn();
        httpClientMock.execute(getMozilla);
        httpClientMock.execute(getChrome);
        httpClientMock.debugOff();

        assertTrue(debugger.matching.contains("header User-Agent is \"Mozilla\""));
        assertFalse(debugger.notMatching.contains("header User-Agent is \"Chrome\""));
    }

    @Test
    public void should_put_message_about_missing_parameter() throws IOException {
        httpClientMock.onGet("/login").withParameter("foo", "bar");
        httpClientMock.execute(httpGet("http://localhost/login"));
        assertTrue(debugger.notMatching.contains("parameter foo occurs in request"));
    }

    @Test
    public void should_put_message_about_matching_parameter() throws IOException {
        httpClientMock
                .onGet("/login").withParameter("foo", "bar")
                .doReturn("login");
        httpClientMock.debugOn();
        httpClientMock.execute(httpGet("http://localhost/login?foo=bar"));
        assertTrue(debugger.matching.contains("parameter foo is \"bar\""));
    }

    @Test
    public void should_put_message_about_not_matching_parameter() throws IOException {
        httpClientMock.onGet("/login")
                .withParameter("foo", "bar")
                .doReturn("login");
        httpClientMock.execute(httpGet("http://localhost/login?foo=bbb"));
        assertTrue(debugger.notMatching.contains("parameter foo is \"bar\""));
    }

    @Test
    public void should_put_message_about_redundant_parameter() throws IOException {
        httpClientMock.onGet("/login")
                .doReturn("login");
        httpClientMock.execute(httpGet("http://localhost/login?foo=bbb"));
        assertTrue(debugger.notMatching.contains("parameter foo is redundant"));
    }

    @Test
    public void should_put_message_with_all_parameter_matchers() throws IOException {
        httpClientMock.onGet("/login")
                .withParameter("foo", Matchers.startsWith("a"))
                .withParameter("foo", Matchers.endsWith("b"))
                .doReturn("login");
        httpClientMock.debugOn();
        httpClientMock.execute(httpGet("http://localhost/login?foo=aabb"));
        assertTrue(debugger.matching.contains("parameter foo is a string starting with \"a\" and a string ending with \"b\""));
    }

    @Test
    public void should_put_message_about_not_matching_reference() throws IOException {
        httpClientMock.onGet("/login#foo")
                .doReturn("login");
        httpClientMock.execute(httpGet("http://localhost/login"));
        assertTrue(debugger.notMatching.contains("reference is \"foo\""));
    }

    @Test
    public void should_put_message_about_matching_reference() throws IOException {
        httpClientMock.onGet("/login#foo")
                .doReturn("login");
        httpClientMock.debugOn();
        httpClientMock.execute(httpGet("http://localhost/login#foo"));
        assertTrue(debugger.matching.contains("reference is \"foo\""));
    }

    @Test
    public void should_not_put_message_about_reference_when_it_is_not_used() throws IOException {
        httpClientMock.onGet("/login").doReturn("login");
        httpClientMock.debugOn();
        httpClientMock.execute(httpGet("http://localhost/login"));
        assertTrue(debugger.matching.stream().noneMatch(s -> s.startsWith("reference")));
        assertTrue(debugger.notMatching.stream().noneMatch(s -> s.startsWith("reference")));
    }

    @Test
    public void should_put_message_about_matching_http_method() throws IOException {
        httpClientMock.onGet("/login").doReturn("login");
        httpClientMock.debugOn();
        httpClientMock.execute(httpGet("http://localhost/login"));
        assertTrue(debugger.matching.contains("HTTP method is GET"));
    }

    @Test
    public void should_put_message_about_not_matching_http_method() throws IOException {
        httpClientMock.onGet("/login").doReturn("login");
        httpClientMock.debugOn();
        httpClientMock.execute(httpPost("http://localhost/login"));
        assertTrue(debugger.notMatching.contains("HTTP method is GET"));
    }

    @Test
    public void should_put_message_about_not_matching_URL() throws IOException {
        httpClientMock.onGet("http://localhost:8080/login").doReturn("login");
        httpClientMock.debugOn();
        httpClientMock.execute(httpPost("https://www.google.com"));
        assertTrue(debugger.notMatching.contains("schema is \"http\""));
        assertTrue(debugger.notMatching.contains("host is \"localhost\""));
        assertTrue(debugger.notMatching.contains("path is \"/login\""));
        assertTrue(debugger.notMatching.contains("port is <8080>"));
    }

    @Test
    public void should_put_message_about_matching_URL() throws IOException {
        httpClientMock.onGet("http://localhost:8080/login").doReturn("login");
        httpClientMock.debugOn();
        httpClientMock.execute(httpPost("http://localhost:8080/login"));
        assertTrue(debugger.matching.contains("schema is \"http\""));
        assertTrue(debugger.matching.contains("host is \"localhost\""));
        assertTrue(debugger.matching.contains("path is \"/login\""));
        assertTrue(debugger.matching.contains("port is <8080>"));
    }
}

class TestDebugger extends Debugger {
    public final ArrayList<String> matching = new ArrayList<>();
    public final ArrayList<String> notMatching = new ArrayList<>();
    public final ArrayList<String> requests = new ArrayList<>();

    @Override
    public Rule debug(List<Rule> rules, Request request) {
        this.requests.add(request.getUri());
        return super.debug(rules, request);
    }

    @Override
    public void message(boolean matching, String expected) {
        super.message(matching, expected);
        if (matching) {
            this.matching.add(expected);
        } else {
            this.notMatching.add(expected);
        }
    }
}

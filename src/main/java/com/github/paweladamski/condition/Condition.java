package com.github.paweladamski.condition;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.protocol.HttpContext;

public interface Condition {
    boolean matches(HttpHost httpHost, HttpRequest httpRequest, HttpContext httpContext);
}
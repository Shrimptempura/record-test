package com.mytest.oplib.dto;

import org.apache.catalina.connector.Response;

public record BookBestResponse(Response response) {

    public record Response(Header header, Body body) {

    }
}

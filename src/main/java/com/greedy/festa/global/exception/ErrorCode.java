package com.greedy.festa.global.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

    String name();

    String getMessage();

    HttpStatus getStatus();
}

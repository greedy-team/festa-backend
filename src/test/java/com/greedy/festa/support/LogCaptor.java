package com.greedy.festa.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

/** 대상 클래스의 로거에 실제 Logback appender를 붙여 남은 로그를 받아온다. */
public final class LogCaptor implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCaptor(Class<?> type) {
        this.logger = (Logger) LoggerFactory.getLogger(type);
        this.appender.start();
        this.logger.addAppender(appender);
    }

    public static LogCaptor forClass(Class<?> type) {
        return new LogCaptor(type);
    }

    public List<String> messagesAt(Level level) {
        return appender.list.stream()
                .filter(event -> event.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    public List<String> allMessages() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    /** 남은 로그 줄들이 달고 있던 MDC 값. afterCommit 콜백이 요청 스레드를 벗어나면 비어 나온다. */
    public List<String> mdcValues(String key) {
        return appender.list.stream()
                .map(event -> event.getMDCPropertyMap().get(key))
                .toList();
    }

    public List<Throwable> thrown() {
        return appender.list.stream()
                .map(ILoggingEvent::getThrowableProxy)
                .filter(ThrowableProxy.class::isInstance)
                .map(proxy -> ((ThrowableProxy) proxy).getThrowable())
                .toList();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}

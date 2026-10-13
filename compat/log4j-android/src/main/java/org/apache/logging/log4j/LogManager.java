/*
 * Substituto de org.apache.logging.log4j.LogManager (log4j-api 2.21.1) para Android.
 *
 * O original descobre a classe que chamou getLogger() inspecionando a pilha com recursos da
 * JVM de desktop (sun.reflect / StackWalker / SecurityManager) que não existem no Android, e
 * lança UnsupportedOperationException("No class provided..."). O Symja chama
 * LogManager.getLogger() sem argumento no inicializador estático de ExprEvaluator, então o
 * Symja inteiro não carrega no Android sem este substituto.
 *
 * Aqui todo logger vem de um SimpleLoggerContext único (do próprio log4j-api), com nome
 * fixo quando não informado. Mesmas assinaturas públicas do original.
 */
package org.apache.logging.log4j;

import java.net.URI;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.message.StringFormatterMessageFactory;
import org.apache.logging.log4j.simple.SimpleLoggerContext;
import org.apache.logging.log4j.simple.SimpleLoggerContextFactory;
import org.apache.logging.log4j.spi.LoggerContext;
import org.apache.logging.log4j.spi.LoggerContextFactory;

public final class LogManager {
    public static final String FACTORY_PROPERTY_NAME = "log4j2.loggerContextFactory";
    public static final String ROOT_LOGGER_NAME = "";
    private static final String NOME_PADRAO = "symja";
    private static volatile LoggerContextFactory factory = new SimpleLoggerContextFactory();
    private static final SimpleLoggerContext CONTEXTO = new SimpleLoggerContext();

    private LogManager() { }

    private static String nome(Object o) {
        if (o == null) return NOME_PADRAO;
        if (o instanceof Class) return ((Class<?>) o).getName();
        if (o instanceof String) return (String) o;
        return o.getClass().getName();
    }

    public static boolean exists(String name) { return CONTEXTO.hasLogger(name); }

    public static LoggerContext getContext() { return CONTEXTO; }
    public static LoggerContext getContext(boolean currentContext) { return CONTEXTO; }
    public static LoggerContext getContext(ClassLoader loader, boolean currentContext) { return CONTEXTO; }
    public static LoggerContext getContext(ClassLoader loader, boolean currentContext, Object externalContext) { return CONTEXTO; }
    public static LoggerContext getContext(ClassLoader loader, boolean currentContext, URI configLocation) { return CONTEXTO; }
    public static LoggerContext getContext(ClassLoader loader, boolean currentContext, Object externalContext, URI configLocation) { return CONTEXTO; }
    public static LoggerContext getContext(ClassLoader loader, boolean currentContext, Object externalContext, URI configLocation, String name) { return CONTEXTO; }

    public static void shutdown() { }
    public static void shutdown(boolean currentContext) { }
    public static void shutdown(boolean currentContext, boolean allContexts) { }
    public static void shutdown(LoggerContext context) { }

    public static LoggerContextFactory getFactory() { return factory; }
    public static void setFactory(LoggerContextFactory f) { factory = f; }

    public static Logger getFormatterLogger() { return CONTEXTO.getLogger(NOME_PADRAO, StringFormatterMessageFactory.INSTANCE); }
    public static Logger getFormatterLogger(Class<?> clazz) { return CONTEXTO.getLogger(nome(clazz), StringFormatterMessageFactory.INSTANCE); }
    public static Logger getFormatterLogger(Object value) { return CONTEXTO.getLogger(nome(value), StringFormatterMessageFactory.INSTANCE); }
    public static Logger getFormatterLogger(String name) { return CONTEXTO.getLogger(nome(name), StringFormatterMessageFactory.INSTANCE); }

    public static Logger getLogger() { return CONTEXTO.getLogger(NOME_PADRAO); }
    public static Logger getLogger(Class<?> clazz) { return CONTEXTO.getLogger(nome(clazz)); }
    public static Logger getLogger(Class<?> clazz, MessageFactory mf) { return CONTEXTO.getLogger(nome(clazz), mf); }
    public static Logger getLogger(MessageFactory mf) { return CONTEXTO.getLogger(NOME_PADRAO, mf); }
    public static Logger getLogger(Object value) { return CONTEXTO.getLogger(nome(value)); }
    public static Logger getLogger(Object value, MessageFactory mf) { return CONTEXTO.getLogger(nome(value), mf); }
    public static Logger getLogger(String name) { return CONTEXTO.getLogger(nome(name)); }
    public static Logger getLogger(String name, MessageFactory mf) { return CONTEXTO.getLogger(nome(name), mf); }
    public static Logger getRootLogger() { return CONTEXTO.getLogger(ROOT_LOGGER_NAME); }
}

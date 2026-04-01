package jp.co.translacat.global.converter;

public interface BaseEnum {
    Object getValue();

    boolean matches(String source);
}

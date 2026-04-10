package com.vagent.eval.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 与 {@link com.vagent.eval.EvalApiProperties} 共用字段定义，并承担 {@code vagent.eval.api.*} 的
 * {@link ConfigurationProperties} 绑定。
 *
 * <p>部分本地/历史代码（如 {@code EvalApiAuthFilter}）按类型注入 {@code com.vagent.eval.security.EvalApiProperties}，
 * 若缺少本类会导致启动失败；业务代码仍可使用父类型 {@link com.vagent.eval.EvalApiProperties} 接收同一 Bean。</p>
 */
@ConfigurationProperties(prefix = "vagent.eval.api")
public class EvalApiProperties extends com.vagent.eval.EvalApiProperties {}

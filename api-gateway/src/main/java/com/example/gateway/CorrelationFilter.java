package com.example.gateway;

import java.util.UUID;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class CorrelationFilter implements GlobalFilter, Ordered {

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String value = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        String traceId = value != null && value.matches("[a-zA-Z0-9-]{1,64}")
                ? value : UUID.randomUUID().toString();

        exchange.getResponse().getHeaders().set("X-Trace-Id", traceId);
        var request = exchange.getRequest().mutate()
                .headers(headers -> headers.set("X-Trace-Id", traceId))
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }
}

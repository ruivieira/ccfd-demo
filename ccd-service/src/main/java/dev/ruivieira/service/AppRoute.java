package dev.ruivieira.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ruivieira.model.Transaction;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class AppRoute extends RouteBuilder {

    public void configure() {

        final String BROKER_URL = System.getenv("BROKER_URL");

        from("kafka:ccd?brokers=" + BROKER_URL).routeId("mainRoute")
                .process(exchange -> {
                    // deserialise Kafka message
                    final ObjectMapper mapper = new ObjectMapper();
                    final Transaction transaction = mapper.readValue(exchange.getIn().getBody().toString(), Transaction.class);
                    final Map<String, Object> map = new HashMap<>();
                    map.put("account_id", transaction.getId());
                    map.put("transaction_amount", transaction.getAmount());
                    exchange.getOut().setBody(map);
                })
                .marshal(new JacksonDataFormat())
                .to("http://localhost:8090/rest/server/containers/ccd-kjar-1_0-SNAPSHOT/processes/ccd-kjar.CCDProcess/instances");
    }
}

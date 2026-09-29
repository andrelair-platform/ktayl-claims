package com.andrelair.ktayl.claims.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;

/** SOAP client to the live GlobalCore legacy (Slice B). Active only under the `soap` profile. */
@Configuration
@Profile("soap")
public class SoapClientConfig {

    @Bean
    Jaxb2Marshaller globalCoreMarshaller() {
        Jaxb2Marshaller m = new Jaxb2Marshaller();
        m.setContextPath("com.andrelair.ktayl.claims.legacy.gen");
        return m;
    }

    @Bean
    WebServiceTemplate globalCoreWsTemplate(
            Jaxb2Marshaller globalCoreMarshaller,
            @Value("${globalcore.soap.url:http://localhost:8080/ws}") String uri) {
        WebServiceTemplate t = new WebServiceTemplate();
        t.setMarshaller(globalCoreMarshaller);
        t.setUnmarshaller(globalCoreMarshaller);
        t.setDefaultUri(uri);
        return t;
    }
}

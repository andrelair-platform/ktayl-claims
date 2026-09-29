package com.andrelair.globalcore.config;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.config.annotation.WsConfigurerAdapter;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

/** Contract-first Spring-WS: SOAP served at /ws/*, WSDL at /ws/claims.wsdl (generated from the XSD). */
@EnableWs
@Configuration
public class WebServiceConfig extends WsConfigurerAdapter {

    static final String NAMESPACE = "http://globalcore.andrelair.com/claims";

    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext ctx) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(ctx);
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    @Bean(name = "claims")
    public DefaultWsdl11Definition defaultWsdl11Definition(XsdSchema globalcoreSchema) {
        DefaultWsdl11Definition wsdl = new DefaultWsdl11Definition();
        wsdl.setPortTypeName("GlobalCorePort");
        wsdl.setLocationUri("/ws");
        wsdl.setTargetNamespace(NAMESPACE);
        wsdl.setSchema(globalcoreSchema);
        return wsdl;
    }

    @Bean
    public XsdSchema globalcoreSchema() {
        return new SimpleXsdSchema(new ClassPathResource("xsd/globalcore.xsd"));
    }

    @Bean
    public Jaxb2Marshaller marshaller() {
        Jaxb2Marshaller m = new Jaxb2Marshaller();
        m.setContextPath("com.andrelair.globalcore.gen");
        return m;
    }
}

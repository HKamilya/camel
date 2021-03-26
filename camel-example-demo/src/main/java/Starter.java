import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultMessage;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

//каждый route лучше запускать по отдельности
public class Starter {

    public static void main(String[] args) throws Exception {
        PropertiesConfiguration config = new PropertiesConfiguration();
        config.load("application.properties");
        String password = config.getString("password");
        String username = config.getString("username");
        String url = config.getString("url");

        CamelContext camel = new DefaultCamelContext();
        camel.getPropertiesComponent().setLocation("classpath:application.properties");
        camel.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:foo?period=60000")
                        .log("Hello camel");
            }
        });
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                url,
                username,
                password
        );


        camel.getRegistry().bind("temp", dataSource);

        // работа с jdbc
        camel.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:foo?period=60000")
                        .routeId("JDBC Route")
                        .setHeader("key", constant(1))
                        .setBody(simple("select id, firstname from table_2 where id > :?key"))
                        .to("jdbc:temp?useHeadersAsParameters=true")
                        .log(">>> ${body}")
                        .process(exchange -> {
                            Message in = exchange.getIn();
                            Object body = in.getBody();
                            DefaultMessage defaultMessage = new DefaultMessage(exchange);
                            defaultMessage.setHeaders(in.getHeaders());
                            defaultMessage.setHeader("head", "jdbc");
                            defaultMessage.setBody(body.toString() + "\n" + in.getHeaders().toString());

                            exchange.setMessage(defaultMessage);
                        })
                        .to("file:{{jdbc}}?fileName=done-${date:now:yyyyMMdd}-${headers.head}11.txt");
            }
        });

        //работа с файлами
        camel.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:{{from}}")
                        .routeId("file moving")
//                        .log(" >>>> ${body}")
                        .convertBodyTo(String.class)
                        .to("log:?showBody=true&showHeaders=true")
                        .choice()
                        .when(exchange -> ((String) exchange.getIn().getBody()).contains("=a"))
                        .to("file:{{toA}}")
                        .when(exchange -> ((String) exchange.getIn().getBody()).contains("=b"))
                        .to("file:{{toB}}")
                        .otherwise()
                        .to("file:{{from}}");
            }
        });

        //самый простой пример работы с файлами
        camel.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:camel-example-demo/files/from").to("file:camel-example-demo/files");
            }
        });

        camel.start();
        Thread.sleep(4_000);
        camel.stop();

    }
}

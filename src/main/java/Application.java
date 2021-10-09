import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

// Caution: в браузере стоит ограничение на кол-во подключений к одному хосту
// Если захотите больше 5-6 подключений, то вам нужно несколько браузеров
public class Application {
    private static final WebClient webClient = WebClient.create("http://localhost:8080/sse");


    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Flux<ByteBuf> flux = Flux.interval(Duration.ofSeconds(1))
            .map(o -> Map.of("tick", o))
            .log()
            .share()
            .map(Application::toByteBuf);

    public static void main(String[] args) {
        DisposableServer server = HttpServer.create()
                .route(
                        routes -> routes
                                .get("/sse.html", (request, response) -> response.sendFile(Path.of("sse.html")))
                                .get("/sse.js", (request, response) -> {
                                    response.header("Content-Type", "text/javascript");
                                    return response.sendFile(Path.of("sse.js"));
                                })
                                .get("/sse", (request, response) ->
                                        response.sse().send(proxyFlux()))
                )
                .port(8081)
                .bindNow();

        server.onDispose().block();
    }


    private static ByteBuf toByteBuf(Object any) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write("data: ".getBytes(Charset.defaultCharset()));
            mapper.writeValue(out, any);
            out.write("\n\n".getBytes(Charset.defaultCharset()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ByteBufAllocator.DEFAULT
                .buffer()
                .writeBytes(out.toByteArray());
    }

    private static Flux<ByteBuf> proxyFlux() {
        return webClient.get()
                .retrieve()
                .bodyToFlux(ByteBuf.class);
    }
}

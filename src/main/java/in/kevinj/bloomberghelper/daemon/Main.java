package in.kevinj.bloomberghelper.daemon;

import in.kevinj.bloomberghelper.daemon.controllers.BloombergHelper;

import java.io.IOException;
import java.net.URI;

import javax.ws.rs.core.UriBuilder;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;

public class Main {
	public static void main(String[] args) throws IOException {
        URI serverUri = UriBuilder.fromUri("http://localhost/").port(25274).build();
		HttpServer server = GrizzlyHttpServerFactory.createHttpServer(serverUri, create());
//		SSLContextConfigurator sslCon = new SSLContextConfigurator();
//		sslCon.setKeyStoreFile("");
//		sslCon.setKeyStorePass("");
//		URI serverUri = UriBuilder.fromUri("https://localhost/").port(25274).build();
//		HttpServer server = GrizzlyHttpServerFactory.createHttpServer(serverUri, create(), true, new SSLEngineConfigurator(sslCon));
		server.start();
	}

	public static ResourceConfig create() {
		ResourceConfig resources = new ResourceConfig();
		resources.register(MultiPartFeature.class);
		resources.packages(BloombergHelper.class.getPackage().toString());
		return resources;
	}
}

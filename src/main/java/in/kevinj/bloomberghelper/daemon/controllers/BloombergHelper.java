package in.kevinj.bloomberghelper.daemon.controllers;

import in.kevinj.bloomberghelper.common.Role;
import in.kevinj.bloomberghelper.daemon.PartialMultiPart;
import in.kevinj.bloomberghelper.daemon.model.Model;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.CompletionCallback;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.glassfish.grizzly.ReadHandler;
import org.glassfish.grizzly.http.io.NIOInputStream;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.media.multipart.BodyPart;
import org.glassfish.jersey.media.multipart.Boundary;
import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.server.ChunkedOutput;

@Path("bloomberghelper")
public class BloombergHelper {
	private static final Logger LOG = Logger.getLogger(BloombergHelper.class.getName());

	@GET
	@Path("TERM_CLIENT/subscribe")
	public void termClientSubscribe(@QueryParam("new") @DefaultValue("false") String registerParam, @Context Request req, @Suspended AsyncResponse asyncResponse) {
		LOG.fine("Received term client subscribe request from " + req.getRemoteAddr());
		boolean register = Boolean.parseBoolean(registerParam);
		String[] credentials;
		if (req.getAuthorization() == null
				|| (credentials = new String(Base64.decodeBase64(req.getAuthorization().replaceFirst("[Bb]asic ", ""))).split(":")).length != 2) {
			asyncResponse.resume(Response
					.status(Response.Status.UNAUTHORIZED)
					.header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"bloomberghelper\"")
					.header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
					.entity("you must enter a key and password")
					.build());
			return;
		}
		if (register) {
			if (!Model.INSTANCE.register(credentials[0], credentials[1], Role.TERM_CLIENT)) {
				LOG.info("Term client " + req.getRemoteAddr() + " registered using key " + credentials[0]);
				asyncResponse.resume(Response
						.status(Response.Status.UNAUTHORIZED)
						.header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
						.entity("key already in use")
						.build());
				return;
			}
		} else {
			if (!Model.INSTANCE.login(credentials[0], credentials[1], Role.TERM_CLIENT)) {
				LOG.fine(credentials[0] + " logged in");
				asyncResponse.resume(Response
						.status(Response.Status.UNAUTHORIZED)
						.header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
						.entity("key is not registered or password is incorrect")
						.build());
				return;
			}
		}

		/*ChunkedOutput<byte[]> output = new ChunkedOutput<>(byte[].class);

		new Thread(() -> {
			try {
				//TODO: heartbeat every 30 seconds
				for (int i = 0; i < 10; i++) {
					Thread.sleep(2000);
					output.write(new byte[] { (byte) i });
				}
				System.out.println("CONNECTION CLOSED: EOF");
			} catch (Throwable th) {
				System.out.println("CONNECTION CLOSED: PREMATURE");
			} finally {
				try {
					output.close();
				} catch (IOException e) { }
			}
		}).start();

		asyncResponse.resume(Response
				.status(Response.Status.OK)
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM)
				.entity(output)
				.build());*/

		MediaType mediaType = new MediaType("multipart", "mixed", Collections.singletonMap(Boundary.BOUNDARY_PARAMETER, Boundary.createBoundary()));
		PartialMultiPart mp = new PartialMultiPart(mediaType);
		ChunkedOutput<PartialMultiPart> output = new ChunkedOutput<>(PartialMultiPart.class);

		new Thread(() -> {
			try {
				//TODO: heartbeat every 30 seconds
				char[] s = new char[10];
				for (int i = 0; i < s.length; i++)
					s[i] = (char) (i % 26 + 'a');
				for (int i = 0; i < 10; i++) {
					Thread.sleep(2000);
					String str = i + " " + System.currentTimeMillis() + " " + String.valueOf(s);
					byte[] bytes = str.getBytes();
					System.out.println("TERM SEND " + str);
					BodyPart bp = new BodyPart(bytes, MediaType.APPLICATION_OCTET_STREAM_TYPE).contentDisposition(ContentDisposition.type("attachment").fileName("test.txt").size(bytes.length).build());
					bp.getHeaders().add(HttpHeaders.CONTENT_LENGTH, Integer.toString(bytes.length));
					mp.bodyPart(bp);
					output.write(mp);
				}
				mp.bodyPart(new BodyPart("EOMP", MediaType.TEXT_PLAIN_TYPE));
				System.out.println("TERM SEND EOMP");
				mp.finished();
				output.write(mp);
				LOG.info("CONNECTION CLOSED: EOF");
			} catch (Throwable th) {
				LOG.log(Level.WARNING, "CONNECTION CLOSED: PREMATURE", th);
			} finally {
				try {
					mp.close();
					output.close();
				} catch (IOException e) { }
			}
		}).start();

        asyncResponse.resume(Response
				.status(Response.Status.OK)
				.header(HttpHeaders.CONTENT_TYPE, mediaType)
				.entity(output)
				.build());
	}

	@POST
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.TEXT_PLAIN + "; charset=utf-8")
	@Path("TERM_CLIENT/publish")
	public void termClientPublish(@Context Request req, @Suspended AsyncResponse asyncResponse) {
		LOG.fine("Received term client publish stream from " + req.getRemoteAddr());
		Charset charset;
		if (req.getCharacterEncoding() != null)
			charset = Charset.forName(req.getCharacterEncoding());
		else
			charset = Charset.defaultCharset();
		NIOInputStream stream = req.getNIOInputStream();
		byte[] buffer = new byte[4096];
		stream.notifyAvailable(new ReadHandler() {
			@Override
			public void onDataAvailable() throws Exception {
				synchronized (buffer) {
					while (stream.isReady()) {
						int read = stream.read(buffer);
						System.out.print("TERM RECV " + new String(buffer, 0, read, charset));
					}
					stream.notifyAvailable(this);
				}
			}

			@Override
			public void onError(Throwable t) {
				LOG.log(Level.WARNING, "CONNECTION CLOSED: PREMATURE", t);
				try {
					stream.close();
					asyncResponse.resume(Response
							.status(Response.Status.OK)
							.header("Connection", "Close")
							.entity("OK")
							.build());
				} catch (IOException e) { }
			}

			@Override
			public void onAllDataRead() throws Exception {
				LOG.info("CONNECTION CLOSED: EOF");
				stream.close();
				asyncResponse.resume(Response
						.status(Response.Status.OK)
						.header("Connection", "Close")
						.entity("OK")
						.build());
			}
		});
	}

	@POST
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.TEXT_PLAIN + "; charset=utf-8")
	@Path("DEV_CLIENT/subscribe")
	public void devClientSubscribe(@Context Request req, @Suspended AsyncResponse asyncResponse) {
		LOG.fine("Received dev client subscribe request from " + req.getRemoteAddr());
		ChunkedOutput<String> output = new ChunkedOutput<String>(String.class);

		new Thread(() -> {
			try {
				//TODO: heartbeat every 30 seconds
				for (int i = 0; i < 10; i++) {
					Thread.sleep(2000);
					output.write(i + "\r\n");
					System.out.print("DEV SEND " + i + "\r\n");
				}
				LOG.info("CONNECTION CLOSED: EOF");
			} catch (Throwable th) {
				LOG.log(Level.WARNING, "CONNECTION CLOSED: PREMATURE", th);
			} finally {
				try {
					output.close();
				} catch (IOException e) { }
			}
		}).start();

		asyncResponse.resume(Response
				.status(Response.Status.OK)
				.entity(output)
				.build());
	}

	@GET
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@Path("downloadbin")
	public void downloadBin(@QueryParam("key") String key, @Context Request req, @Suspended AsyncResponse asyncResponse) {
		asyncResponse.register((CompletionCallback) (throwable -> {
			if (throwable != null) {
				throw new WebApplicationException("Failed to upload file", throwable);
			}
		}));
		asyncResponse.setTimeout(1, TimeUnit.MINUTES);
		asyncResponse.setTimeoutHandler(resp -> {
			//Model.INSTANCE.getClient(key).disconnected(role);
			asyncResponse.resume("HEARTBEAT");
		});

		try (InputStream file = new FileInputStream("C:\\Users\\Kevin\\Google Drive\\Big Videos\\Urusei Yatsura\\Films\\[LRE]Urusei Yatsura - Movie 1 Only You [05ABAA82].mkv")) {
			StreamingOutput streamer = output -> {
				try {
					IOUtils.copyLarge(file, output);
				} catch (Exception e) {
					throw new WebApplicationException("Failed to upload file", e, Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to upload file").build());
				}
			};
			Response.ResponseBuilder res = Response.ok(streamer)
					.status(Response.Status.OK)
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"test.zip\"")
					.header(HttpHeaders.CONTENT_LENGTH, file.available())
					.header(HttpHeaders.CACHE_CONTROL, "no-cache, must-revalidate")
					.header("Pragma", "no-cache")
					.header("Content-Transfer-Encoding", "binary")
					.header("Connection", "Close");
			asyncResponse.resume(res.build());
		} catch (IOException e) {
			throw new WebApplicationException("Failed to upload file", e);
		}
	}

	@GET
	@Path("uploadbin")
	public Response uploadBinPage() {
		return Response.status(Response.Status.OK).entity("<form action=\"uploadbin\" method=\"post\" enctype=\"multipart/form-data\"><input type=\"file\" name=\"file\"><input type=\"submit\"></form>").build();
	}

	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces(MediaType.TEXT_PLAIN + "; charset=utf-8")
	@Path("uploadbin")
	public void uploadBin(@FormDataParam("file") InputStream uploadedInputStream, @FormDataParam("file") FormDataContentDisposition fileDetail, @HeaderParam("Content-Length") int length, @Context Request req, @Suspended AsyncResponse asyncResponse) {
		try (OutputStream file = new FileOutputStream("C:\\Users\\Kevin\\test.file")) {
			IOUtils.copyLarge(uploadedInputStream, file);
			asyncResponse.resume("SUCCESS");
		} catch (IOException e) {
			throw new WebApplicationException("Failed to download file", e);
		}
	}
}

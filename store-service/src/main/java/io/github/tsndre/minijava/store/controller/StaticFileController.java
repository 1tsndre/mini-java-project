package io.github.tsndre.minijava.store.controller;

import io.github.tsndre.minijava.store.config.AppConfig;
import io.github.tsndre.minijava.store.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Serves uploaded images under /uploads/ and the API docs under /docs/, like Go's http.FileServer
 * without directory listings: the names of all uploaded files, including ones no longer
 * referenced, cannot be enumerated.
 */
@Controller
public class StaticFileController {

    private static final String UPLOADS = "/uploads";
    private static final String DOCS = "/docs";
    private static final Path DOCS_DIR = Path.of("./docs");
    /** The exact header values Go's net/http sends. */
    private static final String HTML_UTF8 = "text/html; charset=utf-8";
    private static final String TEXT_UTF8 = "text/plain; charset=utf-8";

    private final Path uploadDir;

    public StaticFileController(AppConfig config) {
        this.uploadDir = Path.of(config.upload().dir());
    }

    /** "/uploads" and "/docs" redirect to the directory, as Go's router does (307, with a small HTML body). */
    @RequestMapping(value = {UPLOADS, DOCS}, method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<String> redirectToDirectory(HttpServletRequest request) {
        String target = request.getRequestURI() + "/";
        if (request.getQueryString() != null) {
            target += "?" + request.getQueryString();
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                .header(HttpHeaders.LOCATION, target)
                .header(HttpHeaders.CONTENT_TYPE, HTML_UTF8);
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return response.build();
        }
        return response.body("<a href=\"" + HtmlUtils.htmlEscape(target) + "\">Temporary Redirect</a>.\n\n");
    }

    @RequestMapping(value = {UPLOADS + "/**", DOCS + "/**"}, method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<?> serve(HttpServletRequest request) throws IOException {
        String uri = request.getRequestURI();
        boolean uploads = uri.startsWith(UPLOADS + "/");
        Path root = uploads ? uploadDir : DOCS_DIR;
        String relative = UriUtils.decode(uri.substring((uploads ? UPLOADS : DOCS).length() + 1), StandardCharsets.UTF_8);

        if (relative.isEmpty() || relative.endsWith("/")) {
            throw ApiException.notFound();
        }

        Path base = root.toAbsolutePath().normalize();
        Path file = base.resolve(relative).normalize();
        if (!file.startsWith(base) || !Files.exists(file)) {
            return pageNotFound();
        }
        if (Files.isDirectory(file)) {
            // Like Go's file server: a bare 301 to the trailing-slash form, which is refused above.
            String target = file.getFileName() + "/";
            if (request.getQueryString() != null) {
                target += "?" + request.getQueryString();
            }
            return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY).header(HttpHeaders.LOCATION, target).build();
        }

        Resource resource = new FileSystemResource(file);
        MediaType contentType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        Instant lastModified = Files.getLastModifiedTime(file).toInstant();
        return ResponseEntity.ok()
                .contentType(contentType)
                .lastModified(lastModified)
                .body(resource);
    }

    /** The plain-text 404 of Go's file server, for a file that does not exist. */
    private static ResponseEntity<String> pageNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .header(HttpHeaders.CONTENT_TYPE, TEXT_UTF8)
                .header("X-Content-Type-Options", "nosniff")
                .body("404 page not found\n");
    }
}

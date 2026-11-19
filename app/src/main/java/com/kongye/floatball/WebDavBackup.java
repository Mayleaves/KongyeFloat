package com.kongye.floatball;

import android.util.Xml;
import android.net.Network;
import java.io.*;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.xmlpull.v1.XmlPullParser;

/** Explicit WebDAV operations with bounded downloads and origin-checked redirects. */
final class WebDavBackup {
    private static final MediaType XML = MediaType.parse("application/xml; charset=utf-8");
    private static final MediaType ZIP = MediaType.parse("application/zip");
    private static final long MAX_BACKUP = 23L * 1024 * 1024;
    private static final String PROPERTIES = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            + "<d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/><d:getcontentlength/>"
            + "<d:getlastmodified/><d:getetag/></d:prop></d:propfind>";
    private static final OkHttpClient HTTP = new OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).writeTimeout(45, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build();

    record Entry(String name, HttpUrl url, long size, String modified, String etag) {}
    private final HttpUrl root, directory;
    private final String authorization;
    private final OkHttpClient http;

    WebDavBackup(WebDavSettings.Config config) throws IOException {
        this(config, null);
    }

    WebDavBackup(WebDavSettings.Config config, Network network) throws IOException {
        http = network == null ? HTTP : HTTP.newBuilder()
                .connectionPool(new ConnectionPool()).socketFactory(network.getSocketFactory())
                .dns(host -> Arrays.asList(network.getAllByName(host))).build();
        HttpUrl parsed = HttpUrl.parse(config.server().trim());
        if (config.server().length() > 4000 || parsed == null || !parsed.username().isEmpty() || !parsed.password().isEmpty()
                || parsed.query() != null || parsed.fragment() != null)
            throw new IOException("请输入完整的 HTTP 或 HTTPS WebDAV 服务器地址");
        if (config.username().contains(":") || config.username().contains("\n") || config.username().contains("\r"))
            throw new IOException("WebDAV 用户名格式不正确");
        if (config.username().length() > 2000 || config.password().length() > 4000 || config.folder().length() > 1000)
            throw new IOException("WebDAV 配置信息过长");
        root = asDirectory(parsed);
        HttpUrl.Builder path = root.newBuilder();
        String folder = config.folder().trim();
        if (folder.equals(WebDavSettings.FOLDER) && root.pathSegments().size() > 1
                && root.pathSegments().get(root.pathSegments().size() - 2).equals(folder)) folder = "";
        for (String segment : folder.split("/")) {
            if (segment.isEmpty()) continue;
            if (segment.equals(".") || segment.equals("..") || segment.contains("\\") || segment.chars().anyMatch(c -> c < 32))
                throw new IOException("请输入有效的备份目录，例如 KongyeFloat");
            path.addPathSegment(segment);
        }
        directory = asDirectory(path.build());
        authorization = config.username().isEmpty() && config.password().isEmpty() ? ""
                : Credentials.basic(config.username(), config.password(), StandardCharsets.UTF_8);
    }

    void test() throws IOException { collection(directory); }

    void prepareDirectory() throws IOException {
        try { collection(directory); return; }
        catch (DavStatus failure) { if (failure.code != 404) throw failure; }
        HttpUrl base = collection(root);
        List<String> segments = directory.pathSegments();
        int start = root.pathSegments().size() - 1;
        for (int i = start; i < segments.size(); i++) {
            if (segments.get(i).isEmpty()) continue;
            base = base.newBuilder().addPathSegment(segments.get(i)).addPathSegment("").build();
            try { base = collection(base); continue; }
            catch (DavStatus failure) { if (failure.code != 404) throw failure; }
            try (Response response = request("MKCOL", base, null, Map.of())) {
                if (response.code() != 201 && response.code() != 405) throw status(response.code());
            }
            base = collection(base);
        }
    }

    private HttpUrl collection(HttpUrl url) throws IOException {
        try (Response response = request("PROPFIND", url, RequestBody.create(XML, PROPERTIES), Map.of("Depth", "0"))) {
            if (response.code() != 207) throw status(response.code());
            byte[] data = read(response.body(), 2 * 1024 * 1024);
            if (!isCollection(data)) throw new IOException("这个地址不是可用的 WebDAV 目录");
            return asDirectory(response.request().url());
        }
    }

    List<Entry> list() throws IOException {
        try (Response response = request("PROPFIND", directory, RequestBody.create(XML, PROPERTIES), Map.of("Depth", "1"))) {
            if (response.code() != 207) throw status(response.code());
            return parse(read(response.body(), 2 * 1024 * 1024), asDirectory(response.request().url()));
        }
    }

    String upload(File file, String name) throws IOException {
        if (!backupName(name) || file.length() > MAX_BACKUP) throw new IOException("备份文件无效或过大");
        HttpUrl base = collection(directory);
        HttpUrl destination = base.newBuilder().addPathSegment(name).build();
        HttpUrl temporary = base.newBuilder().addPathSegment(name + ".upload-" + UUID.randomUUID()).build();
        boolean committed = false;
        try {
            try (Response response = request("PUT", temporary, RequestBody.create(ZIP, file), Map.of("If-None-Match", "*"))) {
                if (response.code() != 201 && response.code() != 204 && response.code() != 200) throw status(response.code());
            }
            try (Response response = request("MOVE", temporary, null, Map.of("Destination", destination.toString(), "Overwrite", "F"))) {
                if (response.code() != 201 && response.code() != 204) throw status(response.code());
            }
            committed = true; return name;
        } finally {
            if (!committed) {
                boolean interrupted = Thread.interrupted();
                try (Response ignored = request("DELETE", temporary, null, Map.of())) { }
                catch (IOException ignored) { }
                finally { if (interrupted) Thread.currentThread().interrupt(); }
            }
        }
    }

    void download(Entry entry, File target) throws IOException {
        if (!backupName(entry.name()) || !sameOrigin(directory, entry.url()) || entry.size() > MAX_BACKUP)
            throw new IOException("备份文件无效或过大");
        try (Response response = request("GET", entry.url(), null, match(entry))) {
            if (response.code() != 200) throw status(response.code());
            ResponseBody body = response.body();
            if (body == null || body.contentLength() > MAX_BACKUP) throw new IOException("备份文件过大或无法读取");
            long total = 0;
            try (InputStream in = body.byteStream(); OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                for (int count; (count = in.read(buffer)) != -1;) {
                    total += count;
                    if (total > MAX_BACKUP) throw new IOException("备份文件过大");
                    out.write(buffer, 0, count);
                }
            }
            if (body.contentLength() >= 0 && total != body.contentLength()) throw new IOException("备份下载不完整，请重试");
        } catch (IOException failure) { target.delete(); throw failure; }
    }

    void delete(Entry entry) throws IOException {
        if (!backupName(entry.name()) || !sameOrigin(directory, entry.url())) throw new IOException("无效的备份文件");
        try (Response response = request("DELETE", entry.url(), null, match(entry))) {
            if (response.code() != 200 && response.code() != 204) throw status(response.code());
        }
    }

    private static Map<String, String> match(Entry entry) {
        String etag = entry.etag();
        return etag.length() >= 2 && etag.charAt(0) == '"' && etag.charAt(etag.length()-1) == '"'
                && etag.chars().allMatch(c -> c >= 32 && c < 127) ? Map.of("If-Match", etag) : Map.of();
    }

    private Response request(String method, HttpUrl url, RequestBody body, Map<String, String> headers) throws IOException {
        HttpUrl current = url;
        for (int redirect = 0; redirect < 4; redirect++) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("备份操作已取消");
            Request.Builder builder = new Request.Builder().url(current).method(method, body)
                    .header("User-Agent", "KongyeFloat-WebDAV")
                    .header("Cache-Control", "no-store").header("Accept-Encoding", "identity");
            if (!authorization.isEmpty()) builder.header("Authorization", authorization);
            headers.forEach(builder::header);
            Response response;
            try { response = http.newCall(builder.build()).execute(); }
            catch (javax.net.ssl.SSLException failure) { throw new IOException("WebDAV 证书校验失败，请检查服务器证书", failure); }
            catch (SocketTimeoutException failure) { throw new IOException("连接 WebDAV 超时，请稍后重试", failure); }
            if (method.equals("DELETE")) return response;
            if (!Set.of(301, 302, 307, 308).contains(response.code())) return response;
            String location = response.header("Location"); HttpUrl next = location == null ? null : current.resolve(location);
            response.close();
            if (next == null || !sameOrigin(url, next) || !next.username().isEmpty() || !next.password().isEmpty()
                    || next.query() != null || next.fragment() != null)
                throw new IOException("服务器重定向到其他地址，请填写最终 WebDAV 地址");
            current = next;
        }
        throw new IOException("服务器重定向过多，请检查 WebDAV 地址");
    }

    private static final class DavStatus extends IOException {
        final int code;
        DavStatus(int code, String message) { super(message); this.code = code; }
    }

    private static DavStatus status(int code) {
        return new DavStatus(code, switch (code) {
            case 401 -> "WebDAV 账号或密码不正确";
            case 403 -> "WebDAV 没有访问或写入权限";
            case 404, 409 -> "WebDAV 目录或备份不存在，请检查目录配置";
            case 405, 501 -> "服务器不支持所需的 WebDAV 操作";
            case 412 -> "备份已变化或文件名已存在，请刷新后重试";
            case 413, 507 -> "WebDAV 存储空间不足或文件大小超限";
            case 423 -> "WebDAV 文件已锁定，请稍后重试";
            default -> "WebDAV 请求失败（" + code + "）";
        });
    }

    private static HttpUrl asDirectory(HttpUrl url) {
        return url.encodedPath().endsWith("/") ? url : url.newBuilder().addPathSegment("").build();
    }

    private static boolean sameOrigin(HttpUrl a, HttpUrl b) {
        return a.scheme().equals(b.scheme()) && a.host().equals(b.host()) && a.port() == b.port();
    }

    private static boolean backupName(String name) {
        return name.matches("KongyeFloat_[A-Za-z0-9_-]+\\.zip");
    }

    private static byte[] read(ResponseBody body, int maximum) throws IOException {
        if (body == null || body.contentLength() > maximum) throw new IOException("WebDAV 返回的数据过大");
        try (InputStream in = body.byteStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            for (int count; (count = in.read(buffer)) != -1;) {
                if (count > maximum - out.size()) throw new IOException("WebDAV 返回的数据过大");
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }
    }

    private static XmlPullParser parser(byte[] xml) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        parser.setInput(new ByteArrayInputStream(xml), "UTF-8"); return parser;
    }

    private static boolean isCollection(byte[] xml) throws IOException {
        try {
            XmlPullParser parser = parser(xml); boolean collection = false, success = false, multistatus = false;
            for (int event; (event = parser.nextToken()) != XmlPullParser.END_DOCUMENT;) {
                if (event == XmlPullParser.DOCDECL) throw new IOException("WebDAV 返回的 XML 格式不支持");
                if (event != XmlPullParser.START_TAG || !"DAV:".equals(parser.getNamespace())) continue;
                if (parser.getDepth() == 1 && parser.getName().equals("multistatus")) multistatus = true;
                if (parser.getName().equals("collection")) collection = true;
                if (parser.getName().equals("status")) success |= parser.nextText().matches("HTTP/[^ ]+ 2\\d\\d.*");
            }
            return multistatus && collection && success;
        } catch (Exception failure) { throw new IOException("无法读取 WebDAV 目录信息", failure); }
    }

    static List<Entry> parse(byte[] xml, HttpUrl base) throws IOException {
        try {
            XmlPullParser parser = parser(xml); List<Entry> entries = new ArrayList<>(); Set<String> seen = new HashSet<>();
            String href = "", modified = "", etag = ""; long size = -1; boolean collection = false, success = false;
            int responses = 0; boolean multistatus = false;
            for (int event; (event = parser.nextToken()) != XmlPullParser.END_DOCUMENT;) {
                if (event == XmlPullParser.DOCDECL) throw new IOException("WebDAV 返回的 XML 格式不支持");
                if (!"DAV:".equals(parser.getNamespace())) continue;
                if (event == XmlPullParser.START_TAG && parser.getDepth() == 1 && parser.getName().equals("multistatus")) multistatus = true;
                if (event == XmlPullParser.START_TAG) switch (parser.getName()) {
                    case "response" -> { href = ""; modified = ""; etag = ""; size = -1; collection = false; success = false; if (++responses > 5000) throw new IOException("WebDAV 目录中的文件过多"); }
                    case "href" -> href = parser.nextText();
                    case "getlastmodified" -> modified = parser.nextText();
                    case "getetag" -> etag = parser.nextText();
                    case "getcontentlength" -> { try { size = Long.parseLong(parser.nextText()); } catch (NumberFormatException ignored) { size = -1; } }
                    case "collection" -> collection = true;
                    case "status" -> success |= parser.nextText().matches("HTTP/[^ ]+ 2\\d\\d.*");
                    default -> { }
                }
                if (event == XmlPullParser.END_TAG && parser.getName().equals("response") && success && !collection) {
                    HttpUrl url = base.resolve(href.trim());
                    if (url == null || !sameOrigin(base, url) || url.query() != null || url.fragment() != null
                            || !url.username().isEmpty() || !url.password().isEmpty()) continue;
                    List<String> path = url.pathSegments(), parent = base.pathSegments();
                    String name = path.get(path.size() - 1);
                    if (path.size() != parent.size() || !path.subList(0, path.size() - 1).equals(parent.subList(0, parent.size() - 1))
                            || !backupName(name) || !seen.add(url.toString())) continue;
                    entries.add(new Entry(name, url, size, modified, etag));
                }
            }
            if (!multistatus) throw new IOException("WebDAV 返回的列表格式不正确");
            entries.sort(Comparator.comparing(Entry::name).reversed()); return List.copyOf(entries);
        } catch (Exception failure) { throw new IOException("无法读取 WebDAV 备份列表，请检查服务器地址", failure); }
    }
}

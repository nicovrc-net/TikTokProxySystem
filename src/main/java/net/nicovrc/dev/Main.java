package net.nicovrc.dev;

import com.amihaiemil.eoyaml.Yaml;
import com.amihaiemil.eoyaml.YamlMapping;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    private static final Pattern matcher_url = Pattern.compile("(GET|HEAD) /\\?url=(.+) HTTP");
    private static final Pattern matcher_http_version = Pattern.compile("HTTP/1\\.(\\d)");
    private static final Pattern matcher_get_head = Pattern.compile("^(GET|HEAD)");
    private static final Pattern matcher_tiktokUrl = Pattern.compile("tiktok\\.com");

    public static void main(String[] args) {

        try {
            if (!new File("./config.yml").exists()){
                String text = "ProxyServer: ''\n" +
                        "ProxyPort: 3128";

                FileWriter file = new FileWriter("./config.yml");
                PrintWriter pw = new PrintWriter(new BufferedWriter(file));
                pw.print(text);
                pw.close();
                file.close();
            }
        } catch (Exception e){
            e.printStackTrace();
            return;
        }

        final String ProxyIP;
        final int ProxyPort;
        try {
            YamlMapping input = Yaml.createYamlInput(new File("./config.yml")).readYamlMapping();

            ProxyIP = input.string("ProxyServer");
            ProxyPort = input.integer("ProxyPort");
        } catch (Exception e){
            e.printStackTrace();
            return;
        }

        // HTTP通信受け取り
        new Thread(()-> {
            try {
                ServerSocket svSock = new ServerSocket(9999);

                while (true) {
                    Socket sock = svSock.accept();
                    new Thread(() -> {
                        try {
                            final InputStream in = sock.getInputStream();
                            final OutputStream out = sock.getOutputStream();

                            byte[] data = new byte[1000000];
                            int readSize = in.read(data);
                            if (readSize <= 0) {
                                sock.close();
                                return;
                            }
                            data = Arrays.copyOf(data, readSize);

                            final String httpRequest = new String(data, StandardCharsets.UTF_8);
                            System.out.println(httpRequest);

                            String httpVersion = "1.1";
                            Matcher matcher1 = matcher_http_version.matcher(httpRequest);
                            if (matcher1.find()){
                                httpVersion = "1." + matcher1.group(1);
                            }

                            String get = "GET";
                            Matcher matcher2 = matcher_get_head.matcher(httpRequest);
                            if (matcher2.find()){
                                get = matcher2.group(1);
                            }

                            Matcher matcher = matcher_url.matcher(httpRequest);
                            if (!matcher.find()){
                                out.write(("HTTP/"+httpVersion+" 404 Not Found\nContent-Type: text/plain;\n\n").getBytes(StandardCharsets.UTF_8));
                                if (get.equals("GET")){
                                    out.write("404_1".getBytes());
                                }
                                out.flush();

                                in.close();
                                out.close();
                                sock.close();
                                return;
                            }

                            String proxyUrl = URLDecoder.decode(matcher.group(2), StandardCharsets.UTF_8);
                            //System.out.println(proxyUrl);
                            Matcher matcher3 = matcher_tiktokUrl.matcher(proxyUrl);
                            if (!matcher3.find()){
                                out.write(("HTTP/"+httpVersion+" 404 Not Found\nContent-Type: text/plain;\n\n").getBytes(StandardCharsets.UTF_8));
                                if (get.equals("GET")){
                                    out.write("404_2".getBytes());
                                }
                                out.flush();

                                in.close();
                                out.close();
                                sock.close();
                                return;
                            }


                            String[] split = proxyUrl.split("&cookiee=");

                            if (split.length != 2){
                                out.write(("HTTP/"+httpVersion+" 404 Not Found\nContent-Type: text/plain;\n\n").getBytes(StandardCharsets.UTF_8));
                                if (get.equals("GET")){
                                    out.write("404_3".getBytes());
                                }
                                out.flush();

                                in.close();
                                out.close();
                                sock.close();
                                return;
                            }


                            final OkHttpClient.Builder builder = new OkHttpClient.Builder();
                            final OkHttpClient client = !ProxyIP.isEmpty() ? builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ProxyIP, ProxyPort))).build() : new OkHttpClient();
                            final byte[] bytes;

                            //System.out.println(split[0]);
                            //System.out.println(split[1]);

                            Request request = null;
                            if (get.equals("GET")){
                                request = new Request.Builder()
                                        .url(split[0])
                                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0 nicovrc.net/2.0")
                                        .addHeader("Cookie", split[1])
                                        .addHeader("Origin", "https://www.tiktok.com")
                                        .addHeader("Referer", "https://www.tiktok.com/")
                                        .addHeader("Connection", "keep-alive")
                                        .build();
                            } else {
                                request = new Request.Builder()
                                        .url(split[0])
                                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0 nicovrc.net/2.0")
                                        .addHeader("Cookie", split[1])
                                        .addHeader("Origin", "https://www.tiktok.com")
                                        .addHeader("Referer", "https://www.tiktok.com/")
                                        .addHeader("Connection", "keep-alive")
                                        .head()
                                        .build();
                            }
                            Response response = client.newCall(request).execute();
                            int code = response.code();
                            String header = response.header("Content-Type");
                            if (response.body() != null){
                                bytes = response.body().bytes();
                            } else {
                                bytes = null;
                            }
                            response.close();
                            //System.out.println(code);
                            //System.out.println(new String(bytes, StandardCharsets.UTF_8));

                            if (code == 200){
                                out.write(("HTTP/"+httpVersion+" 200 OK\nContent-Type: "+header+";\n\n").getBytes(StandardCharsets.UTF_8));
                                if (bytes != null){
                                    out.write(bytes);
                                }
                            } else {
                                out.write(("HTTP/"+httpVersion+" 404 Not Found\nContent-Type: text/plain;\n\n").getBytes(StandardCharsets.UTF_8));
                                if (get.equals("GET")){
                                    out.write("404".getBytes(StandardCharsets.UTF_8));
                                }
                            }

                            out.flush();
                            in.close();
                            out.close();
                            sock.close();

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
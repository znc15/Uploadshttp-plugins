package com.littlesheep.uploadshttp;
import org.bukkit.plugin.java.JavaPlugin;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.json.simple.JSONObject;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import org.bukkit.command.CommandExecutor;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.net.HttpURLConnection;
import java.net.URL;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
public class Main extends JavaPlugin implements CommandExecutor {
    private LuckPerms luckPerms;
    private HttpServer server;
    private boolean isServerRunning = false;
    @Override
    public void onEnable() {
        // 注册插件的命令
        getCommand("uploadshttp").setExecutor(this);
        // 加载配置文件
        saveDefaultConfig();
        // 从配置文件中读取主机和端口配置
        String host = getConfig().getString("server.host", "127.0.0.1");
        int port = getConfig().getInt("server.port", 9001);
        getLogger().info("==========================================");
        getLogger().info(getDescription().getName());
        getLogger().info("版本: " + getDescription().getVersion());
        getLogger().info("作者: " + getDescription().getAuthors());
        getLogger().info("Github: https://github.com/znc15/uploadshttp");
        getLogger().info("QQ群: 690216634");
        if (getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            getLogger().warning("无LuckPerms插件，请安装！");
            getLogger().info("❛‿˂̵✧");
            getLogger().info("==========================================");
            getServer().getPluginManager().disablePlugin(this);
        } else {
            getLogger().info("检测到LuckPerms插件");
            getLogger().info("插件已经启动成功。");
            getLogger().info("❛‿˂̵✧");
            getLogger().info("==========================================");
            try {
                luckPerms = net.luckperms.api.LuckPermsProvider.get();
                initApiService(host, port);
            } catch (Exception e) {
                getLogger().severe("无法加载LuckPerms!");
                e.printStackTrace();
                getServer().getPluginManager().disablePlugin(this);
            }
        }
    }
    private void initApiService(String host, int port) {
        try {
            server = HttpServer.create(new InetSocketAddress(host, port), 0);
            server.createContext("/", new MyHandler());
            server.setExecutor(null); // 创建默认执行器
            server.start();
            isServerRunning = true;
            getLogger().info("HTTP服务器已启动，地址: " + host + ":" + port);
        } catch (IOException e) {
            getLogger().severe("无法启动HTTP服务器!");
            e.printStackTrace();
        }
    }
    class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String path = t.getRequestURI().getPath();
            if (path.equals("/favicon.ico")) {
                t.sendResponseHeaders(200, 0);
                OutputStream os = t.getResponseBody();
                os.close();
                return;
            }
            // 构建 JSON 对象
            JSONObject jsonResponse = new JSONObject();
            if (path.equals("/") || path.equals("")) {
                jsonResponse.put("status", true);
                jsonResponse.put("message", "Hello, World!");
            } else {
                // 提取用户名
                String username = path.substring(1);
                CompletableFuture<UUID> uuidFuture = getUUIDByUsername(username);
                UUID playerUUID = uuidFuture.join();
                if (playerUUID != null) {
                    // 检查玩家是否在线
                    Player player = Bukkit.getPlayerExact(username);
                    if (player != null && player.isOnline()) {
                        // 获取LuckPerms用户信息
                        User user = luckPerms.getUserManager().getUser(playerUUID);
                        if (user != null) {
                            // 构建JSON响应
                            jsonResponse.put("status", true);
                            jsonResponse.put("username", username);
                            jsonResponse.put("groups", user.getPrimaryGroup());
                            jsonResponse.put("avatarUrl", "https://minotar.net/avatar/" + username);
                        } else {
                            jsonResponse.put("status", false);
                            jsonResponse.put("message", "用户未找到");
                        }
                    } else {
                        jsonResponse.put("status", false);
                        jsonResponse.put("message", "玩家不在线");
                    }
                } else {
                    jsonResponse.put("status", false);
                    jsonResponse.put("message", "用户不存在");
                }
            }
            getLogger().info("收到来自 " + t.getRemoteAddress() + " 的请求，响应: " + jsonResponse.toJSONString());
            t.getResponseHeaders().set("Content-Type", "application/json");
            String response = jsonResponse.toJSONString();
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            t.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = t.getResponseBody();
            os.write(responseBytes);
            os.close();
        }
        private CompletableFuture<UUID> getUUIDByUsername(String username) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");

                    int responseCode = connection.getResponseCode();

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        try (InputStream inputStream = connection.getInputStream()) {
                            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                            JSONParser parser = new JSONParser();
                            JSONObject json = (JSONObject) parser.parse(content);

                            if (json != null && json.containsKey("id")) {
                                String id = json.get("id").toString();
                                return UUID.fromString(id.replaceAll(
                                        "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"
                                ));
                            }
                        }
                    } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                        System.out.println("用户 " + username + " 不存在");
                    } else {
                        System.out.println("获取用户 UUID 时发生错误，HTTP 响应码: " + responseCode);
                    }
                } catch (IOException | ParseException e) {
                    e.printStackTrace();
                }
                return null;
            });
        }
    }

    @Override
        public void onDisable() {
            if (server != null) {
                server.stop(0);
            }
    }
}

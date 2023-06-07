package com.nopvpplugin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NoPvPPlugin extends JavaPlugin implements CommandExecutor, Listener {

    private Map<UUID, Boolean> pvpDisabledPlayers = new HashMap<>();
    private Connection connection;

    @Override
    public void onEnable() {
        // Регистрируем команду и устанавливаем Executor
        getCommand("nopvpadmin").setExecutor(this);

        // Регистрируем слушателя событий
        getServer().getPluginManager().registerEvents(this, this);

        // Устанавливаем соединение с базой данных MySQL
        connectToDatabase();
    }

    @Override
    public void onDisable() {
        // Сохраняем данные о PvP-статусе игроков при выключении плагина
        savePlayerData();

        // Закрываем соединение с базой данных MySQL
        disconnectFromDatabase();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("nopvpadmin")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Эту команду могут использовать только игроки!");
                return true;
            }

            Player player = (Player) sender;

            // Проверяем разрешение на использование команды /nopvpadmin
            if (!player.hasPermission("nopvp.use")) {
                player.sendMessage("У вас нет разрешения на использование этой команды!");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage("Использование команды: /nopvpadmin <игрок> <on|off>");
                return true;
            }

            String targetName = args[0];
            Player target = Bukkit.getPlayer(targetName);

            if (target == null) {
                player.sendMessage("Игрок '" + targetName + "' не найден!");
                return true;
            }

            boolean disablePvp = args[1].equalsIgnoreCase("on");

            // Проверяем разрешение на использование команды /nopvpadmin для других игроков
            if (!player.hasPermission("nopvp.other") && !player.equals(target)) {
                player.sendMessage("У вас нет разрешения на использование этой команды для других игроков!");
                return true;
            }

            // Устанавливаем PvP-статус для игрока
            setPvPDisabled(target, disablePvp);

            String status = disablePvp ? "отключено" : "включено";
            player.sendMessage("PvP для игрока " + target.getName() + " было " + status + ".");

            return true;
        }

        return false;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            Player victim = (Player) event.getEntity();
            Player attacker = (Player) event.getDamager();

            // Проверяем PvP-статус игроков и отбрасываем их при необходимости
            if (isPvPDisabled(victim)) {
                victim.setVelocity(attacker.getLocation().getDirection().multiply(-3));
                event.setCancelled(true);
            } else if (isPvPDisabled(attacker)) {
                attacker.setVelocity(victim.getLocation().getDirection().multiply(-3));
                event.setCancelled(true);
            }
        }
    }

    private boolean isPvPDisabled(Player player) {
        return pvpDisabledPlayers.containsKey(player.getUniqueId()) && pvpDisabledPlayers.get(player.getUniqueId());
    }

    private void setPvPDisabled(Player player, boolean disable) {
        if (disable) {
            pvpDisabledPlayers.put(player.getUniqueId(), true);
        } else {
            pvpDisabledPlayers.remove(player.getUniqueId());
        }

        // Сохраняем PvP-статус игрока в базу данных
        savePlayerData(player.getUniqueId(), disable);
    }

    private void connectToDatabase() {
        String host = "mysql4.joinserver.xyz";
        int port = 3306;
        String database = "s126835_firehvh";
        String username = "u126835_HQgn2MOcgM";
        String password = "I!CO^spoA.2L9HUixjpn@9LP";

        try {
            // Загружаем драйвер JDBC для MySQL
            Class.forName("com.mysql.jdbc.Driver");

            // Устанавливаем соединение с базой данных MySQL
            connection = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + database, username, password);
            createTables();
            loadPlayerData();
        } catch (ClassNotFoundException e) {
            getLogger().severe("Драйвер JDBC для MySQL не найден!");
            e.printStackTrace();
        } catch (SQLException e) {
            getLogger().severe("Ошибка при подключении к базе данных MySQL: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void disconnectFromDatabase() {
        try {
            // Закрываем соединение с базой данных MySQL
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            getLogger().severe("Ошибка при отключении от базы данных MySQL: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTables() throws SQLException {
        PreparedStatement statement = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS pvp_status ("
                        + "player_uuid VARCHAR(36) PRIMARY KEY,"
                        + "disabled BOOLEAN NOT NULL)"
        );
        statement.executeUpdate();
        statement.close();
    }

    private void loadPlayerData() {
        try {
            PreparedStatement statement = connection.prepareStatement("SELECT * FROM pvp_status");
            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                UUID playerUUID = UUID.fromString(resultSet.getString("player_uuid"));
                boolean disabled = resultSet.getBoolean("disabled");

                pvpDisabledPlayers.put(playerUUID, disabled);
            }

            resultSet.close();
            statement.close();
        } catch (SQLException e) {
            getLogger().severe("Ошибка при загрузке данных игроков из базы данных MySQL: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void savePlayerData(UUID playerUUID, boolean disabled) {
        try {
            PreparedStatement statement = connection.prepareStatement(
                    "REPLACE INTO pvp_status (player_uuid, disabled) VALUES (?, ?)"
            );
            statement.setString(1, playerUUID.toString());
            statement.setBoolean(2, disabled);
            statement.executeUpdate();
            statement.close();
        } catch (SQLException e) {
            getLogger().severe("Ошибка при сохранении данных игрока в базу данных MySQL: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void savePlayerData() {
        for (Map.Entry<UUID, Boolean> entry : pvpDisabledPlayers.entrySet()) {
            savePlayerData(entry.getKey(), entry.getValue());
        }
    }
}

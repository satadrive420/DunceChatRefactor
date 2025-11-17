package gg.corn.DunceChat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;

import org.bukkit.entity.Player;

public class UserData {

	public static void updateUser(Player player, boolean updateLogin, boolean updateLogout) {
		//plugin.getConfig().set("users." + player.getUniqueId() + ".name", player.getName());
		try (Connection connection = MySQLHandler.getConnection()) {
			String query = "INSERT INTO users (uuid, display_name) VALUES (?, ?) ON DUPLICATE KEY UPDATE display_name = ?";
			PreparedStatement preparedStatement = connection.prepareStatement(query);
			preparedStatement.setString(1, player.getUniqueId().toString());
			preparedStatement.setString(2, player.getName());
			preparedStatement.setString(3, player.getName());
			preparedStatement.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		if (updateLogin) {
			//plugin.getConfig().set("users." + player.getUniqueId() + ".lastLogin", (new Date()).toString());

			try (Connection connection = MySQLHandler.getConnection()) {
				String query = "INSERT INTO users (uuid, last_login) VALUES (?, ?) ON DUPLICATE KEY UPDATE last_login = ?";
				PreparedStatement preparedStatement = connection.prepareStatement(query);
				preparedStatement.setString(1, player.getUniqueId().toString());
				preparedStatement.setTimestamp(2, new java.sql.Timestamp(new Date().getTime()));
				preparedStatement.setTimestamp(3, new java.sql.Timestamp(new Date().getTime()));
				preparedStatement.executeUpdate();
			} catch (SQLException e) {
				e.printStackTrace();
			}

		}
		if (updateLogout)
			try (Connection connection = MySQLHandler.getConnection()) {
				String query = "INSERT INTO users (uuid, last_logout) VALUES (?, ?) ON DUPLICATE KEY UPDATE last_logout = ?";
				PreparedStatement preparedStatement = connection.prepareStatement(query);
				preparedStatement.setString(1, player.getUniqueId().toString());
				preparedStatement.setTimestamp(2, new java.sql.Timestamp(new Date().getTime()));
				preparedStatement.setTimestamp(3, new java.sql.Timestamp(new Date().getTime()));
				preparedStatement.executeUpdate();
			} catch (SQLException e) {
				e.printStackTrace();
			}
	}

}

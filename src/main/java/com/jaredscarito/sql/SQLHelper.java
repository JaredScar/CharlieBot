package com.jaredscarito.sql;

import java.sql.*;

public class SQLHelper {
    private static SQLHelper instance = null;
    private Connection conn;
    private boolean isConnected = false;
    
    private SQLHelper(String ip, int port, String dbName, String username, String password) throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver").newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
        conn = DriverManager.getConnection(
                "jdbc:mysql://" + ip + ":" + port + "/" + dbName,username,password);
        this.isConnected = true;
    }

    public static synchronized SQLHelper getInstance(String ip, int port, String dbName, String username, String password) throws SQLException {
        if (instance == null) {
            instance = new SQLHelper(ip, port, dbName, username, password);
        }
        // Check if connection is still valid, reconnect if needed
        try {
            if (instance.conn == null || instance.conn.isClosed() || !instance.conn.isValid(2)) {
                if (instance.conn != null && !instance.conn.isClosed()) {
                    instance.conn.close();
                }
                instance.conn = DriverManager.getConnection(
                        "jdbc:mysql://" + ip + ":" + port + "/" + dbName, username, password);
                instance.isConnected = true;
            }
        } catch (SQLException e) {
            // Connection is invalid, create a new one
            if (instance.conn != null && !instance.conn.isClosed()) {
                try {
                    instance.conn.close();
                } catch (SQLException ignored) {}
            }
            instance.conn = DriverManager.getConnection(
                    "jdbc:mysql://" + ip + ":" + port + "/" + dbName, username, password);
            instance.isConnected = true;
        }
        return instance;
    }

    public static SQLHelper getInstance() {
        return instance;
    }

    public Connection getConn() {
        return this.conn;
    }

    public boolean isConnected() {
        try {
            if (conn != null && !conn.isClosed() && conn.isValid(2)) {
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    public void close() {
        try {
            if (this.conn != null && !this.conn.isClosed()) {
                this.conn.close();
            }
            this.isConnected = false;
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public ResultSet runQuery(String query) {
        try (Statement stmt = conn.createStatement()) {
            return stmt.executeQuery(query);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean runStatement(String state) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(state);
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}

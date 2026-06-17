package com.mycompany.sistech.controlador;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;

public class Ctrl_Producto {

    // Clase interna para mapear los datos (Modelo)
    class ProductoObj {
        String codigo, nombre, categoria;
        int stock; 
        double precio;

        public ProductoObj(String c, String n, String cat, int s, double p) {
            codigo = c; nombre = n; categoria = cat; stock = s; precio = p;
        }
    }

    // Datos de conexión centralizados
    private final String url = "jdbc:mysql://localhost/sistemainventario";
    private final String userDB = "root";
    private final String passDB = "";

    // Método para Guardar
    public boolean guardarNuevoProducto(String codigo, String nombre, String categoria, int stock, double precio) {
        String sql = "INSERT INTO productos (codigo, nombre, categoria, stock, precio) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(url, userDB, passDB);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, codigo);
            pstmt.setString(2, nombre);
            pstmt.setString(3, categoria);
            pstmt.setInt(4, stock);
            pstmt.setDouble(5, precio);

            return pstmt.executeUpdate() > 0;

        } catch (Exception e) {
            System.out.println("Error al guardar producto: " + e.getMessage());
            return false;
        }
    }

    // Método para Leer y devolver JSON
    public String obtenerListaProductosJSON() {
        List<ProductoObj> lista = new ArrayList<>();
        String sql = "SELECT codigo, nombre, categoria, stock, precio FROM productos ORDER BY nombre ASC";

        try (Connection conn = DriverManager.getConnection(url, userDB, passDB);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                lista.add(new ProductoObj(
                    rs.getString("codigo"),
                    rs.getString("nombre"),
                    rs.getString("categoria"),
                    rs.getInt("stock"),
                    rs.getDouble("precio")
                ));
            }
        } catch (Exception e) {
            System.out.println("Error al leer productos: " + e.getMessage());
            // Datos de prueba en caso de que falle la BD
            lista.add(new ProductoObj("ERR-00", "Fallo de conexión a BD", "N/A", 0, 0.0));
        }

        return new Gson().toJson(lista);
    }
}
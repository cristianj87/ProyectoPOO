package com.mycompany.sistech.controlador;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;

public class Ctrl_Dashboard {

    private final String url = "jdbc:mysql://localhost/sistemainventario";
    private final String userDB = "root";
    private final String passDB = "";

    // Estructura interna para que Gson construya el JSON exactamente como lo pide Chart.js
    class DatosGrafica {
        List<String> etiquetas = new ArrayList<>();
        List<Double> valores = new ArrayList<>();
    }

    // 1. OBTENER LOS 5 EQUIPOS CON MENOR STOCK
    public String obtenerBajosInventarios() {
        DatosGrafica datos = new DatosGrafica();
        // Ordenamos de menor a mayor y solo tomamos los primeros 5
        String sql = "SELECT nombre, stock FROM productos ORDER BY stock ASC LIMIT 5";

        try (Connection conn = DriverManager.getConnection(url, userDB, passDB);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                // Si el nombre es muy largo, lo recortamos un poco para que quepa en la gráfica
                String nombreCorto = rs.getString("nombre");
                if(nombreCorto.length() > 15) { nombreCorto = nombreCorto.substring(0, 15) + "..."; }
                
                datos.etiquetas.add(nombreCorto);
                datos.valores.add(rs.getDouble("stock"));
            }

            // Si no hay productos en la base de datos, mandamos un aviso a la gráfica
            if (datos.etiquetas.isEmpty()) {
                datos.etiquetas.add("Sin inventario");
                datos.valores.add(0.0);
            }

        } catch (Exception e) {
            System.out.println("Error al obtener stock para dashboard: " + e.getMessage());
        }
        return new Gson().toJson(datos);
    }

    // 2. OBTENER LA SUMA DE VENTAS POR DÍA (Últimos 7 días)
    public String obtenerVentasRecientes() {
        DatosGrafica datos = new DatosGrafica();
        
        // Agrupamos por fecha y sumamos el total. DATE() quita las horas/minutos para agrupar mejor.
        String sql = "SELECT DATE(fecha) as dia, SUM(total) as ingresos FROM ventas GROUP BY DATE(fecha) ORDER BY dia ASC LIMIT 7";

        try (Connection conn = DriverManager.getConnection(url, userDB, passDB);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                datos.etiquetas.add(rs.getString("dia"));
                datos.valores.add(rs.getDouble("ingresos"));
            }

            // Si no se han registrado ventas, mostramos un día en 0
            if (datos.etiquetas.isEmpty()) {
                datos.etiquetas.add("Sin ventas aún");
                datos.valores.add(0.0);
            }

        } catch (Exception e) {
            System.out.println("Error al obtener ventas para dashboard: " + e.getMessage());
        }
        return new Gson().toJson(datos);
    }
}